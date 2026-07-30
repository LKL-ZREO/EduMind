<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js/lib/core'
import cLanguage from 'highlight.js/lib/languages/c'
import cppLanguage from 'highlight.js/lib/languages/cpp'
import javaLanguage from 'highlight.js/lib/languages/java'
import javascriptLanguage from 'highlight.js/lib/languages/javascript'
import jsonLanguage from 'highlight.js/lib/languages/json'
import pythonLanguage from 'highlight.js/lib/languages/python'
import typescriptLanguage from 'highlight.js/lib/languages/typescript'
import 'highlight.js/styles/github-dark.css'
import request from '@/api/request'
import { getApiErrorMessage, isAbortError } from '@/api/errors'
import {
  createChatSession,
  deleteChatSession,
  getChatMessages,
  listChatSessions,
  streamChat,
  streamImageChat,
  updateChatSession,
  type ChatHistoryMessage,
  type ChatMode,
  type ChatSession,
  type ChatStreamEvent,
} from '@/api/chat'
import { addCalendarPlan, getDashboardClasses, type DashboardClass } from '@/api/dashboard'
import { useChatStore } from '@/stores/chat'
import { renderMarkdown } from '@/utils/safeHtml'

hljs.registerLanguage('c', cLanguage)
hljs.registerLanguage('cpp', cppLanguage)
hljs.registerLanguage('java', javaLanguage)
hljs.registerLanguage('javascript', javascriptLanguage)
hljs.registerLanguage('json', jsonLanguage)
hljs.registerLanguage('python', pythonLanguage)
hljs.registerLanguage('typescript', typescriptLanguage)

marked.use(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code: string, language: string) {
      return language && hljs.getLanguage(language)
        ? hljs.highlight(code, { language }).value
        : hljs.highlightAuto(code).value
    },
  }),
)
marked.setOptions({ breaks: true, gfm: true })

interface KnowledgeBaseOption {
  id: number
  name: string
  description?: string
}

interface ToolStep {
  tool: string
  label: string
  status: 'running' | 'success' | 'error'
  elapsedMs?: number
}

interface Citation {
  documentId: string
  documentName: string
  sectionIndex: number
  excerpt: string
}

interface UiMessage {
  localId: string
  role: 'user' | 'assistant'
  content: string
  html?: string
  time: string
  state?: 'streaming' | 'complete' | 'stopped' | 'error'
  toolSteps: ToolStep[]
  citations: Citation[]
  attachmentName?: string
}

interface LessonArtifact {
  type: 'lesson_plan'
  title: string
  content: string
}

const router = useRouter()
const chatStore = useChatStore()

const sessions = ref<ChatSession[]>([])
const activeSessionId = ref('')
const messages = ref<UiMessage[]>([])
const classes = ref<DashboardClass[]>([])
const knowledgeBases = ref<KnowledgeBaseOption[]>([])
const selectedClassId = ref<number | null>(null)
const selectedKbIds = ref<number[]>([])
const activeMode = ref<ChatMode>('auto')
const sessionSearch = ref('')
const inputMessage = ref('')
const selectedFile = ref<File | null>(null)
const selectedFileUrl = ref('')
const isLoading = ref(false)
const isInitializing = ref(true)
const isConnected = ref(true)
const abortController = ref<AbortController | null>(null)
const messageContainer = ref<HTMLElement | null>(null)
const textarea = ref<HTMLTextAreaElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const sessionsOpen = ref(false)
const artifactOpen = ref(false)

const artifact = ref<LessonArtifact | null>(null)
const artifactDraft = ref('')
const artifactEditing = ref(false)
const calendarTopic = ref('')
const calendarKnowledge = ref('')
const calendarDate = ref(new Date().toISOString().slice(0, 10))
const savingArtifact = ref(false)

let localMessageSequence = 0

const activeSession = computed(
  () => sessions.value.find((session) => session.sessionId === activeSessionId.value) || null,
)

const activeClass = computed(
  () => classes.value.find((item) => item.id === selectedClassId.value) || null,
)

const selectedKnowledgeBases = computed(() =>
  knowledgeBases.value.filter((item) => selectedKbIds.value.includes(item.id)),
)

const filteredSessions = computed(() => {
  const query = sessionSearch.value.trim().toLowerCase()
  if (!query) return sessions.value
  return sessions.value.filter((session) =>
    `${session.title} ${session.className || ''}`.toLowerCase().includes(query),
  )
})

const contextualPrompts = computed(() => {
  const className = activeClass.value?.name || '当前班级'
  return [
    {
      icon: '🧭',
      title: '生成备课方案',
      description: '结合班级薄弱点与知识库形成可落地教案',
      mode: 'lesson_plan' as ChatMode,
      prompt: `请结合${className}的近期学情和课程知识库，生成一份20分钟的针对性复习课方案，包含教学目标、重难点、教学流程、互动问题和课后练习。`,
    },
    {
      icon: '📊',
      title: '分析班级学情',
      description: '查询实时数据并给出教学建议',
      mode: 'learning_analysis' as ChatMode,
      prompt: `请分析${className}当前整体学情、薄弱知识点和需要重点关注的问题，并给出下一步教学建议。`,
    },
    {
      icon: '🧩',
      title: '生成分层练习',
      description: '从知识库生成基础、提高和挑战题',
      mode: 'auto' as ChatMode,
      prompt: `请根据${className}当前薄弱知识点生成6道分层练习，分为基础、提高、挑战三档，并附参考答案与解析。`,
    },
    {
      icon: '👤',
      title: '诊断单个学生',
      description: '输入姓名后查询成绩趋势和薄弱点',
      mode: 'learning_analysis' as ChatMode,
      prompt:
        '请先询问我要分析的学生姓名，然后查询其最近成绩趋势、薄弱知识点，并给出个性化辅导建议。',
    },
  ]
})

onMounted(loadWorkspace)

onUnmounted(() => {
  abortController.value?.abort()
  revokeSelectedFileUrl()
})

async function loadWorkspace() {
  isInitializing.value = true
  try {
    await Promise.all([loadContextOptions(), refreshSessions()])
    if (sessions.value.length) await selectSession(sessions.value[0]!.sessionId)
    else await newConversation()
  } catch (error: unknown) {
    isConnected.value = false
    ElMessage.error(getApiErrorMessage(error, 'AI 工作台加载失败'))
  } finally {
    isInitializing.value = false
  }
}

