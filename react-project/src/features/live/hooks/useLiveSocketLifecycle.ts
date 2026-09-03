import { useEffect } from 'react'
import { liveSocket } from '@/features/live/realtime/liveSocket'
import { useLiveStore } from '@/features/live/store/liveStore'

export function useLiveSocketLifecycle() {
  const role = useLiveStore((state) => state.role)
  const sessionInfo = useLiveStore((state) => state.sessionInfo)

  useEffect(() => {
    if (!role || !sessionInfo) return
    liveSocket.connect({
      role,
      sessionId: sessionInfo.sessionId,
      token: sessionInfo.token || undefined,
      onStatus: (status) => useLiveStore.getState().setConnectionStatus(status),
      onEvent: (event) => {
        useLiveStore.getState().applySocketEvent(event)
        if (event.type === 'teacherStatus' && event.payload.sessionEnded) liveSocket.disconnect()
      },
    })
    return () => liveSocket.disconnect()
  }, [role, sessionInfo])
}

export function publishLiveMessage(destination: string, body: object) {
  return liveSocket.publish(destination, body)
}
