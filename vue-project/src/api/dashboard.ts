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

export async function generateTeachingPlan(classId: number) {
  const res = await request.post('/dashboard/teaching-plan/generate', { classId })
  return res.data
}
