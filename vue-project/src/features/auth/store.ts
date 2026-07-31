import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import request from '@/shared/api/request'
import { getApiErrorMessage } from '@/shared/api/errors'
import type { ApiResponse } from '@/shared/api/types'

export type AuthenticatedUser = {
  id: number
  username: string
  email: string | null
}

type AuthResponse = AuthenticatedUser & { sessionId?: string }

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthenticatedUser | null>(null)
  const initialized = ref(false)
  const isAuthenticated = computed(() => user.value !== null)
  let initialization: Promise<void> | null = null

  // Remove credentials left by the former JWT implementation.
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('auth:user')

  function setUser(value: AuthenticatedUser | null) {
    user.value = value
  }

  async function ensureInitialized() {
    if (initialized.value) return
    if (initialization) return initialization

    initialization = request
      .get('/auth/me')
      .then((response) => {
        const result = response.data as ApiResponse<AuthenticatedUser>
        if (result.code === 200) setUser(result.data)
        else setUser(null)
      })
      .catch(() => setUser(null))
      .finally(() => {
        initialized.value = true
        initialization = null
      })
    return initialization
  }

  async function login(payload: { username: string; password: string }) {
    try {
      const response = await request.post<ApiResponse<AuthResponse>>('/auth/login', payload)
      const data = response.data
      if (data.code !== 200) throw new Error(data.message || '登录失败')

      const { id, username, email, sessionId } = data.data
      setUser({ id, username, email: email ?? null })
      initialized.value = true
      if (sessionId) localStorage.setItem('sessionId', sessionId)
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, '登录失败'))
    }
  }

  async function register(payload: { username: string; email: string; password: string }) {
    try {
      const response = await request.post<ApiResponse<null>>('/auth/register', payload)
      const data = response.data
      if (data.code !== 200) throw new Error(data.message || '注册失败')
    } catch (error: unknown) {
      throw new Error(getApiErrorMessage(error, '注册失败'))
    }
  }

  async function logout() {
    try {
      await request.post('/auth/logout')
    } finally {
      setUser(null)
      initialized.value = true
      localStorage.removeItem('sessionId')
    }
  }

  return {
    user,
    initialized,
    isAuthenticated,
    ensureInitialized,
    login,
    register,
    logout,
  }
})
