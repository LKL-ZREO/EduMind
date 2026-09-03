import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfToken } from '@/shared/api/csrf'
import { server } from '@/test/server'
import { consumeEventStream, streamChat } from './chatApi'

function streamingResponse(chunks: string[]) {
  const encoder = new TextEncoder()
  return new Response(
    new ReadableStream({
      start(controller) {
        for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
        controller.close()
      },
    }),
    { headers: { 'Content-Type': 'text/event-stream' } },
  )
}

describe('SSE parser', () => {
  beforeEach(() => clearCsrfToken())

  it('reassembles frames split across network chunks', async () => {
    const onEvent = vi.fn()
    const response = streamingResponse([
      'event: token\ndata: {"content":"你',
      '好"}\n\nevent: tool_started\ndata: {"tool":"rag",',
      '"label":"检索知识库"}\n\nevent: done\ndata: {}\n\n',
    ])

    await consumeEventStream(response, onEvent)

    expect(onEvent).toHaveBeenCalledTimes(3)
    expect(onEvent).toHaveBeenNthCalledWith(1, { type: 'token', data: { content: '你好' } })
    expect(onEvent).toHaveBeenNthCalledWith(2, {
      type: 'tool_started',
      data: { tool: 'rag', label: '检索知识库' },
    })
    expect(onEvent).toHaveBeenNthCalledWith(3, { type: 'done', data: {} })
  })

  it('joins multiline data and preserves non-JSON content', async () => {
    const onEvent = vi.fn()
    await consumeEventStream(
      streamingResponse(['event: token\ndata: 第一行\ndata: 第二行']),
      onEvent,
    )

    expect(onEvent).toHaveBeenCalledWith({
      type: 'token',
      data: { content: '第一行\n第二行' },
    })
  })

  it('dispatches an error event before rejecting the stream', async () => {
    const onEvent = vi.fn()
    const response = streamingResponse(['event: error\ndata: {"message":"模型不可用"}\n\n'])

    await expect(consumeEventStream(response, onEvent)).rejects.toThrow('模型不可用')
    expect(onEvent).toHaveBeenCalledWith({
      type: 'error',
      data: { message: '模型不可用' },
    })
  })

  it('refreshes an expired CSRF token and retries the stream once', async () => {
    const csrfTokens: string[] = []
    let csrfRequests = 0
    let streamRequests = 0
    server.use(
      http.get('*/api/auth/csrf', () => {
        csrfRequests += 1
        return HttpResponse.json({
          code: 200,
          message: 'success',
          data: { token: `csrf-${csrfRequests}` },
        })
      }),
      http.post('*/api/chat/stream', ({ request }) => {
        streamRequests += 1
        csrfTokens.push(request.headers.get('X-XSRF-TOKEN') || '')
        if (streamRequests === 1) {
          return HttpResponse.json({ code: 40301, message: 'CSRF token expired' }, { status: 403 })
        }
        return new HttpResponse('event: done\ndata: {}\n\n', {
          headers: { 'Content-Type': 'text/event-stream' },
        })
      }),
    )

    await streamChat(
      { message: '测试', sessionId: 'session-1', classId: null, kbIds: [], mode: 'auto' },
      new AbortController().signal,
      vi.fn(),
    )

    expect(csrfRequests).toBe(2)
    expect(streamRequests).toBe(2)
    expect(csrfTokens).toEqual(['csrf-1', 'csrf-2'])
  })
})
