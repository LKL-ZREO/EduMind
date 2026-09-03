import { beforeEach, describe, expect, it } from 'vitest'
import type { LiveSessionInfo, QuestionBoardItem } from '@/features/live/model/types'
import { useLiveStore } from './liveStore'

const session: LiveSessionInfo = {
  sessionId: 7,
  sessionCode: 'ABC234',
  title: '函数复习',
  className: '高一一班',
  teacherName: '王老师',
  token: 'student-token',
  studentId: 'S01',
  studentName: '张三',
  currentInteraction: null,
}

const board: QuestionBoardItem[] = [
  {
    questionId: 11,
    interactionId: null,
    type: 'CHOICE',
    title: '函数定义域是什么？',
    description: null,
    options: null,
    correctKey: 'A',
    knowledgePoint: '函数',
    difficulty: 'easy',
    status: 'UNSENT',
    sortOrder: 1,
    timeLimit: 60,
    sendCount: 0,
    createdAt: '',
    activatedAt: null,
    deadlineEpochMs: null,
    totalStudents: 0,
    respondedCount: 0,
    correctRate: null,
    distribution: {},
  },
]

describe('live store', () => {
  beforeEach(() => useLiveStore.getState().reset())

  it('applies interaction and statistics events to the same classroom snapshot', () => {
    const store = useLiveStore.getState()
    store.startSession('teacher', { ...session, token: '', studentId: '', studentName: '' })
    store.hydrateTeacher({
      history: [],
      board,
      presence: {
        count: 1,
        students: [{ studentId: 'S01', studentName: '张三' }],
        absentCount: 0,
        absentStudents: [],
      },
    })
    store.applySocketEvent({
      type: 'interaction',
      payload: {
        interactionId: 101,
        questionId: 11,
        type: 'CHOICE',
        status: 'ACTIVE',
        title: '函数定义域是什么？',
        description: null,
        options: null,
        correctKey: 'A',
        timeLimit: 60,
        deadlineEpochMs: 100_000,
        serverTime: '2026-08-03T10:00:00',
      },
    })
    useLiveStore.getState().applySocketEvent({
      type: 'stats',
      payload: {
        interactionId: 101,
        status: 'ACTIVE',
        totalStudents: 1,
        respondedCount: 1,
        distribution: { A: { count: 1, percent: 100 } },
        correctRate: 100,
        unrespondedStudents: [],
      },
    })

    expect(useLiveStore.getState().questionBoard[0]).toMatchObject({
      interactionId: 101,
      respondedCount: 1,
      correctRate: 100,
    })
    expect(useLiveStore.getState().interactionHistory[0]).toMatchObject({
      interactionId: 101,
      respondedCount: 1,
    })
  })

  it('tracks the current student hand state from a shared hand queue', () => {
    useLiveStore.getState().startSession('student', session)
    useLiveStore.getState().applySocketEvent({
      type: 'handQueue',
      payload: { waiting: [{ studentId: 'S01', studentName: '张三', raisedAt: 1 }], called: [] },
    })
    expect(useLiveStore.getState().handRaised).toBe(true)

    useLiveStore
      .getState()
      .applySocketEvent({ type: 'teacherStatus', payload: { online: false, sessionEnded: true } })
    expect(useLiveStore.getState()).toMatchObject({ teacherOnline: false, sessionEnded: true })
  })
})
