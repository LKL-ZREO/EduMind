import axios, { type InternalAxiosRequestConfig } from 'axios'
import { getCached, setCache } from './cache'

type RetryableRequest = InternalAxiosRequestConfig & { _csrfRetry?: boolean }

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
  withCredentials: true,
})

let csrfToken: string | null = null
let csrfRequest: Promise<string> | null = null

function isUnsafe(method?: string) {
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes((method || 'GET').toUpperCase())
}

export async function ensureCsrfToken(force = false): Promise<string> {
  if (csrfToken && !force) return csrfToken
  if (csrfRequest && !force) return csrfRequest

  csrfRequest = axios
    .get('/api/auth/csrf', { withCredentials: true })
    .then((response) => {
      const token = response.data?.data?.token
      if (!token) throw new Error('未获取到 CSRF token')
      csrfToken = token
      return token as string
    })
    .finally(() => {
      csrfRequest = null
    })
  return csrfRequest
}

export async function getCsrfHeaders(): Promise<Record<string, string>> {
  return { 'X-XSRF-TOKEN': await ensureCsrfToken() }
}

request.interceptors.request.use(
  async (config) => {
    if (isUnsafe(config.method)) {
      config.headers.set('X-XSRF-TOKEN', await ensureCsrfToken())
    }
    return config
  },
  (error) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as RetryableRequest | undefined
    if (
      error.response?.status === 403 &&
      error.response?.data?.code === 40301 &&
      original &&
      !original._csrfRetry
    ) {
      original._csrfRetry = true
      original.headers.set('X-XSRF-TOKEN', await ensureCsrfToken(true))
      return request(original)
    }

    const url = String(original?.url || '')
    const isSessionProbe = url.includes('/auth/me')
    const isAuthAttempt = url.includes('/auth/login') || url.includes('/auth/register')
    const isStudentPage = window.location.pathname.startsWith('/live/')
    if (error.response?.status === 401 && !isSessionProbe && !isAuthAttempt && !isStudentPage) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('auth:user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export async function checkHealth() {
  const cached = getCached('health')
  if (cached) return cached

  const res = await request.get('/chat/health')
  const status = res.data.status
  setCache('health', status)
  return status
}

export default request
