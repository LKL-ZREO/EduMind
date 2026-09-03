import type { LoaderFunctionArgs } from 'react-router'
import { redirect } from 'react-router'
import { currentUserQueryOptions } from '@/features/auth/api/authQueries'
import { queryClient } from '@/shared/query/queryClient'

const DEFAULT_TEACHER_PATH = '/teacher/chat'

export async function requireAuthLoader({ request }: LoaderFunctionArgs) {
  const user = await queryClient.ensureQueryData(currentUserQueryOptions())
  if (user) return user

  const requestedUrl = new URL(request.url)
  const requestedPath = `${requestedUrl.pathname}${requestedUrl.search}${requestedUrl.hash}`
  return redirect(`/login?redirect=${encodeURIComponent(requestedPath)}`)
}

export async function guestOnlyLoader() {
  const user = await queryClient.ensureQueryData(currentUserQueryOptions())
  if (user) return redirect(DEFAULT_TEACHER_PATH)
  return null
}

export function redirectToTeacherHome() {
  return redirect(DEFAULT_TEACHER_PATH)
}

export function redirectToPublicHome() {
  return redirect('/')
}
