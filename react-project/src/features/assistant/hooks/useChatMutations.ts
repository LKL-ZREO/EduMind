import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  addCalendarPlan,
  createChatSession,
  deleteChatSession,
  updateChatSession,
} from '@/features/assistant/api/chatApi'
import { assistantKeys } from '@/features/assistant/api/chatQueries'
import type {
  CalendarPlanPayload,
  ChatSession,
  ChatSessionMutation,
} from '@/features/assistant/model/types'

export function sortChatSessions(sessions: ChatSession[]) {
  return [...sessions].sort((left, right) => {
    if (left.pinned !== right.pinned) return left.pinned ? -1 : 1
    return String(right.updatedAt || '').localeCompare(String(left.updatedAt || ''))
  })
}

export function useChatSessionMutations() {
  const queryClient = useQueryClient()

  const upsertSession = (session: ChatSession) => {
    queryClient.setQueryData<ChatSession[]>(assistantKeys.sessions(), (current = []) =>
      sortChatSessions([
        session,
        ...current.filter((item) => item.sessionId !== session.sessionId),
      ]),
    )
  }

  return {
    createSession: useMutation({
      mutationFn: createChatSession,
      onSuccess: upsertSession,
    }),
    updateSession: useMutation({
      mutationFn: ({ sessionId, payload }: { sessionId: string; payload: ChatSessionMutation }) =>
        updateChatSession(sessionId, payload),
      onSuccess: upsertSession,
    }),
    deleteSession: useMutation({
      mutationFn: deleteChatSession,
      onSuccess: (_, sessionId) => {
        queryClient.setQueryData<ChatSession[]>(assistantKeys.sessions(), (current = []) =>
          current.filter((session) => session.sessionId !== sessionId),
        )
        queryClient.removeQueries({ queryKey: assistantKeys.sessionMessages(sessionId) })
      },
    }),
    addCalendarPlan: useMutation({
      mutationFn: (payload: CalendarPlanPayload) => addCalendarPlan(payload),
    }),
  }
}
