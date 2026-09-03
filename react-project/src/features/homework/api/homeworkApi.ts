import apiClient from '@/shared/api/client'
import type { ApiResponse } from '@/shared/api/types'
import { unwrapApiResponse } from '@/shared/api/unwrap'
import type {
  ClassOption,
  GradingResult,
  HomeworkDraft,
  HomeworkTask,
  PublicTask,
  PublishDraftPayload,
  SaveDraftPayload,
  SubmissionContent,
  SubmitResponseData,
  TaskDetail,
  TeachingQuestion,
} from '@/features/homework/model/types'

export async function getTasksForClasses(classIds: number[]): Promise<HomeworkTask[]> {
  const results = await Promise.allSettled(
    classIds.map((classId) =>
      apiClient.get<ApiResponse<HomeworkTask[]>>('/tasks', { params: { classId } }),
    ),
  )
  return results.flatMap((result) => {
    if (result.status !== 'fulfilled') return []
    try {
      return unwrapApiResponse(result.value.data, '加载已发布作业失败') || []
    } catch {
      return []
    }
  })
}

export async function getHomeworkDrafts(): Promise<HomeworkDraft[]> {
  const response = await apiClient.get<ApiResponse<HomeworkDraft[]>>('/tasks/drafts')
  return unwrapApiResponse(response.data, '加载草稿失败') || []
}

export async function saveHomeworkDraft(
  payload: SaveDraftPayload,
  draftId?: number | null,
): Promise<HomeworkDraft> {
  const response = draftId
    ? await apiClient.put<ApiResponse<HomeworkDraft>>(`/tasks/drafts/${draftId}`, payload)
    : await apiClient.post<ApiResponse<HomeworkDraft>>('/tasks/drafts', payload)
  return unwrapApiResponse(response.data, '保存草稿失败')
}

export async function deleteHomeworkDraft(draftId: number): Promise<void> {
  const response = await apiClient.delete<ApiResponse<null>>(`/tasks/drafts/${draftId}`)
  unwrapApiResponse(response.data, '删除草稿失败')
}

export async function publishHomeworkDraft(
  draftId: number,
  payload: PublishDraftPayload,
): Promise<HomeworkTask[]> {
  const response = await apiClient.post<ApiResponse<HomeworkTask[]>>(
    `/tasks/drafts/${draftId}/publish`,
    payload,
  )
  return unwrapApiResponse(response.data, '发布作业失败') || []
}

export async function searchHomeworkQuestions(keyword = ''): Promise<TeachingQuestion[]> {
  const response = await apiClient.get<ApiResponse<TeachingQuestion[]>>('/questions', {
    params: keyword ? { keyword } : undefined,
  })
  return unwrapApiResponse(response.data, '加载题库失败') || []
}

export async function getTaskDetail(taskId: number): Promise<TaskDetail> {
  const response = await apiClient.get<ApiResponse<TaskDetail>>(`/tasks/${taskId}`)
  return unwrapApiResponse(response.data, '加载作业详情失败')
}

export async function getPublicClasses(): Promise<ClassOption[]> {
  const response = await apiClient.get<ApiResponse<ClassOption[]>>('/homework/classes')
  return unwrapApiResponse(response.data, '加载班级失败') || []
}

export async function getPublicTasks(classId: number): Promise<PublicTask[]> {
  const response = await apiClient.get<ApiResponse<PublicTask[]>>('/homework/tasks', {
    params: { classId },
  })
  return unwrapApiResponse(response.data, '加载作业失败') || []
}

export async function submitHomework(payload: {
  file: File
  classId: number
  taskId: number
  confirm: boolean
  onProgress?: (percent: number) => void
}): Promise<ApiResponse<SubmitResponseData>> {
  const formData = new FormData()
  formData.append('file', payload.file)
  formData.append('expectedClassId', String(payload.classId))
  formData.append('expectedTaskId', String(payload.taskId))
  if (payload.confirm) formData.append('confirm', 'true')
  const response = await apiClient.post<ApiResponse<SubmitResponseData>>(
    '/homework/submit',
    formData,
    {
      timeout: 120_000,
      onUploadProgress: (event) => {
        if (event.total) payload.onProgress?.(Math.round((event.loaded / event.total) * 100))
      },
    },
  )
  return response.data
}

export async function bindStudentQq(payload: {
  studentId: string
  studentName: string
  qqNumber: string
}): Promise<void> {
  const response = await apiClient.post<ApiResponse<null>>('/homework/bind-qq', payload)
  unwrapApiResponse(response.data, '绑定 QQ 失败')
}

export async function getGradingResult(submissionId: number): Promise<GradingResult> {
  const response = await apiClient.get<ApiResponse<GradingResult>>(
    `/homework/result/${submissionId}`,
  )
  return unwrapApiResponse(response.data, '查询批改状态失败')
}

export async function getSubmissionContent(submissionId: number): Promise<SubmissionContent> {
  const response = await apiClient.get<ApiResponse<SubmissionContent>>(
    `/submissions/${submissionId}/content`,
  )
  return unwrapApiResponse(response.data, '加载提交内容失败')
}
