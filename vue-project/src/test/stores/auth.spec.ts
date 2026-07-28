import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/api/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import request from '@/api/request'

describe('useAuthStore — Session Cookie authentication', () => {
  const mockGet = request.get as ReturnType<typeof vi.fn>
  const mockPost = request.post as ReturnType<typeof vi.fn>

  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    mockGet.mockReset()
    mockPost.mockReset()
  })

  it('login stores user state and chat session without a browser token', async () => {
    mockPost.mockResolvedValueOnce({
      data: {
        code: 200,
        data: {
          id: 1,
          username: 'teacher1',
          email: 't1@school.edu',
          sessionId: 'chat-session-123',
        },
      },
    })

    const store = useAuthStore()
    await store.login({ username: 'teacher1', password: 'password123' })

    expect(store.user).toEqual({ id: 1, username: 'teacher1', email: 't1@school.edu' })
    expect(store.isAuthenticated).toBe(true)
    expect(localStorage.getItem('sessionId')).toBe('chat-session-123')
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('auth:user')).toBeNull()
  })

  it('login failure leaves the store unauthenticated', async () => {
    mockPost.mockRejectedValueOnce({
      response: { status: 401, data: { code: 1003, message: '密码错误' } },
    })
    const store = useAuthStore()

    await expect(store.login({ username: 'teacher1', password: 'wrong' })).rejects.toThrow(
      '密码错误',
    )

    expect(store.user).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('restores an authenticated user from auth/me', async () => {
    mockGet.mockResolvedValueOnce({
      data: {
        code: 200,
        data: { id: 9, username: 'restored', email: null },
      },
    })
    const store = useAuthStore()

    await store.ensureInitialized()

    expect(store.initialized).toBe(true)
    expect(store.isAuthenticated).toBe(true)
    expect(store.user?.username).toBe('restored')
    expect(mockGet).toHaveBeenCalledWith('/auth/me')
  })

  it('treats a missing server session as logged out', async () => {
    mockGet.mockRejectedValueOnce({ response: { status: 401 } })
    const store = useAuthStore()

    await store.ensureInitialized()

    expect(store.initialized).toBe(true)
    expect(store.user).toBeNull()
    expect(store.isAuthenticated).toBe(false)
  })

  it('register delegates to the public auth endpoint', async () => {
    mockPost.mockResolvedValueOnce({ data: { code: 200 } })
    const store = useAuthStore()

    await store.register({
      username: 'newuser',
      email: 'u@school.edu',
      password: 'password123',
    })

    expect(mockPost).toHaveBeenCalledWith('/auth/register', {
      username: 'newuser',
      email: 'u@school.edu',
      password: 'password123',
    })
  })

  it('logout invalidates the server session and clears local chat state', async () => {
    mockPost
      .mockResolvedValueOnce({
        data: {
          code: 200,
          data: { id: 1, username: 'teacher', email: null, sessionId: 'chat-session' },
        },
      })
      .mockResolvedValueOnce({ data: { code: 200 } })
    const store = useAuthStore()
    await store.login({ username: 'teacher', password: 'password123' })

    await store.logout()

    expect(mockPost).toHaveBeenLastCalledWith('/auth/logout')
    expect(store.user).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(localStorage.getItem('sessionId')).toBeNull()
  })

  it('removes credentials left by the legacy JWT implementation', () => {
    localStorage.setItem('token', 'legacy-jwt')
    localStorage.setItem('auth:user', '{"username":"cached"}')

    useAuthStore()

    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('auth:user')).toBeNull()
  })
})
