export type ChatMode = 'auto' | 'lesson_plan' | 'learning_analysis' | 'grading'

export type ChatSession = {
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

export type ChatHistoryMessage = {
  id?: number
  role: 'user' | 'assistant'
  content: string
  createdAt?: string
  model?: string
}

export type ChatSessionMutation = {
  title?: string
  classId: number | null
  kbIds: number[]
  mode: ChatMode
  pinned?: boolean
}

export type ChatStreamRequest = {
  message: string
  sessionId: string
  classId: number | null
  kbIds: number[]
  mode: ChatMode
}

export type ChatStreamEvent = {
  type: string
  data: Record<string, unknown>
}

export type ClassroomOption = {
  id: number
  name: string
}

export type KnowledgeBaseOption = {
  id: number
  name: string
  description?: string
}

export type ChatContextOptions = {
  classes: ClassroomOption[]
  knowledgeBases: KnowledgeBaseOption[]
}

export type ToolStep = {
  tool: string
  label: string
  status: 'running' | 'success' | 'error'
  elapsedMs?: number
}

export type Citation = {
  documentId: string
  documentName: string
  sectionIndex: number
  excerpt: string
}

export type MessageState = 'streaming' | 'complete' | 'stopped' | 'error'

export type UiMessage = {
  localId: string
  role: 'user' | 'assistant'
  content: string
  time: string
  state: MessageState
  toolSteps: ToolStep[]
  citations: Citation[]
  attachmentName?: string
}

export type LessonArtifact = {
  type: 'lesson_plan'
  title: string
  content: string
}

export type CalendarPlanPayload = {
  classId: number
  weekNumber: number
  plannedDate: string | null
  topic: string
  knowledgePoints: string | null
}
