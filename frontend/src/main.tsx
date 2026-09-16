import React, {
    useEffect,
    useMemo,
    useRef,
    useState
} from 'react'
import ReactDOM from 'react-dom/client'

import {
    getCurrentDecisionDossier
} from './api'

import type {
    DecisionDossier
} from './api'

import {
    ActionPanel,
    ReasonPanel,
    SensePanel,
    VerifyPanel
} from './DecisionPanels'

import { ReplayPanel } from './ReplayPanel'

import {
    loadTraceHistory
} from './live'

import type {
    OperationTraceEvent
} from './live'

import './styles.css'

type ConnectionState =
    | 'CONNECTING'
    | 'SYNCING'
    | 'LIVE'
    | 'RECONNECTING'
    | 'DEGRADED'

function formatTime(value: string) {
    const date = new Date(value)

    if (Number.isNaN(date.getTime())) {
        return value
    }

    return date.toLocaleTimeString(
        [],
        {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        }
    )
}

function formatEvidence(
    evidence: Record<string, unknown>
) {
    return Object.entries(evidence)
        .map(([key, value]) => {
            if (
                value !== null &&
                typeof value === 'object'
            ) {
                return `${key}: ${JSON.stringify(value)}`
            }

            return `${key}: ${String(value)}`
        })
        .join(' · ')
}

