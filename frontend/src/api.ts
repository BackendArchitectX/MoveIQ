import type { OperationTraceEvent } from './live'

export type Situation = {
    id: string
    correlationKey: string
    businessUnit: string
    situationType: string
    status: string
    affectedEmployees: number
    delayMinutes: number
}

export type ReplayStatus =
    | 'STOPPED'
    | 'RUNNING'
    | 'PAUSED'
    | 'COMPLETED'
    | 'FAILED'

export type ReplayState = {
    status: ReplayStatus
    speed: number
    processed: number
    total: number
    eventsPerSecond: number
    replayTime: string | null
    firstEventTime: string | null
    lastEventTime: string | null
    lastError: string | null
}

export type ReasoningSnapshot = {
    situationId: string
    businessUnit: string
    office: string | null
    shift: string | null
    direction: string | null
    eventTime: string
    currentAvgDelay: number
    baselineAvgDelay: number | null
    deltaPct: number | null
    currentSampleSize: number
    baselineSampleSize: number
    coveragePct: number
    trustStatus: 'HIGH' | 'MEDIUM' | 'LOW'
    recommendation: string
    methodologyVersion: string
    computedAt: string
}

export type VerificationSnapshot = {
    executionId: string
    situationId: string
    baselineEventTime: string
    baselineAvgDelay: number
    observedSampleSize: number
    observedAvgDelay: number | null
    changePct: number | null
    outcome:
        | 'OBSERVED_IMPROVEMENT'
        | 'NO_MATERIAL_CHANGE'
        | 'WORSENED'
        | 'INSUFFICIENT_EVIDENCE'
    methodologyVersion: string
    updatedAt: string
}

export type ControlRoomState = {
    source: ReplayState
    reason: ReasoningSnapshot | null
    verify: VerificationSnapshot | null
    generatedAt: string
}

export type ActionProposal = {
    id: string
    situationId: string
    actionType: string
    status: string
    evidenceHash: string
}

export type ExecutionReceipt = {
    executionId: string
    proposalId: string
    status: string
    externalReference: string | null
}

export type DecisionCaseInfo = {
    decisionId: string
    replaySessionId: string | null
    status: string
    scopeKey: string
    triggerEventId: string | null
    situationId: string | null
    openedEventTime: string
    detectedEventTime: string | null
    closedEventTime: string | null
    createdAt: string
    updatedAt: string
}

export type DecisionSense = {
    state: string
    windowEventCount: number | null
    detectedWindowEventCount: number | null
    threshold: number | null
    affectedEmployeeTrips: number | null
    delayMinutesSum: number | null
    averageDelayMinutes: number | null
    thresholdCrossed: boolean
    watermarkEpochMillis: number | null
    sourceEventIds: string[]
}

export type DecisionReason = {
    state: string
    currentAvgDelay: number | null
    baselineAvgDelay: number | null
    deltaPct: number | null
    currentSampleSize: number | null
    baselineSampleSize: number | null
    coveragePct: number | null
    trustStatus: string | null
    recommendation: string | null
    methodologyVersion: string | null
    dataCutoff: string | null
    futureDataExcluded: boolean | null
}

export type DecisionAct = {
    state: string
    latestEventType: string | null
    evidence: Record<string, unknown>
}

export type DecisionVerify = {
    state: string
    latestEventType: string | null
    evidence: Record<string, unknown>
}

export type DecisionDossier = {
    caseInfo: DecisionCaseInfo
    sense: DecisionSense
    reason: DecisionReason
    act: DecisionAct
    verify: DecisionVerify
    proof: OperationTraceEvent[]
    latestSequence: number | null
    generatedAt: string
}

export async function getSituations(): Promise<Situation[]> {
    const response = await fetch('/api/v1/situations')

    if (!response.ok) {
        throw new Error(
            `Failed to load situations: ${response.status}`
        )
    }

    return response.json()
}

export async function getControlRoomState(): Promise<ControlRoomState> {
    const response = await fetch('/api/v1/control-room/state')

    if (!response.ok) {
        throw new Error(
            `Failed to load control-room state: ${response.status}`
        )
    }

    return response.json()
}

export async function getCurrentDecisionDossier():
    Promise<DecisionDossier | null> {
    const response = await fetch(
        '/api/v1/control-room/cases/current',
        {
            cache: 'no-store'
        }
    )

    if (response.status === 404) {
        return null
    }

    if (!response.ok) {
        const detail = await response.text()

        throw new Error(
            detail ||
            `Failed to load current decision case: ${response.status}`
        )
    }

    return response.json()
}

export async function getDecisionDossier(
    decisionId: string
): Promise<DecisionDossier> {
    const response = await fetch(
        `/api/v1/control-room/cases/${encodeURIComponent(decisionId)}`,
        {
            cache: 'no-store'
        }
    )

    if (!response.ok) {
        const detail = await response.text()

        throw new Error(
            detail ||
            `Failed to load decision case: ${response.status}`
        )
    }

    return response.json()
}

async function jsonRequest<T>(
    path: string,
    method: 'POST' | 'PUT',
    body: unknown
): Promise<T> {
    const response = await fetch(path, {
        method,
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(body)
    })

    if (!response.ok) {
        const detail = await response.text()

        throw new Error(
            detail ||
            `Request failed: ${response.status}`
        )
    }

    return response.json()
}

export function proposeAction(
    situationId: string,
    actionType: string
) {
    return jsonRequest<ActionProposal>(
        `/api/v1/situations/${situationId}/actions`,
        'POST',
        { actionType }
    )
}

export function approveAction(
    proposalId: string,
    approvedBy: string,
    idempotencyKey: string
) {
    return jsonRequest<ExecutionReceipt>(
        `/api/v1/actions/${proposalId}/approve`,
        'POST',
        {
            approvedBy,
            idempotencyKey
        }
    )
}

async function replayRequest(
    path: string,
    method: 'GET' | 'POST' | 'PUT' = 'GET',
    body?: unknown
): Promise<ReplayState> {
    const response = await fetch(
        `/api/v1/replay${path}`,
        {
            method,
            headers:
                body === undefined
                    ? undefined
                    : {
                        'Content-Type':
                            'application/json'
                    },
            body:
                body === undefined
                    ? undefined
                    : JSON.stringify(body)
        }
    )

    if (!response.ok) {
        const detail = await response.text()

        throw new Error(
            detail ||
            `Replay request failed: ${response.status}`
        )
    }

    return response.json()
}

export function getReplayState() {
    return replayRequest('/status')
}

export function startReplay(speed: number) {
    return replayRequest(
        '/start',
        'POST',
        { speed }
    )
}

export function pauseReplay() {
    return replayRequest(
        '/pause',
        'POST'
    )
}

export function resumeReplay() {
    return replayRequest(
        '/resume',
        'POST'
    )
}

export function stopReplay() {
    return replayRequest(
        '/stop',
        'POST'
    )
}

export function setReplaySpeed(speed: number) {
    return replayRequest(
        '/speed',
        'PUT',
        { speed }
    )
}