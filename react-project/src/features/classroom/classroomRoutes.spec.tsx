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

function emptyCoursePresets() {
  return http.get('*/api/courses/presets', () => result({}))
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

const mathCourse = {
  id: 10,
  name: '高中数学',
  systemPrompt: '数学助手',
  knowledgeScope: '必修一',
  teacherId: 1,
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
}

describe('classroom routes', () => {
  beforeEach(() => {
    queryClient.clear()
    clearCsrfToken()
    localStorage.clear()
  })

  it('renders grouped classes and keeps search in the URL', async () => {
    server.use(
      authenticatedUser(),
      http.get('*/api/courses', () => result([mathCourse])),
      emptyCoursePresets(),
      http.get('*/api/teacher/classes', () =>
        result([
          {
            courseGroup: '高中数学',
            courseId: 10,
            classes: [
              {
                id: 101,
                name: '高一三班',
                description: '函数与集合',
                courseGroup: '高中数学',
                courseId: 10,
                qqGroupId: '',
                studentCount: 36,
                inviteCode: 'MATH01',
                status: 'ACTIVE',
                createdAt: '2026-07-01T00:00:00',
              },
            ],
          },
        ]),
      ),
    )
    const user = userEvent.setup()
    const router = renderRoute('/teacher/classes')

    expect(await screen.findByText('高一三班')).toBeVisible()
    expect(screen.getByText('36')).toBeVisible()

    await user.type(screen.getByPlaceholderText('搜索班级或课程'), '三班')
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).get('q')).toBe('三班'),
    )
  })

  it('creates a class and refreshes the class-groups query', async () => {
    let created = false
    const createSpy = vi.fn()
    server.use(
      authenticatedUser(),
      http.get('*/api/courses', () => result([mathCourse])),
      emptyCoursePresets(),
      http.get('*/api/auth/csrf', () => result({ token: 'classroom-csrf' })),
      http.get('*/api/teacher/classes', () =>
        result(
          created
            ? [
                {
                  courseGroup: null,
                  courseId: null,
                  classes: [
                    {
                      id: 202,
                      name: '新建班级',
                      description: '',
                      courseGroup: '',
                      courseId: null,
                      qqGroupId: '',
                      studentCount: 0,
                      inviteCode: 'NEW202',
                      status: 'ACTIVE',
                      createdAt: '2026-08-03T00:00:00',
                    },
                  ],
                },
              ]
            : [],
        ),
      ),
      http.post('*/api/teacher/classes', async ({ request }) => {
        createSpy(await request.json())
        created = true
        return result({ id: 202, name: '新建班级', inviteCode: 'NEW202' })
      }),
    )
    const user = userEvent.setup()
    renderRoute('/teacher/classes')

    await user.click(await screen.findByRole('button', { name: '创建班级' }))
    await user.type(screen.getByLabelText('班级名称'), '新建班级')
    await user.click(screen.getByRole('button', { name: /^创\s*建$/ }))

    expect(await screen.findByText('新建班级')).toBeVisible()
    expect(createSpy).toHaveBeenCalledWith({ name: '新建班级' })
  })

  it('removes a student and refreshes class detail', async () => {
    let students = [
      {
        studentId: 'S001',
        studentName: '张三',
        source: 'manual',
        createdAt: '2026-07-01T00:00:00',
      },
    ]
    server.use(
      authenticatedUser(),
      http.get('*/api/courses', () => result([mathCourse])),
      http.get('*/api/teacher/classes', () => result([])),
      http.get('*/api/teacher/classes/101', () =>
        result({
          class: {
            id: 101,
            name: '高一三班',
            description: '函数与集合',
            courseGroup: '高中数学',
            courseId: 10,
            qqGroupId: '',
            inviteCode: 'MATH01',
            status: 'ACTIVE',
            createdAt: '2026-07-01T00:00:00',
          },
          students,
        }),
      ),
      http.get('*/api/auth/csrf', () => result({ token: 'classroom-csrf' })),
      http.delete('*/api/teacher/classes/101/students/S001', () => {
        students = []
        return result(null)
      }),
    )
    const user = userEvent.setup()
    renderRoute('/teacher/classes/101')

    expect(await screen.findByText('张三')).toBeVisible()
    await user.click(screen.getByRole('button', { name: '移除学生 张三' }))
    await user.click(await screen.findByRole('button', { name: /^移\s*除$/ }))

    await waitFor(() => expect(screen.queryByText('张三')).not.toBeInTheDocument())
  })
})
