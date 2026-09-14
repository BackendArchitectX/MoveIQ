export type Situation = {
    id: string
    correlationKey: string
    businessUnit: string
    situationType: string
    status: string
    affectedEmployees: number
    delayMinutes: number
}

export type ReplayStatus = 'STOPPED' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED'

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

export async function getSituations(): Promise<Situation[]> {
    const response = await fetch('/api/v1/situations')

    if (!response.ok) {
        throw new Error(`Failed to load situations: ${response.status}`)
    }

    return response.json()
}

async function replayRequest(
    path: string,
    method: 'GET' | 'POST' | 'PUT' = 'GET',
    body?: unknown
): Promise<ReplayState> {
    const response = await fetch(`/api/v1/replay${path}`, {
        method,
        headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body)
    })

    if (!response.ok) {
        const detail = await response.text()
        throw new Error(detail || `Replay request failed: ${response.status}`)
    }

    return response.json()
}

export function getReplayState() {
    return replayRequest('/status')
}

export function startReplay(speed: number) {
    return replayRequest('/start', 'POST', { speed })
}

export function pauseReplay() {
    return replayRequest('/pause', 'POST')
}

export function resumeReplay() {
    return replayRequest('/resume', 'POST')
}

export function stopReplay() {
    return replayRequest('/stop', 'POST')
}

export function setReplaySpeed(speed: number) {
    return replayRequest('/speed', 'PUT', { speed })
}
