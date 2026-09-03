import { useCallback, useEffect, useRef } from 'react'
import { App } from 'antd'
import { useQueryClient } from '@tanstack/react-query'
import { streamChat, streamImageChat } from '@/features/assistant/api/chatApi'
import { assistantKeys } from '@/features/assistant/api/chatQueries'
import {
  applyStreamEvent,
  createUiMessage,
  finishAssistantMessage,
} from '@/features/assistant/model/messages'
import type {
  ChatSession,
  ChatStreamEvent,
  LessonArtifact,
  UiMessage,
} from '@/features/assistant/model/types'
import { useChatRunStore } from '@/features/assistant/store/chatRunStore'
import { getApiErrorMessage, isAbortError } from '@/shared/api/errors'

type ChatStreamRunnerOptions = {
  onArtifact: (sessionId: string, artifact: LessonArtifact) => void
  onConnectionChange: (connected: boolean) => void
}

export function useChatStreamRunner({ onArtifact, onConnectionChange }: ChatStreamRunnerOptions) {
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const setResponding = useChatRunStore((state) => state.setResponding)
  const abortControllerRef = useRef<AbortController | null>(null)
  const messageSequence = useRef(0)

  const stopGeneration = useCallback(() => {
    abortControllerRef.current?.abort()
  }, [])

  useEffect(() => {
    return () => {
      abortControllerRef.current?.abort()
      setResponding(false)
    }
  }, [setResponding])

  const nextMessageId = useCallback((role: 'user' | 'assistant') => {
    messageSequence.current += 1
    return `${role}-${Date.now()}-${messageSequence.current}`
  }, [])

  const setSessionMessages = useCallback(
    (sessionId: string, update: (current: UiMessage[]) => UiMessage[]) => {
      queryClient.setQueryData<UiMessage[]>(
        assistantKeys.sessionMessages(sessionId),
        (current = []) => update(current),
      )
    },
    [queryClient],
  )

  const runStream = useCallback(
    async (session: ChatSession, text: string, file?: File) => {
      const userMessage = createUiMessage(nextMessageId('user'), 'user', text, file?.name)
      const assistantId = nextMessageId('assistant')
      const assistantMessage = createUiMessage(assistantId, 'assistant', '')
      setSessionMessages(session.sessionId, (current) => [
        ...current,
        userMessage,
        assistantMessage,
      ])
      setResponding(true)
      onConnectionChange(true)
      const controller = new AbortController()
      abortControllerRef.current = controller

      const handleEvent = (event: ChatStreamEvent) => {
        let nextArtifact: LessonArtifact | undefined
        setSessionMessages(session.sessionId, (current) => {
          const reduction = applyStreamEvent(current, assistantId, event)
          nextArtifact = reduction.artifact
          return reduction.messages
        })
        if (nextArtifact) onArtifact(session.sessionId, nextArtifact)
      }

      try {
        if (file) {
          const formData = new FormData()
          formData.append('file', file)
          formData.append('message', text)
          formData.append('sessionId', session.sessionId)
          await streamImageChat(formData, controller.signal, handleEvent)
        } else {
          await streamChat(
            {
              message: text,
              sessionId: session.sessionId,
              classId: session.classId,
              kbIds: session.kbIds,
              mode: session.mode,
            },
            controller.signal,
            handleEvent,
          )
        }
        setSessionMessages(session.sessionId, (current) =>
          finishAssistantMessage(current, assistantId, 'complete'),
        )
        await queryClient.invalidateQueries({ queryKey: assistantKeys.sessions() })
      } catch (error: unknown) {
        if (isAbortError(error)) {
          setSessionMessages(session.sessionId, (current) =>
            finishAssistantMessage(current, assistantId, 'stopped'),
          )
        } else {
          setSessionMessages(session.sessionId, (current) =>
            finishAssistantMessage(current, assistantId, 'error', '本次回答未能完成，请稍后重试。'),
          )
          onConnectionChange(false)
          message.error(getApiErrorMessage(error, 'AI 服务暂时不可用'))
        }
      } finally {
        if (abortControllerRef.current === controller) abortControllerRef.current = null
        setResponding(false)
      }
    },
    [
      message,
      nextMessageId,
      onArtifact,
      onConnectionChange,
      queryClient,
      setResponding,
      setSessionMessages,
    ],
  )

  return { runStream, stopGeneration }
}
