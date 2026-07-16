import request from './request'

export async function getDashboardClasses() {
  const res = await request.get('/dashboard/classes')
  return res.data
}

export async function getDashboardMetrics(classId: number) {
  const res = await request.get('/dashboard/metrics', { params: { classId } })
  return res.data
}

export async function getScoreDistribution(classId: number) {
  const res = await request.get('/dashboard/score-distribution', { params: { classId } })
  return res.data
}

export async function getKnowledgeMastery(classId: number) {
  const res = await request.get('/dashboard/knowledge-mastery', { params: { classId } })
  return res.data
}

export async function getFrequentErrors(classId: number, knowledgePoint?: string) {
  const res = await request.get('/dashboard/frequent-errors', {
    params: { classId, knowledgePoint },
  })
  return res.data
}

export async function getStudents(classId: number, sortBy?: string, search?: string) {
  const res = await request.get('/dashboard/students', {
    params: { classId, sortBy, search },
  })
  return res.data
}

export async function addTeacherKnowledge(classId: number, data: Record<string, unknown>) {
  const res = await request.post('/dashboard/teacher-knowledge/add', {
    classId,
    ...data,
  })
  return res.data
}

export async function updateTeacherKnowledge(id: number, data: Record<string, unknown>) {
  const res = await request.put(`/dashboard/teacher-knowledge/${id}`, data)
  return res.data
}

export async function batchUpdateTeacherKnowledge(items: Record<string, unknown>[]) {
  const res = await request.post('/dashboard/teacher-knowledge/batch', items)
  return res.data
}

export async function getStudentProgress(classId: number, studentId: string) {
  const res = await request.get('/dashboard/student-progress', {
    params: { classId, studentId },
  })
  return res.data
}

export async function getWeakPoints(classId: number) {
  const res = await request.get('/dashboard/weak-points', { params: { classId } })
  return res.data
}

export async function generateTeachingPlan(data: {
  classId: number
  goals: string[]
  weakKnowledgePoints: string[]
  planType?: string
}) {
  const res = await request.post('/dashboard/teaching-plan/generate', data)
  return res.data
}

// ==================== 教学日历 ====================

export interface WeekItem {
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

export interface WeekGroup {
  weekNumber: number
  label: string
  items: WeekItem[]
}

export interface TimelineDTO {
  weeks: WeekGroup[]
}

export async function getTimeline(classId: number, limit = 15) {
  const res = await request.get('/dashboard/timeline', { params: { classId, limit } })
  return res.data
}

// 教学日历 CRUD
export interface CalendarPlan {
  id?: number
  classId: number
  weekNumber: number
  plannedDate?: string
  topic: string
  knowledgePoints?: string
  status?: string
}

export async function getTeachingCalendar(classId: number) {
  const res = await request.get('/dashboard/teaching-calendar', { params: { classId } })
  return res.data
}

export async function addCalendarPlan(plan: CalendarPlan) {
  const res = await request.post('/dashboard/teaching-calendar/add', plan)
  return res.data
}

export async function deleteCalendarPlan(id: number) {
  const res = await request.delete(`/dashboard/teaching-calendar/${id}`)
  return res.data
}

// ==================== 备课学情仪表盘 ====================

export interface PreLessonWeakPoint {
  name: string
  errorCount: number
  mastery: number
  severity: string
}

export interface PreLessonTierGroup {
  label: string
  range: string
  count: number
  suggestion: string
}

export interface PreLessonOverview {
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
  tieredGroups: PreLessonTierGroup[]
}

export async function getPreLessonOverview(classId: number) {
  const res = await request.get('/dashboard/pre-lesson', { params: { classId } })
  return res.data
}

/** AI 备课建议（独立接口，加载较慢，前端异步调用） */
export async function getPreLessonSuggestion(classId: number) {
  const res = await request.get('/dashboard/pre-lesson/suggestion', { params: { classId } })
  return res.data
}
