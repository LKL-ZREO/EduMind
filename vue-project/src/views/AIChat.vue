<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'
import request, { getCsrfHeaders } from '@/api/request'
import { getApiErrorMessage, isAbortError } from '@/api/errors'
import { useChatStore } from '@/stores/chat'

/* ====== Markdown Setup ====== */
marked.use(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code: string, lang: string) {
      if (lang && hljs.getLanguage(lang)) {
        return hljs.highlight(code, { language: lang }).value
      }
      return hljs.highlightAuto(code).value
    },
  }),
)
marked.setOptions({ breaks: true, gfm: true })

function md2html(text: string): string {
  if (!text) return ''
  try {
    return marked.parse(text) as string
  } catch {
    return text.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}

/* ====== Types ====== */
interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  html?: string
  time: string
  model?: string
}

interface ChatHistoryItem {
  sessionId: string
  role: 'user' | 'assistant'
  content: string
  createdAt?: string
  model?: string
}

interface SsePayload {
  sessionId?: string
  content?: string
  message?: string
}

/* ====== State ====== */
const messages = ref<ChatMessage[]>([])
const inputMessage = ref('')
const isLoading = ref(false)
const isTyping = ref(false)
const isConnected = ref(false)
const sessionId = ref('')
const selectedFile = ref<File | null>(null)
const messageContainer = ref<HTMLElement | null>(null)
const textarea = ref<HTMLTextAreaElement | null>(null)

// SSE 流控 & 路由守卫
const chatStore = useChatStore()
const abortController = ref<AbortController | null>(null)
const streamingIdx = ref(-1) // 当前正在流式接收的消息索引，-1 表示无

const QUICK = ['帮我写一段 C 语言代码', '解释一下指针和内存管理', '如何学习数据结构？', '讲个笑话']

/* ====== Init ====== */
onMounted(async () => {
  checkConnection()
  await loadHistory()
  nextTick(autoResize)
})

onUnmounted(() => {
  // 中止正在进行的 SSE 流，防止僵尸连接堆积
  abortController.value?.abort()
})

async function checkConnection() {
  try {
    await request.get('/chat/health')
    isConnected.value = true
  } catch {
    isConnected.value = false
  }
}

