import { ConfigProvider } from 'antd'
import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MaterialDetailModal } from './MaterialDetailModal'
import { queryClient } from '@/shared/query/queryClient'
import { server } from '@/test/server'

function result(data: unknown) {
  return HttpResponse.json({ code: 200, message: 'success', data })
}

function renderModal(props: Partial<React.ComponentProps<typeof MaterialDetailModal>> = {}) {
  render(
    <ConfigProvider>
      <QueryClientProvider client={queryClient}>
        <MaterialDetailModal type="quiz" id={11} onClose={vi.fn()} {...props} />
      </QueryClientProvider>
    </ConfigProvider>,
  )
}

describe('material detail modal', () => {
  beforeEach(() => queryClient.clear())

  it('navigates between saved classroom questions by identifier', async () => {
    server.use(
      http.get('*/api/questions/:id', ({ params }) =>
        result({
          id: Number(params.id),
          type: 'CHOICE',
          title: params.id === '11' ? '第一题' : '第二题',
          options: [],
          correctKey: 'A',
          difficulty: 'medium',
          timeLimit: 60,
        }),
      ),
    )
    const user = userEvent.setup()
    renderModal({ questionIds: [11, 12] })

    expect(await screen.findByRole('heading', { name: '第一题' })).toBeInTheDocument()
    expect(screen.getByText('第 1 / 2 题')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '下一题' }))

    expect(await screen.findByRole('heading', { name: '第二题' })).toBeInTheDocument()
    expect(screen.getByText('第 2 / 2 题')).toBeInTheDocument()
  })

  it('renders self-test questions inside a preview material', async () => {
    server.use(
      http.get('*/api/preview/21', () =>
        result({
          id: 21,
          title: '函数预习',
          guideText: '先阅读定义。',
          discussionQuestion: '函数为什么重要？',
          questions: [
            {
              type: 'CHOICE',
              question: '下列哪项是函数？',
              options: [
                { key: 'A', text: '选项 A' },
                { key: 'B', text: '选项 B' },
              ],
              correctKey: 'A',
            },
          ],
        }),
      ),
    )
    renderModal({ type: 'preview', id: 21 })

    expect(await screen.findByRole('heading', { name: '函数预习' })).toBeInTheDocument()
    expect(screen.getByText('下列哪项是函数？', { exact: false })).toBeInTheDocument()
    expect(screen.getByText('参考答案：A')).toBeInTheDocument()
  })
})
