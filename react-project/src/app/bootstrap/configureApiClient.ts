import { setUnauthorizedHandler } from '@/shared/api/client'
import { queryClient } from '@/shared/query/queryClient'
import { clearLegacyCredentials } from '@/features/auth/api/authApi'
import { authKeys } from '@/features/auth/api/authQueries'

let configured = false

export function configureApiClient() {
  clearLegacyCredentials()
  if (configured) return
  configured = true

  setUnauthorizedHandler(() => {
    queryClient.clear()
    queryClient.setQueryData(authKeys.currentUser(), null)

    const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
    if (window.location.pathname === '/login' || window.location.pathname === '/register') return

    const loginUrl = `/login?redirect=${encodeURIComponent(currentPath)}`
    window.location.assign(loginUrl)
  })
}
