import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { App, Alert, Button, Input, Modal, Spin } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import {
  chatContextQueryOptions,
  chatMessagesQueryOptions,
  chatSessionsQueryOptions,
  assistantKeys,
} from '@/features/assistant/api/chatQueries'
import { ChatComposer } from '@/features/assistant/components/ChatComposer'
import { ContextHeader } from '@/features/assistant/components/ContextHeader'
import { MessageList } from '@/features/assistant/components/MessageList'
import { SessionSidebar } from '@/features/assistant/components/SessionSidebar'
import { useChatSessionMutations } from '@/features/assistant/hooks/useChatMutations'
import { useChatNavigationGuard } from '@/features/assistant/hooks/useChatNavigationGuard'
import { useChatStreamRunner } from '@/features/assistant/hooks/useChatStream'
import { getContextualPrompts, type ContextualPrompt } from '@/features/assistant/model/prompts'
import type {
  CalendarPlanPayload,
  ChatMode,
  ChatSession,
  LessonArtifact,
  UiMessage,
} from '@/features/assistant/model/types'
import { useChatRunStore } from '@/features/assistant/store/chatRunStore'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './ChatPage.module.css'

type RenameState = { session: ChatSession; value: string } | null
type BranchState = { message: UiMessage; value: string } | null
const EMPTY_MESSAGES: UiMessage[] = []
const ArtifactDrawer = lazy(() =>
  import('@/features/assistant/components/ArtifactDrawer').then((module) => ({
    default: module.ArtifactDrawer,
  })),
)

