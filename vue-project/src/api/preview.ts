import request from './request'

export interface QuestionOption {
  key: string
  text: string
}

export interface QuestionItem {
  question: string
  options: QuestionOption[] | null
  correctKey: string
  explanation: string
}

export interface PreviewTaskDTO {
  id: number
  classId: number
  title: string
  knowledgePoint: string
  guideText: string
  questions: QuestionItem[]
  discussionQuestion: string
  status: string
  createdAt: string
}

/** 教师：AI 生成预习任务 */
export async function createPreviewTask(data: { classId: number; knowledgePoint: string; topic?: string }) {
  const res = await request.post('/preview/create', data)
  return res.data
}

/** 教师：获取班级预习任务列表 */
export async function listPreviewTasks(classId: number) {
  const res = await request.get('/preview/list', { params: { classId } })
  return res.data
}

/** 公开：获取单个预习任务详情 */
export async function getPreviewTask(taskId: number) {
  const res = await request.get(`/preview/${taskId}`)
  return res.data
}

/** 教师：关闭预习任务 */
export async function closePreviewTask(taskId: number) {
  const res = await request.post(`/preview/${taskId}/close`)
  return res.data
}
