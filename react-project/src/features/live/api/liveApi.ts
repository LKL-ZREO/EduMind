import apiClient from '@/shared/api/client'
import type { ApiResponse } from '@/shared/api/types'
import { unwrapApiResponse } from '@/shared/api/unwrap'
import type {
  ActiveLiveSession,
  ConfusionResult,
  ConfusionStats,
  InteractionDetail,
  InteractionHistoryItem,
  InteractionPush,
  InteractionTiming,
  LiveSessionCreated,
  LiveSessionInfo,
  LiveStats,
  OnlineStudents,
  QuestionBoardItem,
  StudentProfile,
} from '@/features/live/model/types'

async function getResult<T>(
  url: string,
  params?: Record<string, unknown>,
  headers?: Record<string, string>,
) {
  const response = await apiClient.get<ApiResponse<T>>(url, { params, headers })
  return unwrapApiResponse(response.data, '课堂数据加载失败')
}

export async function createLiveSession(payload: { classId: number; title?: string }) {
  const response = await apiClient.post<ApiResponse<LiveSessionCreated>>('/live/create', payload)
  return unwrapApiResponse(response.data, '创建课堂失败')
}

export async function endLiveSession(sessionId: number) {
  const response = await apiClient.post<ApiResponse<null>>(`/live/end/${sessionId}`)
  unwrapApiResponse(response.data, '结束课堂失败')
}

export const getActiveLiveSession = (classId: number) =>
  getResult<ActiveLiveSession>('/live/active', { classId })

export async function joinLiveSession(payload: {
  code: string
  studentId: string
  studentName?: string
}) {
  const response = await apiClient.post<ApiResponse<LiveSessionInfo>>('/live/join', payload)
  return unwrapApiResponse(response.data, '加入课堂失败')
}

export async function quickJoinLiveSession(code: string) {
  const response = await apiClient.post<ApiResponse<LiveSessionInfo>>('/live/quick-join', { code })
  return unwrapApiResponse(response.data, '自动加入课堂失败')
}

export async function unbindStudentDevice() {
  const response = await apiClient.post<ApiResponse<null>>('/live/device/unbind')
  unwrapApiResponse(response.data, '清除设备身份失败')
}

export const previewLiveSession = (code: string) =>
  getResult<LiveSessionInfo>(`/live/session/${encodeURIComponent(code)}`)

export const getOnlineStudents = (sessionId: number) =>
  getResult<OnlineStudents>(`/live/session/${sessionId}/students`)

export const getInteractionStats = (sessionId: number) =>
  getResult<LiveStats | null>(`/live/session/${sessionId}/interaction-stats`)

export const getQuestionBoard = (sessionId: number) =>
  getResult<QuestionBoardItem[]>(`/live/session/${sessionId}/question-board`)

export async function sendQuestion(sessionId: number, questionId: number, timeLimit?: number) {
  const response = await apiClient.post<ApiResponse<InteractionPush>>(
    `/live/session/${sessionId}/question/${questionId}/send`,
    timeLimit ? { timeLimit } : {},
  )
  return unwrapApiResponse(response.data, '发送题目失败')
}

export async function extendInteraction(sessionId: number, interactionId: number, seconds: number) {
  const response = await apiClient.post<ApiResponse<InteractionTiming>>(
    `/live/session/${sessionId}/interaction/${interactionId}/extend`,
    { seconds },
  )
  return unwrapApiResponse(response.data, '延长作答时间失败')
}

export const getInteractionHistory = (
  sessionId: number,
  studentId?: string,
  classroomToken?: string,
) =>
  getResult<InteractionHistoryItem[]>(
    `/live/session/${sessionId}/interactions`,
    studentId ? { studentId } : undefined,
    classroomToken ? { Authorization: `Bearer ${classroomToken}` } : undefined,
  )

export const getInteractionDetail = (sessionId: number, interactionId: number) =>
  getResult<InteractionDetail>(`/live/session/${sessionId}/interaction/${interactionId}/detail`)

export const getConfusionStats = (sessionId: number) =>
  getResult<ConfusionStats>(`/live/session/${sessionId}/confusion-stats`)

export async function markConfusion(sessionId: number, interactionId: number, token: string) {
  const response = await apiClient.post<ApiResponse<ConfusionResult>>(
    '/live/confusion/mark',
    { sessionId, interactionId },
    { timeout: 30_000, headers: { Authorization: `Bearer ${token}` } },
  )
  return unwrapApiResponse(response.data, '生成解析失败')
}

export const getStudentProfile = (studentId: string, classId: number) =>
  getResult<StudentProfile>(`/live/student/${encodeURIComponent(studentId)}/profile`, { classId })

export const getLiveReport = (
  sessionId: number,
  params: { title: string; duration: string; online: number; absent: number; qa: number },
) => getResult<{ html: string }>(`/live/session/${sessionId}/report`, params)
