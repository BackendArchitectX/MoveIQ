import React, { useEffect, useMemo, useRef, useState } from 'react'
import ReactDOM from 'react-dom/client'
import { ReplayPanel } from './ReplayPanel'
import { loadTraceHistory, OperationTraceEvent, TraceStage } from './live'
import './styles.css'

type ConnectionState = 'CONNECTING' | 'SYNCING' | 'LIVE' | 'RECONNECTING' | 'DEGRADED'

const STAGES: TraceStage[] = ['SENSE', 'REASON', 'ACT', 'VERIFY']

function formatTime(value: string) {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function formatEvidence(evidence: Record<string, unknown>) {
    return Object.entries(evidence).map(([key, value]) => `${key}: ${String(value)}`).join(' · ')
}

function evidenceNumber(event: OperationTraceEvent | undefined, key: string) {
    const value = event?.evidence[key]
    if (typeof value === 'number' && Number.isFinite(value)) return value
    if (typeof value === 'string') {
        const parsed = Number(value)
        if (Number.isFinite(parsed)) return parsed
    }
    return null
}

function evidenceBoolean(event: OperationTraceEvent | undefined, key: string) {
    const value = event?.evidence[key]
    return value === true || value === 'true'
}

function SenseCard({ windowEvent, fallbackEvent }: { windowEvent?: OperationTraceEvent; fallbackEvent?: OperationTraceEvent }) {
    const event = windowEvent ?? fallbackEvent
    const count = evidenceNumber(windowEvent, 'windowEventCount')
    const threshold = evidenceNumber(windowEvent, 'threshold')
    const affected = evidenceNumber(windowEvent, 'affectedEmployees')
    const delay = evidenceNumber(windowEvent, 'delayMinutes')
    const crossed = evidenceBoolean(windowEvent, 'thresholdCrossed')
    const included = evidenceBoolean(windowEvent, 'includedInWindow')
    const progress = count != null && threshold != null && threshold > 0 ? Math.min(100, Math.max(0, (count / threshold) * 100)) : 0
    const steps = threshold && threshold > 0 ? Math.min(Math.floor(threshold), 12) : 0

    return (
        <article className={`stage-card stage-sense ${event ? 'stage-active' : 'stage-waiting'}`}>
            <div className="stage-heading">
                <span>SENSE</span>
                <span className={`stage-status ${crossed ? 'status-detected' : ''}`}>{crossed ? 'THRESHOLD CROSSED' : event ? 'SENSING' : 'WAITING'}</span>
            </div>
            {windowEvent && count != null && threshold != null ? (
                <>
                    <div className="sense-window-row">
                        <div><span className="sense-label">SLIDING WINDOW</span><strong className="sense-count">{count} / {threshold}</strong></div>
                        <span className="sense-time">{formatTime(windowEvent.recordedAt)}</span>
                    </div>
                    <div className="sense-progress" role="progressbar" aria-label="Detection threshold progress" aria-valuemin={0} aria-valuemax={threshold} aria-valuenow={count}>
                        <span style={{ width: `${progress}%` }} />
                    </div>
                    {steps > 0 && <div className="sense-steps" aria-hidden="true">{Array.from({ length: steps }).map((_, index) => <span key={index} className={index < count ? 'sense-step active' : 'sense-step'} />)}</div>}
                    <div className="sense-metrics">
                        <div><span>Affected</span><strong>{affected ?? '—'}</strong></div>
                        <div><span>Delay evidence</span><strong>{delay == null ? '—' : `${delay}m`}</strong></div>
                    </div>
                    <p className="sense-scope" title={windowEvent.scopeKey ?? undefined}>{windowEvent.scopeKey ?? 'Unknown scope'}</p>
                    {!included && <small className="sense-late">Latest accepted event was outside the active event-time window.</small>}
                </>
            ) : event ? (
                <><strong className="event-type">{event.eventType}</strong><p>{event.summary}</p><small>#{event.sequence} · {formatTime(event.recordedAt)}</small></>
            ) : <p className="waiting-copy">Waiting for backend detection state.</p>}
        </article>
    )
}

function App() {
    const [traces, setTraces] = useState<OperationTraceEvent[]>([])
    const [connection, setConnection] = useState<ConnectionState>('CONNECTING')
    const [followLive, setFollowLive] = useState(true)
    const [selectedSequence, setSelectedSequence] = useState<number | null>(null)
    const lastSequence = useRef(0)
    const buffering = useRef(true)
    const pendingLive = useRef<OperationTraceEvent[]>([])

    function merge(incoming: OperationTraceEvent[]) {
        if (incoming.length === 0) return
        lastSequence.current = Math.max(lastSequence.current, ...incoming.map(item => item.sequence))
        setTraces(current => {
            const bySequence = new Map<number, OperationTraceEvent>()
            current.forEach(item => bySequence.set(item.sequence, item))
            incoming.forEach(item => bySequence.set(item.sequence, item))
            return [...bySequence.values()].sort((a, b) => a.sequence - b.sequence)
        })
    }

    useEffect(() => {
        let disposed = false
        const source = new EventSource('/api/v1/live/stream')

        const connected = async () => {
            if (disposed) return
            setConnection('SYNCING')
            buffering.current = true
            try {
                const history = await loadTraceHistory(lastSequence.current)
                if (disposed) return
                const buffered = pendingLive.current
                pendingLive.current = []
                merge([...history, ...buffered])
                buffering.current = false
                setConnection('LIVE')
            } catch (error) {
                console.error('Trace catch-up failed', error)
                buffering.current = false
                setConnection('DEGRADED')
            }
        }

        const trace = (event: Event) => {
            try {
                const parsed = JSON.parse((event as MessageEvent).data) as OperationTraceEvent
                if (buffering.current) pendingLive.current.push(parsed)
                else merge([parsed])
            } catch (error) {
                console.error('Invalid trace SSE payload', error)
            }
        }

        source.addEventListener('connected', connected)
        source.addEventListener('trace', trace)
        source.onerror = () => { if (!disposed) setConnection('RECONNECTING') }
        return () => { disposed = true; source.close() }
    }, [])

    const latest = traces.length ? traces[traces.length - 1] : null
    useEffect(() => {
        if (followLive && latest && selectedSequence !== latest.sequence) setSelectedSequence(latest.sequence)
    }, [followLive, latest, selectedSequence])

    const selected = traces.find(item => item.sequence === selectedSequence) ?? latest
    const latestByStage = useMemo(() => {
        const map = new Map<TraceStage, OperationTraceEvent>()
        traces.forEach(item => map.set(item.stage, item))
        return map
    }, [traces])
    const latestWindow = useMemo(() => [...traces].reverse().find(item => item.stage === 'SENSE' && item.eventType === 'WINDOW_UPDATED'), [traces])
    const visibleTape = useMemo(() => [...traces].reverse().slice(0, 50), [traces])

    return (
        <main className="shell">
            <header className="topbar">
                <div>
                    <div className="brand-row"><span className="brand">MOVEIQ</span><span className={`connection connection-${connection.toLowerCase()}`}><span className="pulse-dot" />{connection}</span></div>
                    <h1>Mobility Operations Control Room</h1>
                    <p className="subtitle">Backend-generated decision state. No simulated browser metrics.</p>
                </div>
                <div className="sequence-box"><span>TRACE SEQUENCE</span><strong>{lastSequence.current || '—'}</strong></div>
            </header>

            <ReplayPanel />

            <section className="stage-grid">
                {STAGES.map(stage => {
                    const event = latestByStage.get(stage)
                    if (stage === 'SENSE') return <SenseCard key={stage} windowEvent={latestWindow} fallbackEvent={event} />
                    return (
                        <article key={stage} className={`stage-card stage-${stage.toLowerCase()} ${event ? 'stage-active' : 'stage-waiting'}`}>
                            <div className="stage-heading"><span>{stage}</span><span className="stage-status">{event ? 'BACKEND EVENT' : 'WAITING'}</span></div>
                            {event ? <><strong className="event-type">{event.eventType}</strong><p>{event.summary}</p><small>#{event.sequence} · {formatTime(event.recordedAt)}</small></> : <p className="waiting-copy">Waiting for a durable {stage} event.</p>}
                        </article>
                    )
                })}
            </section>

            <section className="workspace">
                <div className="event-tape">
                    <div className="panel-heading">
                        <div><span className="panel-kicker">LIVE EVENT TAPE</span><h2>Durable decision stream</h2></div>
                        <button className={followLive ? 'follow active' : 'follow'} onClick={() => setFollowLive(value => !value)}>{followLive ? '● Following live' : 'Follow live'}</button>
                    </div>
                    <div className="tape-list">
                        {visibleTape.length === 0 && <div className="empty">Waiting for operation traces...</div>}
                        {visibleTape.map(item => (
                            <button key={item.sequence} className={`trace-row trace-${item.stage.toLowerCase()} ${selected?.sequence === item.sequence ? 'selected' : ''}`} onClick={() => { setFollowLive(false); setSelectedSequence(item.sequence) }}>
                                <span className="trace-sequence">#{item.sequence}</span><span className={`stage-pill pill-${item.stage.toLowerCase()}`}>{item.stage}</span><span className="trace-time">{formatTime(item.recordedAt)}</span><span className="trace-content"><strong>{item.eventType}</strong><span>{item.summary}</span></span>
                            </button>
                        ))}
                    </div>
                </div>

                <aside className="evidence-panel">
                    <span className="panel-kicker">SELECTED EVIDENCE</span>
                    {selected ? <>
                        <h2>{selected.eventType}</h2>
                        <div className="detail-grid">
                            <div><span>Stage</span><strong>{selected.stage}</strong></div><div><span>Sequence</span><strong>#{selected.sequence}</strong></div>
                            <div><span>Event time</span><strong>{formatTime(selected.eventTime)}</strong></div><div><span>Recorded</span><strong>{formatTime(selected.recordedAt)}</strong></div>
                        </div>
                        <div className="evidence-block"><span>Situation</span><code>{selected.situationId ?? 'Not created yet'}</code></div>
                        <div className="evidence-block"><span>Scope</span><code>{selected.scopeKey ?? '—'}</code></div>
                        <div className="evidence-block"><span>Source event</span><code>{selected.sourceEventId ?? '—'}</code></div>
                        <div className="evidence-block"><span>Evidence</span><p>{formatEvidence(selected.evidence) || 'No evidence payload'}</p></div>
                    </> : <p>No trace selected.</p>}
                </aside>
            </section>
        </main>
    )
}

ReactDOM.createRoot(document.getElementById('root')!).render(<App />)
