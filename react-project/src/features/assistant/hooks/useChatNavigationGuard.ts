import { useCallback, useEffect } from 'react'
import { useBeforeUnload, useBlocker } from 'react-router'

const LEAVE_MESSAGE = 'AI 正在回复中，切换页面将中断回复。确定离开吗？'

export function useChatNavigationGuard(responding: boolean, stopGeneration: () => void) {
  const blocker = useBlocker(responding)

  useBeforeUnload(
    useCallback(
      (event) => {
        if (!responding) return
        event.preventDefault()
        event.returnValue = ''
      },
      [responding],
    ),
  )

  useEffect(() => {
    if (blocker.state !== 'blocked') return
    if (window.confirm(LEAVE_MESSAGE)) {
      stopGeneration()
      blocker.proceed()
    } else {
      blocker.reset()
    }
  }, [blocker, stopGeneration])
}
