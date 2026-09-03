import { App as AntdApp, ConfigProvider } from 'antd'
import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routeObjects } from '@/app/router'
import type { FlatNode } from '@/features/knowledge/model/types'
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

function knowledgeSpaces() {
  return [
    http.get('*/api/shared-kb/my', () => HttpResponse.json([])),
    http.get('*/api/shared-kb/joined', () => HttpResponse.json([])),
  ]
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

function folder(id: number, label: string): FlatNode {
  return {
    id,
    userId: 1,
    parentId: null,
    label,
    nodeType: 'folder',
    docId: null,
    sortOrder: 0,
    createdAt: '2026-08-01T00:00:00',
    updatedAt: '2026-08-03T00:00:00',
  }
}

describe('knowledge routes', () => {
  beforeEach(() => {
    queryClient.clear()
    clearCsrfToken()
    localStorage.clear()
  })

  it('loads a selected document and sanitizes its Markdown preview', async () => {
    const documentNode: FlatNode = {
      id: 2,
      userId: 1,
      parentId: 1,
      label: '函数笔记.md',
      nodeType: 'file',
      docId: 'doc-functions',
      sortOrder: 0,
      createdAt: '2026-08-01T00:00:00',
      updatedAt: '2026-08-03T00:00:00',
    }
    server.use(
      authenticatedUser(),
      ...knowledgeSpaces(),
      http.get('*/api/documents/directory/tree', () =>
        HttpResponse.json([folder(1, '高一数学'), documentNode]),
      ),
      http.get('*/api/documents/doc-functions/content', () =>
        HttpResponse.text('# 函数学习\n\n<img src="x" onerror="alert(1)">'),
      ),
      http.get('*/api/documents/doc-functions/materials', () =>
        HttpResponse.json({ previews: [], questions: [] }),
      ),
    )
    const user = userEvent.setup()
    renderRoute('/teacher/docs')

    await user.click((await screen.findAllByText('函数笔记.md'))[0]!)

    expect(await screen.findByRole('heading', { name: '函数学习' })).toBeVisible()
    expect(document.querySelector('img[src="x"]')).not.toHaveAttribute('onerror')
  })

  it('creates a folder and refetches only the active directory tree', async () => {
    let tree: FlatNode[] = []
    let treeRequests = 0
    const createSpy = vi.fn()
    server.use(
      authenticatedUser(),
      ...knowledgeSpaces(),
      http.get('*/api/auth/csrf', () => result({ token: 'knowledge-csrf' })),
      http.get('*/api/documents/directory/tree', () => {
        treeRequests += 1
        return HttpResponse.json(tree)
      }),
      http.post('*/api/documents/directory/folder', async ({ request }) => {
        createSpy(await request.json())
        tree = [folder(10, '必修一')]
        return HttpResponse.json({ id: 10 })
      }),
    )
    const user = userEvent.setup()
    renderRoute('/teacher/docs')

    await user.click(await screen.findByRole('button', { name: '新建文件夹' }))
    await user.type(screen.getByPlaceholderText('文件夹名称'), '必修一')
    await user.click(screen.getByRole('button', { name: /^创\s*建$/ }))

    expect((await screen.findAllByText('必修一')).length).toBeGreaterThan(0)
    expect(createSpy).toHaveBeenCalledWith({ label: '必修一', kbId: null })
    expect(treeRequests).toBe(2)
  })

  it('consumes a deep-linked invitation token and removes it from the URL', async () => {
    const joinSpy = vi.fn()
    server.use(
      authenticatedUser(),
      ...knowledgeSpaces(),
      http.get('*/api/auth/csrf', () => result({ token: 'knowledge-csrf' })),
      http.get('*/api/documents/directory/tree', () => HttpResponse.json([])),
      http.post('*/api/shared-kb/join', ({ request }) => {
        joinSpy(new URL(request.url).searchParams.get('token'))
        return HttpResponse.json(null)
      }),
    )
    const user = userEvent.setup()
    const router = renderRoute('/teacher/docs?joinToken=team-123')

    const dialog = await screen.findByRole('dialog', { name: '加入团队知识库' })
    expect(within(dialog).getByDisplayValue('team-123')).toHaveValue('team-123')
    await user.click(within(dialog).getByRole('button', { name: /^加\s*入$/ }))

    await waitFor(() => expect(joinSpy).toHaveBeenCalledWith('team-123'))
    await waitFor(() => expect(router.state.location.search).toBe(''))
  })
})
