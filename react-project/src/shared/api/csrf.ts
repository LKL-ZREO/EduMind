import axios from 'axios'

type CsrfResponse = {
  data?: {
    token?: unknown
  }
}

let csrfToken: string | null = null
let csrfRequest: Promise<string> | null = null

export function isUnsafeMethod(method?: string) {
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes((method || 'GET').toUpperCase())
}

export async function ensureCsrfToken(force = false): Promise<string> {
  if (csrfToken && !force) return csrfToken
  if (csrfRequest && !force) return csrfRequest

  csrfRequest = axios
    .get<CsrfResponse>('/api/auth/csrf', { withCredentials: true })
    .then((response) => {
      const token = response.data.data?.token
      if (typeof token !== 'string' || !token) {
        throw new Error('未获取到 CSRF token')
      }
      csrfToken = token
      return token
    })
    .finally(() => {
      csrfRequest = null
    })

  return csrfRequest
}

export async function getCsrfHeaders(): Promise<Record<string, string>> {
  return { 'X-XSRF-TOKEN': await ensureCsrfToken() }
}

export function clearCsrfToken() {
  csrfToken = null
  csrfRequest = null
}
