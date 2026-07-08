import request from './request'

// ======================== 共享知识库 ========================

export async function getMyKbs() {
  const res = await request.get('/shared-kb/my')
  return res.data
}

export async function getJoinedKbs() {
  const res = await request.get('/shared-kb/joined')
  return res.data
}

export async function createKb(data: { name: string; description: string }) {
  const res = await request.post('/shared-kb/create', data)
  return res.data
}

export async function updateKb(id: number, data: { name: string; description: string }) {
  const res = await request.put(`/shared-kb/${id}`, data)
  return res.data
}

export async function deleteKb(id: number) {
  const res = await request.delete(`/shared-kb/${id}`)
  return res.data
}

export async function getKbMembers(kbId: number) {
  const res = await request.get(`/shared-kb/${kbId}/members`)
  return res.data
}

export async function removeKbMember(kbId: number, userId: number) {
  const res = await request.delete(`/shared-kb/${kbId}/members/${userId}`)
  return res.data
}

export async function generateInviteToken(kbId: number) {
  const res = await request.post(`/shared-kb/${kbId}/invite`, {})
  return res.data
}

export async function joinKbByToken(token: string) {
  const res = await request.post(`/shared-kb/join?token=${encodeURIComponent(token)}`)
  return res.data
}

// ======================== 文档与目录树 ========================

export async function getDirectoryTree(kbId?: string) {
  const res = await request.get(`/documents/directory/tree${kbId ? `?kbId=${kbId}` : ''}`)
  return res.data
}

export async function renameDirectory(id: number, label: string) {
  const res = await request.put(`/documents/directory/${id}/rename`, { label })
  return res.data
}

export async function deleteDirectory(id: number) {
  const res = await request.delete(`/documents/directory/${id}`)
  return res.data
}

export async function createFolder(parentId: number | null, name: string, kbId?: number | null) {
  const body: Record<string, unknown> = { name, parentId }
  if (kbId) body.kbId = kbId
  const res = await request.post('/documents/directory/folder', body)
  return res.data
}

export async function moveDirectory(id: number, targetParentId: number | null) {
  const res = await request.put(`/documents/directory/${id}/move`, { targetParentId })
  return res.data
}

export async function uploadDocument(formData: FormData) {
  const res = await request.post('/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}

export async function getDocumentContent(docId: number) {
  const res = await request.get(`/documents/${docId}/content`)
  return res.data
}
