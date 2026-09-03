import apiClient, { notifyUnauthorized } from '@/shared/api/client'
import { ensureCsrfToken } from '@/shared/api/csrf'
import type { ApiResponse } from '@/shared/api/types'
import { unwrapApiResponse } from '@/shared/api/unwrap'
import type {
  CalendarPlanPayload,
  ChatContextOptions,
  ChatHistoryMessage,
  ChatSession,
  ChatSessionMutation,
  ChatStreamEvent,
  ChatStreamRequest,
  ClassroomOption,
  KnowledgeBaseOption,
} from '@/features/assistant/model/types'

type ApiProblemBody = {
  code?: unknown
  message?: unknown
  detail?: unknown
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function textValue(value: unknown, fallback: string) {
  return typeof value === 'string' ? value : fallback
}

export async function listChatSessions(): Promise<ChatSession[]> {
  const response = await apiClient.get<ChatSession[]>('/chat/sessions')
  return response.data || []
}

export async function createChatSession(payload: ChatSessionMutation): Promise<ChatSession> {
  const response = await apiClient.post<ChatSession>('/chat/sessions', payload)
  return response.data
}

export async function updateChatSession(
  sessionId: string,
  payload: ChatSessionMutation,
): Promise<ChatSession> {
  const encodedSessionId = encodeURIComponent(sessionId)
  const response = await apiClient.patch<ChatSession>(`/chat/sessions/${encodedSessionId}`, payload)
  return response.data
}

export async function deleteChatSession(sessionId: string): Promise<void> {
  await apiClient.delete(`/chat/sessions/${encodeURIComponent(sessionId)}`)
}

export async function getChatMessages(sessionId: string): Promise<ChatHistoryMessage[]> {
  const response = await apiClient.get<ChatHistoryMessage[]>(
    `/chat/sessions/${encodeURIComponent(sessionId)}/messages`,
  )
  return (response.data || []).filter(
    (message): message is ChatHistoryMessage =>
      (message.role === 'user' || message.role === 'assistant') &&
      typeof message.content === 'string',
  )
}

export async function getChatContextOptions(): Promise<ChatContextOptions> {
  const [classResponse, ownedResponse, joinedResponse] = await Promise.all([
    apiClient.get<ApiResponse<ClassroomOption[]>>('/dashboard/classes'),
    apiClient.get<KnowledgeBaseOption[]>('/shared-kb/my'),
    apiClient.get<KnowledgeBaseOption[]>('/shared-kb/joined'),
  ])
  const classes = unwrapApiResponse(classResponse.data, '加载班级上下文失败') || []
  const knowledgeBaseMap = new Map<number, KnowledgeBaseOption>()
  for (const knowledgeBase of [...ownedResponse.data, ...joinedResponse.data]) {
    knowledgeBaseMap.set(knowledgeBase.id, knowledgeBase)
  }
  return { classes, knowledgeBases: [...knowledgeBaseMap.values()] }
}

export async function addCalendarPlan(payload: CalendarPlanPayload): Promise<void> {
  const response = await apiClient.post<ApiResponse<unknown>>(
    '/dashboard/teaching-calendar/add',
    payload,
  )
  unwrapApiResponse(response.data, '保存备课日历失败')
}

export async function streamChat(
  payload: ChatStreamRequest,
  signal: AbortSignal,
  onEvent: (event: ChatStreamEvent) => void,
) {
  await postEventStream('/api/chat/stream', JSON.stringify(payload), true, signal, onEvent)
}

export async function streamImageChat(
  formData: FormData,
  signal: AbortSignal,
  onEvent: (event: ChatStreamEvent) => void,
) {
  await postEventStream('/api/chat/multimodal/stream', formData, false, signal, onEvent)
}

async function postEventStream(
  url: string,
  body: BodyInit,
  json: boolean,
  signal: AbortSignal,
  onEvent: (event: ChatStreamEvent) => void,
  csrfRetried = false,
): Promise<void> {
  const token = await ensureCsrfToken(csrfRetried)
  const response = await fetch(url, {
    method: 'POST',
    credentials: 'same-origin',
    signal,
    headers: {
      Accept: 'text/event-stream',
      'X-XSRF-TOKEN': token,
      ...(json ? { 'Content-Type': 'application/json' } : {}),
    },
    body,
  })

  if (!response.ok) {
    const problem = await readProblem(response)
    if (response.status === 403 && problem.code === 40301 && !csrfRetried) {
      return postEventStream(url, body, json, signal, onEvent, true)
    }
    if (response.status === 401) notifyUnauthorized()
    throw new Error(problem.message || `AI 服务暂时不可用（HTTP ${response.status}）`)
  }

  await consumeEventStream(response, onEvent)
}

async function readProblem(response: Response): Promise<{ code?: number; message?: string }> {
  try {
    const body = (await response.json()) as ApiProblemBody
    const code = typeof body.code === 'number' ? body.code : undefined
    const message =
      typeof body.message === 'string'
        ? body.message
        : typeof body.detail === 'string'
          ? body.detail
          : undefined
    return { code, message }
  } catch {
    return {}
  }
}

export async function consumeEventStream(
  response: Response,
  onEvent: (event: ChatStreamEvent) => void,
) {
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
  let data: Record<string, unknown>
  try {
    const parsed: unknown = JSON.parse(raw)
    data = isRecord(parsed) ? parsed : { content: raw }
  } catch {
    data = { content: raw }
  }

  onEvent({ type, data })
  if (type === 'error') throw new Error(textValue(data.message, 'AI 服务暂时不可用'))
}
