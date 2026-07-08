import request from './request'

export interface Task {
  id: number
  classId: number
  taskName: string
  description: string
  deadline: string
  allowLate: boolean
  latePenalty: number
  status: string
  createdAt: string
}

export async function getTasksByClass(classId: number) {
  const res = await request.get(`/tasks?classId=${classId}`)
  return res.data
}

export async function getTaskDetail(taskId: number) {
  const res = await request.get(`/tasks/${taskId}`)
  return res.data
}

export async function createTask(data: {
  classId: number
  taskName: string
  description: string
  deadline: string
  allowLate?: boolean
  latePenalty?: number
}) {
  const res = await request.post('/tasks', data)
  return res.data
}

export async function updateTask(taskId: number, data: {
  taskName?: string
  description?: string
  deadline?: string
  allowLate?: boolean
  latePenalty?: number
}) {
  const res = await request.put(`/tasks/${taskId}`, data)
  return res.data
}

export async function deleteTask(taskId: number) {
  const res = await request.delete(`/tasks/${taskId}`)
  return res.data
}

export async function testReminder(taskId: number) {
  const res = await request.post(`/tasks/${taskId}/test-reminder`)
  return res.data
}
