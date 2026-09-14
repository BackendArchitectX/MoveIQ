import { useEffect, useState } from 'react'
import { getControlRoomState, ReasoningSnapshot } from './api'

function fmt(value: number | null, suffix = '') {
    return value == null ? '—' : `${value.toFixed(1)}${suffix}`
}

export function ReasonPanel() {
    const [reason, setReason] = useState<ReasoningSnapshot | null>(null)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        let disposed = false
        let timer: number | undefined

        const refresh = async () => {
            try {
                const state = await getControlRoomState()
                if (!disposed) {
                    setReason(state.reason)
                    setError(null)
                }
            } catch (e) {
                if (!disposed) setError(e instanceof Error ? e.message : String(e))
            } finally {
                if (!disposed) timer = window.setTimeout(refresh, 1000)
            }
        }

        void refresh()
        return () => {
            disposed = true
            if (timer !== undefined) window.clearTimeout(timer)
        }
    }, [])

    return (
        <article className={`stage-card stage-reason ${reason ? 'stage-active' : 'stage-waiting'}`}>
            <div className="stage-heading">
                <span>REASON</span>
                <span className="stage-status">{reason ? `${reason.trustStatus} TRUST` : error ? 'DEGRADED' : 'WAITING'}</span>
            </div>
            {reason ? (
                <>
                    <strong className="event-type">{reason.recommendation.split('_').join(' ')}</strong>
                    <div className="sense-metrics">
                        <div><span>Current delay</span><strong>{fmt(reason.currentAvgDelay, 'm')}</strong></div>
                        <div><span>Baseline</span><strong>{fmt(reason.baselineAvgDelay, 'm')}</strong></div>
                        <div><span>Delta</span><strong>{fmt(reason.deltaPct, '%')}</strong></div>
                        <div><span>Coverage</span><strong>{fmt(reason.coveragePct, '%')}</strong></div>
                    </div>
                    <p>{reason.currentSampleSize} current trips · {reason.baselineSampleSize} baseline trips</p>
                    <small>{reason.methodologyVersion} · cutoff {new Date(reason.eventTime).toLocaleTimeString()}</small>
                </>
            ) : (
                <p className="waiting-copy">{error ?? 'Waiting for a threshold-crossing signal and deterministic context.'}</p>
            )}
        </article>
    )
}
