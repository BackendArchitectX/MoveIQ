import React, {
    useEffect,
    useMemo,
    useRef,
    useState
} from 'react'
import ReactDOM from 'react-dom/client'
import {
    loadTraceHistory,
    OperationTraceEvent,
    TraceStage
} from './live'
import './styles.css'

type ConnectionState =
    | 'CONNECTING'
    | 'SYNCING'
    | 'LIVE'
    | 'RECONNECTING'
    | 'DEGRADED'

const STAGES: TraceStage[] = [
    'SENSE',
    'REASON',
    'ACT',
    'VERIFY'
]

function formatTime(value: string) {
    const date = new Date(value)

    if (Number.isNaN(date.getTime())) {
        return value
    }

    return date.toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    })
}

function formatEvidence(
    evidence: Record<string, unknown>
) {
    return Object.entries(evidence)
        .map(([key, value]) => `${key}: ${String(value)}`)
        .join(' · ')
}

function App() {
    const [traces, setTraces] = useState<
        OperationTraceEvent[]
    >([])

    const [connection, setConnection] =
        useState<ConnectionState>('CONNECTING')

    const [followLive, setFollowLive] = useState(true)

    const [selectedSequence, setSelectedSequence] =
        useState<number | null>(null)

    const lastSequence = useRef(0)
    const buffering = useRef(true)

    const pendingLive = useRef<
        OperationTraceEvent[]
    >([])

    function merge(
        incoming: OperationTraceEvent[]
    ) {
        if (incoming.length === 0) {
            return
        }

        const maxSequence = Math.max(
            ...incoming.map(item => item.sequence)
        )

        lastSequence.current = Math.max(
            lastSequence.current,
            maxSequence
        )

        setTraces(current => {
            const bySequence = new Map<
                number,
                OperationTraceEvent
            >()

            current.forEach(item =>
                bySequence.set(item.sequence, item)
            )

            incoming.forEach(item =>
                bySequence.set(item.sequence, item)
            )

            return [...bySequence.values()].sort(
                (a, b) => a.sequence - b.sequence
            )
        })
    }

    useEffect(() => {
        let disposed = false

        const source = new EventSource(
            '/api/v1/live/stream'
        )

        const handleConnected = async () => {
            if (disposed) {
                return
            }

            setConnection('SYNCING')
            buffering.current = true

            try {
                /*
                 * Important:
                 *
                 * SSE is already connected before history is read.
                 * Any trace arriving while history loads is buffered.
                 *
                 * Therefore there is no:
                 *
                 * history query
                 *      ↓
                 * event commits here and disappears
                 *      ↓
                 * SSE connects
                 */

                const history = await loadTraceHistory(
                    lastSequence.current
                )

                if (disposed) {
                    return
                }

                const buffered = pendingLive.current
                pendingLive.current = []

                merge([
                    ...history,
                    ...buffered
                ])

                buffering.current = false
                setConnection('LIVE')
            } catch (error) {
                console.error(
                    'Trace catch-up failed',
                    error
                )

                buffering.current = false
                setConnection('DEGRADED')
            }
        }

        const handleTrace = (event: Event) => {
            const message = event as MessageEvent

            try {
                const trace =
                    JSON.parse(
                        message.data
                    ) as OperationTraceEvent

                if (buffering.current) {
                    pendingLive.current.push(trace)
                    return
                }

                merge([trace])
            } catch (error) {
                console.error(
                    'Invalid trace SSE payload',
                    error
                )
            }
        }

        source.addEventListener(
            'connected',
            handleConnected
        )

        source.addEventListener(
            'trace',
            handleTrace
        )

        source.onerror = () => {
            if (!disposed) {
                setConnection('RECONNECTING')
            }
        }

        return () => {
            disposed = true
            source.close()
        }
    }, [])

    const latest =
        traces.length > 0
            ? traces[traces.length - 1]
            : null

    useEffect(() => {
        if (
            followLive &&
            latest &&
            selectedSequence !== latest.sequence
        ) {
            setSelectedSequence(latest.sequence)
        }
    }, [
        followLive,
        latest,
        selectedSequence
    ])

    const selected =
        traces.find(
            trace =>
                trace.sequence === selectedSequence
        ) ??
        latest

    const latestByStage = useMemo(() => {
        const map =
            new Map<
                TraceStage,
                OperationTraceEvent
            >()

        traces.forEach(trace => {
            map.set(trace.stage, trace)
        })

        return map
    }, [traces])

    const visibleTape = useMemo(
        () =>
            [...traces]
                .reverse()
                .slice(0, 50),
        [traces]
    )

    return (
        <main className="shell">
            <header className="topbar">
                <div>
                    <div className="brand-row">
            <span className="brand">
              MOVEIQ
            </span>

                        <span
                            className={`connection connection-${connection.toLowerCase()}`}
                        >
              <span className="pulse-dot" />
                            {connection}
            </span>
                    </div>

                    <h1>
                        Mobility Operations Control Room
                    </h1>

                    <p className="subtitle">
                        Backend-generated decision trace.
                        No simulated browser state.
                    </p>
                </div>

                <div className="sequence-box">
                    <span>TRACE SEQUENCE</span>
                    <strong>
                        {lastSequence.current || '—'}
                    </strong>
                </div>
            </header>

            <section className="stage-grid">
                {STAGES.map(stage => {
                    const event =
                        latestByStage.get(stage)

                    return (
                        <article
                            key={stage}
                            className={`stage-card stage-${stage.toLowerCase()} ${
                                event
                                    ? 'stage-active'
                                    : 'stage-waiting'
                            }`}
                        >
                            <div className="stage-heading">
                                <span>{stage}</span>

                                <span className="stage-status">
                  {event
                      ? 'BACKEND EVENT'
                      : 'WAITING'}
                </span>
                            </div>

                            {event ? (
                                <>
                                    <strong className="event-type">
                                        {event.eventType}
                                    </strong>

                                    <p>{event.summary}</p>

                                    <small>
                                        #{event.sequence}
                                        {' · '}
                                        {formatTime(
                                            event.recordedAt
                                        )}
                                    </small>
                                </>
                            ) : (
                                <p className="waiting-copy">
                                    Waiting for a durable{' '}
                                    {stage} event.
                                </p>
                            )}
                        </article>
                    )
                })}
            </section>

            <section className="workspace">
                <div className="event-tape">
                    <div className="panel-heading">
                        <div>
              <span className="panel-kicker">
                LIVE EVENT TAPE
              </span>

                            <h2>
                                Durable decision stream
                            </h2>
                        </div>

                        <button
                            className={
                                followLive
                                    ? 'follow active'
                                    : 'follow'
                            }
                            onClick={() =>
                                setFollowLive(value => !value)
                            }
                        >
                            {followLive
                                ? '● Following live'
                                : 'Follow live'}
                        </button>
                    </div>

                    <div className="tape-list">
                        {visibleTape.length === 0 && (
                            <div className="empty">
                                Waiting for operation
                                traces...
                            </div>
                        )}

                        {visibleTape.map(trace => (
                            <button
                                key={trace.sequence}
                                className={`trace-row trace-${trace.stage.toLowerCase()} ${
                                    selected?.sequence ===
                                    trace.sequence
                                        ? 'selected'
                                        : ''
                                }`}
                                onClick={() => {
                                    setFollowLive(false)
                                    setSelectedSequence(
                                        trace.sequence
                                    )
                                }}
                            >
                <span className="trace-sequence">
                  #{trace.sequence}
                </span>

                                <span
                                    className={`stage-pill pill-${trace.stage.toLowerCase()}`}
                                >
                  {trace.stage}
                </span>

                                <span className="trace-time">
                  {formatTime(
                      trace.recordedAt
                  )}
                </span>

                                <span className="trace-content">
                  <strong>
                    {trace.eventType}
                  </strong>

                  <span>
                    {trace.summary}
                  </span>
                </span>
                            </button>
                        ))}
                    </div>
                </div>

                <aside className="evidence-panel">
          <span className="panel-kicker">
            SELECTED EVIDENCE
          </span>

                    {selected ? (
                        <>
                            <h2>
                                {selected.eventType}
                            </h2>

                            <div className="detail-grid">
                                <div>
                                    <span>Stage</span>
                                    <strong>
                                        {selected.stage}
                                    </strong>
                                </div>

                                <div>
                                    <span>Sequence</span>
                                    <strong>
                                        #{selected.sequence}
                                    </strong>
                                </div>

                                <div>
                                    <span>Event time</span>
                                    <strong>
                                        {formatTime(
                                            selected.eventTime
                                        )}
                                    </strong>
                                </div>

                                <div>
                                    <span>Recorded</span>
                                    <strong>
                                        {formatTime(
                                            selected.recordedAt
                                        )}
                                    </strong>
                                </div>
                            </div>

                            <div className="evidence-block">
                                <span>Situation</span>
                                <code>
                                    {selected.situationId ??
                                        'Not created yet'}
                                </code>
                            </div>

                            <div className="evidence-block">
                                <span>Scope</span>
                                <code>
                                    {selected.scopeKey ??
                                        '—'}
                                </code>
                            </div>

                            <div className="evidence-block">
                                <span>Source event</span>
                                <code>
                                    {selected.sourceEventId ??
                                        '—'}
                                </code>
                            </div>

                            <div className="evidence-block">
                                <span>Evidence</span>
                                <p>
                                    {formatEvidence(
                                        selected.evidence
                                    ) || 'No evidence payload'}
                                </p>
                            </div>
                        </>
                    ) : (
                        <p>
                            No trace selected.
                        </p>
                    )}
                </aside>
            </section>
        </main>
    )
}

ReactDOM.createRoot(
    document.getElementById('root')!
).render(<App />)