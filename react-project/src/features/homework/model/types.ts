export type QuestionType = 'CHOICE' | 'OPEN' | 'EXERCISE' | 'HOMEWORK'
export type QuestionDifficulty = 'easy' | 'medium' | 'hard'

export type QuestionOption = {
  key: string
  text: string
}

export type DraftQuestion = {
  id?: number
  type?: QuestionType
  title: string
  requirement: string
  options?: QuestionOption[] | null
  correctKey?: string | null
  explanation?: string | null
  knowledgePoint?: string | null
  difficulty?: QuestionDifficulty | null
  timeLimit?: number | null
  score: number
  uploadRequired: boolean
}

export type TeachingQuestion = DraftQuestion & {
  id: number
  type: QuestionType
  sourceDocId: string | null
  aiGenerated: boolean
  archived: boolean
  createdAt: string
  updatedAt: string
}

export type HomeworkDraft = {
  id: number
  taskName: string
  description?: string
  deadline?: string
  allowLate: boolean
  latePenalty: number
  status: string
  createdAt: string
  updatedAt: string
  questions: DraftQuestion[]
}

export type SaveDraftPayload = {
  taskName: string
  description?: string
  deadline?: string | null
  allowLate: boolean
  latePenalty: number
  questions: DraftQuestion[]
}

export type PublishDraftPayload = {
  classIds: number[]
  taskName?: string
  deadline?: string | null
  allowLate?: boolean
  latePenalty?: number
}

export type HomeworkTask = {
  id: number
  classId: number
  taskName: string
  description: string
  deadline?: string
  allowLate: boolean
  latePenalty: number
  status: string
  createdAt: string
  submittedCount?: number
  totalSubmissions?: number
  avgScore?: number
  expired?: boolean
}

export type ClassOption = { id: number; name: string }

export type TaskGroup = {
  key: string
  taskName: string
  description: string
  deadline?: string
  allowLate: boolean
  latePenalty: number
  status: string
  createdAt: string
  tasks: HomeworkTask[]
  classNames: string[]
}

export type ScoreDistribution = {
  excellent: number
  good: number
  medium: number
  pass: number
  fail: number
}

export type TaskSubmission = {
  submissionId: number
  studentName: string
  studentId?: string
  score: number | null
  finalScore: number | null
  isLate: boolean
  penaltyApplied?: boolean
  submittedAt: string | null
}

export type TaskDetail = HomeworkTask & {
  distribution: ScoreDistribution
  submittedCount: number
  totalSubmissions: number
  avgScore: number
  submissions: TaskSubmission[]
}

export type PublicTask = Pick<
  HomeworkTask,
  'id' | 'taskName' | 'description' | 'deadline' | 'allowLate' | 'latePenalty'
>

export type GradingStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export type GradingResult = {
  submissionId: number
  status: GradingStatus
  studentName?: string
  assignmentName?: string
  submitCount?: number
  remainingAttempts?: number
  totalScore?: number
  contentScore?: number
  finalScore?: number | null
  overallComment?: string
  strengths?: string[]
  weaknesses?: string[]
  suggestions?: string
  isLate?: boolean
  penaltyApplied?: boolean
  errorMessage?: string
}

export type MismatchWarning = { fileNameValue: string; selectedValue: string }

export type SubmitResponseData = {
  needBind?: boolean
  studentId?: string
  studentName?: string
  className?: string
  assignmentName?: string
  submissionId?: number
  submitCount?: number
  remainingAttempts?: number
  warnings?: {
    classMismatch?: MismatchWarning
    taskMismatch?: MismatchWarning
  }
}

export type SubmissionContent = {
  submissionId: number
  fileName: string
  studentName: string
  content: string
}