function App() {
    const [
        connection,
        setConnection
    ] = useState<ConnectionState>(
        'CONNECTING'
    )

    const [
        transportSequence,
        setTransportSequence
    ] = useState(0)

    const [
        dossier,
        setDossier
    ] = useState<DecisionDossier | null>(
        null
    )

    const [
        dossierError,
        setDossierError
    ] = useState<string | null>(
        null
    )

    const [
        followLive,
        setFollowLive
    ] = useState(true)

    const [
        selectedSnapshot,
        setSelectedSnapshot
    ] = useState<OperationTraceEvent | null>(
        null
    )

    const lastSequence =
        useRef(0)

    const buffering =
        useRef(true)

    const pendingLive =
        useRef<OperationTraceEvent[]>([])

    const syncGeneration =
        useRef(0)

    function observe(
        incoming: OperationTraceEvent[]
    ) {
        if (incoming.length === 0) {
            return
        }

        const maximum =
            Math.max(
                lastSequence.current,
                ...incoming.map(
                    item => item.sequence
                )
            )

        lastSequence.current =
            maximum

        setTransportSequence(
            maximum
        )
    }

    useEffect(() => {
        let disposed = false

        const source =
            new EventSource(
                '/api/v1/live/stream'
            )

        const connected =
            async () => {
                if (disposed) {
                    return
                }

                const generation =
                    ++syncGeneration.current

                setConnection(
                    'SYNCING'
                )

                buffering.current =
                    true

                try {
                    const history =
                        await loadTraceHistory(
                            lastSequence.current
                        )

                    if (
                        disposed ||
                        generation !==
                        syncGeneration.current
                    ) {
                        return
                    }

                    const buffered =
                        pendingLive.current

                    pendingLive.current =
                        []

                    observe([
                        ...history,
                        ...buffered
                    ])

                    buffering.current =
                        false

                    setConnection(
                        'LIVE'
                    )
                } catch (error) {
                    if (
                        disposed ||
                        generation !==
                        syncGeneration.current
                    ) {
                        return
                    }

                    console.error(
                        'Trace catch-up failed',
                        error
                    )

                    const buffered =
                        pendingLive.current

                    pendingLive.current =
                        []

                    observe(
                        buffered
                    )

                    buffering.current =
                        false

                    setConnection(
                        'DEGRADED'
                    )
                }
            }

        const trace =
            (event: Event) => {
                try {
                    const parsed =
                        JSON.parse(
                            (
                                event as MessageEvent
                            ).data
                        ) as OperationTraceEvent

                    if (
                        buffering.current
                    ) {
                        pendingLive.current.push(
                            parsed
                        )
                    } else {
                        observe([
                            parsed
                        ])
                    }
                } catch (error) {
                    console.error(
                        'Invalid trace SSE payload',
                        error
                    )
                }
            }

        source.addEventListener(
            'connected',
            connected
        )

        source.addEventListener(
            'trace',
            trace
        )

        source.onerror =
            () => {
                if (!disposed) {
                    syncGeneration.current++

                    buffering.current =
                        true

                    setConnection(
                        'RECONNECTING'
                    )
                }
            }

        return () => {
            disposed = true
            syncGeneration.current++
            source.close()
        }
    }, [])

    useEffect(() => {
        let disposed = false

        let timer:
            number |
            undefined

        const refresh =
            async () => {
                try {
                    const current =
                        await getCurrentDecisionDossier()

                    if (!disposed) {
                        setDossier(
                            current
                        )

                        setDossierError(
                            null
                        )
                    }
                } catch (error) {
                    if (!disposed) {
                        setDossierError(
                            error instanceof Error
                                ? error.message
                                : String(error)
                        )
                    }
                } finally {
                    if (!disposed) {
                        timer =
                            window.setTimeout(
                                refresh,
                                750
                            )
                    }
                }
            }

        void refresh()

        return () => {
            disposed = true

            if (
                timer !== undefined
            ) {
                window.clearTimeout(
                    timer
                )
            }
        }
    }, [])

    const decisionId =
        dossier?.caseInfo.decisionId ??
        null

    useEffect(() => {
        setFollowLive(true)
        setSelectedSnapshot(null)
    }, [
        decisionId
    ])

    const proof =
        dossier?.proof ??
        []

    const latest =
        proof.length > 0
            ? proof[
            proof.length - 1
                ]
            : null

    const selected =
        followLive
            ? latest
            : selectedSnapshot

    const visibleTape =
        useMemo(
            () =>
                [...proof]
                    .reverse()
                    .slice(
                        0,
                        50
                    ),
            [proof]
        )

    const displayedSequence =
        Math.max(
            transportSequence,
            dossier?.latestSequence ??
            0
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
                            className={
                                `connection connection-${connection.toLowerCase()}`
                            }
                        >
                            <span className="pulse-dot" />
                            {connection}
                        </span>
                    </div>

                    <h1>
                        Mobility Operations Control Room
                    </h1>

                    <p className="subtitle">
                        One durable decision case. One evidence chain.
                        Backend-generated state only.
                    </p>
                </div>

                <div className="sequence-box">
                    <span>
                        DECISION CASE
                    </span>

                    <strong>
                        {decisionId
                            ? decisionId.slice(
                                0,
                                8
                            )
                            : '—'}
                    </strong>

                    <span>
                        {dossier?.caseInfo.status ??
                            'WAITING'}
                        {' · '}
                        TRACE{' '}
                        {displayedSequence ||
                            '—'}
                    </span>
                </div>
            </header>

            <ReplayPanel />

            <section className="stage-grid">
                <SensePanel
                    dossier={dossier}
                    error={dossierError}
                />

                <ReasonPanel
                    dossier={dossier}
                    error={dossierError}
                />

                <ActionPanel
                    dossier={dossier}
                    error={dossierError}
                />

                <VerifyPanel
                    dossier={dossier}
                    error={dossierError}
                />
            </section>

            <section className="workspace">
                <div className="event-tape">
                    <div className="panel-heading">
                        <div>
                            <span className="panel-kicker">
                                DECISION PROOF LEDGER
                            </span>

                            <h2>
                                One case · ordered backend evidence
                            </h2>
                        </div>

                        <button
                            className={
                                followLive
                                    ? 'follow active'
                                    : 'follow'
                            }
                            onClick={
                                () => {
                                    if (followLive) {
                                        setFollowLive(false)
                                        setSelectedSnapshot(
                                            latest
                                        )
                                    } else {
                                        setSelectedSnapshot(
                                            null
                                        )
                                        setFollowLive(true)
                                    }
                                }
                            }
                        >
                            {followLive
                                ? '● Following case'
                                : 'Follow case'}
                        </button>
                    </div>

                    <div className="tape-list">
                        {!dossier && (
                            <div className="empty">
                                Waiting for a decision case...
                            </div>
                        )}

                        {dossier &&
                            visibleTape.length ===
                            0 && (
                                <div className="empty">
                                    Case exists but no proof events are available.
                                </div>
                            )}

                        {visibleTape.map(
                            item => (
                                <button
                                    key={
                                        item.sequence
                                    }
                                    className={
                                        `trace-row trace-${item.stage.toLowerCase()} ${
                                            selected?.sequence ===
                                            item.sequence
                                                ? 'selected'
                                                : ''
                                        }`
                                    }
                                    onClick={
                                        () => {
                                            setFollowLive(
                                                false
                                            )

                                            setSelectedSnapshot(
                                                item
                                            )
                                        }
                                    }
                                >
                                    <span className="trace-sequence">
                                        #{item.sequence}
                                    </span>

                                    <span
                                        className={
                                            `stage-pill pill-${item.stage.toLowerCase()}`
                                        }
                                    >
                                        {item.stage}
                                    </span>

                                    <span className="trace-time">
                                        {formatTime(
                                            item.recordedAt
                                        )}
                                    </span>

                                    <span className="trace-content">
                                        <strong>
                                            {item.eventType}
                                        </strong>

                                        <span>
                                            {item.summary}
                                        </span>
                                    </span>
                                </button>
                            )
                        )}
                    </div>
                </div>

                <aside className="evidence-panel">
                    <span className="panel-kicker">
                        SELECTED PROOF
                    </span>

                    {selected ? (
                        <>
                            <h2>
                                {selected.eventType}
                            </h2>

                            <div className="detail-grid">
                                <div>
                                    <span>
                                        Stage
                                    </span>

                                    <strong>
                                        {selected.stage}
                                    </strong>
                                </div>

                                <div>
                                    <span>
                                        Sequence
                                    </span>

                                    <strong>
                                        #{selected.sequence}
                                    </strong>
                                </div>

                                <div>
                                    <span>
                                        Event time
                                    </span>

                                    <strong>
                                        {formatTime(
                                            selected.eventTime
                                        )}
                                    </strong>
                                </div>

                                <div>
                                    <span>
                                        Recorded
                                    </span>

                                    <strong>
                                        {formatTime(
                                            selected.recordedAt
                                        )}
                                    </strong>
                                </div>
                            </div>

                            <div className="evidence-block">
                                <span>
                                    Decision
                                </span>

                                <code>
                                    {selected.decisionId ??
                                        '—'}
                                </code>
                            </div>

                            <div className="evidence-block">
                                <span>
                                    Situation
                                </span>

                                <code>
                                    {selected.situationId ??
                                        'Not created yet'}
                                </code>
                            </div>

                            <div className="evidence-block">
                                <span>
                                    Scope
                                </span>

                                <code>
                                    {selected.scopeKey ??
                                        '—'}
                                </code>
                            </div>

                            <div className="evidence-block">
                                <span>
                                    Source event
                                </span>

                                <code>
                                    {selected.sourceEventId ??
                                        '—'}
                                </code>
                            </div>

                            <div className="evidence-block">
                                <span>
                                    Evidence
                                </span>

                                <p>
                                    {formatEvidence(
                                            selected.evidence
                                        ) ||
                                        'No evidence payload'}
                                </p>
                            </div>
                        </>
                    ) : (
                        <p>
                            No case-scoped proof selected.
                        </p>
                    )}
                </aside>
            </section>
        </main>
    )
}

ReactDOM
    .createRoot(
        document.getElementById(
            'root'
        )!
    )
    .render(
        <App />
    )