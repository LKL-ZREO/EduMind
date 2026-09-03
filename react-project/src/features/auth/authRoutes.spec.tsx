import { App as AntdApp, ConfigProvider } from 'antd'
import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routeObjects } from '@/app/router'
import { clearCsrfToken } from '@/shared/api/csrf'
import { queryClient } from '@/shared/query/queryClient'
import { server } from '@/test/server'

function authResponse(data: unknown, code = 200, message = 'success') {
  return HttpResponse.json({ code, message, data })
}

function renderRoute(initialEntry: string) {
  const router = createMemoryRouter(routeObjects, { initialEntries: [initialEntry] })
  render(
    <ConfigProvider>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>,
  )
  return router
}

describe('authentication routes', () => {
  beforeEach(() => {
    queryClient.clear()
    clearCsrfToken()
    localStorage.clear()
  })

  it('redirects an unauthenticated teacher route to login with the original path', async () => {
    server.use(http.get('*/api/auth/me', () => authResponse(null, 401, '未登录')))
    const router = renderRoute('/teacher/classes?tab=active')

    expect(await screen.findByRole('heading', { name: '欢迎回来' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toContain('redirect=')
    expect(new URLSearchParams(router.state.location.search).get('redirect')).toBe(
      '/teacher/classes?tab=active',
    )
  })

  it('redirects an authenticated guest away from login', async () => {
    server.use(
      http.get('*/api/auth/me', () =>
        authResponse({ id: 7, username: 'teacher7', email: 'teacher7@example.com' }),
      ),
      http.get('*/api/chat/sessions', () => HttpResponse.json([])),
      http.get('*/api/dashboard/classes', () => authResponse([])),
      http.get('*/api/shared-kb/my', () => HttpResponse.json([])),
      http.get('*/api/shared-kb/joined', () => HttpResponse.json([])),
    )
    const router = renderRoute('/login')

    expect(await screen.findByRole('heading', { name: '今天想先处理哪项教学工作？' })).toBeVisible()
    expect(screen.getByText('teacher7')).toBeVisible()
    expect(router.state.location.pathname).toBe('/teacher/chat')
  })

  it('logs in, stores the chat session, and returns to a safe teacher route', async () => {
    const loginSpy = vi.fn()
    server.use(
      http.get('*/api/auth/me', () => authResponse(null, 401, '未登录')),
      http.get('*/api/auth/csrf', () => authResponse({ token: 'csrf-test-token' })),
      http.get('*/api/teacher/classes', () => authResponse([])),
      http.get('*/api/courses', () => authResponse([])),
      http.get('*/api/courses/presets', () => authResponse({})),
      http.post('*/api/auth/login', async ({ request }) => {
        loginSpy(await request.json())
        return authResponse({
          id: 1,
          username: 'teacher1',
          email: 'teacher1@example.com',
          sessionId: 'chat-session-1',
        })
      }),
    )
    const user = userEvent.setup()
    const router = renderRoute('/login?redirect=%2Fteacher%2Fclasses')

    await user.type(await screen.findByLabelText('用户名'), 'teacher1')
    await user.type(screen.getByLabelText('密码'), 'password123')
    await user.click(screen.getByRole('button', { name: '进入教学工作台' }))

    expect(await screen.findByRole('heading', { name: '班级管理' })).toBeVisible()
    expect(router.state.location.pathname).toBe('/teacher/classes')
    expect(localStorage.getItem('sessionId')).toBe('chat-session-1')
    expect(loginSpy).toHaveBeenCalledWith({ username: 'teacher1', password: 'password123' })
  })

  it('validates password confirmation before registration', async () => {
    const registerSpy = vi.fn()
    server.use(
      http.get('*/api/auth/me', () => authResponse(null, 401, '未登录')),
      http.post('*/api/auth/register', () => {
        registerSpy()
        return authResponse(null)
      }),
    )
    const user = userEvent.setup()
    renderRoute('/register')

    await user.type(await screen.findByLabelText('用户名'), 'teacher2')
    await user.type(screen.getByLabelText('邮箱'), 'teacher2@example.com')
    await user.type(screen.getByLabelText('密码'), 'password123')
    await user.type(screen.getByLabelText('确认密码'), 'different123')
    await user.click(screen.getByRole('button', { name: '创建教师账户' }))

    expect(await screen.findByText('两次输入的密码不一致')).toBeInTheDocument()
    expect(registerSpy).not.toHaveBeenCalled()
  })
})
