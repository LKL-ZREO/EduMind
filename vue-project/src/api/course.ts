import request from './request'

export interface Course {
  id: number
  name: string
  systemPrompt: string
  knowledgeScope: string
  teacherId: number
  createdAt: string
  updatedAt: string
}

export interface PresetTemplate {
  key: string
  name: string
  prompt: string
}

export async function getPresets(): Promise<Record<string, PresetTemplate>> {
  const res = await request.get('/courses/presets')
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取预设失败')
}

export async function createCourse(data: {
  name: string
  presetKey?: string
  systemPrompt?: string
  knowledgeScope?: string
}): Promise<Course> {
  const res = await request.post('/courses', data)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '创建失败')
}

export async function updateCourse(id: number, data: {
  name?: string
  systemPrompt?: string
  knowledgeScope?: string
}): Promise<void> {
  const res = await request.put(`/courses/${id}`, data)
  if (res.data.code === 200) return
  throw new Error(res.data.message || '保存失败')
}

export async function deleteCourse(id: number): Promise<void> {
  const res = await request.delete(`/courses/${id}`)
  if (res.data.code === 200) return
  throw new Error(res.data.message || '删除失败')
}
