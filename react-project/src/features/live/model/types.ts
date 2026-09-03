export type LiveRole = 'teacher' | 'student'
export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected'
export type InteractionType = 'CHOICE' | 'OPEN' | 'EXERCISE'
export type InteractionStatus = 'ACTIVE' | 'CLOSED'

export type StudentSummary = { studentId: string; studentName: string }
export type OptionItem = { key: string; text: string }

export type InteractionPush = {
  interactionId: number
  questionId: number
  type: InteractionType
  status: InteractionStatus
  title: string
  description: string | null
  options: OptionItem[] | null
  correctKey: string | null
  timeLimit: number | null
  deadlineEpochMs: number | null
  serverTime: string
}

export type LiveSessionInfo = {
  sessionId: number
  sessionCode: string
  title: string
  className: string
  teacherName: string
  token: string
  studentId: string
  studentName: string
  requiresStudentName?: boolean
  currentInteraction: InteractionPush | null
  startedAt?: string
}

export type LiveSessionCreated = {
  sessionId: number
  sessionCode: string
  title: string
  classId: number
  status: string
  startedAt: string
}

export type ActiveLiveSession = {
  hasActive: boolean
  sessionId?: number
  sessionCode?: string
  title?: string
  status?: string
  startedAt?: string
}

export type DistributionItem = { count: number; percent: number }

export type LiveStats = {
  interactionId: number
  status: string
  totalStudents: number
  respondedCount: number
  distribution: Record<string, DistributionItem>
  correctRate: number | null
  unrespondedStudents: string[]
}

export type QAQuestion = {
  id: number
  question: string
  similarCount: number
  answered: boolean
  answerText: string | null
  createdAt: string
}

export type QAMessage = { topQuestions: QAQuestion[] }

export type OnlineStudents = {
  count: number
  students: StudentSummary[]
  absentCount: number
  absentStudents: StudentSummary[]
}

export type InteractionHistoryItem = {
  interactionId: number
  questionId: number
  type: InteractionType
  title: string
  description: string | null
  options: OptionItem[] | null
  correctKey: string | null
  timeLimit: number | null
  status: string
  knowledgePoint?: string | null
  difficulty?: string | null
  createdAt: string
  totalStudents: number
  respondedCount: number
  correctRate: number | null
  myAnswer: string | null
  myCorrect: boolean | null
}

export type ResponseItem = {
  studentId: string
  studentName: string
  answer: string
  isCorrect: boolean | null
  respondedAt: string
}

export type InteractionDetail = {
  interactionId: number
  questionId: number
  type: InteractionType
  title: string
  description: string | null
  options: OptionItem[] | null
  correctKey: string | null
  timeLimit: number | null
  status: string
  difficulty: string | null
  explanation: string | null
  totalStudents: number
  respondedCount: number
  correctRate: number | null
  distribution: Record<string, DistributionItem>
  unrespondedStudents: string[]
  responses: ResponseItem[]
}

export type ReactionMessage = {
  type: string
  emoji: string
  studentId: string
  studentName: string
  timestamp: number
}

export type StudentProfile = {
  studentId: string
  classId: number
  totalSessions: number
  totalInteractions: number
  totalAnswers: number
  correctAnswers: number
  correctRate: number
  participationRate: number
}

export type QuestionBoardItem = {
  questionId: number
  interactionId: number | null
  type: InteractionType
  title: string
  description: string | null
  options: OptionItem[] | null
  correctKey: string | null
  knowledgePoint: string | null
  difficulty: 'easy' | 'medium' | 'hard' | null
  status: 'UNSENT' | 'ACTIVE' | 'CLOSED'
  sortOrder: number
  timeLimit: number | null
  sendCount: number
  createdAt: string
  activatedAt: string | null
  deadlineEpochMs: number | null
  totalStudents: number
  respondedCount: number
  correctRate: number | null
  distribution: Record<string, DistributionItem>
}

export type InteractionTiming = {
  interactionId: number
  deadlineEpochMs: number
  addedSeconds: number
}

export type HandEntry = { studentId: string; studentName: string; raisedAt: number }
export type HandQueue = { waiting: HandEntry[]; called: HandEntry[] }
export type TeacherStatus = { online: boolean; sessionEnded?: boolean }
export type ConfusionStats = { stats: Array<{ name: string; count: number }>; total: number }
export type ConfusionResult = { knowledgePoint: string; explanation: string }

export type LiveSocketEvent =
  | { type: 'stats'; payload: LiveStats }
  | { type: 'interaction'; payload: InteractionPush }
  | { type: 'timing'; payload: InteractionTiming }
  | { type: 'qa'; payload: QAMessage }
  | { type: 'presence'; payload: OnlineStudents }
  | { type: 'reaction'; payload: ReactionMessage }
  | { type: 'handQueue'; payload: HandQueue }
  | { type: 'teacherStatus'; payload: TeacherStatus }
