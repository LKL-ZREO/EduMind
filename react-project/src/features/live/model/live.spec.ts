import { describe, expect, it } from 'vitest'
import {
  formatLiveDuration,
  mergeBoardPush,
  mergeHistoryPush,
  normalizeLiveCode,
  remainingSeconds,
  responsePercent,
} from './live'
import type { InteractionPush, QuestionBoardItem } from './types'

const boardItem: QuestionBoardItem = {
  questionId: 11,
  interactionId: null,
  type: 'CHOICE',
  title: '函数定义域是什么？',
  description: null,
  options: [{ key: 'A', text: '自变量的取值范围' }],
  correctKey: 'A',
  knowledgePoint: '函数',
  difficulty: 'easy',
  status: 'UNSENT',
  sortOrder: 1,
  timeLimit: 60,
  sendCount: 0,
  createdAt: '2026-08-03T10:00:00',
  activatedAt: null,
  deadlineEpochMs: null,
  totalStudents: 0,
  respondedCount: 0,
  correctRate: null,
  distribution: {},
}

const push: InteractionPush = {
  interactionId: 101,
  questionId: 11,
  type: 'CHOICE',
  status: 'ACTIVE',
  title: '函数定义域是什么？',
  description: null,
  options: [{ key: 'A', text: '自变量的取值范围' }],
  correctKey: 'A',
  timeLimit: 60,
  deadlineEpochMs: 100_000,
  serverTime: '2026-08-03T10:01:00',
}

describe('live classroom model', () => {
  it('normalizes classroom codes and removes ambiguous characters', () => {
    expect(normalizeLiveCode(' ab i-l0 234xyz ')).toBe('AB234X')
    expect(normalizeLiveCode('abc234567')).toBe('ABC234')
  })

  it('merges an interaction push without mutating board or history snapshots', () => {
    const board = [boardItem]
    const nextBoard = mergeBoardPush(board, push)
    const history = mergeHistoryPush([], push)

    expect(nextBoard[0]).toMatchObject({ interactionId: 101, status: 'ACTIVE', sendCount: 1 })
    expect(board[0]).toMatchObject({ interactionId: null, status: 'UNSENT', sendCount: 0 })
    expect(history[0]).toMatchObject({ interactionId: 101, respondedCount: 0, myAnswer: null })
  })

  it('derives countdown, response percentage, and readable durations', () => {
    expect(remainingSeconds(105_100, 100_000)).toBe(6)
    expect(remainingSeconds(null, 100_000)).toBeNull()
    expect(responsePercent({ totalStudents: 30, respondedCount: 12 })).toBe(40)
    expect(formatLiveDuration(30)).toBe('30秒')
    expect(formatLiveDuration(90)).toBe('1分30秒')
    expect(formatLiveDuration(null)).toBe('不限时')
  })
})
