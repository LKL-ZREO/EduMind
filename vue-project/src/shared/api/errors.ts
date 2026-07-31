import axios from 'axios'
import type { ApiProblem } from './types'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function getProblemMessage(value: unknown): string | undefined {
  if (!isRecord(value)) return undefined
  if (typeof value.message === 'string') return value.message
  return typeof value.detail === 'string' ? value.detail : undefined
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiProblem>(error)) {
    return (
      error.response?.data?.message ?? error.response?.data?.detail ?? error.message ?? fallback
    )
  }
  if (isRecord(error)) {
    const response = error.response
    if (isRecord(response)) {
      const responseMessage = getProblemMessage(response.data)
      if (responseMessage) return responseMessage
    }
    const message = getProblemMessage(error)
    if (message) return message
  }
  return error instanceof Error ? error.message : fallback
}

export function getApiErrorData<T>(error: unknown): T | undefined {
  return axios.isAxiosError<T>(error) ? error.response?.data : undefined
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException
    ? error.name === 'AbortError'
    : error instanceof Error && error.name === 'AbortError'
}