/* ====== History ====== */
function genSessionId() {
  return `s_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
}

async function loadHistory() {
  try {
    const r = await request.get<ChatHistoryItem[]>('/chat/history')
    const list = r.data
    if (!list?.length) {
      sessionId.value = genSessionId()
      return
    }
    const groups = new Map<string, ChatHistoryItem[]>()
    list.forEach((m) => {
      if (!groups.has(m.sessionId)) groups.set(m.sessionId, [])
      groups.get(m.sessionId)!.push(m)
    })
    const latest = Array.from(groups.entries()).pop()
    if (!latest) {
      sessionId.value = genSessionId()
      return
    }
    sessionId.value = latest[0]
    messages.value = latest[1].map((m) => addMsg(m.role, m.content, m.createdAt, m.model))
  } catch {
    sessionId.value = genSessionId()
  }
}

async function clearChat() {
  try {
    const r = await request.post('/chat/clear')
    if (r.data) sessionId.value = r.data.sessionId
    else sessionId.value = genSessionId()
  } catch {
    sessionId.value = genSessionId()
  }
  messages.value = []
  ElMessage.success('对话已清空')
}

/* ====== Send ====== */
async function sendMessage() {
  const msg = inputMessage.value.trim()
  if (selectedFile.value) {
    if (isImageFile(selectedFile.value)) {
      await sendImageMessage(msg)
      return
    }
    await uploadAndGrade(msg)
    return
  }
  if (!msg) return
  messages.value.push(addMsg('user', msg))
  inputMessage.value = ''
  autoResize()
  scrollBottom()
  isLoading.value = isTyping.value = true
  chatStore.setResponding(true)
  try {
    await streamChat(msg)
  } finally {
    isLoading.value = isTyping.value = false
    chatStore.setResponding(false)
  }
}

async function streamChat(userMsg: string) {
  abortController.value?.abort()
  messages.value.push(addMsg('assistant', ''))
  const idx = messages.value.length - 1
  streamingIdx.value = idx
  const controller = new AbortController()
  abortController.value = controller
  const url = `/api/chat/stream?message=${encodeURIComponent(userMsg)}&sessionId=${sessionId.value}`

  try {
    const r = await fetch(url, {
      headers: { Accept: 'text/event-stream' },
      credentials: 'same-origin',
      signal: controller.signal,
    })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    isConnected.value = true
    await consumeSse(r, idx)
  } catch (error: unknown) {
    if (isAbortError(error)) return
    const target = messages.value[idx]
    if (target && !target.content) {
      target.content = 'AI 服务暂时不可用，请稍后重试。'
    }
    if (target) target.html = md2html(target.content)
    ElMessage.error(getApiErrorMessage(error, '连接失败'))
  } finally {
    streamingIdx.value = -1
    abortController.value = null
  }
}

async function consumeSse(response: Response, messageIdx: number) {
  if (!response.body) throw new Error('浏览器未收到流式响应体')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let displayQueue: string[] = []
  let displayCursor = 0
  let displayTimer: ReturnType<typeof setTimeout> | null = null
  let drainResolver: (() => void) | null = null

  const pendingCharacters = () => displayQueue.length - displayCursor

  const settleDrain = () => {
    if (pendingCharacters() === 0 && drainResolver) {
      const resolve = drainResolver
      drainResolver = null
      resolve()
    }
  }

  const scheduleDisplay = () => {
    if (displayTimer !== null || pendingCharacters() === 0) return
    displayTimer = setTimeout(renderDisplayQueue, 20)
  }

  function renderDisplayQueue() {
    displayTimer = null
    const target = messages.value[messageIdx]
    if (!target) {
      displayQueue = []
      displayCursor = 0
      settleDrain()
      return
    }

    const backlog = pendingCharacters()
    const batchSize =
      backlog > 240 ? 12 : backlog > 120 ? 8 : backlog > 60 ? 4 : backlog > 20 ? 2 : 1
    const end = Math.min(displayCursor + batchSize, displayQueue.length)
    target.content += displayQueue.slice(displayCursor, end).join('')
    displayCursor = end
    scrollBottom()

    if (displayCursor === displayQueue.length) {
      displayQueue = []
      displayCursor = 0
      settleDrain()
    } else {
      scheduleDisplay()
    }
  }

  const enqueueDisplay = (text: string) => {
    if (!text) return
    displayQueue.push(...Array.from(text))
    isTyping.value = false
    scheduleDisplay()
  }

  const waitForDisplayDrain = () => {
    if (pendingCharacters() === 0) return Promise.resolve()
    return new Promise<void>((resolve) => {
      drainResolver = resolve
    })
  }

  const flushDisplayQueue = () => {
    if (displayTimer !== null) clearTimeout(displayTimer)
    displayTimer = null
    const target = messages.value[messageIdx]
    if (target && pendingCharacters() > 0) {
      target.content += displayQueue.slice(displayCursor).join('')
    }
    displayQueue = []
    displayCursor = 0
    settleDrain()
  }

  const handleFrame = (frame: string) => {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of frame.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
    if (!dataLines.length) return false

    const raw = dataLines.join('\n')
    if (raw === '[DONE]') return true
    let payload: SsePayload
    try {
      const parsed = JSON.parse(raw) as unknown
      payload =
        typeof parsed === 'object' && parsed !== null ? (parsed as SsePayload) : { content: raw }
    } catch {
      payload = { content: raw }
    }

    if (event === 'session' && payload.sessionId) sessionId.value = payload.sessionId
    if (event === 'token' || event === 'message') {
      if (!messages.value[messageIdx]) throw new Error('流式消息状态已丢失')
      enqueueDisplay(payload.content || '')
    }
    if (event === 'error') throw new Error(payload.message || 'AI 服务暂时不可用')
    return event === 'done' || raw === '[DONE]'
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })

      let match = /\r?\n\r?\n/.exec(buffer)
      while (match) {
        const frame = buffer.slice(0, match.index)
        buffer = buffer.slice(match.index + match[0].length)
        if (handleFrame(frame)) {
          await waitForDisplayDrain()
          return
        }
        match = /\r?\n\r?\n/.exec(buffer)
      }
      if (done) {
        if (buffer.trim()) handleFrame(buffer)
        await waitForDisplayDrain()
        return
      }
    }
  } finally {
    reader.releaseLock()
    flushDisplayQueue()
    const target = messages.value[messageIdx]
    if (target) target.html = md2html(target.content)
  }
}

/* ====== File Upload & Grade ====== */
function isImageFile(file: File) {
  return file.type.startsWith('image/')
}

function handleFileSelect(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (f) {
    selectedFile.value = f
    if (!inputMessage.value.trim()) inputMessage.value = '请批改这份作业'
  }
}
function clearFile() {
  selectedFile.value = null
  const el = document.querySelector<HTMLInputElement>('input[type="file"]')
  if (el) el.value = ''
}

function useQuickPrompt(prompt: string) {
  inputMessage.value = prompt
  void sendMessage()
}

async function sendImageMessage(req: string) {
  const file = selectedFile.value!
  const message = req || '请分析这张图片'
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('message', message)
    fd.append('sessionId', sessionId.value)

    messages.value.push(addMsg('user', `图片: ${file.name}\n${message}`))
    inputMessage.value = ''
    autoResize()
    clearFile()
    scrollBottom()
    isLoading.value = isTyping.value = true
    chatStore.setResponding(true)

    abortController.value?.abort()
    messages.value.push(addMsg('assistant', ''))
    const idx = messages.value.length - 1
    streamingIdx.value = idx
    const controller = new AbortController()
    abortController.value = controller

    const csrfHeaders = await getCsrfHeaders()
    const res = await fetch('/api/chat/multimodal/stream', {
      method: 'POST',
      headers: { Accept: 'text/event-stream', ...csrfHeaders },
      credentials: 'same-origin',
      body: fd,
      signal: controller.signal,
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    await consumeSse(res, idx)
  } catch (error: unknown) {
    if (isAbortError(error)) return
    ElMessage.error(getApiErrorMessage(error, '图片分析失败'))
    const current = streamingIdx.value >= 0 ? messages.value[streamingIdx.value] : null
    if (current && !current.content) {
      current.content = '图片分析失败，请稍后重试。'
      current.html = md2html(current.content)
    }
  } finally {
    streamingIdx.value = -1
    abortController.value = null
    isLoading.value = isTyping.value = false
    chatStore.setResponding(false)
  }
}

async function uploadAndGrade(req: string) {
  const file = selectedFile.value!
  try {
    const fd = new FormData()
    fd.append('file', file)
    const up = await request.post<{ filePath: string }>('/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    const fp = up.data.filePath
    messages.value.push(addMsg('user', `📎 ${file.name}\n${req}`))
    inputMessage.value = ''
    autoResize()
    clearFile()
    scrollBottom()
    isLoading.value = isTyping.value = true
    const gr = await request.post<{ content: string }>('/chat/grade', {
      filePath: fp,
      requirement: req || '请批改',
      sessionId: sessionId.value,
    })
    messages.value.push(addMsg('assistant', gr.data.content, undefined, 'OpenClaw'))
    scrollBottom()
  } catch (error: unknown) {
    const message = getApiErrorMessage(error, '批改失败')
    ElMessage.error(message)
    messages.value.push(addMsg('assistant', `批改失败: ${message}`))
  } finally {
    isLoading.value = isTyping.value = false
  }
}

/* ====== Helpers ====== */
function now() {
  const d = new Date()
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
function fmtTime(ts?: string) {
  if (!ts) return now()
  const d = new Date(ts)
  return `${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function addMsg(
  role: 'user' | 'assistant',
  content: string,
  time?: string,
  model?: string,
): ChatMessage {
  const t = fmtTime(time)
  return {
    role,
    content,
    html: role === 'assistant' && content ? md2html(content) : undefined,
    time: t,
    model,
  }
}

function handleEnter(e: KeyboardEvent) {
  if (!e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}
function autoResize() {
  if (!textarea.value) return
  textarea.value.style.height = 'auto'
  textarea.value.style.height = Math.min(textarea.value.scrollHeight, 120) + 'px'
}
function scrollBottom() {
  nextTick(() => {
    if (messageContainer.value)
      messageContainer.value.scrollTop = messageContainer.value.scrollHeight
  })
}

/* Watch: re-render last assistant message on content change (streaming) */
function copyText(text: string) {
  navigator.clipboard.writeText(text).then(() => ElMessage.success('已复制'))
}
</script>

<template>
  <div class="chat-page">
    <!-- Header -->
    <header class="chat-hdr">
      <div class="hdr-left">
        <div class="av">AI</div>
        <div>
          <h2>AI 助手</h2>
          <span class="dot" :class="isConnected ? 'on' : 'off'">{{
            isConnected ? '在线' : '离线'
          }}</span>
        </div>
      </div>
      <button class="ico" title="清空对话" @click="clearChat">
        <svg
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path
            d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"
          />
        </svg>
      </button>
    </header>

    <!-- Body -->
    <main class="chat-body" ref="messageContainer">
      <!-- Welcome -->
      <div v-if="messages.length === 0" class="welcome">
        <div class="w-emoji">👋</div>
        <h3>你好！我是 AI 助手</h3>
        <p>有什么我可以帮你的吗？</p>
        <div class="chips">
          <button v-for="(a, i) in QUICK" :key="i" class="chip" @click="useQuickPrompt(a)">
            {{ a }}
          </button>
        </div>
      </div>

      <!-- Messages -->
      <div v-for="(msg, i) in messages" :key="i" :class="['row', msg.role]">
        <div class="row-av">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
        <div class="row-bd">
          <div :class="['bubble', msg.role]">
            <template v-if="msg.role === 'user'">
              <div class="plain">{{ msg.content }}</div>
            </template>
            <template v-else>
              <!-- 流式接收中：纯文本，避免频繁 Markdown 解析阻塞主线程 -->
              <div v-if="i === streamingIdx" class="plain">{{ msg.content }}</div>
              <!-- 已完成的消息：使用预计算 HTML -->
              <div v-else v-html="msg.html || md2html(msg.content)" class="md-body" />
            </template>
          </div>
          <div class="meta">
            <span>{{ msg.time }}</span>
            <button
              v-if="msg.role === 'assistant' && msg.content"
              class="cp"
              @click="copyText(msg.content)"
            >
              复制
            </button>
          </div>
        </div>
      </div>

      <!-- Typing -->
      <div v-if="isTyping" class="row assistant">
        <div class="row-av">AI</div>
        <div class="dots"><span></span><span></span><span></span></div>
      </div>
    </main>

    <!-- Footer -->
    <footer class="chat-ftr">
      <div v-if="selectedFile" class="file-bar">
        <span class="ftag">📎 {{ selectedFile.name }} <button @click="clearFile">✕</button></span>
      </div>
      <div class="inp-row">
        <label class="at-btn" title="上传文件">
          📎<input
            type="file"
            @change="handleFileSelect"
            accept="image/*,.txt,.pdf,.doc,.docx,.c,.cpp,.java"
            hidden
          />
        </label>
        <textarea
          ref="textarea"
          v-model="inputMessage"
          @keydown.enter="handleEnter"
          @input="autoResize"
          :placeholder="selectedFile ? '输入批改要求...' : '输入消息...'"
          rows="1"
          :disabled="isLoading"
        />
        <button
          class="sd-btn"
          @click="sendMessage"
          :disabled="(!inputMessage.trim() && !selectedFile) || isLoading"
        >
          <svg
            v-if="!isLoading"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
          </svg>
          <span v-else class="spin"></span>
        </button>
      </div>
      <div class="hint">
        {{ selectedFile ? 'Enter 上传并批改' : 'Enter 发送，Shift+Enter 换行' }}
      </div>
    </footer>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 860px;
  margin: 0 auto;
  background: #f8fafc;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* Header */
.chat-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.hdr-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hdr-left h2 {
  margin: 0;
  font-size: 16px;
  color: #1f2937;
}
.av {
  width: 38px;
  height: 38px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
}
.dot {
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.dot::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}
.dot.on {
  color: #22c55e;
}
.dot.on::before {
  background: #22c55e;
}
.dot.off {
  color: #9ca3af;
}
.dot.off::before {
  background: #9ca3af;
}
.ico {
  background: none;
  border: none;
  padding: 6px;
  cursor: pointer;
  color: #9ca3af;
  border-radius: 6px;
  transition: all 0.15s;
}
.ico:hover {
  background: #fee2e2;
  color: #ef4444;
}

/* Body */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.welcome {
  text-align: center;
  padding: 48px 20px;
}
.w-emoji {
  font-size: 48px;
  margin-bottom: 12px;
}
.welcome h3 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #1f2937;
}
.welcome p {
  margin: 0 0 20px;
  color: #6b7280;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.chip {
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  transition: all 0.15s;
}
.chip:hover {
  background: #6366f1;
  color: #fff;
  border-color: #6366f1;
}

/* Msg */
.row {
  display: flex;
  gap: 10px;
  max-width: 88%;
  animation: fadeIn 0.25s;
}
.row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.row-av {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}
.row.user .row-av {
  background: #6366f1;
  color: #fff;
}
.row.assistant .row-av {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
}
.row-bd {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: hidden;
  min-width: 0;
}
.bubble {
  padding: 12px 16px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.7;
  overflow-wrap: break-word;
  overflow: hidden;
}
.bubble.user {
  background: #6366f1;
  color: #fff;
  border-bottom-right-radius: 4px;
}
.bubble.assistant {
  background: #fff;
  color: #1f2937;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.plain {
  white-space: pre-wrap;
}

/* Markdown body */
.md-body :deep(pre) {
  background: #1e293b;
  border-radius: 8px;
  padding: 14px 16px;
  overflow-x: auto;
  margin: 8px 0;
}
.md-body :deep(pre code) {
  color: #e2e8f0;
  font-size: 13px;
  font-family: 'Consolas', 'Monaco', monospace;
  background: transparent;
}
.md-body :deep(code) {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #e11d48;
}
.md-body :deep(table) {
  border-collapse: collapse;
  margin: 8px 0;
  width: 100%;
}
.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 6px 12px;
  text-align: left;
}
.md-body :deep(th) {
  background: #f9fafb;
  font-weight: 600;
}
.md-body :deep(blockquote) {
  border-left: 3px solid #6366f1;
  padding-left: 12px;
  color: #6b7280;
  margin: 8px 0;
}
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
  margin: 12px 0 6px;
  color: #1f2937;
}
.md-body :deep(ul),
.md-body :deep(ol) {
  padding-left: 20px;
}
.md-body :deep(a) {
  color: #6366f1;
}
.md-body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

