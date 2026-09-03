import type { ApiResponse } from './types'

export function unwrapApiResponse<T>(response: ApiResponse<T>, fallbackMessage: string): T {
  if (response.code !== 200) {
    throw new Error(response.message || fallbackMessage)
  }
  return response.data
}
