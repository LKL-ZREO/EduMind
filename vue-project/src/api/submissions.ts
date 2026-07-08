import request from './request'

export async function getSubmissionContent(submissionId: string) {
  const res = await request.get(`/submissions/${submissionId}/content`)
  return res.data
}
