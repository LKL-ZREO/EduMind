import { App as AntdApp, ConfigProvider } from 'antd'
import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routeObjects } from '@/app/router'
import { clearCsrfToken } from '@/shared/api/csrf'
import { queryClient } from '@/shared/query/queryClient'
import { server } from '@/test/server'

function result(data: unknown, code = 200, message = 'success') {
  return HttpResponse.json({ code, message, data })
}

const session = {
  sessionId: 'session-1',
  title: '函数复习',
  classId: 101,
  className: '高一三班',
  courseId: 10,
  kbIds: [8],
  mode: 'auto',
  pinned: false,
  createdAt: '2026-08-03T08:00:00',
  updatedAt: '2026-08-03T08:00:00',
}

function authenticatedUser() {
  return http.get('*/api/auth/me', () =>
    result({ id: 1, username: 'teacher1', email: 'teacher1@example.com' }),
  )
}

function contextHandlers() {
  return [
    http.get('*/api/dashboard/classes', () => result([{ id: 101, name: '高一三班' }])),
    http.get('*/api/shared-kb/my', () =>
      HttpResponse.json([{ id: 8, name: '函数知识库', description: '数学资料' }]),
    ),
    http.get('*/api/shared-kb/joined', () => HttpResponse.json([])),
  ]
}

function renderRoute(initialEntry = '/teacher/chat') {
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

describe('AI chat route', () => {
  beforeEach(() => {
    queryClient.clear()
    clearCsrfToken()
    localStorage.clear()
  })

  it('loads sessions, context, and message history', async () => {
    server.use(
      authenticatedUser(),
      ...contextHandlers(),
      http.get('*/api/chat/sessions', () => HttpResponse.json([session])),
      http.get('*/api/chat/sessions/session-1/messages', () =>
        HttpResponse.json([
          { id: 1, role: 'user', content: '帮我复习函数', createdAt: '2026-08-03T08:00:00' },
          {
            id: 2,
            role: 'assistant',
            content: '先从**定义域**开始。',
            createdAt: '2026-08-03T08:00:01',
          },
        ]),
      ),
    )
    const user = userEvent.setup()
    renderRoute()

    expect(await screen.findByText('函数复习')).toBeVisible()
    expect(await screen.findByText('帮我复习函数')).toBeVisible()
    expect(await screen.findByText('定义域')).toBeVisible()
    await user.click(screen.getByRole('combobox', { name: '知识源' }))
    expect(await screen.findByRole('option', { name: '函数知识库' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  it('creates a session and renders tokens from the SSE response', async () => {
    let created = false
    const streamSpy = vi.fn()
    server.use(
      authenticatedUser(),
      ...contextHandlers(),
      http.get('*/api/chat/sessions', () => HttpResponse.json(created ? [session] : [])),
      http.post('*/api/chat/sessions', async ({ request }) => {
        created = true
        expect(await request.json()).toMatchObject({ classId: 101, kbIds: [8], mode: 'auto' })
        return HttpResponse.json(session)
      }),
      http.get('*/api/auth/csrf', () => result({ token: 'chat-csrf' })),
      http.post('*/api/chat/stream', async ({ request }) => {
        streamSpy(await request.json())
        return new HttpResponse(
          'event: token\ndata: {"content":"你好"}\n\n' +
            'event: token\ndata: {"content":"老师"}\n\n' +
            'event: done\ndata: {}\n\n',
          { headers: { 'Content-Type': 'text/event-stream' } },
        )
      }),
    )
    const user = userEvent.setup()
    renderRoute()

    await user.type(await screen.findByPlaceholderText(/向教学 Agent/), '请给我建议')
    await user.click(screen.getByRole('button', { name: '发送消息' }))

    expect(await screen.findByText('你好老师')).toBeVisible()
    expect(streamSpy).toHaveBeenCalledWith({
      message: '请给我建议',
      sessionId: 'session-1',
      classId: 101,
      kbIds: [8],
      mode: 'auto',
    })
  })

  it('blocks navigation while streaming and aborts only after confirmation', async () => {
    server.use(
      authenticatedUser(),
      ...contextHandlers(),
      http.get('*/api/chat/sessions', () => HttpResponse.json([session])),
      http.get('*/api/chat/sessions/session-1/messages', () => HttpResponse.json([])),
      http.get('*/api/auth/csrf', () => result({ token: 'chat-csrf' })),
      http.post('*/api/chat/stream', async () => {
        await delay(10_000)
        return new HttpResponse('event: done\ndata: {}\n\n', {
          headers: { 'Content-Type': 'text/event-stream' },
        })
      }),
      http.get('*/api/teacher/classes', () => result([])),
      http.get('*/api/courses', () => result([])),
      http.get('*/api/courses/presets', () => result({})),
    )
    const confirmSpy = vi
      .spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true)
    const user = userEvent.setup()
    const router = renderRoute()

    await user.type(await screen.findByPlaceholderText(/向教学 Agent/), '持续生成')
    await user.click(screen.getByRole('button', { name: '发送消息' }))
    expect(await screen.findByRole('button', { name: '停止生成' })).toBeVisible()

    await user.click(screen.getByRole('menuitem', { name: '班级管理' }))
    await waitFor(() => expect(confirmSpy).toHaveBeenCalledTimes(1))
    expect(router.state.location.pathname).toBe('/teacher/chat')

    await user.click(screen.getByRole('menuitem', { name: '班级管理' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/teacher/classes'))
    expect(confirmSpy).toHaveBeenCalledTimes(2)
  })
})
