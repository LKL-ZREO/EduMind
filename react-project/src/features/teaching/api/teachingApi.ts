import apiClient from '@/shared/api/client'
import type { ApiResponse } from '@/shared/api/types'
import { unwrapApiResponse } from '@/shared/api/unwrap'
import type {
  CalendarPlan,
  ConfusionEvent,
  ConfusionStat,
  DashboardClass,
  DashboardMetrics,
  FrequentError,
  KnowledgeMastery,
  LiveConfusions,
  PreLessonOverview,
  PreviewTask,
  ReclassificationTask,
  ScoreDistribution,
  StudentInsight,
  StudentOverview,
  TeacherKnowledgeItem,
  Timeline,
} from '@/features/teaching/model/types'

async function getResult<T>(url: string, params?: Record<string, unknown>, fallback = '加载失败') {
  const response = await apiClient.get<ApiResponse<T>>(url, { params })
  return unwrapApiResponse(response.data, fallback)
}

export const getDashboardClasses = () =>
  getResult<DashboardClass[]>('/dashboard/classes', undefined, '加载班级失败')

export const getDashboardMetrics = (classId: number) =>
  getResult<DashboardMetrics>('/dashboard/metrics', { classId }, '加载核心指标失败')

export const getScoreDistribution = (classId: number) =>
  getResult<ScoreDistribution[]>('/dashboard/score-distribution', { classId }, '加载成绩分布失败')

export const getKnowledgeMastery = (classId: number) =>
  getResult<KnowledgeMastery[]>('/dashboard/knowledge-mastery', { classId }, '加载知识点掌握度失败')

export const getFrequentErrors = (classId: number, knowledgePoint?: string) =>
  getResult<FrequentError[]>(
    '/dashboard/frequent-errors',
    { classId, ...(knowledgePoint ? { knowledgePoint } : {}) },
    '加载错题详情失败',
  )

export const getStudentOverview = (classId: number) =>
  getResult<StudentOverview[]>('/dashboard/students', { classId, sortBy: 'score' }, '加载学生失败')

export const getStudentInsight = (classId: number, student: StudentOverview) =>
  getResult<StudentInsight>(
    '/dashboard/student-insight',
    {
      classId,
      studentName: student.name,
      ...(student.studentId ? { studentId: student.studentId } : {}),
    },
    '加载学生诊断失败',
  )

export async function getConfusionSignals(classId: number) {
  const [events, stats, live] = await Promise.all([
    getResult<ConfusionEvent[]>('/dashboard/student-confusions', { classId }),
    getResult<ConfusionStat[]>('/dashboard/student-confusions/stats', { classId }),
    getResult<LiveConfusions>('/dashboard/live-confusions', { classId }),
  ])
  return { events, stats, live }
}

export async function addTeacherKnowledge(classId: number, name: string, color: string) {
  const response = await apiClient.post<ApiResponse<ReclassificationTask>>(
    '/dashboard/teacher-knowledge/add',
    { classId, name, color },
  )
  return unwrapApiResponse(response.data, '添加知识点失败')
}

export async function saveTeacherKnowledge(classId: number, items: TeacherKnowledgeItem[]) {
  const response = await apiClient.post<ApiResponse<ReclassificationTask>>(
    '/dashboard/teacher-knowledge/batch',
    { classId, items },
  )
  return unwrapApiResponse(response.data, '保存知识点失败')
}

export const getReclassificationTask = (classId: number, taskId: string) =>
  getResult<ReclassificationTask>(
    `/dashboard/knowledge-reclassification/${encodeURIComponent(taskId)}`,
    { classId },
    '加载重分类进度失败',
  )

export async function generateTeachingPlan(payload: {
  classId: number
  goals: string[]
  weakKnowledgePoints: string[]
  planType: string
}) {
  const response = await apiClient.post<ApiResponse<string>>(
    '/dashboard/teaching-plan/generate',
    payload,
  )
  return unwrapApiResponse(response.data, '生成教案失败')
}

export const getPreLessonOverview = (classId: number) =>
  getResult<PreLessonOverview>('/dashboard/pre-lesson', { classId }, '加载备课学情失败')

export const getPreLessonSuggestion = (classId: number) =>
  getResult<{ suggestion: string }>(
    '/dashboard/pre-lesson/suggestion',
    { classId },
    '加载 AI 建议失败',
  )

export const getTimeline = (classId: number, limit = 12) =>
  getResult<Timeline>('/dashboard/timeline', { classId, limit }, '加载教学时间线失败')

export async function addCalendarPlan(plan: CalendarPlan) {
  const response = await apiClient.post<ApiResponse<CalendarPlan>>(
    '/dashboard/teaching-calendar/add',
    plan,
  )
  return unwrapApiResponse(response.data, '加入教学日历失败')
}

export async function createPreviewTask(payload: {
  classId: number
  knowledgePoint: string
  topic?: string
  docId?: string
}) {
  const response = await apiClient.post<ApiResponse<PreviewTask>>('/preview/create', payload, {
    timeout: 180_000,
  })
  return unwrapApiResponse(response.data, '生成预习任务失败')
}

export const listPreviewTasks = (classId: number) =>
  getResult<PreviewTask[]>('/preview/list', { classId }, '加载预习任务失败')

export const getPreviewTask = (taskId: number) =>
  getResult<PreviewTask>(`/preview/${taskId}`, undefined, '加载预习任务失败')

export async function closePreviewTask(taskId: number) {
  const response = await apiClient.post<ApiResponse<null>>(`/preview/${taskId}/close`)
  unwrapApiResponse(response.data, '关闭预习任务失败')
}