export function ChatPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { message, modal } = App.useApp()
  const sessionsQuery = useQuery(chatSessionsQueryOptions())
  const contextQuery = useQuery(chatContextQueryOptions())
  const mutations = useChatSessionMutations()
  const responding = useChatRunStore((state) => state.responding)
  const [selectedSessionId, setSelectedSessionId] = useState('')
  const [draftClassId, setDraftClassId] = useState<number | null | undefined>(undefined)
  const [draftKbIds, setDraftKbIds] = useState<number[] | null>(null)
  const [draftMode, setDraftMode] = useState<ChatMode>('auto')
  const [input, setInput] = useState('')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [selectedFileUrl, setSelectedFileUrl] = useState('')
  const [connected, setConnected] = useState(true)
  const [sessionsOpen, setSessionsOpen] = useState(false)
  const [artifactOpen, setArtifactOpen] = useState(false)
  const [artifacts, setArtifacts] = useState<Record<string, LessonArtifact>>({})
  const [renameState, setRenameState] = useState<RenameState>(null)
  const [branchState, setBranchState] = useState<BranchState>(null)
  const messageContainerRef = useRef<HTMLDivElement>(null)

  const sessions = sessionsQuery.data || []
  const activeSessionId = selectedSessionId || sessions[0]?.sessionId || ''
  const activeSession = sessions.find((session) => session.sessionId === activeSessionId) || null
  const messagesQuery = useQuery({
    ...chatMessagesQueryOptions(activeSessionId),
    enabled: Boolean(activeSessionId),
  })
  const messages = messagesQuery.data || EMPTY_MESSAGES
  const classes = contextQuery.data?.classes || []
  const knowledgeBases = contextQuery.data?.knowledgeBases || []
  const effectiveClassId = activeSession
    ? activeSession.classId
    : draftClassId === undefined
      ? classes[0]?.id || null
      : draftClassId
  const effectiveKbIds = activeSession
    ? activeSession.kbIds
    : draftKbIds || knowledgeBases.map((knowledgeBase) => knowledgeBase.id)
  const effectiveMode = activeSession?.mode || draftMode
  const activeClass = classes.find((item) => item.id === effectiveClassId)
  const prompts = useMemo(
    () => getContextualPrompts(activeClass?.name || '当前班级'),
    [activeClass?.name],
  )
  const historyArtifact = useMemo<LessonArtifact | null>(() => {
    if (effectiveMode !== 'lesson_plan') return null
    const lastAssistant = [...messages]
      .reverse()
      .find((chatMessage) => chatMessage.role === 'assistant' && chatMessage.content)
    return lastAssistant
      ? { type: 'lesson_plan', title: 'AI 备课方案', content: lastAssistant.content }
      : null
  }, [effectiveMode, messages])
  const artifact = artifacts[activeSessionId] || historyArtifact

  const handleArtifact = useCallback((sessionId: string, nextArtifact: LessonArtifact) => {
    setArtifacts((current) => ({ ...current, [sessionId]: nextArtifact }))
    setArtifactOpen(true)
  }, [])
  const { runStream, stopGeneration } = useChatStreamRunner({
    onArtifact: handleArtifact,
    onConnectionChange: setConnected,
  })

  useChatNavigationGuard(responding, stopGeneration)

  useEffect(() => {
    if (!selectedFileUrl) return
    return () => URL.revokeObjectURL(selectedFileUrl)
  }, [selectedFileUrl])

  useEffect(() => {
    const container = messageContainerRef.current
    if (container) container.scrollTop = container.scrollHeight
  }, [messages])

  async function createConversation(mode: ChatMode = effectiveMode) {
    if (responding) stopGeneration()
    try {
      const created = await mutations.createSession.mutateAsync({
        classId: effectiveClassId,
        kbIds: effectiveKbIds,
        mode,
      })
      queryClient.setQueryData<UiMessage[]>(assistantKeys.sessionMessages(created.sessionId), [])
      setSelectedSessionId(created.sessionId)
      setSessionsOpen(false)
      return created
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '创建会话失败'))
      return null
    }
  }

  function selectSession(sessionId: string) {
    if (responding) stopGeneration()
    setSelectedSessionId(sessionId)
    setSessionsOpen(false)
  }

  async function updateSession(session: ChatSession, patch: Partial<ChatSession>) {
    try {
      return await mutations.updateSession.mutateAsync({
        sessionId: session.sessionId,
        payload: {
          title: patch.title ?? session.title,
          classId: patch.classId !== undefined ? patch.classId : session.classId,
          kbIds: patch.kbIds || session.kbIds,
          mode: patch.mode || session.mode,
          pinned: patch.pinned !== undefined ? patch.pinned : session.pinned,
        },
      })
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '更新会话失败'))
      return null
    }
  }

  async function handleClassChange(classId: number | null) {
    if (!activeSession) {
      setDraftClassId(classId)
      return
    }
    await updateSession(activeSession, { classId })
  }

  async function handleKnowledgeBasesChange(kbIds: number[]) {
    if (!activeSession) {
      setDraftKbIds(kbIds)
      return
    }
    await updateSession(activeSession, { kbIds })
  }

  async function handleModeChange(mode: ChatMode) {
    if (!activeSession) {
      setDraftMode(mode)
      return
    }
    await updateSession(activeSession, { mode })
  }

  async function sendMessage(
    overrideText?: string,
    modeOverride?: ChatMode,
    forceNewSession = false,
  ) {
    if (responding) return
    const file = overrideText === undefined ? selectedFile : null
    const text =
      (overrideText ?? input).trim() ||
      (file ? '请分析这张图片，并结合当前课程上下文给出教学建议。' : '')
    if (!text) return

    let session = forceNewSession ? null : activeSession
    if (!session) session = await createConversation(modeOverride || effectiveMode)
    else if (modeOverride && session.mode !== modeOverride) {
      session = await updateSession(session, { mode: modeOverride })
    }
    if (!session) return

    if (file) {
      setSelectedFile(null)
      setSelectedFileUrl('')
    }
    setInput('')
    await runStream(session, text, file || undefined)
  }

  async function handleQuickPrompt(prompt: ContextualPrompt) {
    await sendMessage(prompt.prompt, prompt.mode, messages.length > 0)
  }

  async function retryAssistant(index: number) {
    const previousUser = messages
      .slice(0, index)
      .reverse()
      .find((chatMessage) => chatMessage.role === 'user')
    if (previousUser) await sendMessage(previousUser.content)
  }

  function confirmDeleteSession(session: ChatSession) {
    modal.confirm({
      title: `删除“${session.title}”？`,
      content: '会话及其历史消息将被永久删除。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        if (session.sessionId === activeSessionId && responding) stopGeneration()
        try {
          await mutations.deleteSession.mutateAsync(session.sessionId)
          if (session.sessionId === activeSessionId) setSelectedSessionId('')
          message.success('会话已删除')
        } catch (error: unknown) {
          message.error(getApiErrorMessage(error, '删除会话失败'))
          throw error
        }
      },
    })
  }

  async function copyText(text: string) {
    try {
      await navigator.clipboard.writeText(text)
      message.success('已复制')
    } catch {
      message.warning('复制失败，请手动选择')
    }
  }

  async function saveCalendar(payload: CalendarPlanPayload) {
    try {
      await mutations.addCalendarPlan.mutateAsync(payload)
      message.success('已加入备课日历，完整方案保留在当前会话')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '保存备课日历失败'))
      throw error
    }
  }

  const initializing =
    sessionsQuery.isPending ||
    contextQuery.isPending ||
    (Boolean(activeSessionId) && messagesQuery.isPending)
  const workspaceError = sessionsQuery.error || contextQuery.error || messagesQuery.error

  return (
    <div className={styles.workspace}>
      <SessionSidebar
        sessions={sessions}
        activeSessionId={activeSessionId}
        loading={sessionsQuery.isPending}
        connected={connected && !workspaceError}
        mobileOpen={sessionsOpen}
        onMobileClose={() => setSessionsOpen(false)}
        onCreate={() => void createConversation()}
        onSelect={selectSession}
        onRename={(session) => setRenameState({ session, value: session.title })}
        onTogglePin={(session) => void updateSession(session, { pinned: !session.pinned })}
        onDelete={confirmDeleteSession}
      />

      <section className={styles.conversation}>
        <ContextHeader
          classes={classes}
          knowledgeBases={knowledgeBases}
          classId={effectiveClassId}
          kbIds={effectiveKbIds}
          mode={effectiveMode}
          disabled={responding || mutations.updateSession.isPending}
          onClassChange={(value) => void handleClassChange(value)}
          onKnowledgeBasesChange={(value) => void handleKnowledgeBasesChange(value)}
          onModeChange={(value) => void handleModeChange(value)}
          onOpenSessions={() => setSessionsOpen(true)}
          onOpenArtifact={() => setArtifactOpen(true)}
        />

        {workspaceError && !initializing ? (
          <div className={styles.errorState}>
            <Alert
              showIcon
              type="error"
              message="AI 工作台加载失败"
              description={getApiErrorMessage(workspaceError, '无法加载对话数据')}
              action={
                <Button
                  onClick={() => {
                    void sessionsQuery.refetch()
                    void contextQuery.refetch()
                    if (activeSessionId) void messagesQuery.refetch()
                  }}
                >
                  重试
                </Button>
              }
            />
          </div>
        ) : (
          <MessageList
            loading={initializing}
            messages={messages}
            prompts={prompts}
            className={activeClass?.name}
            knowledgeBaseCount={effectiveKbIds.length}
            onPrompt={(prompt) => void handleQuickPrompt(prompt)}
            onCopy={(text) => void copyText(text)}
            onRetry={(index) => void retryAssistant(index)}
            onEdit={(chatMessage) =>
              setBranchState({ message: chatMessage, value: chatMessage.content })
            }
            onOpenCitation={() => void navigate('/teacher/docs')}
            containerRef={messageContainerRef}
          />
        )}

        <ChatComposer
          value={input}
          responding={responding}
          selectedFile={selectedFile}
          selectedFileUrl={selectedFileUrl}
          onChange={setInput}
          onFileSelect={(file) => {
            if (!file.type.startsWith('image/')) {
              message.warning('当前仅支持图片、试题截图和代码截图')
              return
            }
            setSelectedFile(file)
            setSelectedFileUrl(URL.createObjectURL(file))
          }}
          onClearFile={() => {
            setSelectedFile(null)
            setSelectedFileUrl('')
          }}
          onSend={() => void sendMessage()}
          onStop={stopGeneration}
        />
      </section>

      {artifactOpen && (
        <Suspense
          fallback={
            <div className={styles.operationSpin}>
              <Spin />
            </div>
          }
        >
          <ArtifactDrawer
            key={`${activeSessionId}-${artifact?.content || 'empty'}`}
            open
            artifact={artifact}
            classId={effectiveClassId}
            className={activeClass?.name}
            sessionTitle={activeSession?.title}
            saving={mutations.addCalendarPlan.isPending}
            onClose={() => setArtifactOpen(false)}
            onApplyEdit={(content) => {
              if (!activeSessionId || !artifact) return
              setArtifacts((current) => ({
                ...current,
                [activeSessionId]: { ...artifact, content },
              }))
            }}
            onCopy={(content) => void copyText(content)}
            onSaveCalendar={saveCalendar}
            onOpenPreLesson={() =>
              void navigate(
                `/teacher/pre-lesson${effectiveClassId ? `?classId=${effectiveClassId}` : ''}`,
              )
            }
          />
        </Suspense>
      )}

      <Modal
        open={renameState !== null}
        title="重命名会话"
        okText="保存"
        cancelText="取消"
        confirmLoading={mutations.updateSession.isPending}
        onCancel={() => setRenameState(null)}
        onOk={() => {
          if (!renameState?.value.trim()) return
          void updateSession(renameState.session, { title: renameState.value.trim() }).then(
            (updated) => {
              if (updated) setRenameState(null)
            },
          )
        }}
      >
        <Input
          autoFocus
          value={renameState?.value || ''}
          maxLength={80}
          onChange={(event) =>
            setRenameState((current) =>
              current ? { ...current, value: event.target.value } : current,
            )
          }
          onPressEnter={() => {
            const value = renameState?.value.trim()
            if (!renameState || !value) return
            void updateSession(renameState.session, { title: value }).then((updated) => {
              if (updated) setRenameState(null)
            })
          }}
        />
      </Modal>

      <Modal
        open={branchState !== null}
        title="编辑并创建分支会话"
        okText="创建分支并发送"
        cancelText="取消"
        onCancel={() => setBranchState(null)}
        onOk={() => {
          const value = branchState?.value.trim()
          if (!value) return
          setBranchState(null)
          void sendMessage(value, effectiveMode, true)
        }}
      >
        <Input.TextArea
          rows={5}
          value={branchState?.value || ''}
          onChange={(event) =>
            setBranchState((current) =>
              current ? { ...current, value: event.target.value } : current,
            )
          }
        />
      </Modal>

      {(mutations.createSession.isPending || mutations.deleteSession.isPending) && (
        <div className={styles.operationSpin}>
          <Spin />
        </div>
      )}
    </div>
  )
}
