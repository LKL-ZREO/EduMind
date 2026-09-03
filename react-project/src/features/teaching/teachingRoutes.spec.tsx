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

vi.mock('@/shared/charts/EChart', () => ({
  EChart: ({ ariaLabel }: { ariaLabel: string }) => <div role="img" aria-label={ariaLabel} />,
}))

function result(data: unknown, code = 200, message = 'success') {
  return HttpResponse.json({ code, message, data })
}

function authenticatedUser() {
  return http.get('*/api/auth/me', () =>
    result({ id: 1, username: 'teacher1', email: 'teacher1@example.com' }),
  )
}

function teachingClasses() {
  return http.get('*/api/dashboard/classes', () => result([{ id: 10, name: '高一一班' }]))
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

describe('teaching routes', () => {
  beforeEach(() => {
    queryClient.clear()
    clearCsrfToken()
    localStorage.clear()
  })

  it('renders the dashboard from independently cached teaching queries', async () => {
    server.use(
      authenticatedUser(),
      teachingClasses(),
      http.get('*/api/dashboard/metrics', () =>
        result({
          totalStudents: 30,
          studentTrend: 1,
          totalHomework: 5,
          newHomework: 1,
          avgScore: 76.5,
          scoreTrend: 2,
          warningStudents: 1,
        }),
      ),
      http.get('*/api/dashboard/score-distribution', () =>
        result([{ range: '60-69', count: 4, percentage: 13.3, color: '#4aa39a' }]),
      ),
      http.get('*/api/dashboard/knowledge-mastery', () =>
        result([
          {
            id: 1,
            name: '函数单调性',
            mastery: 52,
            errorCount: 8,
            criticalCount: 2,
            color: '#ef6b73',
          },
        ]),
      ),
      http.get('*/api/dashboard/frequent-errors', () =>
        result([
          {
            question: '判断函数的单调区间',
            difficulty: 'MEDIUM',
            errorRate: 48,
            errorCount: 12,
            knowledgePoint: '函数单调性',
          },
        ]),
      ),
      http.get('*/api/dashboard/students', () =>
        result([
          {
            id: 1,
            name: '张三',
            studentId: 'S01',
            avgScore: 55,
            homeworkCount: 4,
            errorCount: 8,
            trend: -2,
            needAttention: true,
          },
        ]),
      ),
      http.get('*/api/dashboard/student-confusions', () =>
        result([
          {
            id: 1,
            studentName: '张三',
            knowledgePoint: '函数单调性',
            question: '为什么要先求定义域？',
            createdAt: '2026-08-03T10:00:00',
          },
        ]),
      ),
      http.get('*/api/dashboard/student-confusions/stats', () =>
        result([{ name: '函数单调性', count: 2 }]),
      ),
      http.get('*/api/dashboard/live-confusions', () =>
        result({ stats: [{ name: '函数单调性', count: 1 }], events: [], total: 1 }),
      ),
    )
    renderRoute('/teacher/data')

    expect(await screen.findByRole('heading', { name: '教学数据中心' })).toBeVisible()
    const averageScoreLabel = await screen.findByText('班级平均分')
    expect(averageScoreLabel.closest('.ant-card-body')).toHaveTextContent('76.5')
    expect(screen.getByRole('img', { name: '班级成绩分布柱状图' })).toBeVisible()
    expect(screen.getByRole('img', { name: '知识点掌握度条形图' })).toBeVisible()
    expect(screen.getByRole('button', { name: '张三' })).toBeVisible()
    expect(screen.getAllByText(/函数单调性/).length).toBeGreaterThan(1)
  })

  it('autosaves an editable pre-lesson draft for the selected class', async () => {
    server.use(
      authenticatedUser(),
      teachingClasses(),
      http.get('*/api/dashboard/pre-lesson', () =>
        result({
          classId: 10,
          className: '高一一班',
          avgScore: 76,
          totalStudents: 30,
          warningCount: 2,
          weakPoints: [{ name: '函数单调性', errorCount: 8, mastery: 52, severity: 'HIGH' }],
          liveSessionCount: 1,
          liveAvgCorrectRate: 68,
          participationRate: 88,
          aiSuggestion: '先用典型错题复习定义域。',
          tieredGroups: [{ label: '基础组', range: '0-59', count: 2, suggestion: '提供步骤卡' }],
        }),
      ),
      http.get('*/api/dashboard/timeline', () => result({ weeks: [] })),
    )
    renderRoute('/teacher/pre-lesson')

    expect(await screen.findByRole('heading', { name: '课前备课工作台' })).toBeVisible()
    const topicInput = await screen.findByDisplayValue('函数单调性巩固与应用')
    await userEvent.setup().clear(topicInput)
    await userEvent.setup().type(topicInput, '函数单调性专项课')

    await waitFor(
      () =>
        expect(localStorage.getItem('edumind:lesson-preparation:10')).toContain('函数单调性专项课'),
      { timeout: 2_000 },
    )
  })

  it('loads a public preview and keeps quiz answers as local UI state', async () => {
    server.use(
      http.get('*/api/preview/88', () =>
        result({
          id: 88,
          classId: 10,
          title: '函数单调性课前预习',
          knowledgePoint: '函数单调性',
          guideText: '先阅读 **定义**，再完成自测。<script>alert(1)</script>',
          questions: [
            {
              question: '增函数的定义是什么？',
              options: [
                { key: 'A', text: '自变量增大时函数值增大' },
                { key: 'B', text: '函数值恒定' },
              ],
              correctKey: 'A',
              explanation: '比较定义域内任意两个自变量。',
            },
            {
              question: '说明定义域为什么重要。',
              options: null,
              correctKey: '单调性只在定义域内讨论。',
              explanation: '先确定研究范围。',
            },
          ],
          discussionQuestion: '为什么定义域是判断单调性的前提？',
          status: 'ACTIVE',
          createdAt: '2026-08-03T10:00:00',
        }),
      ),
    )
    const user = userEvent.setup()
    const { container } = renderRoute('/preview/88')

    expect(await screen.findByRole('heading', { name: '函数单调性课前预习' })).toBeVisible()
    expect(container.querySelector('script')).toBeNull()
    await user.click(screen.getByRole('radio', { name: /A\. 自变量增大时函数值增大/ }))
    await user.click(screen.getByRole('button', { name: '提交自测' }))
    expect(await screen.findByText('答对 1 / 1 题')).toBeVisible()
    expect(screen.getByText('参考答案：A')).toBeVisible()
    expect(screen.getByText('参考答案：单调性只在定义域内讨论。')).toBeVisible()
  })
})
