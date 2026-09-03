export type AuthenticatedUser = {
  id: number
  username: string
  email: string | null
}

export type LoginPayload = {
  username: string
  password: string
}

export type RegisterPayload = {
  username: string
  email: string
  password: string
}

export type LoginResult = AuthenticatedUser & {
  sessionId?: string
}