async function loadContextOptions() {
  const [classResponse, ownedResponse, joinedResponse] = await Promise.all([
    getDashboardClasses(),
    request.get<KnowledgeBaseOption[]>('/shared-kb/my'),
    request.get<KnowledgeBaseOption[]>('/shared-kb/joined'),
  ])
  classes.value = classResponse.data || []
  const kbMap = new Map<number, KnowledgeBaseOption>()
  ;[...ownedResponse.data, ...joinedResponse.data].forEach((kb) => kbMap.set(kb.id, kb))
  knowledgeBases.value = Array.from(kbMap.values())
  if (selectedClassId.value === null && classes.value.length)
    selectedClassId.value = classes.value[0]!.id
  if (!selectedKbIds.value.length) selectedKbIds.value = knowledgeBases.value.map((kb) => kb.id)
}

async function refreshSessions() {
  sessions.value = await listChatSessions()
  isConnected.value = true
}

async function newConversation(mode: ChatMode = 'auto') {
  if (isLoading.value) stopGeneration()
  const created = await createChatSession({
    classId: selectedClassId.value,
    kbIds: selectedKbIds.value,
    mode,
  })
  sessions.value = [
    created,
    ...sessions.value.filter((item) => item.sessionId !== created.sessionId),
  ]
  activeSessionId.value = created.sessionId
  activeMode.value = created.mode
  messages.value = []
  clearArtifact()
  sessionsOpen.value = false
  await nextTick(() => textarea.value?.focus())
}

async function selectSession(sessionId: string) {
  if (sessionId === activeSessionId.value && messages.value.length) return
  if (isLoading.value) stopGeneration()
  const session = sessions.value.find((item) => item.sessionId === sessionId)
  if (!session) return
  activeSessionId.value = sessionId
  selectedClassId.value = session.classId
  selectedKbIds.value = [...session.kbIds]
  activeMode.value = session.mode || 'auto'
  clearArtifact()
  const history = await getChatMessages(sessionId)
  messages.value = history.map(historyToUiMessage)
  restoreArtifactFromHistory()
  sessionsOpen.value = false
  scrollBottom()
}

function historyToUiMessage(message: ChatHistoryMessage): UiMessage {
  return {
    localId: `history-${message.id || ++localMessageSequence}`,
    role: message.role,
    content: message.content,
    html: message.role === 'assistant' ? renderMarkdown(message.content) : undefined,
    time: formatTime(message.createdAt),
    state: 'complete',
    toolSteps: [],
    citations: [],
  }
}

async function persistSessionContext(extra: Partial<{ title: string; pinned: boolean }> = {}) {
  if (!activeSessionId.value) return
  const updated = await updateChatSession(activeSessionId.value, {
    title: extra.title,
    classId: selectedClassId.value,
    kbIds: selectedKbIds.value,
    mode: activeMode.value,
    pinned: extra.pinned,
  })
  const index = sessions.value.findIndex((item) => item.sessionId === updated.sessionId)
  if (index >= 0) sessions.value[index] = updated
  sessions.value = [...sessions.value].sort(compareSessions)
}

function compareSessions(left: ChatSession, right: ChatSession) {
  if (left.pinned !== right.pinned) return left.pinned ? -1 : 1
  return String(right.updatedAt || '').localeCompare(String(left.updatedAt || ''))
}

