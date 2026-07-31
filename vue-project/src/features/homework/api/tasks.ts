import request from '@/shared/api/request'
import type { ApiResponse } from '@/shared/api/types'

export interface Task {
  id: number
  classId: number
  taskName: string
  description: string
  deadline?: string
  allowLate: boolean
  latePenalty: number
  status: string
  createdAt: string
}

export interface DraftQuestion {
  id?: number
  type?: 'CHOICE' | 'OPEN' | 'EXERCISE' | 'HOMEWORK'
  title: string
  requirement: string
  options?: Array<{ key: string; text: string }> | null
  correctKey?: string | null
  explanation?: string | null
  knowledgePoint?: string | null
  difficulty?: 'easy' | 'medium' | 'hard' | null
  timeLimit?: number | null
  score: number
  uploadRequired: boolean
}

export interface HomeworkDraft {
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

export interface SaveDraftPayload {
  taskName: string
  description?: string
  deadline?: string | null
  allowLate?: boolean
  latePenalty?: number
  questions: DraftQuestion[]
}

export async function getTasksByClass(classId: number) {
  const res = await request.get<ApiResponse<Task[]>>(`/tasks?classId=${classId}`)
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
  deadline?: string | null
  allowLate?: boolean
  latePenalty?: number
}) {
  const res = await request.post('/tasks', data)
  return res.data
}

export async function updateTask(
  taskId: number,
  data: {
    taskName?: string
    description?: string
    deadline?: string | null
    allowLate?: boolean
    latePenalty?: number
  },
) {
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

export async function getDrafts() {
  const res = await request.get('/tasks/drafts')
  return res.data
}

export async function getDraft(draftId: number) {
  const res = await request.get(`/tasks/drafts/${draftId}`)
  return res.data
}

export async function saveDraft(data: SaveDraftPayload, draftId?: number | null) {
  const res = draftId
    ? await request.put(`/tasks/drafts/${draftId}`, data)
    : await request.post('/tasks/drafts', data)
  return res.data
}

export async function deleteDraft(draftId: number) {
  const res = await request.delete(`/tasks/drafts/${draftId}`)
  return res.data
}

export async function publishDraft(
  draftId: number,
  data: {
    classIds: number[]
    taskName?: string
    deadline?: string | null
    allowLate?: boolean
    latePenalty?: number
  },
) {
  const res = await request.post(`/tasks/drafts/${draftId}/publish`, data)
  return res.data
}
