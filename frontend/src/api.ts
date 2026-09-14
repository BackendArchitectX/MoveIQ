export type Situation = {
    id: string
    correlationKey: string
    businessUnit: string
    situationType: string
    status: string
    affectedEmployees: number
    delayMinutes: number
}

export async function getSituations(): Promise<Situation[]> {
    const response = await fetch('/api/v1/situations')

    if (!response.ok) {
        throw new Error(`Failed to load situations: ${response.status}`)
    }

    return response.json()
}