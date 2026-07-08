import request from './request'

/** 获取聊天历史 */
export async function getChatHistory() {
  const res = await request.get('/chat/history')
  return res.data
}

/** 清除聊天历史 */
export async function clearChatHistory() {
  const res = await request.post('/chat/clear')
  return res.data
}

/** 批量批改 */
export async function batchGrade() {
  const res = await request.post('/chat/grade')
  return res.data
}

/** 上传文件 */
export async function uploadFile(formData: FormData) {
  const res = await request.post('/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data
}
