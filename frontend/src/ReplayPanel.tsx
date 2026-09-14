import { useEffect, useMemo, useState } from 'react'
import {
    getReplayState,
    pauseReplay,
    ReplayState,
    resumeReplay,
    setReplaySpeed,
    startReplay,
    stopReplay
} from './api'

const SPEEDS = [1, 5, 20, 100]

function formatReplayTime(value: string | null) {
    if (!value) return '—'
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

export function ReplayPanel() {
    const [state, setState] = useState<ReplayState | null>(null)
    const [speed, setSpeed] = useState(20)
    const [error, setError] = useState<string | null>(null)
    const [busy, setBusy] = useState(false)

    async function refresh() {
        try {
            const next = await getReplayState()
            setState(next)
            setSpeed(next.speed)
            setError(null)
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : String(cause))
        }
    }

    useEffect(() => {
        void refresh()
        const timer = window.setInterval(() => void refresh(), 1000)
        return () => window.clearInterval(timer)
    }, [])

    async function run(operation: () => Promise<ReplayState>) {
        setBusy(true)
        setError(null)
        try {
            const next = await operation()
            setState(next)
            setSpeed(next.speed)
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : String(cause))
        } finally {
            setBusy(false)
        }
    }

    const progress = useMemo(() => {
        if (!state || state.total <= 0) return 0
        return Math.min(100, (state.processed / state.total) * 100)
    }, [state])

    const status = state?.status ?? 'LOADING'
    const running = status === 'RUNNING'
    const paused = status === 'PAUSED'

    return (
        <section className="stage-card stage-active" style={{ marginBottom: 18 }}>
            <div className="stage-heading">
                <span>REAL DATA · REPLAY</span>
                <span className="stage-status">{status}</span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 12, margin: '14px 0' }}>
                <div><small>PROCESSED</small><strong style={{ display: 'block' }}>{state ? state.processed.toLocaleString() : '—'} / {state ? state.total.toLocaleString() : '—'}</strong></div>
                <div><small>REPLAY CLOCK</small><strong style={{ display: 'block' }}>{formatReplayTime(state?.replayTime ?? null)}</strong></div>
                <div><small>THROUGHPUT</small><strong style={{ display: 'block' }}>{state ? `${state.eventsPerSecond.toFixed(1)} evt/s` : '—'}</strong></div>
                <div><small>SPEED</small><strong style={{ display: 'block' }}>{state ? `${state.speed}×` : `${speed}×`}</strong></div>
            </div>

            <div className="sense-progress" role="progressbar" aria-label="Replay progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}>
                <span style={{ width: `${progress}%` }} />
            </div>

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center', marginTop: 14 }}>
                <button disabled={busy || running || paused} onClick={() => void run(() => startReplay(speed))}>START</button>
                <button disabled={busy || !running} onClick={() => void run(pauseReplay)}>PAUSE</button>
                <button disabled={busy || !paused} onClick={() => void run(resumeReplay)}>RESUME</button>
                <button disabled={busy || (!running && !paused)} onClick={() => void run(stopReplay)}>STOP</button>

                <label style={{ marginLeft: 8 }}>
                    <span style={{ marginRight: 6 }}>Speed</span>
                    <select
                        value={speed}
                        disabled={busy}
                        onChange={event => {
                            const next = Number(event.target.value)
                            setSpeed(next)
                            if (running || paused) void run(() => setReplaySpeed(next))
                        }}
                    >
                        {SPEEDS.map(value => <option key={value} value={value}>{value}×</option>)}
                    </select>
                </label>
            </div>

            {state?.lastError && <p className="sense-late">Replay failure: {state.lastError}</p>}
            {error && <p className="sense-late">Control error: {error}</p>}
            <small style={{ display: 'block', marginTop: 10 }}>
                Backend-driven event-time replay. Browser controls do not synthesize mobility events.
            </small>
        </section>
    )
}
