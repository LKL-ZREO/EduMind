export type DashboardClass = { id: number; name: string }

export type DashboardMetrics = {
  totalStudents: number
  studentTrend: number
  totalHomework: number
  newHomework: number
  avgScore: number
  scoreTrend: number
  warningStudents: number
}

export type ScoreDistribution = {
  range: string
  count: number
  percentage: number
  color: string
}

export type KnowledgeMastery = {
  id?: number
  name: string
  mastery: number
  errorCount: number
  criticalCount: number
  color?: string
}

export type FrequentError = {
  question: string
  difficulty: string
  difficultyLabel?: string
  errorRate: number
  errorCount: number
  knowledgePoint: string
  affectedStudentCount?: number
  affectedStudentRate?: number
  assignmentCount?: number
  latestSeenAt?: string
}

export type StudentOverview = {
  id?: number
  name: string
  studentId?: string
  avgScore: number
  homeworkCount: number
  errorCount: number
  trend: number
  needAttention: boolean
}

export type ScorePoint = {
  no: number
  submissionId: number
  assignmentName: string
  date: string
  score: number
  change: number
  late: boolean
}

export type StudentInsight = {
  student: { id?: number; studentId?: string; name: string }
  summary: {
    avgScore: number
    latestScore: number
    highestScore: number
    lowestScore: number
    completedCount: number
    lateCount: number
    totalErrorCount: number
    criticalErrorCount: number
    latestChange: number
  }
  risk: { level: 'LOW' | 'MEDIUM' | 'HIGH'; reasons: string[]; suggestions: string[] }
  scoreHistory: ScorePoint[]
  weakKnowledgePoints: Array<{
    name: string
    errorCount: number
    criticalCount: number
    latestSeenAt?: string
  }>
  recentErrors: Array<{
    id: number
    submissionId: number
    assignmentName: string
    knowledgePoint: string
    errorText: string
    severity: string
    createdAt: string
  }>
}

export type ConfusionStat = { name: string; count: number }
export type ConfusionEvent = {
  id: number
  studentName?: string
  knowledgePoint: string
  question?: string
  createdAt: string
}
export type LiveConfusions = {
  stats: ConfusionStat[]
  events: ConfusionEvent[]
  total: number
}

export type ReclassificationTask = {
  taskId: string
  classId: number
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED'
  total: number
  processed: number
  reclassified: number
  remainingOther: number
  failed: number
  errorMessage?: string
}

export type TeacherKnowledgeItem = {
  id?: number
  name: string
  color: string
  sortOrder: number
}

export type PreLessonWeakPoint = {
  name: string
  errorCount: number
  mastery: number
  severity: string
}

export type PreLessonTier = {
  label: string
  range: string
  count: number
  suggestion: string
}

export type PreLessonOverview = {
  classId: number
  className: string
  avgScore: number
  totalStudents: number
  warningCount: number
  weakPoints: PreLessonWeakPoint[]
  liveSessionCount: number
  liveAvgCorrectRate: number
  participationRate: number
  aiSuggestion: string
  tieredGroups: PreLessonTier[]
}

export type TimelineItem = {
  type: string
  typeLabel: string
  icon: string
  id: number
  title: string
  date: string
  time: string
  status: string
  detail: string | null
  interactionCount?: number
  avgCorrectRate?: number
  topConfusion?: string
}

export type Timeline = {
  weeks: Array<{ weekNumber: number; label: string; items: TimelineItem[] }>
}

export type CalendarPlan = {
  id?: number
  classId: number
  weekNumber: number
  plannedDate?: string | null
  topic: string
  knowledgePoints?: string | null
  status?: string
}

export type PreviewQuestion = {
  question: string
  options: Array<{ key: string; text: string }> | null
  correctKey: string
  explanation: string
}

export type PreviewTask = {
  id: number
  classId: number
  title: string
  knowledgePoint: string
  guideText: string
  questions: PreviewQuestion[]
  discussionQuestion: string
  status: string
  createdAt: string
}

export type LessonStage = {
  id: string
  phase: string
  title: string
  minutes: number
  teacherAction: string
  studentAction: string
  resource: string
}

export type MaterialKey = 'preview' | 'questions' | 'homework'

export type LessonDraft = {
  classId: number
  topic: string
  plannedDate: string
  duration: number
  knowledgePoints: string[]
  objectives: string[]
  evidenceIds: string[]
  stages: LessonStage[]
  differentiation: Array<{ label: string; range: string; count: number; strategy: string }>
  materialReady: Record<MaterialKey, boolean>
  notes: string
  status: 'DRAFT' | 'READY'
}
