import { queryOptions } from '@tanstack/react-query'
import {
  getChatContextOptions,
  getChatMessages,
  listChatSessions,
} from '@/features/assistant/api/chatApi'
import { historyToUiMessage } from '@/features/assistant/model/messages'

export const assistantKeys = {
  all: ['assistant'] as const,
  sessions: () => [...assistantKeys.all, 'sessions'] as const,
  messages: () => [...assistantKeys.all, 'messages'] as const,
  sessionMessages: (sessionId: string) => [...assistantKeys.messages(), sessionId] as const,
  context: () => [...assistantKeys.all, 'context'] as const,
}

export const chatSessionsQueryOptions = () =>
  queryOptions({
    queryKey: assistantKeys.sessions(),
    queryFn: listChatSessions,
    staleTime: 30_000,
  })

export const chatMessagesQueryOptions = (sessionId: string) =>
  queryOptions({
    queryKey: assistantKeys.sessionMessages(sessionId),
    queryFn: async () => (await getChatMessages(sessionId)).map(historyToUiMessage),
    staleTime: 30_000,
  })

export const chatContextQueryOptions = () =>
  queryOptions({
    queryKey: assistantKeys.context(),
    queryFn: getChatContextOptions,
    staleTime: 5 * 60_000,
  })
