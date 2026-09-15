import { useEffect, useState } from 'react'
import { getControlRoomState, VerificationSnapshot } from './api'

function fmt(value: number | null, suffix = '') {
    return value == null ? '—' : `${value.toFixed(1)}${suffix}`
}

export function VerifyPanel() {
    const [verify, setVerify] = useState<VerificationSnapshot | null>(null)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        let disposed = false
        let timer: number | undefined
        const refresh = async () => {
            try {
                const state = await getControlRoomState()
                if (!disposed) { setVerify(state.verify); setError(null) }
            } catch (e) {
                if (!disposed) setError(e instanceof Error ? e.message : String(e))
            } finally {
                if (!disposed) timer = window.setTimeout(refresh, 1000)
            }
        }
        void refresh()
        return () => { disposed = true; if (timer !== undefined) window.clearTimeout(timer) }
    }, [])

    return (
        <article className={`stage-card stage-verify ${verify ? 'stage-active' : 'stage-waiting'}`}>
            <div className="stage-heading">
                <span>VERIFY</span>
                <span className="stage-status">{verify ? verify.outcome.split('_').join(' ') : error ? 'DEGRADED' : 'WAITING'}</span>
            </div>
            {verify ? <>
                <strong className="event-type">OBSERVATIONAL OUTCOME</strong>
                <div className="sense-metrics">
                    <div><span>Before action</span><strong>{fmt(verify.baselineAvgDelay, 'm')}</strong></div>
                    <div><span>Observed after</span><strong>{fmt(verify.observedAvgDelay, 'm')}</strong></div>
                    <div><span>Change</span><strong>{fmt(verify.changePct, '%')}</strong></div>
                    <div><span>Samples</span><strong>{verify.observedSampleSize}</strong></div>
                </div>
                <p>Observed after the action marker; historical replay does not establish causality.</p>
                <small>{verify.methodologyVersion} · cutoff {new Date(verify.baselineEventTime).toLocaleTimeString()}</small>
            </> : <p className="waiting-copy">{error ?? 'Waiting for an executed action and post-action evidence.'}</p>}
        </article>
    )
}