async function renameSession(session: ChatSession) {
  try {
    const result = await ElMessageBox.prompt('输入新的会话名称', '重命名会话', {
      inputValue: session.title,
      inputPattern: /\S+/,
      inputErrorMessage: '名称不能为空',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    if (session.sessionId !== activeSessionId.value) await selectSession(session.sessionId)
    await persistSessionContext({ title: result.value.trim() })
  } catch {
    // User cancelled.
  }
}

async function togglePin(session: ChatSession) {
  if (session.sessionId !== activeSessionId.value) await selectSession(session.sessionId)
  await persistSessionContext({ pinned: !session.pinned })
}

async function removeSession(session: ChatSession) {
  try {
    await ElMessageBox.confirm(`删除“${session.title}”？此操作不可恢复。`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  if (session.sessionId === activeSessionId.value && isLoading.value) stopGeneration()
  await deleteChatSession(session.sessionId)
  sessions.value = sessions.value.filter((item) => item.sessionId !== session.sessionId)
  if (session.sessionId === activeSessionId.value) {
    if (sessions.value.length) await selectSession(sessions.value[0]!.sessionId)
    else await newConversation()
  }
  ElMessage.success('会话已删除')
}

async function useQuickPrompt(item: (typeof contextualPrompts.value)[number]) {
  if (messages.value.length) await newConversation(item.mode)
  else {
    activeMode.value = item.mode
    await persistSessionContext()
  }
  inputMessage.value = item.prompt
  await sendMessage()
}

async function sendMessage() {
  if (isLoading.value) return
  const text = inputMessage.value.trim()
  if (selectedFile.value) {
    await sendImageMessage(text || '请分析这张图片，并结合当前课程上下文给出教学建议。')
    return
  }
  if (!text || !activeSessionId.value) return

  messages.value.push(createUiMessage('user', text))
  const assistant = createUiMessage('assistant', '')
  assistant.state = 'streaming'
  messages.value.push(assistant)
  inputMessage.value = ''
  autoResize()
  scrollBottom()
  await runTextStream(text, assistant)
}

async function runTextStream(text: string, assistant: UiMessage) {
  startRun()
  const controller = new AbortController()
  abortController.value = controller
  try {
    await streamChat(
      {
        message: text,
        sessionId: activeSessionId.value,
        classId: selectedClassId.value,
        kbIds: selectedKbIds.value,
        mode: activeMode.value,
      },
      controller.signal,
      (event) => handleStreamEvent(event, assistant),
    )
    assistant.state = 'complete'
    assistant.html = renderMarkdown(assistant.content)
    await refreshSessionsPreservingActive()
  } catch (error: unknown) {
    finishFailedRun(error, assistant)
  } finally {
    finishRun()
  }
}

async function sendImageMessage(text: string) {
  const file = selectedFile.value
  if (!file || !activeSessionId.value) return
  const user = createUiMessage('user', text)
  user.attachmentName = file.name
  messages.value.push(user)
  const assistant = createUiMessage('assistant', '')
  assistant.state = 'streaming'
  messages.value.push(assistant)
  const formData = new FormData()
  formData.append('file', file)
  formData.append('message', text)
  formData.append('sessionId', activeSessionId.value)
  clearSelectedFile()
  inputMessage.value = ''
  startRun()
  const controller = new AbortController()
  abortController.value = controller
  try {
    await streamImageChat(formData, controller.signal, (event) =>
      handleStreamEvent(event, assistant),
    )
    assistant.state = 'complete'
    assistant.html = renderMarkdown(assistant.content)
    await refreshSessionsPreservingActive()
  } catch (error: unknown) {
    finishFailedRun(error, assistant)
  } finally {
    finishRun()
  }
}

function startRun() {
  isLoading.value = true
  chatStore.setResponding(true)
}

function finishRun() {
  isLoading.value = false
  abortController.value = null
  chatStore.setResponding(false)
}

function finishFailedRun(error: unknown, assistant: UiMessage) {
  if (isAbortError(error)) {
    assistant.state = 'stopped'
    assistant.html = renderMarkdown(assistant.content)
    return
  }
  assistant.state = 'error'
  if (!assistant.content) assistant.content = '本次回答未能完成，请稍后重试。'
  assistant.html = renderMarkdown(assistant.content)
  isConnected.value = false
  ElMessage.error(getApiErrorMessage(error, 'AI 服务暂时不可用'))
}

function handleStreamEvent(event: ChatStreamEvent, assistant: UiMessage) {
  const data = event.data
  if (event.type === 'token') {
    assistant.content += String(data.content || '')
    scrollBottom()
    return
  }
  if (event.type === 'tool_started') {
    assistant.toolSteps.push({
      tool: String(data.tool || ''),
      label: String(data.label || '调用教学工具'),
      status: 'running',
    })
    scrollBottom()
    return
  }
  if (event.type === 'tool_completed') {
    const tool = String(data.tool || '')
    const step = [...assistant.toolSteps]
      .reverse()
      .find((item) => item.tool === tool && item.status === 'running')
    if (step) {
      step.status = data.success === false ? 'error' : 'success'
      step.elapsedMs = Number(data.elapsedMs || 0)
    }
    return
  }
  if (event.type === 'citation') {
    const citation: Citation = {
      documentId: String(data.documentId || ''),
      documentName: String(data.documentName || '教学资料'),
      sectionIndex: Number(data.sectionIndex || 0),
      excerpt: String(data.excerpt || ''),
    }
    if (
      !assistant.citations.some(
        (item) =>
          item.documentId === citation.documentId && item.sectionIndex === citation.sectionIndex,
      )
    ) {
      assistant.citations.push(citation)
    }
    return
  }
  if (event.type === 'artifact' && data.type === 'lesson_plan') {
    setArtifact({
      type: 'lesson_plan',
      title: String(data.title || 'AI 备课方案'),
      content: String(data.content || assistant.content),
    })
    artifactOpen.value = true
  }
}

function stopGeneration() {
  abortController.value?.abort()
}

async function retryAssistant(index: number) {
  const previousUser = messages.value
    .slice(0, index)
    .reverse()
    .find((item) => item.role === 'user')
  if (!previousUser || isLoading.value) return
  inputMessage.value = previousUser.content
  await sendMessage()
}

async function editAndResend(message: UiMessage) {
  if (isLoading.value) return
  try {
    const result = await ElMessageBox.prompt('修改问题后会创建一个新分支会话。', '编辑并重新发送', {
      inputValue: message.content,
      inputType: 'textarea',
      confirmButtonText: '创建分支并发送',
      cancelButtonText: '取消',
    })
    const value = result.value.trim()
    if (!value) return
    await newConversation(activeMode.value)
    inputMessage.value = value
    await sendMessage()
  } catch {
    // User cancelled.
  }
}

async function refreshSessionsPreservingActive() {
  const current = activeSessionId.value
  await refreshSessions()
  activeSessionId.value = current
}

function handleFileSelect(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('首轮附件支持图片、试题截图和代码截图；文档请先上传到知识库。')
    if (fileInput.value) fileInput.value.value = ''
    return
  }
  revokeSelectedFileUrl()
  selectedFile.value = file
  selectedFileUrl.value = URL.createObjectURL(file)
}

function clearSelectedFile() {
  revokeSelectedFileUrl()
  selectedFile.value = null
  if (fileInput.value) fileInput.value.value = ''
}

function revokeSelectedFileUrl() {
  if (selectedFileUrl.value) URL.revokeObjectURL(selectedFileUrl.value)
  selectedFileUrl.value = ''
}

function createUiMessage(role: 'user' | 'assistant', content: string): UiMessage {
  return {
    localId: `local-${Date.now()}-${++localMessageSequence}`,
    role,
    content,
    time: formatTime(),
    state: 'complete',
    toolSteps: [],
    citations: [],
  }
}

function setArtifact(value: LessonArtifact) {
  artifact.value = value
  artifactDraft.value = value.content
  calendarTopic.value = activeSession.value?.title || 'AI 备课方案'
  artifactEditing.value = false
}

function clearArtifact() {
  artifact.value = null
  artifactDraft.value = ''
  artifactEditing.value = false
}

function restoreArtifactFromHistory() {
  if (activeMode.value !== 'lesson_plan') return
  const lastAssistant = [...messages.value]
    .reverse()
    .find((message) => message.role === 'assistant')
  if (lastAssistant) {
    setArtifact({ type: 'lesson_plan', title: 'AI 备课方案', content: lastAssistant.content })
  }
}

function applyArtifactEdit() {
  if (!artifact.value) return
  artifact.value.content = artifactDraft.value
  artifactEditing.value = false
}

async function saveArtifactToCalendar() {
  if (!artifact.value || !selectedClassId.value) {
    ElMessage.warning('请先选择班级')
    return
  }
  if (!calendarTopic.value.trim()) {
    ElMessage.warning('请填写备课主题')
    return
  }
  savingArtifact.value = true
  try {
    await addCalendarPlan({
      classId: selectedClassId.value,
      weekNumber: 0,
      plannedDate: calendarDate.value || null,
      topic: calendarTopic.value.trim(),
      knowledgePoints: calendarKnowledge.value.trim() || null,
    })
    ElMessage.success('已加入备课日历，完整方案保留在当前会话')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '保存失败'))
  } finally {
    savingArtifact.value = false
  }
}

function copyText(text: string) {
  navigator.clipboard
    .writeText(text)
    .then(() => ElMessage.success('已复制'))
    .catch(() => ElMessage.warning('复制失败，请手动选择'))
}

function handleEnter(event: KeyboardEvent) {
  if (!event.shiftKey) {
    event.preventDefault()
    void sendMessage()
  }
}

function autoResize() {
  if (!textarea.value) return
  textarea.value.style.height = 'auto'
  textarea.value.style.height = `${Math.min(textarea.value.scrollHeight, 150)}px`
}

function scrollBottom() {
  void nextTick(() => {
    if (messageContainer.value)
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight
  })
}

function formatTime(value?: string) {
  const date = value ? new Date(value) : new Date()
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function formatSessionTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) return formatTime(value)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function closeOverlays() {
  sessionsOpen.value = false
  artifactOpen.value = false
}
</script>

<template>
  <div class="agent-workspace" :class="{ 'artifact-visible': artifactOpen }">
    <aside class="session-panel" :class="{ open: sessionsOpen }">
      <div class="session-heading">
        <div>
          <span class="eyebrow">EDUMIND AGENT</span>
          <h1>教学工作台</h1>
        </div>
        <button class="close-mobile" @click="sessionsOpen = false">×</button>
      </div>

      <button class="new-session" @click="newConversation()"><span>＋</span> 新建对话</button>
      <div class="session-search">
        <span>⌕</span>
        <input v-model="sessionSearch" placeholder="搜索对话" />
      </div>

      <div class="session-list">
        <div v-if="!filteredSessions.length" class="session-empty">还没有历史对话</div>
        <button
          v-for="session in filteredSessions"
          :key="session.sessionId"
          class="session-item"
          :class="{ active: session.sessionId === activeSessionId }"
          @click="selectSession(session.sessionId)"
        >
          <span class="session-icon">{{
            session.mode === 'lesson_plan'
              ? '📝'
              : session.mode === 'learning_analysis'
                ? '📊'
                : '✦'
          }}</span>
          <span class="session-copy">
            <strong>{{ session.title }}</strong>
            <small
              >{{ session.className || '通用教学对话' }} ·
              {{ formatSessionTime(session.updatedAt) }}</small
            >
          </span>
          <span v-if="session.pinned" class="pin-mark">●</span>
          <span class="session-actions" @click.stop>
            <el-dropdown trigger="click">
              <button class="more-button">•••</button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="togglePin(session)">{{
                    session.pinned ? '取消置顶' : '置顶'
                  }}</el-dropdown-item>
                  <el-dropdown-item @click="renameSession(session)">重命名</el-dropdown-item>
                  <el-dropdown-item divided @click="removeSession(session)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </button>
      </div>

      <div class="session-footer">
        <span class="status-dot" :class="{ off: !isConnected }"></span>
        {{ isConnected ? 'Agent 服务已就绪' : '连接异常' }}
      </div>
    </aside>

    <section class="conversation-panel">
      <header class="conversation-header">
        <button class="mobile-menu" @click="sessionsOpen = true">☰</button>
        <div class="context-selectors">
          <div class="context-control">
            <span>班级</span>
            <el-select
              v-model="selectedClassId"
              clearable
              placeholder="不限定班级"
              @change="persistSessionContext()"
            >
              <el-option
                v-for="item in classes"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
          </div>
          <div class="context-control source-control">
            <span>知识源</span>
            <el-select
              v-model="selectedKbIds"
              multiple
              collapse-tags
              collapse-tags-tooltip
              placeholder="仅个人知识库"
              @change="persistSessionContext()"
            >
              <el-option
                v-for="kb in knowledgeBases"
                :key="kb.id"
                :label="kb.name"
                :value="kb.id"
              />
            </el-select>
          </div>
        </div>
        <div class="header-actions">
          <span v-if="activeMode !== 'auto'" class="mode-badge">
            {{
              activeMode === 'lesson_plan'
                ? '备课'
                : activeMode === 'learning_analysis'
                  ? '学情分析'
                  : '批改'
            }}
          </span>
          <button class="artifact-toggle" @click="artifactOpen = !artifactOpen">成果</button>
        </div>
      </header>

      <main ref="messageContainer" class="message-list">
        <div v-if="isInitializing" class="loading-state">
          <span class="loader"></span>
          <p>正在准备教学上下文…</p>
        </div>

        <section v-else-if="messages.length === 0" class="welcome-state">
          <div class="welcome-mark">✦</div>
          <span class="welcome-kicker">你的专属教学 Agent</span>
          <h2>今天想先处理哪项教学工作？</h2>
          <p>
            已连接 <strong>{{ activeClass?.name || '通用课程上下文' }}</strong>
            <template v-if="selectedKnowledgeBases.length"
              >，可检索 {{ selectedKnowledgeBases.length }} 个团队知识库</template
            >
          </p>
          <div class="prompt-grid">
            <button
              v-for="item in contextualPrompts"
              :key="item.title"
              @click="useQuickPrompt(item)"
            >
              <span class="prompt-icon">{{ item.icon }}</span>
              <span
                ><strong>{{ item.title }}</strong
                ><small>{{ item.description }}</small></span
              >
              <b>→</b>
            </button>
          </div>
        </section>

        <template v-else>
          <article
            v-for="(message, index) in messages"
            :key="message.localId"
            class="message"
            :class="message.role"
          >
            <div class="message-avatar">{{ message.role === 'user' ? '我' : 'AI' }}</div>
            <div class="message-main">
              <div v-if="message.attachmentName" class="attached-image-note">
                🖼 {{ message.attachmentName }}
              </div>

              <div v-if="message.toolSteps.length" class="agent-steps">
                <div
                  v-for="(step, stepIndex) in message.toolSteps"
                  :key="`${step.tool}-${stepIndex}`"
                  class="agent-step"
                >
                  <span class="step-state" :class="step.status">
                    {{ step.status === 'running' ? '⋯' : step.status === 'success' ? '✓' : '!' }}
                  </span>
                  <span>{{ step.label }}</span>
                  <small v-if="step.elapsedMs !== undefined">{{ step.elapsedMs }} ms</small>
                </div>
              </div>

              <div class="message-bubble">
                <div v-if="message.role === 'user'" class="plain-text">{{ message.content }}</div>
                <div v-else-if="message.state === 'streaming'" class="streaming-text">
                  {{ message.content }}<span class="cursor"></span>
                </div>
                <div
                  v-else
                  class="markdown-body"
                  v-html="message.html || renderMarkdown(message.content)"
                ></div>
              </div>

              <div v-if="message.citations.length" class="citation-block">
                <div class="citation-title">
                  <span>▤</span> 本回答参考了 {{ message.citations.length }} 处知识库内容
                </div>
                <div class="citation-list">
                  <button
                    v-for="(citation, citationIndex) in message.citations"
                    :key="`${citation.documentId}-${citation.sectionIndex}`"
                    @click="router.push('/teacher/docs')"
                  >
                    <b>{{ citationIndex + 1 }}</b>
                    <span
                      ><strong>{{ citation.documentName }}</strong
                      ><small
                        >第 {{ citation.sectionIndex + 1 }} 节 · {{ citation.excerpt }}</small
                      ></span
                    >
                  </button>
                </div>
              </div>

              <div class="message-meta">
                <span>{{ message.time }}</span>
                <template v-if="message.role === 'assistant' && message.content">
                  <button @click="copyText(message.content)">复制</button>
                  <button @click="retryAssistant(index)">重新生成</button>
                  <span v-if="message.state === 'stopped'" class="stopped-label">已停止</span>
                </template>
                <button v-else-if="message.role === 'user'" @click="editAndResend(message)">
                  编辑并分支
                </button>
              </div>
            </div>
          </article>
        </template>
      </main>

      <footer class="composer-area">
        <div v-if="selectedFile" class="file-preview">
          <img :src="selectedFileUrl" alt="待分析图片" />
          <span
            ><strong>{{ selectedFile.name }}</strong
            ><small>将作为视觉材料发送</small></span
          >
          <button @click="clearSelectedFile">×</button>
        </div>
        <div class="composer">
          <label class="attach-button" title="上传试题、作业或代码截图">
            <span>＋</span>
            <input ref="fileInput" type="file" accept="image/*" hidden @change="handleFileSelect" />
          </label>
          <textarea
            ref="textarea"
            v-model="inputMessage"
            rows="1"
            :placeholder="
              selectedFile
                ? '告诉 Agent 如何分析这张图片…'
                : '向教学 Agent 描述你的目标，或输入 / 选择任务…'
            "
            @input="autoResize"
            @keydown.enter="handleEnter"
          ></textarea>
          <button
            v-if="isLoading"
            class="send-button stop"
            title="停止生成"
            @click="stopGeneration"
          >
            <span></span>
          </button>
          <button
            v-else
            class="send-button"
            :disabled="!inputMessage.trim() && !selectedFile"
            title="发送"
            @click="sendMessage"
          >
            ↑
          </button>
        </div>
        <div class="composer-hint">
          <span>Enter 发送 · Shift + Enter 换行</span>
          <span>涉及发布、成绩修改等操作前会请求教师确认</span>
        </div>
      </footer>
    </section>

    <aside class="artifact-panel" :class="{ open: artifactOpen }">
      <div class="artifact-header">
        <div>
          <span class="eyebrow">CONTEXT & OUTPUT</span>
          <h2>{{ artifact ? '教学成果' : '教学上下文' }}</h2>
        </div>
        <button @click="artifactOpen = false">×</button>
      </div>

      <template v-if="artifact">
        <div class="artifact-type">
          <span>📝</span>
          <div>
            <strong>{{ artifact.title }}</strong
            ><small>可编辑 · 可加入备课日历</small>
          </div>
        </div>
        <div class="artifact-toolbar">
          <button :class="{ active: !artifactEditing }" @click="artifactEditing = false">
            预览
          </button>
          <button :class="{ active: artifactEditing }" @click="artifactEditing = true">编辑</button>
          <button @click="copyText(artifact.content)">复制</button>
        </div>
        <textarea v-if="artifactEditing" v-model="artifactDraft" class="artifact-editor"></textarea>
        <div
          v-else
          class="artifact-content markdown-body"
          v-html="renderMarkdown(artifact.content)"
        ></div>
        <button v-if="artifactEditing" class="apply-edit" @click="applyArtifactEdit">
          应用修改
        </button>

        <div class="calendar-card">
          <span class="card-label">加入备课日历</span>
          <label>主题<input v-model="calendarTopic" placeholder="例如：指针与数组复习课" /></label>
          <label
            >知识点<input v-model="calendarKnowledge" placeholder="指针、数组、内存管理"
          /></label>
          <label>计划日期<input v-model="calendarDate" type="date" /></label>
          <button :disabled="savingArtifact" @click="saveArtifactToCalendar">
            {{ savingArtifact ? '保存中…' : '确认加入备课日历' }}
          </button>
          <button
            class="secondary"
            @click="
              router.push(
                `/teacher/pre-lesson${selectedClassId ? `?classId=${selectedClassId}` : ''}`,
              )
            "
          >
            打开备课工作台
          </button>
        </div>
      </template>

      <template v-else>
        <div class="context-card primary">
          <span class="card-icon">🏫</span>
          <div>
            <small>当前班级</small><strong>{{ activeClass?.name || '未指定班级' }}</strong>
            <p>Agent 的学情查询和备课建议将限定在这里。</p>
          </div>
        </div>
        <div class="context-card">
          <div class="context-card-heading">
            <span class="card-icon">📚</span>
            <div>
              <small>已启用知识源</small
              ><strong>{{ selectedKnowledgeBases.length }} 个团队知识库</strong>
            </div>
          </div>
          <ul v-if="selectedKnowledgeBases.length">
            <li v-for="kb in selectedKnowledgeBases" :key="kb.id"><span></span>{{ kb.name }}</li>
          </ul>
          <p v-else>当前仅检索教师个人知识库。</p>
          <button @click="router.push('/teacher/docs')">管理知识库 →</button>
        </div>
        <div class="agent-capabilities">
          <span class="card-label">本次可用能力</span>
          <div>
            <i>⌕</i><span><strong>课程知识检索</strong><small>RAG + 来源引用</small></span>
          </div>
          <div>
            <i>▥</i><span><strong>班级实时数据</strong><small>成绩、作业、薄弱点</small></span>
          </div>
          <div>
            <i>◫</i><span><strong>视觉材料分析</strong><small>试题、作业、代码截图</small></span>
          </div>
          <div>
            <i>✎</i><span><strong>教学成果生成</strong><small>备课、练习与辅导建议</small></span>
          </div>
        </div>
      </template>
    </aside>

    <button
      v-if="sessionsOpen || artifactOpen"
      class="mobile-overlay"
      @click="closeOverlays"
    ></button>
  </div>
</template>

<style scoped>
.agent-workspace {
  --ink: #172033;
  --muted: #718096;
  --line: #e5eaf1;
  --soft: #f5f7fb;
  --primary: #5b5bd6;
  --primary-dark: #4747b8;
  display: grid;
  grid-template-columns: 224px minmax(430px, 1fr) 300px;
  grid-template-rows: minmax(0, 1fr);
  height: calc(100vh - 104px);
  height: calc(100dvh - 104px);
  max-height: calc(100vh - 104px);
  max-height: calc(100dvh - 104px);
  min-height: 620px;
  overflow: hidden;
  color: var(--ink);
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: 0 12px 42px rgba(32, 45, 75, 0.08);
}

button,
input,
textarea {
  font: inherit;
}

button {
  color: inherit;
}

.session-panel,
.artifact-panel {
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  background: #f8f9fc;
}

.session-panel {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--line);
  padding: 20px 14px 14px;
}

