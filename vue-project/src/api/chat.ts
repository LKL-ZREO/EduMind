import request, { getCsrfHeaders } from './request'

export type ChatMode = 'auto' | 'lesson_plan' | 'learning_analysis' | 'grading'

export interface ChatSession {
  sessionId: string
  title: string
  classId: number | null
  className: string | null
  courseId: number | null
  kbIds: number[]
  mode: ChatMode
  pinned: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ChatHistoryMessage {
  id?: number
  role: 'user' | 'assistant'
  content: string
  createdAt?: string
  model?: string
}

export interface ChatSessionMutation {
  title?: string
  classId: number | null
  kbIds: number[]
  mode: ChatMode
  pinned?: boolean
}

export interface ChatStreamRequest {
  message: string
  sessionId: string
  classId: number | null
  kbIds: number[]
  mode: ChatMode
}

export interface ChatStreamEvent {
  type: string
  data: Record<string, unknown>
}

export async function listChatSessions() {
  const response = await request.get<ChatSession[]>('/chat/sessions')
  return response.data
}

export async function createChatSession(payload: ChatSessionMutation) {
  const response = await request.post<ChatSession>('/chat/sessions', payload)
  return response.data
}

export async function updateChatSession(sessionId: string, payload: ChatSessionMutation) {
  const response = await request.patch<ChatSession>(`/chat/sessions/${sessionId}`, payload)
  return response.data
}

export async function deleteChatSession(sessionId: string) {
  await request.delete(`/chat/sessions/${sessionId}`)
}

export async function getChatMessages(sessionId: string) {
  const response = await request.get<ChatHistoryMessage[]>(`/chat/sessions/${sessionId}/messages`)
  return response.data
}

export async function streamChat(
  payload: ChatStreamRequest,
  signal: AbortSignal,
  onEvent: (event: ChatStreamEvent) => void,
) {
  const csrfHeaders = await getCsrfHeaders()
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    credentials: 'same-origin',
    signal,
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...csrfHeaders,
    },
    body: JSON.stringify(payload),
  })
  if (!response.ok) throw new Error(await readErrorMessage(response, 'AI 服务暂时不可用'))
  await consumeEventStream(response, onEvent)
}

export async function streamImageChat(
  formData: FormData,
  signal: AbortSignal,
  onEvent: (event: ChatStreamEvent) => void,
) {
  const csrfHeaders = await getCsrfHeaders()
  const response = await fetch('/api/chat/multimodal/stream', {
    method: 'POST',
    credentials: 'same-origin',
    signal,
    headers: { Accept: 'text/event-stream', ...csrfHeaders },
    body: formData,
  })
  if (!response.ok) throw new Error(await readErrorMessage(response, '图片分析失败'))
  await consumeEventStream(response, onEvent)
}

async function consumeEventStream(response: Response, onEvent: (event: ChatStreamEvent) => void) {
  if (!response.body) throw new Error('浏览器未收到流式响应体')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      let boundary = /\r?\n\r?\n/.exec(buffer)
      while (boundary) {
        const frame = buffer.slice(0, boundary.index)
        buffer = buffer.slice(boundary.index + boundary[0].length)
        dispatchFrame(frame, onEvent)
        boundary = /\r?\n\r?\n/.exec(buffer)
      }
      if (done) {
        if (buffer.trim()) dispatchFrame(buffer, onEvent)
        break
      }
    }
  } finally {
    reader.releaseLock()
  }
}

function dispatchFrame(frame: string, onEvent: (event: ChatStreamEvent) => void) {
  let type = 'message'
  const dataLines: string[] = []
  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith('event:')) type = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (!dataLines.length) return
  const raw = dataLines.join('\n')
  let data: Record<string, unknown> = {}
  try {
    const parsed = JSON.parse(raw) as unknown
    if (typeof parsed === 'object' && parsed !== null) data = parsed as Record<string, unknown>
    else data = { content: raw }
  } catch {
    data = { content: raw }
  }
  onEvent({ type, data })
  if (type === 'error') throw new Error(String(data.message || 'AI 服务暂时不可用'))
}

async function readErrorMessage(response: Response, fallback: string) {
  try {
    const body = (await response.json()) as { message?: string; detail?: string }
    return body.message || body.detail || fallback
  } catch {
    return `${fallback}（HTTP ${response.status}）`
  }
}
