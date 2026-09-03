import type {
  ChatHistoryMessage,
  ChatStreamEvent,
  Citation,
  LessonArtifact,
  MessageState,
  UiMessage,
} from './types'

function textValue(value: unknown, fallback = '') {
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return fallback
}

export function formatChatTime(value?: string) {
  const date = value ? new Date(value) : new Date()
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

export function historyToUiMessage(message: ChatHistoryMessage): UiMessage {
  return {
    localId: `history-${message.id || crypto.randomUUID()}`,
    role: message.role,
    content: message.content,
    time: formatChatTime(message.createdAt),
    state: 'complete',
    toolSteps: [],
    citations: [],
  }
}

export function createUiMessage(
  localId: string,
  role: 'user' | 'assistant',
  content: string,
  attachmentName?: string,
): UiMessage {
  return {
    localId,
    role,
    content,
    attachmentName,
    time: formatChatTime(),
    state: role === 'assistant' ? 'streaming' : 'complete',
    toolSteps: [],
    citations: [],
  }
}

function updateMessage(
  messages: UiMessage[],
  localId: string,
  update: (message: UiMessage) => UiMessage,
) {
  return messages.map((message) => (message.localId === localId ? update(message) : message))
}

export function finishAssistantMessage(
  messages: UiMessage[],
  localId: string,
  state: MessageState,
  fallback?: string,
) {
  return updateMessage(messages, localId, (message) => ({
    ...message,
    content: message.content || fallback || '',
    state,
  }))
}

export function applyStreamEvent(
  messages: UiMessage[],
  assistantId: string,
  event: ChatStreamEvent,
): { messages: UiMessage[]; artifact?: LessonArtifact } {
  const data = event.data

  if (event.type === 'token') {
    return {
      messages: updateMessage(messages, assistantId, (message) => ({
        ...message,
        content: message.content + textValue(data.content),
      })),
    }
  }

  if (event.type === 'tool_started') {
    return {
      messages: updateMessage(messages, assistantId, (message) => ({
        ...message,
        toolSteps: [
          ...message.toolSteps,
          {
            tool: textValue(data.tool),
            label: textValue(data.label, '调用教学工具'),
            status: 'running',
          },
        ],
      })),
    }
  }

  if (event.type === 'tool_completed') {
    const tool = textValue(data.tool)
    return {
      messages: updateMessage(messages, assistantId, (message) => {
        let matched = false
        const toolSteps = [...message.toolSteps].reverse().map((step) => {
          if (!matched && step.tool === tool && step.status === 'running') {
            matched = true
            return {
              ...step,
              status: data.success === false ? ('error' as const) : ('success' as const),
              elapsedMs: Number(data.elapsedMs || 0),
            }
          }
          return step
        })
        return { ...message, toolSteps: toolSteps.reverse() }
      }),
    }
  }

  if (event.type === 'citation') {
    const citation: Citation = {
      documentId: textValue(data.documentId),
      documentName: textValue(data.documentName, '教学资料'),
      sectionIndex: Number(data.sectionIndex || 0),
      excerpt: textValue(data.excerpt),
    }
    return {
      messages: updateMessage(messages, assistantId, (message) => {
        const duplicate = message.citations.some(
          (item) =>
            item.documentId === citation.documentId && item.sectionIndex === citation.sectionIndex,
        )
        return duplicate ? message : { ...message, citations: [...message.citations, citation] }
      }),
    }
  }

  if (event.type === 'artifact' && data.type === 'lesson_plan') {
    const assistant = messages.find((message) => message.localId === assistantId)
    return {
      messages,
      artifact: {
        type: 'lesson_plan',
        title: textValue(data.title, 'AI 备课方案'),
        content: textValue(data.content, assistant?.content || ''),
      },
    }
  }

  return { messages }
}
