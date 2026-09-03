import axios from 'axios'
import apiClient from '@/shared/api/client'
import { clearCsrfToken } from '@/shared/api/csrf'
import { getApiErrorMessage } from '@/shared/api/errors'
import type { ApiResponse } from '@/shared/api/types'
import type {
  AuthenticatedUser,
  LoginPayload,
  LoginResult,
  RegisterPayload,
} from '@/features/auth/model/types'

export function clearLegacyCredentials() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('auth:user')
}

export async function getCurrentUser(): Promise<AuthenticatedUser | null> {
  try {
    const response = await apiClient.get<ApiResponse<AuthenticatedUser>>('/auth/me')
    return response.data.code === 200 ? response.data.data : null
  } catch (error: unknown) {
    if (axios.isAxiosError(error) && error.response?.status === 401) return null
    throw new Error(getApiErrorMessage(error, '无法确认登录状态'), { cause: error })
  }
}

export async function login(payload: LoginPayload): Promise<AuthenticatedUser> {
  try {
    const response = await apiClient.post<ApiResponse<LoginResult>>('/auth/login', payload)
    const result = response.data
    if (result.code !== 200) throw new Error(result.message || '登录失败')

    const { id, username, email, sessionId } = result.data
    if (sessionId) localStorage.setItem('sessionId', sessionId)
    return { id, username, email: email ?? null }
  } catch (error: unknown) {
    throw new Error(getApiErrorMessage(error, '登录失败'), { cause: error })
  }
}

export async function register(payload: RegisterPayload): Promise<void> {
  try {
    const response = await apiClient.post<ApiResponse<null>>('/auth/register', payload)
    if (response.data.code !== 200) throw new Error(response.data.message || '注册失败')
  } catch (error: unknown) {
    throw new Error(getApiErrorMessage(error, '注册失败，请稍后重试'), { cause: error })
  }
}

export async function logout(): Promise<void> {
  try {
    await apiClient.post('/auth/logout')
  } finally {
    localStorage.removeItem('sessionId')
    clearLegacyCredentials()
    clearCsrfToken()
  }
}