.session-heading,
.artifact-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.eyebrow {
  display: block;
  margin-bottom: 3px;
  color: #898ca8;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.13em;
}

.session-heading h1,
.artifact-header h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
}

.new-session {
  width: 100%;
  margin: 20px 0 12px;
  padding: 10px 12px;
  color: #fff;
  font-weight: 650;
  text-align: left;
  background: var(--primary);
  border: 0;
  border-radius: 10px;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(91, 91, 214, 0.22);
}

.new-session:hover {
  background: var(--primary-dark);
}

.new-session span {
  margin-right: 7px;
  font-size: 18px;
}

.session-search {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 10px;
  color: #9aa3b5;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 9px;
}

.session-search input {
  width: 100%;
  border: 0;
  outline: 0;
  color: var(--ink);
  background: transparent;
  font-size: 12px;
}

.session-list {
  flex: 1;
  min-height: 0;
  margin-top: 12px;
  overflow-y: auto;
}

.session-empty {
  padding: 32px 8px;
  color: #9aa3b5;
  text-align: center;
  font-size: 12px;
}

.session-item {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  gap: 9px;
  margin: 3px 0;
  padding: 10px 8px;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 10px;
  cursor: pointer;
}

.session-item:hover,
.session-item.active {
  background: #ececfb;
}