/* Meta */
.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #9ca3af;
  padding: 0 4px;
}
.cp {
  background: none;
  border: none;
  padding: 2px 6px;
  font-size: 11px;
  color: #9ca3af;
  cursor: pointer;
  border-radius: 4px;
}
.cp:hover {
  background: #f3f4f6;
  color: #6366f1;
}

/* Dots */
.dots {
  display: flex;
  gap: 4px;
  padding: 14px 16px;
  background: #fff;
  border-radius: 14px;
  border-bottom-left-radius: 4px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.dots span {
  width: 7px;
  height: 7px;
  background: #cbd5e1;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}
.dots span:nth-child(1) {
  animation-delay: -0.32s;
}
.dots span:nth-child(2) {
  animation-delay: -0.16s;
}
@keyframes typing {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* Footer */
.chat-ftr {
  padding: 12px 20px 16px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.file-bar {
  margin-bottom: 8px;
}
.ftag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: #ede9fe;
  border-radius: 6px;
  font-size: 13px;
  color: #7c3aed;
}
.ftag button {
  background: none;
  border: none;
  color: #a78bfa;
  cursor: pointer;
  font-size: 12px;
}
.inp-row {
  display: flex;
  gap: 8px;
  background: #f3f4f6;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 8px 12px;
  transition: border-color 0.2s;
}
.inp-row:focus-within {
  border-color: #6366f1;
}
textarea {
  flex: 1;
  border: none;
  background: transparent;
  resize: none;
  outline: none;
  font-size: 14px;
  line-height: 1.6;
  font-family: inherit;
  max-height: 120px;
}
textarea::placeholder {
  color: #9ca3af;
}
.at-btn,
.sd-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.at-btn {
  color: #9ca3af;
  font-size: 18px;
  padding: 4px;
  border-radius: 6px;
  cursor: pointer;
}
.at-btn:hover {
  background: #e5e7eb;
}
.sd-btn {
  width: 36px;
  height: 36px;
  background: #6366f1;
  color: #fff;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.15s;
}
.sd-btn:hover:not(:disabled) {
  background: #4f46e5;
}
.sd-btn:disabled {
  background: #d1d5db;
  cursor: not-allowed;
}
.spin {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.hint {
  margin-top: 6px;
  padding: 0 4px;
  font-size: 11px;
  color: #9ca3af;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.chat-body::-webkit-scrollbar {
  width: 5px;
}
.chat-body::-webkit-scrollbar-track {
  background: transparent;
}
.chat-body::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}
</style>
