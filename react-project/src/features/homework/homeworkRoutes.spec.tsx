import { App as AntdApp, ConfigProvider } from 'antd'
import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routeObjects } from '@/app/router'
import { clearCsrfToken } from '@/shared/api/csrf'
import { queryClient } from '@/shared/query/queryClient'
import { server } from '@/test/server'

function result(data: unknown, code = 200, message = 'success') {
  return HttpResponse.json({ code, message, data })
}

function authenticatedUser() {
  return http.get('*/api/auth/me', () =>
    result({ id: 1, username: 'teacher1', email: 'teacher1@example.com' }),
  )
}

function classGroups() {
  return http.get('*/api/teacher/classes', () =>
    result([
      {
        courseGroup: '数学',
        courseId: 1,
        classes: [
          {
            id: 10,
            name: '高一一班',
            description: '',
            courseGroup: '数学',
            courseId: 1,
            qqGroupId: '',
            studentCount: 30,
            inviteCode: 'MATH01',
            status: 'ACTIVE',
            createdAt: '2026-08-01T00:00:00',
          },
        ],
      },
    ]),
  )
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

describe('homework routes', () => {
  beforeEach(() => {
    queryClient.clear()
    clearCsrfToken()
    localStorage.clear()
  })

  it('saves the latest teacher draft before publishing it to the selected class', async () => {
    const saveSpy = vi.fn()
    const publishSpy = vi.fn()
    server.use(
      authenticatedUser(),
      classGroups(),
      http.get('*/api/tasks', () => result([])),
      http.get('*/api/tasks/drafts', () => result([])),
      http.get('*/api/questions', () => result([])),
      http.get('*/api/auth/csrf', () => result({ token: 'homework-csrf' })),
      http.post('*/api/tasks/drafts', async ({ request }) => {
        const body = await request.json()
        if (!body || typeof body !== 'object' || Array.isArray(body)) {
          return result(null, 400, 'invalid body')
        }
        saveSpy(body)
        return result({
          id: 41,
          ...body,
          status: 'draft',
          createdAt: '2026-08-03T00:00:00',
          updatedAt: '2026-08-03T00:00:00',
        })
      }),
      http.post('*/api/tasks/drafts/41/publish', async ({ request }) => {
        publishSpy(await request.json())
        return result([])
      }),
    )
    const user = userEvent.setup()
    renderRoute('/teacher/tasks')

    await user.type(await screen.findByLabelText('作业名称'), '函数单元练习')
    await user.type(screen.getByLabelText('截止时间'), '2026-08-10T20:00')
    await user.click(screen.getByRole('button', { name: '发布当前草稿' }))

    await waitFor(() => expect(saveSpy).toHaveBeenCalled())
    expect(saveSpy.mock.calls[0]?.[0]).toMatchObject({ taskName: '函数单元练习' })
    await waitFor(() =>
      expect(publishSpy).toHaveBeenCalledWith(
        expect.objectContaining({ classIds: [10], taskName: '函数单元练习' }),
      ),
    )
  })

  it('submits a public student file and polls until grading completes', async () => {
    server.use(
      http.get('*/api/homework/classes', () => result([{ id: 10, name: '高一一班' }])),
      http.get('*/api/homework/tasks', () =>
        result([
          {
            id: 20,
            taskName: '函数作业',
            description: '<p>完成 <span class="math-inline" data-latex="x^2"></span></p>',
            deadline: '2099-08-10T20:00:00',
            allowLate: true,
            latePenalty: 5,
          },
        ]),
      ),
      http.get('*/api/auth/csrf', () => result({ token: 'public-csrf' })),
      http.post('*/api/homework/submit', () =>
        result({
          submissionId: 99,
          studentId: '2026001',
          studentName: '张三',
          submitCount: 1,
          remainingAttempts: 2,
        }),
      ),
      http.get('*/api/homework/result/99', () =>
        result({
          submissionId: 99,
          status: 'COMPLETED',
          totalScore: 95,
          overallComment: '思路清晰',
          strengths: ['步骤完整'],
          weaknesses: [],
          suggestions: '继续保持',
        }),
      ),
    )
    const user = userEvent.setup()
    const { container } = renderRoute('/')

    const selects = await screen.findAllByRole('combobox')
    await user.click(selects[0]!)
    await user.click(await screen.findByTitle('高一一班'))
    await waitFor(() => expect(selects[1]).toBeEnabled())
    await user.click(selects[1]!)
    await user.click(await screen.findByTitle('函数作业'))

    const input = container.querySelector<HTMLInputElement>('input[type="file"]')
    expect(input).not.toBeNull()
    await user.upload(
      input!,
      new File(['homework'], '2026001_张三_高一一班_函数作业.txt', { type: 'text/plain' }),
    )
    await user.click(screen.getByRole('button', { name: '提交作业' }))

    expect(await screen.findByText('作业提交成功')).toBeVisible()
    expect(await screen.findByText('95')).toBeVisible()
    expect(screen.getByText('思路清晰')).toBeVisible()
  })

  it('renders task statistics and a protected submission source view', async () => {
    server.use(
      authenticatedUser(),
      classGroups(),
      http.get('*/api/tasks/50', () =>
        result({
          id: 50,
          classId: 10,
          taskName: '函数检测',
          description: '',
          deadline: '2026-08-10T20:00:00',
          allowLate: true,
          latePenalty: 5,
          status: 'active',
          createdAt: '2026-08-03T00:00:00',
          submittedCount: 1,
          totalSubmissions: 1,
          avgScore: 88,
          distribution: { excellent: 0, good: 1, medium: 0, pass: 0, fail: 0 },
          submissions: [
            {
              submissionId: 501,
              studentId: '2026001',
              studentName: '张三',
              score: 88,
              finalScore: 88,
              isLate: false,
              submittedAt: '2026-08-03T10:00:00',
            },
          ],
        }),
      ),
      http.get('*/api/submissions/501/content', () =>
        result({
          submissionId: 501,
          studentName: '张三',
          fileName: '函数作业.txt',
          content: '第一题答案：定义域为 R',
        }),
      ),
    )

    const first = renderRoute('/teacher/tasks/50')
    expect(await screen.findByRole('heading', { name: '函数检测' })).toBeVisible()
    expect(screen.getByText('88 分')).toBeVisible()
    first.unmount()
    queryClient.clear()

    renderRoute('/view/submission/501')
    expect(await screen.findByRole('heading', { name: '函数作业.txt' })).toBeVisible()
    expect(screen.getByText('第一题答案：定义域为 R')).toBeVisible()
  })
})