.session-item.active::before {
  position: absolute;
  left: -14px;
  width: 3px;
  height: 26px;
  content: '';
  background: var(--primary);
  border-radius: 0 4px 4px 0;
}

.session-icon {
  display: grid;
  flex: 0 0 29px;
  height: 29px;
  place-items: center;
  background: #fff;
  border-radius: 8px;
  font-size: 13px;
}

.session-copy {
  min-width: 0;
  flex: 1;
}

.session-copy strong,
.session-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-copy strong {
  font-size: 12px;
  font-weight: 650;
}

.session-copy small {
  margin-top: 3px;
  color: #929aad;
  font-size: 9px;
}

.pin-mark {
  color: var(--primary);
  font-size: 7px;
}

.session-actions {
  display: none;
}

.session-item:hover .session-actions {
  display: block;
}

.more-button {
  padding: 4px;
  color: #7b8497;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 10px;
}

.session-footer {
  padding: 11px 8px 2px;
  color: #7f879a;
  border-top: 1px solid var(--line);
  font-size: 10px;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 6px;
  background: #22c55e;
  border-radius: 50%;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.12);
}

.status-dot.off {
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.12);
}

.conversation-panel {
  display: flex;
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  flex-direction: column;
  background: #fff;
}

.conversation-header {
  display: flex;
  flex: 0 0 auto;
  min-height: 66px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--line);
}

