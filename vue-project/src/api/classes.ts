import request from './request'

export interface ClassInfo {
  id: number
  name: string
  courseId?: number
  courseName?: string
  studentCount?: number
  inviteCode?: string
}

export async function getClasses() {
  const res = await request.get('/teacher/classes')
  return res.data
}

export async function getClassDetail(classId: number) {
  const res = await request.get(`/teacher/classes/${classId}`)
  return res.data
}

export async function createClass(data: {
  name: string
  courseId?: number
  description?: string
}) {
  const res = await request.post('/teacher/classes', data)
  return res.data
}

export async function updateClass(classId: number, data: {
  name?: string
  courseId?: number
  description?: string
}) {
  const res = await request.put(`/teacher/classes/${classId}`, data)
  return res.data
}

export async function archiveClass(classId: number) {
  const res = await request.post(`/teacher/classes/${classId}/archive`)
  return res.data
}

export async function deleteClass(classId: number) {
  const res = await request.delete(`/teacher/classes/${classId}`)
  return res.data
}

export async function removeStudent(classId: number, studentId: string) {
  const res = await request.delete(`/teacher/classes/${classId}/students/${studentId}`)
  return res.data
}

export async function importStudents(classId: number, students: { studentId: string; studentName: string }[]) {
  const res = await request.post(`/teacher/classes/${classId}/students/import`, { students })
  return res.data
}
