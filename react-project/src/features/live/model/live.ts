import type {
  InteractionHistoryItem,
  InteractionPush,
  InteractionTiming,
  LiveSessionCreated,
  LiveSessionInfo,
  LiveStats,
  QuestionBoardItem,
} from './types'

const INVALID_LIVE_CODE_CHARACTERS = /[^ABCDEFGHJKMNPQRSTUVWXYZ23456789]/g

export function normalizeLiveCode(value: string) {
  return value
    .toUpperCase()
    .replace(/\s/g, '')
    .replace(INVALID_LIVE_CODE_CHARACTERS, '')
    .slice(0, 6)
}

export function teacherSessionInfo(created: LiveSessionCreated): LiveSessionInfo {
  return {
    sessionId: created.sessionId,
    sessionCode: created.sessionCode,
    title: created.title,
    className: '',
    teacherName: '',
    token: '',
    studentId: '',
    studentName: '',
    currentInteraction: null,
    startedAt: created.startedAt,
  }
}

export function activeTeacherSessionInfo(active: {
  sessionId: number
  sessionCode: string
  title: string
  startedAt?: string
}): LiveSessionInfo {
  return teacherSessionInfo({
    ...active,
    classId: 0,
    status: 'ACTIVE',
    startedAt: active.startedAt || '',
  })
}

export function mergeHistoryPush(history: InteractionHistoryItem[], push: InteractionPush) {
  const existing = history.find((item) => item.interactionId === push.interactionId)
  const next: InteractionHistoryItem = {
    interactionId: push.interactionId,
    questionId: push.questionId,
    type: push.type,
    title: push.title,
    description: push.description,
    options: push.options,
    correctKey: push.correctKey,
    timeLimit: push.timeLimit,
    status: push.status,
    createdAt: push.serverTime,
    totalStudents: existing?.totalStudents || 0,
    respondedCount: existing?.respondedCount || 0,
    correctRate: existing?.correctRate ?? null,
    myAnswer: existing?.myAnswer ?? null,
    myCorrect: existing?.myCorrect ?? null,
  }
  return existing
    ? history.map((item) =>
        item.interactionId === push.interactionId ? { ...item, ...next } : item,
      )
    : [next, ...history]
}

export function mergeBoardPush(board: QuestionBoardItem[], push: InteractionPush) {
  return board.map((item) => {
    if (item.questionId !== push.questionId && item.interactionId !== push.interactionId)
      return item
    return {
      ...item,
      interactionId: push.interactionId,
      status: push.status,
      sendCount: item.interactionId === push.interactionId ? item.sendCount : item.sendCount + 1,
      deadlineEpochMs: push.deadlineEpochMs,
      activatedAt: item.activatedAt || push.serverTime,
      correctKey: push.correctKey ?? item.correctKey,
    }
  })
}

export function mergeLiveStats(
  history: InteractionHistoryItem[],
  board: QuestionBoardItem[],
  stats: LiveStats,
) {
  return {
    history: history.map((item) =>
      item.interactionId === stats.interactionId
        ? {
            ...item,
            respondedCount: stats.respondedCount,
            totalStudents: stats.totalStudents,
            correctRate: stats.correctRate,
            status: stats.status,
          }
        : item,
    ),
    board: board.map((item) =>
      item.interactionId === stats.interactionId
        ? {
            ...item,
            respondedCount: stats.respondedCount,
            totalStudents: stats.totalStudents,
            correctRate: stats.correctRate,
            distribution: stats.distribution,
            status: stats.status as QuestionBoardItem['status'],
          }
        : item,
    ),
  }
}

export function mergeInteractionTiming(
  board: QuestionBoardItem[],
  current: InteractionPush | null,
  timing: InteractionTiming,
) {
  return {
    board: board.map((item) =>
      item.interactionId === timing.interactionId
        ? { ...item, deadlineEpochMs: timing.deadlineEpochMs }
        : item,
    ),
    current:
      current?.interactionId === timing.interactionId
        ? { ...current, deadlineEpochMs: timing.deadlineEpochMs }
        : current,
  }
}

export function responsePercent(item: Pick<QuestionBoardItem, 'totalStudents' | 'respondedCount'>) {
  return item.totalStudents
    ? Math.min(100, Math.round((item.respondedCount * 100) / item.totalStudents))
    : 0
}

export function remainingSeconds(deadlineEpochMs: number | null, now = Date.now()) {
  return deadlineEpochMs ? Math.max(0, Math.ceil((deadlineEpochMs - now) / 1_000)) : null
}

export function formatLiveDuration(seconds: number | null) {
  if (seconds === null) return '不限时'
  if (seconds < 60) return `${seconds}秒`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest ? `${minutes}分${rest}秒` : `${minutes}分钟`
}
