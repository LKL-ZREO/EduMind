import request from './request'

export async function submitHomework(formData: FormData) {
  const res = await request.post('/homework/submit', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res.data
}

export async function getHomeworkResult(submissionId: string) {
  const res = await request.get(`/homework/result/${submissionId}`)
  return res.data
}
