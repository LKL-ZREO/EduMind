import apiClient from '@/shared/api/client'
import type { ApiResponse } from '@/shared/api/types'
import { unwrapApiResponse } from '@/shared/api/unwrap'
import { buildTree } from '@/features/knowledge/model/tree'
import type {
  ClassroomOption,
  DraftDetail,
  FlatNode,
  FolderPayload,
  GeneratedPreview,
  GeneratedQuestion,
  GenerateMaterialsPayload,
  GenResult,
  KbMember,
  KnowledgeSpaces,
  SavePreviewPayload,
  SharedKb,
  TeachingMaterials,
  TreeNode,
  UploadDocumentsPayload,
} from '@/features/knowledge/model/types'

type RawKbMember = Omit<KbMember, 'userId'> & { userId?: number; user_id?: number }

export async function getKnowledgeSpaces(): Promise<KnowledgeSpaces> {
  const [owned, joined] = await Promise.all([
    apiClient.get<SharedKb[]>('/shared-kb/my'),
    apiClient.get<SharedKb[]>('/shared-kb/joined'),
  ])
  return { owned: owned.data || [], joined: joined.data || [] }
}

export async function getDirectoryTree(kbId: number | null): Promise<TreeNode[]> {
  const response = await apiClient.get<FlatNode[]>('/documents/directory/tree', {
    params: kbId === null ? undefined : { kbId },
  })
  return buildTree(response.data || [])
}

export async function createFolder(payload: FolderPayload): Promise<void> {
  await apiClient.post('/documents/directory/folder', payload)
}

export async function renameNode(nodeId: number, label: string): Promise<void> {
  await apiClient.put(`/documents/directory/${nodeId}/rename`, { label })
}

export async function deleteNode(nodeId: number): Promise<void> {
  await apiClient.delete(`/documents/directory/${nodeId}`)
}

export async function moveNode(nodeId: number, targetParentId: number | null): Promise<void> {
  await apiClient.put(`/documents/directory/${nodeId}/move`, { targetParentId })
}

export async function uploadDocuments({
  files,
  parentNodeId,
  kbId,
  onProgress,
}: UploadDocumentsPayload): Promise<number> {
  let completed = 0
  for (const file of files) {
    const formData = new FormData()
    formData.append('file', file)
    if (parentNodeId !== undefined) formData.append('parentNodeId', String(parentNodeId))
    if (kbId !== null) formData.append('kbId', String(kbId))
    await apiClient.post('/documents/upload', formData, {
      timeout: 120_000,
      onUploadProgress: (event) => {
        const current = event.total ? event.loaded / event.total : 0
        onProgress?.(Math.round(((completed + current) / files.length) * 100), file.name)
      },
    })
    completed += 1
    onProgress?.(Math.round((completed / files.length) * 100), file.name)
  }
  return completed
}

export async function getDocumentContent(docId: string): Promise<string> {
  const response = await apiClient.get<string>(`/documents/${encodeURIComponent(docId)}/content`, {
    responseType: 'text',
  })
  return typeof response.data === 'string' ? response.data : ''
}

export async function getDocumentMaterials(docId: string): Promise<TeachingMaterials> {
  const response = await apiClient.get<TeachingMaterials>(
    `/documents/${encodeURIComponent(docId)}/materials`,
  )
  return response.data || { previews: [], questions: [] }
}

export async function createKnowledgeBase(payload: {
  name: string
  description: string
}): Promise<void> {
  await apiClient.post('/shared-kb/create', payload)
}

export async function updateKnowledgeBase(
  kbId: number,
  payload: { name: string; description: string },
): Promise<void> {
  await apiClient.put(`/shared-kb/${kbId}`, payload)
}

export async function deleteKnowledgeBase(kbId: number): Promise<void> {
  await apiClient.delete(`/shared-kb/${kbId}`)
}

export async function joinKnowledgeBase(token: string): Promise<void> {
  await apiClient.post('/shared-kb/join', undefined, { params: { token } })
}

export async function generateInvite(kbId: number): Promise<string> {
  const response = await apiClient.post<{ token: string }>(`/shared-kb/${kbId}/invite`, {})
  return response.data.token
}

export async function getKnowledgeBaseMembers(kbId: number): Promise<KbMember[]> {
  const response = await apiClient.get<RawKbMember[]>(`/shared-kb/${kbId}/members`)
  return (response.data || [])
    .map((member) => ({
      userId: member.userId ?? member.user_id ?? 0,
      username: member.username,
      role: member.role,
    }))
    .filter((member) => member.userId > 0)
}

export async function removeKnowledgeBaseMember(kbId: number, userId: number): Promise<void> {
  await apiClient.delete(`/shared-kb/${kbId}/members/${userId}`)
}

export async function getKnowledgeClasses(): Promise<ClassroomOption[]> {
  const response = await apiClient.get<ApiResponse<ClassroomOption[]>>('/dashboard/classes')
  return unwrapApiResponse(response.data, '加载班级失败') || []
}

export async function generateMaterials(payload: GenerateMaterialsPayload): Promise<GenResult> {
  const response = await apiClient.post<GenResult>('/documents/generate-materials', payload, {
    timeout: 180_000,
  })
  return response.data
}

export async function saveGeneratedPreview(payload: SavePreviewPayload): Promise<void> {
  await apiClient.post('/documents/generate-materials/save-preview', payload)
}

export async function saveGeneratedQuestion(
  question: GeneratedQuestion,
  docId: string,
): Promise<void> {
  const response = await apiClient.post<ApiResponse<GeneratedQuestion>>('/questions', {
    ...question,
    sourceDocId: docId,
    aiGenerated: true,
    score: 10,
    uploadRequired: false,
  })
  unwrapApiResponse(response.data, '保存试题失败')
}

export async function getMaterialDetail(
  type: 'preview' | 'quiz',
  id: number,
): Promise<DraftDetail> {
  if (type === 'preview') {
    const response = await apiClient.get<ApiResponse<GeneratedPreview>>(`/preview/${id}`)
    return { type, data: unwrapApiResponse(response.data, '加载预习材料失败') }
  }
  const response = await apiClient.get<ApiResponse<GeneratedQuestion>>(`/questions/${id}`)
  return { type, data: unwrapApiResponse(response.data, '加载试题失败') }
}

export async function deleteMaterial(type: 'preview' | 'quiz', id: number): Promise<void> {
  if (type === 'preview') {
    await apiClient.delete(`/documents/materials/previews/${id}`)
    return
  }
  const response = await apiClient.delete<ApiResponse<null>>(`/questions/${id}`)
  unwrapApiResponse(response.data, '归档题目失败')
}
