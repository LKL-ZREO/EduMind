import request from './request'
import type { ApiResponse } from './types'

export type QuestionType = 'CHOICE' | 'OPEN' | 'EXERCISE' | 'HOMEWORK'

export interface QuestionOption {
  key: string
  text: string
}

export interface TeachingQuestion {
  id: number
  type: QuestionType
  title: string
  requirement: string | null
  options: QuestionOption[] | null
  correctKey: string | null
  explanation: string | null
  knowledgePoint: string | null
  difficulty: 'easy' | 'medium' | 'hard' | null
  timeLimit: number | null
  score: number
  uploadRequired: boolean
  sourceDocId: string | null
  aiGenerated: boolean
  archived: boolean
  createdAt: string
  updatedAt: string
}

export interface QuestionPayload {
  type?: QuestionType
  title: string
  requirement?: string | null
  options?: QuestionOption[] | null
  correctKey?: string | null
  explanation?: string | null
  knowledgePoint?: string | null
  difficulty?: 'easy' | 'medium' | 'hard' | null
  timeLimit?: number | null
  score?: number
  uploadRequired?: boolean
  sourceDocId?: string | null
  aiGenerated?: boolean
}

export async function searchQuestions(
  params: {
    keyword?: string
    sourceDocId?: string
    type?: QuestionType
  } = {},
) {
  const response = await request.get<ApiResponse<TeachingQuestion[]>>('/questions', { params })
  return response.data
}

export async function getQuestion(id: number) {
  const response = await request.get<ApiResponse<TeachingQuestion>>(`/questions/${id}`)
  return response.data
}

export async function createQuestion(payload: QuestionPayload) {
  const response = await request.post<ApiResponse<TeachingQuestion>>('/questions', payload)
  return response.data
}

export async function updateQuestion(id: number, payload: Partial<QuestionPayload>) {
  const response = await request.patch<ApiResponse<TeachingQuestion>>(`/questions/${id}`, payload)
  return response.data
}

export async function archiveQuestion(id: number) {
  const response = await request.delete<ApiResponse<null>>(`/questions/${id}`)
  return response.data
}
