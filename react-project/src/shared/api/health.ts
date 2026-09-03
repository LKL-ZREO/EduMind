import apiClient from './client'

type HealthResponse = {
  status?: string
}

export async function checkHealth(): Promise<string> {
  const response = await apiClient.get<HealthResponse>('/chat/health')
  return response.data.status || 'UNKNOWN'
}
