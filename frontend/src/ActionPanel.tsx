import React, { useEffect, useState } from 'react'
import { ActionProposal, approveAction, getControlRoomState, proposeAction, ReasoningSnapshot } from './api'

const ACTIONS = [
    { value: 'NOTIFY_SHIFT_LEAD', label: 'Notify shift lead' },
    { value: 'ESCALATE_VENDOR', label: 'Escalate vendor' }
] as const

function friendlyError(error: unknown) {
    const raw = error instanceof Error ? error.message : String(error)
    if (raw.includes('Evidence changed')) return 'Evidence changed after proposal. Generate a new proposal before approval.'
    if (raw.includes('Idempotency key')) return 'This execution token belongs to a different proposal. Start a new action.'
    return raw
}

export function ActionPanel() {
    const [reason, setReason] = useState<ReasoningSnapshot | null>(null)
    const [actionType, setActionType] = useState<(typeof ACTIONS)[number]['value']>('NOTIFY_SHIFT_LEAD')
    const [proposal, setProposal] = useState<ActionProposal | null>(null)
    const [receipt, setReceipt] = useState<{ status: string; externalReference: string | null } | null>(null)
    const [busy, setBusy] = useState(false)
    const [error, setError] = useState<string | null>(null)

    useEffect(() => {
        let disposed = false
        const refresh = async () => {
            try {
                const state = await getControlRoomState()
                if (!disposed) setReason(state.reason)
            } catch {
                // The main control-room connection already exposes degraded state.
            }
        }
        void refresh()
        const timer = window.setInterval(refresh, 1500)
        return () => { disposed = true; window.clearInterval(timer) }
    }, [])

    useEffect(() => {
        if (proposal && reason?.situationId !== proposal.situationId) {
            setProposal(null)
            setReceipt(null)
            setError(null)
        }
    }, [reason?.situationId, proposal])

    async function propose() {
        if (!reason) return
        setBusy(true)
        setError(null)
        setReceipt(null)
        try {
            setProposal(await proposeAction(reason.situationId, actionType))
        } catch (e) {
            setError(friendlyError(e))
        } finally {
            setBusy(false)
        }
    }

    async function approve() {
        if (!proposal) return
        setBusy(true)
        setError(null)
        try {
            const token = `moveiq-ui:${proposal.id}`
            const result = await approveAction(proposal.id, 'control-room-operator', token)
            setReceipt({ status: result.status, externalReference: result.externalReference })
        } catch (e) {
            setError(friendlyError(e))
        } finally {
            setBusy(false)
        }
    }

    return (
        <article className={`stage-card stage-act ${reason ? 'stage-active' : 'stage-waiting'}`}>
            <div className="stage-heading">
                <span>ACT</span>
                <span className={`stage-status ${receipt ? 'status-detected' : ''}`}>
                    {receipt ? 'EXECUTED' : proposal ? 'AWAITING APPROVAL' : reason ? 'READY' : 'WAITING'}
                </span>
            </div>
            {!reason ? <p className="waiting-copy">Waiting for grounded REASON state.</p> : <>
                <p><strong>{reason.recommendation.split('_').join(' ')}</strong></p>
                <small>Recommendation is evidence-derived. Execution below is explicitly simulated.</small>
                {!proposal && !receipt && <div className="act-controls">
                    <label>Operator action
                        <select value={actionType} onChange={event => setActionType(event.target.value as typeof actionType)} disabled={busy}>
                            {ACTIONS.map(action => <option key={action.value} value={action.value}>{action.label}</option>)}
                        </select>
                    </label>
                    <button onClick={propose} disabled={busy}>PROPOSE ACTION</button>
                </div>}
                {proposal && !receipt && <div className="act-approval">
                    <span>Proposal <code>{proposal.id.slice(0, 8)}</code></span>
                    <strong>{proposal.actionType.split('_').join(' ')}</strong>
                    <small>Evidence will be revalidated immediately before execution.</small>
                    <button onClick={approve} disabled={busy}>APPROVE &amp; EXECUTE</button>
                </div>}
                {receipt && <div className="act-receipt">
                    <strong>{receipt.status}</strong>
                    <span>SIMULATED EXECUTOR</span>
                    <code>{receipt.externalReference ?? 'No external reference'}</code>
                </div>}
                {error && <p className="control-error">{error}</p>}
            </>}
        </article>
    )
}
