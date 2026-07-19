import request from './request'
import type { AxiosResponse } from 'axios'

export interface LiveSessionInfo {
  sessionId: number; sessionCode: string; title: string; className: string
  teacherName: string; token: string; studentId: string; studentName: string
  hasActive: boolean; currentInteraction: InteractionPush | null
  startedAt?: string
}
export interface InteractionPush {
  interactionId: number; type: 'CHOICE' | 'OPEN' | 'EXERCISE'; status: 'ACTIVE' | 'CLOSED'
  title: string; description: string; options: OptionItem[] | null; correctKey: string | null
  timeLimit: number | null; deadlineEpochMs: number | null; serverTime: string
}
export interface OptionItem { key: string; text: string }
export interface LiveStats {
  interactionId: number; status: string; totalStudents: number; respondedCount: number
  distribution: Record<string, { count: number; percent: number }>
  correctRate: number | null; unrespondedStudents: string[]
}
export interface QAQuestion { id: number; question: string; similarCount: number; answered: boolean; answerText: string | null; createdAt: string }
export interface QAMessage { topQuestions: QAQuestion[] }

export function createSession(data: { classId: number; title?: string; courseId?: number }) {
  return request.post<any, AxiosResponse<{ data: any }>>('/live/create', data)
}
export function endSession(sessionId: number) { return request.post(`/live/end/${sessionId}`) }
export function getActiveSession(classId: number) {
  return request.get<any, AxiosResponse<{ data: any }>>('/live/active', { params: { classId } })
}
export function joinSession(data: { code: string; studentId: string; studentName: string }) {
  return request.post<any, AxiosResponse<{ data: LiveSessionInfo }>>('/live/join', data)
}
export function previewSession(code: string) {
  return request.get<any, AxiosResponse<{ data: LiveSessionInfo }>>(`/live/session/${code}`)
}
export function getCurrentInteraction(sessionId: number) {
  return request.get<any, AxiosResponse<{ data: InteractionPush }>>(`/live/session/${sessionId}/current-interaction`)
}
export function getOnlineStudents(sessionId: number) {
  return request.get<any, AxiosResponse<{ data: { count: number; students: { studentId: string; studentName: string }[] } }>>(`/live/session/${sessionId}/students`)
}
export function getInteractionStats(sessionId: number) {
  return request.get<any, AxiosResponse<{ data: LiveStats }>>(`/live/session/${sessionId}/interaction-stats`)
}
export interface InteractionHistoryItem {
  interactionId: number; type: string; title: string; description: string | null
  options: OptionItem[] | null; correctKey: string | null; timeLimit: number | null
  status: string; createdAt: string
  totalStudents: number; respondedCount: number; correctRate: number | null
  myAnswer: string | null; myCorrect: boolean | null
}
export function getInteractionHistory(sessionId: number, studentId?: string) {
  return request.get<any, AxiosResponse<{ data: InteractionHistoryItem[] }>>(`/live/session/${sessionId}/interactions`, { params: studentId ? { studentId } : {} })
}
export interface ResponseItem { studentId: string; studentName: string; answer: string; isCorrect: boolean | null; respondedAt: string }
export interface InteractionDetail {
  interactionId: number; type: string; title: string; description: string | null
  options: OptionItem[] | null; correctKey: string | null; timeLimit: number | null; status: string
  totalStudents: number; respondedCount: number; correctRate: number | null
  distribution: Record<string, { count: number; percent: number }>
  unrespondedStudents: string[]
  responses: ResponseItem[]
}
export function getInteractionDetail(sessionId: number, interactionId: number) {
  return request.get<any, AxiosResponse<{ data: InteractionDetail }>>(`/live/session/${sessionId}/interaction/${interactionId}/detail`)
}

// === 1. AI 生成题目 ===
export function generateQuestion(topic: string, type: string) {
  return request.post<any, AxiosResponse<{ data: { type: string; title: string; options: OptionItem[] | null; correctKey: string } }>>('/live/generate-question', { topic, type })
}

// === 2. 表情/举手 ===
export interface ReactionMsg { type: string; emoji: string; studentId: string; studentName: string; timestamp: number }

// === 3. 导出报告 ===
export function getReport(sessionId: number, title: string, duration: string, online: number, absent: number, qa: number) {
  return request.get<any, AxiosResponse<{ data: { html: string } }>>(`/live/session/${sessionId}/report`, { params: { title, duration, online, absent, qa } })
}

// === 5. 学生画像 ===
export interface StudentProfile { studentId: string; classId: number; totalSessions: number; totalInteractions: number; totalAnswers: number; correctAnswers: number; correctRate: number; participationRate: number }
export function getStudentProfile(studentId: string, classId: number) {
  return request.get<any, AxiosResponse<{ data: StudentProfile }>>(`/live/student/${studentId}/profile`, { params: { classId } })
}
