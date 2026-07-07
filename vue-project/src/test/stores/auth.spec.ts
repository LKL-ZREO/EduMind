import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

// Mock the axios request module used by auth store
vi.mock('@/api/request', () => ({
  default: {
    post: vi.fn(),
  },
}))

import request from '@/api/request'

/**
 * Auth Store 测试 — 覆盖登录/注册/登出/localStorage 持久化。
 */
describe('useAuthStore', () => {
  const mockPost = request.post as ReturnType<typeof vi.fn>

  beforeEach(() => {
    // 每个测试前: 创建新 Pinia、清空 localStorage
    setActivePinia(createPinia())
    localStorage.clear()
    mockPost.mockReset()
  })

  // ==================== 登录 ====================

  describe('login', () => {
    it('正常登录 → 设置 user/token/sessionId', async () => {
      mockPost.mockResolvedValueOnce({
        data: {
          code: 200,
          data: {
            id: 1,
            username: 'teacher1',
            email: 't1@school.edu',
            token: 'jwt-token-abc',
            sessionId: 'session-123',
          },
        },
      })

      const store = useAuthStore()
      await store.login({ username: 'teacher1', password: 'password123' })

      expect(store.user).toEqual({ id: 1, username: 'teacher1', email: 't1@school.edu' })
      expect(store.token).toBe('jwt-token-abc')
      expect(localStorage.getItem('token')).toBe('jwt-token-abc')
      expect(localStorage.getItem('sessionId')).toBe('session-123')
      expect(localStorage.getItem('auth:user')).toContain('teacher1')
    })

    it('登录失败 → 抛出异常且不改变状态', async () => {
      mockPost.mockResolvedValueOnce({
        data: { code: 1003, message: '密码错误' },
      })

      const store = useAuthStore()
      await expect(store.login({ username: 't', password: 'wrong' }))
        .rejects.toThrow('密码错误')

      // 状态不变
      expect(store.user).toBeNull()
      expect(store.token).toBeNull()
      expect(localStorage.getItem('token')).toBeNull()
    })

    it('无 sessionId → 不设置 localStorage', async () => {
      mockPost.mockResolvedValueOnce({
        data: {
          code: 200,
          data: {
            id: 1,
            username: 'teacher1',
            email: 't1@school.edu',
            token: 'jwt-token-abc',
            sessionId: null,
          },
        },
      })

      const store = useAuthStore()
      await store.login({ username: 'teacher1', password: 'pw' })

      expect(localStorage.getItem('sessionId')).toBeNull()
    })
  })

  // ==================== 注册 ====================

  describe('register', () => {
    it('正常注册 → 不抛出异常', async () => {
      mockPost.mockResolvedValueOnce({
        data: { code: 200, message: 'success' },
      })

      const store = useAuthStore()
      await expect(store.register({
        username: 'newuser',
        email: 'u@school.edu',
        password: 'password123',
      })).resolves.not.toThrow()
    })

    it('注册失败 → 抛出异常', async () => {
      mockPost.mockResolvedValueOnce({
        data: { code: 1002, message: '用户名已存在' },
      })

      const store = useAuthStore()
      await expect(store.register({
        username: 'existing',
        email: 'u@school.edu',
        password: 'pw',
      })).rejects.toThrow('用户名已存在')
    })
  })

  // ==================== 登出 ====================

  describe('logout', () => {
    it('登出 → 清空 user/token/sessionId', async () => {
      // 先登录
      mockPost.mockResolvedValueOnce({
        data: {
          code: 200,
          data: {
            id: 1, username: 't', email: 't@t.com',
            token: 'jwt', sessionId: 'sess',
          },
        },
      })

      const store = useAuthStore()
      await store.login({ username: 't', password: 'pw' })

      // 确认已登录
      expect(store.user).not.toBeNull()

      // 登出
      store.logout()

      expect(store.user).toBeNull()
      expect(store.token).toBeNull()
      expect(localStorage.getItem('token')).toBeNull()
      expect(localStorage.getItem('sessionId')).toBeNull()
      expect(localStorage.getItem('auth:user')).toBeNull()
    })
  })

  // ==================== 持久化恢复 ====================

  describe('state hydration', () => {
    it('从 localStorage 恢复已持久化的用户', () => {
      localStorage.setItem('token', 'restored-token')
      localStorage.setItem('auth:user', JSON.stringify({
        id: 99, username: 'cached_user', email: 'cached@school.edu',
      }))

      // 重新创建 store（模拟页面刷新）
      const store = useAuthStore()

      expect(store.token).toBe('restored-token')
      expect(store.user).toEqual({ id: 99, username: 'cached_user', email: 'cached@school.edu' })
    })

    it('localStorage 为空 → user 为 null', () => {
      const store = useAuthStore()
      expect(store.user).toBeNull()
      expect(store.token).toBeNull()
    })
  })
})
