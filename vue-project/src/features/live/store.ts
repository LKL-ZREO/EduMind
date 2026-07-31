import { Client } from '@stomp/stompjs'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as liveApi from './api/live'
import type {
  InteractionHistoryItem,
  InteractionPush,
  InteractionTiming,
  LiveSessionInfo,
  LiveStats,
  OnlineStudents,
  QuestionBoardItem,
  QAMessage,
  QAQuestion,
  ReactionMsg,
  StudentProfile,
} from './api/live'

interface HandEntry {
  studentId: string
  studentName: string
  raisedAt: number
}

interface HandQueue {
  waiting: HandEntry[]
  called: HandEntry[]
}

interface TeacherStatus {
  online: boolean
  sessionEnded?: boolean
}

export const useLiveSessionStore = defineStore('liveSession', () => {
  const role = ref<'teacher' | 'student' | null>(null)
  const sessionInfo = ref<LiveSessionInfo | null>(null)
  const currentInteraction = ref<InteractionPush | null>(null)
  const lastStats = ref<LiveStats | null>(null)
  const interactionHistory = ref<InteractionHistoryItem[]>([])
  const questionBoard = ref<QuestionBoardItem[]>([])
  const topQuestions = ref<QAQuestion[]>([])
  const connectionStatus = ref<'disconnected' | 'connecting' | 'connected'>('disconnected')
  const studentCount = ref(0)
  const studentList = ref<OnlineStudents['students']>([])
  const absentCount = ref(0)
  const absentStudents = ref<OnlineStudents['absentStudents']>([])
  const reactions = ref<ReactionMsg[]>([])
  const studentProfile = ref<StudentProfile | null>(null)
  const handQueue = ref<HandQueue>({ waiting: [], called: [] })
  const handRaised = ref(false)
  const teacherOnline = ref(true)
  const sessionEnded = ref(false)
  let stomp: Client | null = null

  const isTeacher = computed(() => role.value === 'teacher')
  const isStudent = computed(() => role.value === 'student')
  const sessionId = computed(() => sessionInfo.value?.sessionId ?? null)

  async function createSession(classId: number, title?: string) {
    const response = await liveApi.createSession({ classId, title })
    const data = response.data.data
    sessionInfo.value = {
      sessionId: data.sessionId,
      sessionCode: data.sessionCode,
      title: data.title,
      className: '',
      teacherName: '',
      token: '',
      studentId: '',
      studentName: '',
      hasActive: true,
      currentInteraction: null,
      startedAt: data.startedAt,
    }
    role.value = 'teacher'
    connect()
    return data
  }

  async function checkActiveSession(classId: number) {
    const response = await liveApi.getActiveSession(classId)
    return response.data.data
  }

  async function endLiveSession() {
    const id = sessionId.value
    try {
      if (id) await liveApi.endSession(id)
    } finally {
      disconnect()
      reset()
    }
  }

  function useStudentSession(data: LiveSessionInfo) {
    disconnect()
    reset()
    sessionInfo.value = data
    role.value = 'student'
    if (data.currentInteraction) currentInteraction.value = data.currentInteraction
    connect(data.token)
    return data
  }

  async function joinSession(code: string, studentId: string, studentName?: string) {
    const response = await liveApi.joinSession({
      code: code.toUpperCase(),
      studentId,
      studentName,
    })
    return useStudentSession(response.data.data)
  }

  async function quickJoinSession(code: string) {
    const response = await liveApi.quickJoinSession(code)
    return useStudentSession(response.data.data)
  }

  function submitResponse(answer: string) {
    const interactionId = currentInteraction.value?.interactionId
    if (!interactionId) return false
    const sent = send(`/app/session/${sessionId.value}/interaction/${interactionId}/respond`, {
      interactionId,
      answer,
      studentId: sessionInfo.value?.studentId,
      studentName: sessionInfo.value?.studentName,
    })
    if (!sent) return false
    const index = interactionHistory.value.findIndex((item) => item.interactionId === interactionId)
    const existing = interactionHistory.value[index]
    if (existing) interactionHistory.value[index] = { ...existing, myAnswer: answer }
    return true
  }

  function askQuestion(question: string) {
    return send(`/app/session/${sessionId.value}/qa/ask`, { question })
  }

  function closeInteraction(interactionId: number) {
    send(`/app/session/${sessionId.value}/interaction/${interactionId}/close`, {})
  }

  async function sendQuestion(questionId: number) {
    const id = sessionId.value
    if (!id) return
    const response = await liveApi.sendQuestion(id, questionId)
    mergeInteractionPush(response.data.data)
  }

  async function extendInteraction(interactionId: number, seconds: number) {
    const id = sessionId.value
    if (!id) return
    const response = await liveApi.extendInteraction(id, interactionId, seconds)
    mergeInteractionTiming(response.data.data)
  }

  function answerQuestion(qaId: number, answerText: string) {
    send(`/app/session/${sessionId.value}/qa/${qaId}/answer`, { answerText })
  }

  function sendReaction(emoji: string) {
    return send(`/app/session/${sessionId.value}/reaction`, {
      emoji,
      studentId: sessionInfo.value?.studentId,
      studentName: sessionInfo.value?.studentName,
      type: 'emoji',
    })
  }

  function raiseHand() {
    const sent = send(`/app/session/${sessionId.value}/hand/raise`, {})
    if (!sent) return false
    handRaised.value = true
    return true
  }

  function lowerHand() {
    const sent = send(`/app/session/${sessionId.value}/hand/lower`, {})
    if (!sent) return false
    handRaised.value = false
    return true
  }

  function callStudent(studentId?: string) {
    send(`/app/session/${sessionId.value}/hand/call`, studentId ? { studentId } : {})
  }

  function dismissHand(studentId: string) {
    send(`/app/session/${sessionId.value}/hand/dismiss`, { studentId })
  }

  async function fetchStudentProfile(studentId: string, classId: number) {
    const response = await liveApi.getStudentProfile(studentId, classId)
    studentProfile.value = response.data.data
  }

  function mergeStats(stats: LiveStats) {
    const index = interactionHistory.value.findIndex(
      (item) => item.interactionId === stats.interactionId,
    )
    const existing = interactionHistory.value[index]
    if (existing) {
      interactionHistory.value[index] = {
        ...existing,
        respondedCount: stats.respondedCount,
        correctRate: stats.correctRate,
        status: stats.status,
      }
    }

    const boardIndex = questionBoard.value.findIndex(
      (item) => item.interactionId === stats.interactionId,
    )
    const boardItem = questionBoard.value[boardIndex]
    if (boardItem) {
      questionBoard.value[boardIndex] = {
        ...boardItem,
        respondedCount: stats.respondedCount,
        totalStudents: stats.totalStudents,
        correctRate: stats.correctRate,
        distribution: stats.distribution,
        status: stats.status as QuestionBoardItem['status'],
      }
    }
  }

  function mergeInteractionPush(push: InteractionPush) {
    const index = interactionHistory.value.findIndex(
      (item) => item.interactionId === push.interactionId,
    )
    const existing = interactionHistory.value[index]
    const item: InteractionHistoryItem = {
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
      totalStudents: 0,
      respondedCount: 0,
      correctRate: null,
      myAnswer: existing?.myAnswer ?? null,
      myCorrect: existing?.myCorrect ?? null,
    }
    if (existing) {
      interactionHistory.value[index] = { ...existing, ...item }
    } else {
      interactionHistory.value.unshift(item)
    }

    const boardIndex = questionBoard.value.findIndex(
      (boardItem) =>
        boardItem.questionId === push.questionId || boardItem.interactionId === push.interactionId,
    )
    const existingBoardItem = questionBoard.value[boardIndex]
    if (existingBoardItem) {
      questionBoard.value[boardIndex] = {
        ...existingBoardItem,
        interactionId: push.interactionId,
        status: push.status,
        sendCount:
          existingBoardItem.interactionId === push.interactionId
            ? existingBoardItem.sendCount
            : existingBoardItem.sendCount + 1,
        deadlineEpochMs: push.deadlineEpochMs,
        activatedAt: existingBoardItem.activatedAt ?? push.serverTime,
        correctKey: push.correctKey ?? existingBoardItem.correctKey,
      }
    }
  }

  function mergeInteractionTiming(timing: InteractionTiming) {
    const boardIndex = questionBoard.value.findIndex(
      (item) => item.interactionId === timing.interactionId,
    )
    const boardItem = questionBoard.value[boardIndex]
    if (boardItem) {
      questionBoard.value[boardIndex] = {
        ...boardItem,
        deadlineEpochMs: timing.deadlineEpochMs,
      }
    }
    if (currentInteraction.value?.interactionId === timing.interactionId) {
      currentInteraction.value.deadlineEpochMs = timing.deadlineEpochMs
    }
  }

  function parseMessage<T>(body: string): T {
    return JSON.parse(body) as T
  }

  function updatePresence(data: OnlineStudents) {
    studentCount.value = data.count
    studentList.value = data.students
    absentCount.value = data.absentCount
    absentStudents.value = data.absentStudents
  }

  function connect(classroomToken?: string) {
    if (stomp?.connected) return
    connectionStatus.value = 'connecting'
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const connectHeaders: Record<string, string> = {
      'X-Session-Id': String(sessionInfo.value?.sessionId ?? ''),
    }
    if (classroomToken) connectHeaders.Authorization = `Bearer ${classroomToken}`

    stomp = new Client({
      brokerURL: `${wsProtocol}//${window.location.host}/ws/live`,
      connectHeaders,
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: subscribeToSession,
      onStompError: markDisconnected,
      onWebSocketClose: markDisconnected,
    })
    stomp.activate()
  }

  function subscribeToSession() {
    connectionStatus.value = 'connected'
    const id = sessionInfo.value?.sessionId
    const client = stomp
    if (!id || !client) return

    if (isTeacher.value) {
      client.subscribe(`/topic/session/${id}/stats`, (message) => {
        const stats = parseMessage<LiveStats>(message.body)
        lastStats.value = stats
        mergeStats(stats)
      })
      client.subscribe(`/topic/session/${id}/interaction`, (message) => {
        mergeInteractionPush(parseMessage<InteractionPush>(message.body))
      })
      client.subscribe(`/topic/session/${id}/interaction-timing`, (message) => {
        mergeInteractionTiming(parseMessage<InteractionTiming>(message.body))
      })
      client.subscribe(`/topic/session/${id}/qa`, (message) => {
        topQuestions.value = parseMessage<QAMessage>(message.body).topQuestions ?? []
      })
      client.subscribe(`/topic/session/${id}/students`, (message) => {
        updatePresence(parseMessage<OnlineStudents>(message.body))
      })
      client.subscribe(`/topic/session/${id}/reactions`, (message) => {
        reactions.value = [...reactions.value.slice(-19), parseMessage<ReactionMsg>(message.body)]
      })
      client.subscribe(`/topic/session/${id}/hand-queue`, (message) => {
        handQueue.value = parseMessage<HandQueue>(message.body)
      })
      void loadTeacherSessionState(id).catch(() => undefined)
      return
    }

    if (isStudent.value) {
      client.subscribe(`/topic/session/${id}/interaction`, (message) => {
        const push = parseMessage<InteractionPush>(message.body)
        currentInteraction.value = push
        mergeInteractionPush(push)
      })
      client.subscribe(`/topic/session/${id}/interaction-timing`, (message) => {
        mergeInteractionTiming(parseMessage<InteractionTiming>(message.body))
      })
      client.subscribe(`/topic/session/${id}/hand-queue`, (message) => {
        const queue = parseMessage<HandQueue>(message.body)
        handQueue.value = queue
        const studentId = sessionInfo.value?.studentId
        if (!studentId) return
        handRaised.value = [...queue.waiting, ...queue.called].some(
          (entry) => entry.studentId === studentId,
        )
      })
      client.subscribe(`/topic/session/${id}/teacher-status`, (message) => {
        const status = parseMessage<TeacherStatus>(message.body)
        teacherOnline.value = status.online
        if (status.sessionEnded) {
          sessionEnded.value = true
          disconnect()
        }
      })
      void loadStudentHistory(id).catch(() => undefined)
    }
  }

  async function loadTeacherSessionState(id: number) {
    const [historyResponse, boardResponse, presenceResponse] = await Promise.all([
      liveApi.getInteractionHistory(id),
      liveApi.getQuestionBoard(id),
      liveApi.getOnlineStudents(id),
    ])
    interactionHistory.value = historyResponse.data.data
    questionBoard.value = boardResponse.data.data
    updatePresence(presenceResponse.data.data)
  }

  async function refreshQuestionBoard() {
    const id = sessionId.value
    if (!id) return
    const response = await liveApi.getQuestionBoard(id)
    questionBoard.value = response.data.data
  }

  async function loadStudentHistory(id: number) {
    const response = await liveApi.getInteractionHistory(
      id,
      sessionInfo.value?.studentId,
      sessionInfo.value?.token,
    )
    interactionHistory.value = response.data.data
  }

  function send(destination: string, body: object) {
    if (!stomp?.connected) return false
    stomp.publish({ destination, body: JSON.stringify(body) })
    return true
  }

  function markDisconnected() {
    connectionStatus.value = 'disconnected'
  }

  function disconnect() {
    if (stomp) {
      void stomp.deactivate()
      stomp = null
    }
    markDisconnected()
  }

  function reset() {
    role.value = null
    sessionInfo.value = null
    currentInteraction.value = null
    lastStats.value = null
    interactionHistory.value = []
    questionBoard.value = []
    topQuestions.value = []
    studentCount.value = 0
    studentList.value = []
    absentCount.value = 0
    absentStudents.value = []
    reactions.value = []
    studentProfile.value = null
    handQueue.value = { waiting: [], called: [] }
    handRaised.value = false
    teacherOnline.value = true
    sessionEnded.value = false
  }

  return {
    role,
    sessionInfo,
    currentInteraction,
    lastStats,
    interactionHistory,
    questionBoard,
    topQuestions,
    connectionStatus,
    studentCount,
    studentList,
    absentCount,
    absentStudents,
    reactions,
    studentProfile,
    handQueue,
    handRaised,
    teacherOnline,
    sessionEnded,
    isTeacher,
    isStudent,
    sessionId,
    createSession,
    checkActiveSession,
    endLiveSession,
    joinSession,
    quickJoinSession,
    submitResponse,
    askQuestion,
    closeInteraction,
    sendQuestion,
    extendInteraction,
    answerQuestion,
    sendReaction,
    raiseHand,
    lowerHand,
    callStudent,
    dismissHand,
    fetchStudentProfile,
    refreshQuestionBoard,
    connect,
    disconnect,
    reset,
  }
})
