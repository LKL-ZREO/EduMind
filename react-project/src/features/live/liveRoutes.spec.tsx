import { App as AntdApp, ConfigProvider } from 'antd'
import { QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routeObjects } from '@/app/router'
import { useLiveStore } from '@/features/live/store/liveStore'
import { clearCsrfToken } from '@/shared/api/csrf'
import { queryClient } from '@/shared/query/queryClient'
import { server } from '@/test/server'

const publishLiveMessage = vi.hoisted(() => vi.fn(() => true))
vi.mock('@/features/live/hooks/useLiveSocketLifecycle', () => ({
  useLiveSocketLifecycle: () => undefined,
  publishLiveMessage,
}))

function result(data: unknown, code = 200, message = 'success') {
  return HttpResponse.json({ code, message, data })
}

function authenticatedUser() {
  return http.get('*/api/auth/me', () =>
    result({ id: 1, username: 'teacher1', email: 'teacher1@example.com' }),
  )
}

function csrf() {
  return http.get('*/api/auth/csrf', () => result({ token: 'live-csrf' }))
}

function renderRoute(initialEntry: string) {
  const router = createMemoryRouter(routeObjects, { initialEntries: [initialEntry] })
  const rendered = render(
    <ConfigProvider>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>,
  )
  return { router, ...rendered }
}

const question = {
  questionId: 11,
  interactionId: null,
  type: 'CHOICE',
  title: '函数定义域是什么？',
  description: '选择最准确的表述',
  options: [
    { key: 'A', text: '自变量允许的取值范围' },
    { key: 'B', text: '函数值的范围' },
  ],
  correctKey: 'A',
  knowledgePoint: '函数',
  difficulty: 'easy',
  status: 'UNSENT',
  sortOrder: 1,
  timeLimit: 60,
  sendCount: 0,
  createdAt: '2026-08-03T10:00:00',
  activatedAt: null,
  deadlineEpochMs: null,
  totalStudents: 0,
  respondedCount: 0,
  correctRate: null,
  distribution: {},
}

describe('live classroom routes', () => {
  beforeEach(() => {
    queryClient.clear()
    clearCsrfToken()
    useLiveStore.getState().reset()
    publishLiveMessage.mockClear()
    localStorage.clear()
  })

  it('renders the public classroom-code entry route', async () => {
    const user = userEvent.setup()
    renderRoute('/live/join')

    expect(await screen.findByRole('heading', { name: '加入实时课堂' })).toBeVisible()
    const input = screen.getByRole('textbox', { name: '6 位课堂码' })
    await user.type(input, 'ab-i0c234')
    expect(input).toHaveValue('ABC234')
    expect(screen.getByRole('button', { name: '进入课堂' })).toBeEnabled()
  })

  it('restores a teacher session and sends a question through the HTTP command endpoint', async () => {
    const sendSpy = vi.fn()
    server.use(
      authenticatedUser(),
      csrf(),
      http.get('*/api/live/active', () =>
        result({
          hasActive: true,
          sessionId: 7,
          sessionCode: 'ABC234',
          title: '函数复习',
          status: 'ACTIVE',
          startedAt: '2026-08-03T10:00:00',
        }),
      ),
      http.get('*/api/live/session/7/interactions', () => result([])),
      http.get('*/api/live/session/7/question-board', () => result([question])),
      http.get('*/api/live/session/7/students', () =>
        result({
          count: 1,
          students: [{ studentId: 'S01', studentName: '张三' }],
          absentCount: 1,
          absentStudents: [{ studentId: 'S02', studentName: '李四' }],
        }),
      ),
      http.get('*/api/live/session/7/interaction-stats', () => result(null)),
      http.get('*/api/live/session/7/confusion-stats', () => result({ stats: [], total: 0 })),
      http.post('*/api/live/session/7/question/11/send', async ({ request }) => {
        sendSpy(await request.json())
        return result({
          interactionId: 101,
          questionId: 11,
          type: 'CHOICE',
          status: 'ACTIVE',
          title: question.title,
          description: question.description,
          options: question.options,
          correctKey: 'A',
          timeLimit: 60,
          deadlineEpochMs: Date.now() + 60_000,
          serverTime: '2026-08-03T10:01:00',
        })
      }),
    )
    const user = userEvent.setup()
    renderRoute('/teacher/live/10')

    expect(await screen.findByRole('heading', { name: '函数复习' })).toBeVisible()
    expect(screen.getByText('ABC234')).toBeVisible()
    expect(screen.getByText('张三')).toBeVisible()
    expect(screen.getByText('李四 · S02')).toBeVisible()
    await user.click(screen.getByRole('button', { name: '发送到课堂' }))

    await waitFor(() => expect(sendSpy).toHaveBeenCalledWith({}))
    expect(await screen.findByText('作答中')).toBeVisible()
  })

  it('confirms a student identity, hydrates history, and publishes an answer', async () => {
    const historyAuthorization = vi.fn()
    const session = {
      sessionId: 7,
      sessionCode: 'ABC234',
      title: '函数复习',
      className: '高一一班',
      teacherName: '王老师',
      token: 'student-token',
      studentId: 'S01',
      studentName: '张三',
      requiresStudentName: false,
      currentInteraction: {
        interactionId: 101,
        questionId: 11,
        type: 'CHOICE',
        status: 'ACTIVE',
        title: '函数定义域是什么？',
        description: null,
        options: question.options,
        correctKey: 'A',
        timeLimit: 60,
        deadlineEpochMs: Date.now() + 60_000,
        serverTime: '2026-08-03T10:01:00',
      },
    }
    server.use(
      csrf(),
      http.get('*/api/live/session/ABC234', () =>
        result({ ...session, token: '', studentId: '', studentName: '', currentInteraction: null }),
      ),
      http.post('*/api/live/quick-join', () => result(null, 401, '设备未绑定')),
      http.post('*/api/live/join', () => result(session)),
      http.get('*/api/live/session/7/interactions', ({ request }) => {
        historyAuthorization(request.headers.get('Authorization'))
        return result([])
      }),
    )
    const user = userEvent.setup()
    renderRoute('/live/ABC234')

    expect(await screen.findByRole('heading', { name: '函数复习' })).toBeVisible()
    await user.type(screen.getByLabelText('学号'), 'S01')
    await user.click(screen.getByRole('button', { name: '确认身份并加入' }))
    expect(await screen.findByText('张三')).toBeVisible()
    expect(historyAuthorization).toHaveBeenCalledWith('Bearer student-token')

    act(() => useLiveStore.getState().setConnectionStatus('connected'))
    await user.click(screen.getByText('自变量允许的取值范围'))
    await user.click(screen.getByRole('button', { name: '提交答案' }))

    expect(publishLiveMessage).toHaveBeenCalledWith(
      '/app/session/7/interaction/101/respond',
      expect.objectContaining({ interactionId: 101, answer: 'A', studentId: 'S01' }),
    )
    expect((await screen.findAllByText('回答正确')).length).toBeGreaterThanOrEqual(1)
  })
})
