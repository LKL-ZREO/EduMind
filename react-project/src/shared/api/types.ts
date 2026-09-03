export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  requestId?: string
  timestamp?: number
}

export interface ApiProblem {
  code?: number
  message?: string
  detail?: string
  requestId?: string
}
