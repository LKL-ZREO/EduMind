import request from './request'
import type { ApiResponse } from './types'

export type InteractionType = 'CHOICE' | 'OPEN' | 'EXERCISE'
export type InteractionStatus = 'ACTIVE' | 'CLOSED'

export interface StudentSummary {
  studentId: string
  studentName: string
}

export interface OptionItem {
  key: string
  text: string
}

export interface InteractionPush {
  interactionId: number
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

export interface LiveSessionInfo {
  sessionId: number
  sessionCode: string
  title: string
  className: string
  teacherName: string
  token: string
  studentId: string
  studentName: string
  hasActive: boolean
  currentInteraction: InteractionPush | null
  startedAt?: string
}

export interface LiveSessionCreated {
  sessionId: number
  sessionCode: string
  title: string
  classId: number
  status: string
  startedAt: string
}

export interface ActiveLiveSession {
  hasActive: boolean
  sessionId?: number
  sessionCode?: string
  title?: string
  status?: string
  startedAt?: string
}

export interface LiveStats {
  interactionId: number
  status: string
  totalStudents: number
  respondedCount: number
  distribution: Record<string, { count: number; percent: number }>
  correctRate: number | null
  unrespondedStudents: string[]
}

export interface QAQuestion {
  id: number
  question: string
  similarCount: number
  answered: boolean
  answerText: string | null
  createdAt: string
}

export interface QAMessage {
  topQuestions: QAQuestion[]
}

export interface OnlineStudents {
  count: number
  students: StudentSummary[]
  absentCount: number
  absentStudents: StudentSummary[]
}

export interface InteractionHistoryItem {
  interactionId: number
  type: string
  title: string
  description: string | null
  options: OptionItem[] | null
  correctKey: string | null
  timeLimit: number | null
  status: string
  createdAt: string
  totalStudents: number
  respondedCount: number
  correctRate: number | null
  myAnswer: string | null
  myCorrect: boolean | null
}

export interface ResponseItem {
  studentId: string
  studentName: string
  answer: string
  isCorrect: boolean | null
  respondedAt: string
}

export interface InteractionDetail {
  interactionId: number
  type: string
  title: string
  description: string | null
  options: OptionItem[] | null
  correctKey: string | null
  timeLimit: number | null
  status: string
  totalStudents: number
  respondedCount: number
  correctRate: number | null
  distribution: Record<string, { count: number; percent: number }>
  unrespondedStudents: string[]
  responses: ResponseItem[]
}

export interface CreateInteractionRequest {
  type: InteractionType
  title: string
  options?: OptionItem[]
  correctKey?: string
  timeLimit?: number
  knowledgePoint?: string
}

export interface GeneratedQuestion {
  type: InteractionType
  title: string
  options: OptionItem[] | null
  correctKey: string | null
}

export interface ReactionMsg {
  type: string
  emoji: string
  studentId: string
  studentName: string
  timestamp: number
}

export interface StudentProfile {
  studentId: string
  classId: number
  totalSessions: number
  totalInteractions: number
  totalAnswers: number
  correctAnswers: number
  correctRate: number
  participationRate: number
}

export interface DraftItem {
  interactionId: number
  type: InteractionType
  title: string
  knowledgePoint: string
  options: OptionItem[] | null
  correctKey: string | null
  timeLimit: number | null
  status: string
  createdAt: string
}

export function createSession(data: { classId: number; title?: string; courseId?: number }) {
  return request.post<ApiResponse<LiveSessionCreated>>('/live/create', data)
}

export function endSession(sessionId: number) {
  return request.post<ApiResponse<null>>(`/live/end/${sessionId}`)
}

export function getActiveSession(classId: number) {
  return request.get<ApiResponse<ActiveLiveSession>>('/live/active', { params: { classId } })
}

export function joinSession(data: { code: string; studentId: string; studentName: string }) {
  return request.post<ApiResponse<LiveSessionInfo>>('/live/join', data)
}

export function previewSession(code: string) {
  return request.get<ApiResponse<LiveSessionInfo>>(`/live/session/${code}`)
}

export function getCurrentInteraction(sessionId: number) {
  return request.get<ApiResponse<InteractionPush | null>>(
    `/live/session/${sessionId}/current-interaction`,
  )
}

export function getOnlineStudents(sessionId: number) {
  return request.get<ApiResponse<OnlineStudents>>(`/live/session/${sessionId}/students`)
}

export function getInteractionStats(sessionId: number) {
  return request.get<ApiResponse<LiveStats>>(`/live/session/${sessionId}/interaction-stats`)
}

export function getInteractionHistory(
  sessionId: number,
  studentId?: string,
  classroomToken?: string,
) {
  return request.get<ApiResponse<InteractionHistoryItem[]>>(
    `/live/session/${sessionId}/interactions`,
    {
      params: studentId ? { studentId } : {},
      headers: classroomToken ? { Authorization: `Bearer ${classroomToken}` } : {},
    },
  )
}

export function getInteractionDetail(sessionId: number, interactionId: number) {
  return request.get<ApiResponse<InteractionDetail>>(
    `/live/session/${sessionId}/interaction/${interactionId}/detail`,
  )
}

export function generateQuestion(topic: string, type: InteractionType) {
  return request.post<ApiResponse<GeneratedQuestion>>('/live/generate-question', { topic, type })
}

export function getReport(
  sessionId: number,
  title: string,
  duration: string,
  online: number,
  absent: number,
  qa: number,
) {
  return request.get<ApiResponse<{ html: string }>>(`/live/session/${sessionId}/report`, {
    params: { title, duration, online, absent, qa },
  })
}

export function getStudentProfile(studentId: string, classId: number) {
  return request.get<ApiResponse<StudentProfile>>(`/live/student/${studentId}/profile`, {
    params: { classId },
  })
}

export function getClassDrafts(classId: number) {
  return request.get<ApiResponse<DraftItem[]>>(`/live/class/${classId}/drafts`)
}
