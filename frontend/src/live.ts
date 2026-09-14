export type TraceStage = 'SENSE' | 'REASON' | 'ACT' | 'VERIFY'

export type OperationTraceEvent = {
    sequence: number
    sessionId: string | null
    situationId: string | null
    sourceEventId: string | null
    scopeKey: string | null
    stage: TraceStage
    eventType: string
    eventTime: string
    recordedAt: string
    summary: string
    evidence: Record<string, unknown>
}

const PAGE_SIZE = 500

export async function loadTraceHistory(
    after: number
): Promise<OperationTraceEvent[]> {
    const all: OperationTraceEvent[] = []
    let cursor = after

    while (true) {
        const response = await fetch(
            `/api/v1/live/history?after=${cursor}&limit=${PAGE_SIZE}`
        )

        if (!response.ok) {
            throw new Error(
                `Trace history request failed: ${response.status}`
            )
        }

        const body = await response.json()

        const batch: OperationTraceEvent[] =
            Array.isArray(body)
                ? body
                : Array.isArray(body?.value)
                    ? body.value
                    : []

        all.push(...batch)

        if (batch.length < PAGE_SIZE) {
            break
        }

        cursor = batch[batch.length - 1].sequence
    }

    return all
}