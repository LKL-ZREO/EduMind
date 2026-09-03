import axios, { type InternalAxiosRequestConfig } from 'axios'
import { ensureCsrfToken, isUnsafeMethod } from './csrf'
import type { ApiProblem } from './types'

type RetryableRequest = InternalAxiosRequestConfig & { _csrfRetry?: boolean }

type UnauthorizedHandler = () => void

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 10_000,
  withCredentials: true,
})

let unauthorizedHandler: UnauthorizedHandler = () => {
  window.location.assign('/login')
}

export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  unauthorizedHandler = handler
  return () => {
    unauthorizedHandler = () => window.location.assign('/login')
  }
}

export function notifyUnauthorized() {
  unauthorizedHandler()
}

apiClient.interceptors.request.use(
  async (config) => {
    if (isUnsafeMethod(config.method)) {
      config.headers.set('X-XSRF-TOKEN', await ensureCsrfToken())
    }
    return config
  },
  (error: unknown) => Promise.reject(error instanceof Error ? error : new Error(String(error))),
)

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError<ApiProblem>(error)) {
      return Promise.reject(error instanceof Error ? error : new Error(String(error)))
    }

    const original = error.config as RetryableRequest | undefined
    if (
      error.response?.status === 403 &&
      error.response.data?.code === 40301 &&
      original &&
      !original._csrfRetry
    ) {
      original._csrfRetry = true
      original.headers.set('X-XSRF-TOKEN', await ensureCsrfToken(true))
      return apiClient(original)
    }

    const url = String(original?.url || '')
    const isSessionProbe = url.includes('/auth/me')
    const isAuthAttempt = url.includes('/auth/login') || url.includes('/auth/register')
    const isStudentLivePage = window.location.pathname.startsWith('/live/')

    if (error.response?.status === 401 && !isSessionProbe && !isAuthAttempt && !isStudentLivePage) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('auth:user')
      unauthorizedHandler()
    }

    return Promise.reject(error)
  },
)

export default apiClient
