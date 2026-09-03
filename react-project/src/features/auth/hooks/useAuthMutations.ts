import { useMutation, useQueryClient } from '@tanstack/react-query'
import { login, logout, register } from '@/features/auth/api/authApi'
import { authKeys } from '@/features/auth/api/authQueries'

export function useLoginMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: login,
    onSuccess: (user) => {
      queryClient.setQueryData(authKeys.currentUser(), user)
    },
  })
}

export function useRegisterMutation() {
  return useMutation({ mutationFn: register })
}

export function useLogoutMutation() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: logout,
    onSettled: () => {
      queryClient.clear()
      queryClient.setQueryData(authKeys.currentUser(), null)
    },
  })
}