.context-selectors {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  gap: 10px;
}

.context-control {
  min-width: 140px;
}

.context-control > span {
  display: block;
  margin-bottom: 3px;
  color: #8d95a8;
  font-size: 9px;
  font-weight: 700;
}

.source-control {
  min-width: 210px;
}

.context-control :deep(.el-select) {
  width: 100%;
}

.context-control :deep(.el-select__wrapper) {
  min-height: 30px;
  padding: 3px 8px;
  background: #f7f8fb;
  box-shadow: none;
  font-size: 11px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-badge {
  padding: 5px 9px;
  color: #5b5bd6;
  background: #eeeeff;
  border-radius: 14px;
  font-size: 10px;
  font-weight: 700;
}

.artifact-toggle,
.mobile-menu,
.close-mobile {
  padding: 7px 9px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  cursor: pointer;
}

.mobile-menu,
.close-mobile,
.artifact-toggle {
  display: none;
}

.message-list {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 26px clamp(20px, 5vw, 62px);
  scroll-behavior: smooth;
}

.loading-state {
  display: grid;
  height: 100%;
  color: var(--muted);
  place-content: center;
  place-items: center;
  font-size: 13px;
}

.loader {
  width: 26px;
  height: 26px;
  margin-bottom: 10px;
  border: 2px solid #e0e3f4;
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.welcome-state {
  max-width: 660px;
  margin: 8vh auto 0;
  text-align: center;
}

.welcome-mark {
  display: grid;
  width: 52px;
  height: 52px;
  margin: 0 auto 14px;
  color: #fff;
  place-items: center;
  background: linear-gradient(140deg, #6969e7, #8989f0);
  border-radius: 16px;
  box-shadow: 0 10px 26px rgba(91, 91, 214, 0.25);
  font-size: 25px;
}

.welcome-kicker {
  color: var(--primary);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.welcome-state h2 {
  margin: 8px 0;
  font-size: clamp(22px, 3vw, 30px);
  letter-spacing: -0.02em;
}

.welcome-state > p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.prompt-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 28px;
  text-align: left;
}

.prompt-grid button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
  cursor: pointer;
  transition: 0.18s ease;
}

.prompt-grid button:hover {
  border-color: #bbbbef;
  box-shadow: 0 8px 22px rgba(44, 57, 90, 0.08);
  transform: translateY(-2px);
}

.prompt-icon {
  display: grid;
  flex: 0 0 36px;
  height: 36px;
  place-items: center;
  background: var(--soft);
  border-radius: 9px;
  font-size: 17px;
}

.prompt-grid button > span:nth-child(2) {
  min-width: 0;
  flex: 1;
}

.prompt-grid strong,
.prompt-grid small {
  display: block;
}

.prompt-grid strong {
  font-size: 12px;
}

.prompt-grid small {
  margin-top: 3px;
  color: #8a93a6;
  font-size: 9px;
  line-height: 1.45;
}

.prompt-grid b {
  color: #a3a9b7;
  font-weight: 400;
}

.message {
  display: flex;
  max-width: 820px;
  gap: 10px;
  margin: 0 auto 24px;
  animation: message-in 0.24s ease;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  display: grid;
  flex: 0 0 30px;
  height: 30px;
  color: #fff;
  place-items: center;
  background: linear-gradient(145deg, #6262dc, #8585eb);
  border-radius: 9px;
  font-size: 10px;
  font-weight: 800;
}

.message.user .message-avatar {
  color: #555c6f;
  background: #eceef4;
}

.message-main {
  max-width: min(82%, 700px);
  min-width: 0;
}

.message.user .message-main {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
}

.message-bubble {
  padding: 12px 15px;
  color: #273044;
  background: #f5f6fa;
  border-radius: 4px 14px 14px 14px;
  font-size: 13px;
  line-height: 1.75;
}

.message.user .message-bubble {
  color: #fff;
  background: var(--primary);
  border-radius: 14px 4px 14px 14px;
}

.plain-text,
.streaming-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  vertical-align: -2px;
  background: var(--primary);
  animation: blink 0.9s infinite;
}

.attached-image-note {
  margin-bottom: 5px;
  padding: 7px 10px;
  color: #5c6480;
  background: #f1f3f8;
  border-radius: 8px;
  font-size: 10px;
}

.agent-steps {
  margin-bottom: 8px;
  padding: 9px 11px;
  background: #fafaff;
  border: 1px solid #e8e8f7;
  border-radius: 10px;
}

.agent-step {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 3px 0;
  color: #5e6680;
  font-size: 10px;
}

.agent-step small {
  margin-left: auto;
  color: #a0a7b7;
}

.step-state {
  display: grid;
  width: 16px;
  height: 16px;
  color: #fff;
  place-items: center;
  background: #a1a7b8;
  border-radius: 50%;
  font-size: 9px;
}

.step-state.running {
  background: var(--primary);
  animation: pulse 1.2s infinite;
}

.step-state.success {
  background: #22a66f;
}

.step-state.error {
  background: #e65858;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 4px 0;
  color: #a0a7b7;
  font-size: 9px;
}

.message-meta button {
  padding: 0;
  color: #7d8598;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 9px;
}

.message-meta button:hover {
  color: var(--primary);
}

.stopped-label {
  color: #d18a2d;
}

.citation-block {
  margin-top: 8px;
  padding: 10px;
  background: #fbfbfd;
  border: 1px solid var(--line);
  border-radius: 10px;
}

.citation-title {
  margin-bottom: 7px;
  color: #6f7890;
  font-size: 10px;
  font-weight: 650;
}

.citation-list {
  display: grid;
  gap: 5px;
}

.citation-list button {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 8px;
  padding: 7px;
  text-align: left;
  background: #fff;
  border: 1px solid #eceff4;
  border-radius: 7px;
  cursor: pointer;
}

.citation-list button:hover {
  border-color: #c7c7ed;
}

.citation-list b {
  display: grid;
  flex: 0 0 19px;
  height: 19px;
  color: var(--primary);
  place-items: center;
  background: #eeeeff;
  border-radius: 5px;
  font-size: 9px;
}

.citation-list span {
  min-width: 0;
}

.citation-list strong,
.citation-list small {
  display: block;
}

.citation-list strong {
  color: #465068;
  font-size: 10px;
}

.citation-list small {
  overflow: hidden;
  margin-top: 2px;
  color: #929aab;
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.markdown-body :deep(p) {
  margin: 0 0 0.7em;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: 1em 0 0.45em;
  color: #1f2940;
  line-height: 1.35;
}

.markdown-body :deep(h1) {
  font-size: 18px;
}
.markdown-body :deep(h2) {
  font-size: 16px;
}
.markdown-body :deep(h3) {
  font-size: 14px;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 1.4em;
}
.markdown-body :deep(pre) {
  overflow-x: auto;
  margin: 9px 0;
  padding: 13px;
  background: #1d2433;
  border-radius: 9px;
}
.markdown-body :deep(pre code) {
  color: #e4e8f1;
  background: transparent;
}
.markdown-body :deep(code) {
  padding: 2px 5px;
  color: #cf476d;
  background: #eceef4;
  border-radius: 4px;
  font-family: Consolas, monospace;
  font-size: 0.92em;
}
.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 6px 8px;
  border: 1px solid #dfe3eb;
  text-align: left;
}
.markdown-body :deep(blockquote) {
  margin: 8px 0;
  padding-left: 10px;
  color: #687187;
  border-left: 3px solid var(--primary);
}

.composer-area {
  position: relative;
  z-index: 2;
  flex: 0 0 auto;
  padding: 10px clamp(18px, 4vw, 48px) 12px;
  background: linear-gradient(to top, #fff 78%, rgba(255, 255, 255, 0));
}

.composer {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 8px 9px;
  background: #fff;
  border: 1px solid #dfe3eb;
  border-radius: 15px;
  box-shadow: 0 8px 28px rgba(38, 50, 80, 0.09);
}

.composer:focus-within {
  border-color: #ababeb;
  box-shadow: 0 8px 28px rgba(91, 91, 214, 0.12);
}

.composer textarea {
  min-height: 34px;
  max-height: 150px;
  flex: 1;
  padding: 7px 2px;
  resize: none;
  border: 0;
  outline: 0;
  color: var(--ink);
  font-size: 12px;
  line-height: 1.55;
}

.attach-button,
.send-button {
  display: grid;
  flex: 0 0 34px;
  height: 34px;
  place-items: center;
  border-radius: 10px;
  cursor: pointer;
}

.attach-button {
  color: #747d91;
  background: #f1f3f7;
  font-size: 19px;
}

.send-button {
  color: #fff;
  background: var(--primary);
  border: 0;
  font-size: 18px;
  font-weight: 650;
}

.send-button:disabled {
  background: #cdd1dc;
  cursor: not-allowed;
}

.send-button.stop {
  background: #fff0f0;
  border: 1px solid #ffd1d1;
}

.send-button.stop span {
  width: 10px;
  height: 10px;
  background: #e25252;
  border-radius: 2px;
}

.composer-hint {
  display: flex;
  justify-content: space-between;
  padding: 6px 5px 0;
  color: #a1a8b6;
  font-size: 8px;
}

.file-preview {
  display: flex;
  align-items: center;
  gap: 9px;
  max-width: 360px;
  margin-bottom: 7px;
  padding: 7px;
  background: #f6f6fc;
  border: 1px solid #e7e7f6;
  border-radius: 10px;
}

.file-preview img {
  width: 38px;
  height: 38px;
  object-fit: cover;
  border-radius: 7px;
}

.file-preview span {
  min-width: 0;
  flex: 1;
}

.file-preview strong,
.file-preview small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-preview strong {
  font-size: 10px;
}
.file-preview small {
  margin-top: 2px;
  color: #8d95a7;
  font-size: 8px;
}
.file-preview button {
  background: transparent;
  border: 0;
  cursor: pointer;
}

.artifact-panel {
  overflow-y: auto;
  padding: 19px 16px;
  border-left: 1px solid var(--line);
}

.artifact-header {
  margin-bottom: 16px;
}

.artifact-header h2 {
  font-size: 16px;
}

.artifact-header > button {
  display: none;
  padding: 3px 6px;
  color: #7a8294;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 20px;
}

.context-card,
.agent-capabilities,
.calendar-card {
  margin-bottom: 12px;
  padding: 13px;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 12px;
}

.context-card.primary {
  display: flex;
  gap: 10px;
  background: linear-gradient(145deg, #f0f0ff, #fafaff);
  border-color: #e1e1fa;
}

.card-icon {
  font-size: 18px;
}

.context-card small,
.context-card strong {
  display: block;
}

.context-card small {
  color: #8e96a8;
  font-size: 8px;
}

.context-card strong {
  margin-top: 2px;
  font-size: 12px;
}

.context-card p {
  margin: 6px 0 0;
  color: #858da0;
  font-size: 9px;
  line-height: 1.55;
}

.context-card-heading {
  display: flex;
  gap: 9px;
}

.context-card ul {
  margin: 10px 0 5px;
  padding: 8px 0;
  border-top: 1px solid #eef0f4;
  border-bottom: 1px solid #eef0f4;
  list-style: none;
}

.context-card li {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 3px 0;
  color: #687187;
  font-size: 9px;
}

.context-card li span {
  width: 5px;
  height: 5px;
  background: #6a6ade;
  border-radius: 50%;
}

.context-card > button,
.secondary {
  padding: 4px 0;
  color: var(--primary);
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 9px;
  font-weight: 650;
}

.card-label {
  display: block;
  margin-bottom: 8px;
  color: #9198a9;
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.agent-capabilities > div {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 0;
}

.agent-capabilities i {
  display: grid;
  width: 27px;
  height: 27px;
  color: var(--primary);
  place-items: center;
  background: #eeeeff;
  border-radius: 7px;
  font-size: 12px;
  font-style: normal;
}

.agent-capabilities strong,
.agent-capabilities small {
  display: block;
}

.agent-capabilities strong {
  font-size: 10px;
}
.agent-capabilities small {
  margin-top: 2px;
  color: #9299aa;
  font-size: 8px;
}

.artifact-type {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px;
  background: linear-gradient(145deg, #eeeeff, #f8f8ff);
  border: 1px solid #dfdffa;
  border-radius: 11px;
}

.artifact-type > span {
  font-size: 22px;
}

.artifact-type strong,
.artifact-type small {
  display: block;
}

.artifact-type strong {
  font-size: 11px;
}
.artifact-type small {
  margin-top: 2px;
  color: #858da3;
  font-size: 8px;
}

.artifact-toolbar {
  display: flex;
  gap: 4px;
  margin: 10px 0 7px;
  padding: 3px;
  background: #eceef3;
  border-radius: 8px;
}

.artifact-toolbar button {
  flex: 1;
  padding: 5px;
  color: #737c90;
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  font-size: 9px;
}

.artifact-toolbar button.active {
  color: #434b5e;
  background: #fff;
  box-shadow: 0 2px 5px rgba(30, 42, 70, 0.08);
}

.artifact-content,
.artifact-editor {
  width: 100%;
  height: 260px;
  overflow-y: auto;
  padding: 11px;
  color: #4d566b;
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-size: 9px;
  line-height: 1.65;
}

.artifact-editor {
  resize: vertical;
  outline: none;
}

.apply-edit {
  width: 100%;
  margin-top: 6px;
  padding: 7px;
  color: #fff;
  background: var(--primary);
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 9px;
}

.calendar-card {
  margin-top: 12px;
}

.calendar-card label {
  display: block;
  margin-bottom: 7px;
  color: #777f91;
  font-size: 8px;
  font-weight: 650;
}

.calendar-card input {
  width: 100%;
  margin-top: 3px;
  padding: 7px 8px;
  color: #4b5467;
  background: #f7f8fa;
  border: 1px solid #e5e8ee;
  border-radius: 7px;
  outline: none;
  font-size: 9px;
}

.calendar-card > button:not(.secondary) {
  width: 100%;
  margin-top: 3px;
  padding: 8px;
  color: #fff;
  background: var(--primary);
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 9px;
  font-weight: 650;
}

.calendar-card .secondary {
  display: block;
  width: 100%;
  margin-top: 6px;
  text-align: center;
}

.mobile-overlay {
  display: none;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@keyframes blink {
  0%,
  45% {
    opacity: 1;
  }
  46%,
  100% {
    opacity: 0;
  }
}
@keyframes pulse {
  50% {
    opacity: 0.55;
    transform: scale(0.9);
  }
}
@keyframes message-in {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
}

@media (max-width: 1280px) {
  .agent-workspace {
    grid-template-columns: 218px minmax(430px, 1fr);
  }

  .artifact-panel {
    position: fixed;
    z-index: 1202;
    top: 68px;
    right: 12px;
    bottom: 12px;
    display: none;
    width: min(340px, calc(100vw - 24px));
    border: 1px solid var(--line);
    border-radius: 15px;
    box-shadow: 0 20px 60px rgba(24, 35, 60, 0.2);
  }

  .artifact-panel.open {
    display: block;
  }

  .artifact-header > button,
  .artifact-toggle {
    display: block;
  }

  .mobile-overlay {
    position: fixed;
    z-index: 1201;
    inset: 0;
    display: block;
    background: rgba(25, 33, 51, 0.2);
    border: 0;
  }
}

@media (max-width: 900px) {
  .agent-workspace {
    grid-template-columns: 1fr;
    height: calc(100vh - 80px);
    min-height: 560px;
    border-radius: 10px;
  }

  .session-panel {
    position: fixed;
    z-index: 1202;
    top: 64px;
    bottom: 8px;
    left: 8px;
    display: none;
    width: 250px;
    border: 1px solid var(--line);
    border-radius: 14px;
    box-shadow: 0 20px 60px rgba(24, 35, 60, 0.2);
  }

  .session-panel.open {
    display: flex;
  }

  .mobile-menu,
  .close-mobile {
    display: block;
  }

  .conversation-header {
    padding-inline: 10px;
  }

  .source-control {
    display: none;
  }

  .message-list {
    padding-inline: 14px;
  }

  .prompt-grid {
    grid-template-columns: 1fr;
  }

  .composer-area {
    padding-inline: 10px;
  }

  .composer-hint span:last-child {
    display: none;
  }
}
</style>
