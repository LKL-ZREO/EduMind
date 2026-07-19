import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { Client } from '@stomp/stompjs'
import type { InteractionPush, LiveStats, QAQuestion, LiveSessionInfo, InteractionHistoryItem, ReactionMsg, StudentProfile } from '../api/live'
import * as liveApi from '../api/live'

export const useLiveSessionStore = defineStore('liveSession', () => {
  const role = ref<'teacher' | 'student' | null>(null)
  const sessionInfo = ref<LiveSessionInfo | null>(null)
  const currentInteraction = ref<InteractionPush | null>(null)
  const lastStats = ref<LiveStats | null>(null)
  const interactionHistory = ref<InteractionHistoryItem[]>([])
  const topQuestions = ref<QAQuestion[]>([])
  const connectionStatus = ref<'disconnected' | 'connecting' | 'connected'>('disconnected')
  const studentCount = ref(0)
  const studentList = ref<{ studentId: string; studentName: string }[]>([])
  const absentCount = ref(0)
  const absentStudents = ref<{ studentId: string; studentName: string }[]>([])
  const reactions = ref<ReactionMsg[]>([])
  const studentProfile = ref<StudentProfile | null>(null)
  // 举手队列
  interface HandEntry { studentId: string; studentName: string; raisedAt: number }
  const handQueue = ref<{ waiting: HandEntry[]; called: HandEntry[] }>({ waiting: [], called: [] })
  const handRaised = ref(false)  // 当前学生是否已举手
  const teacherOnline = ref(true)  // 教师是否在线
  let stomp: Client | null = null

  const isTeacher = computed(() => role.value === 'teacher')
  const isStudent = computed(() => role.value === 'student')
  const sessionId = computed(() => sessionInfo.value?.sessionId ?? null)

  async function createSession(classId: number, title?: string) {
    const res = await liveApi.createSession({ classId, title })
    const data = res.data.data
    sessionInfo.value = { sessionId: data.sessionId, sessionCode: data.sessionCode, title: data.title, className: '', teacherName: '', token: '', studentId: '', studentName: '', hasActive: true, currentInteraction: null, startedAt: data.startedAt }
    role.value = 'teacher'
    connect(localStorage.getItem('token') || '')
    return data
  }

  async function checkActiveSession(classId: number) { const r = await liveApi.getActiveSession(classId); return r.data.data }
  async function endLiveSession() { if (sessionId.value) { try { await liveApi.endSession(sessionId.value) } catch {} } disconnect(); reset() }

  async function joinSession(code: string, studentId: string, studentName: string) {
    const res = await liveApi.joinSession({ code: code.toUpperCase(), studentId, studentName })
    sessionInfo.value = res.data.data; role.value = 'student'
    if (res.data.data.currentInteraction) currentInteraction.value = res.data.data.currentInteraction
    connect(res.data.data.token)
    return res.data.data
  }

  function submitResponse(answer: string) {
    const iid = currentInteraction.value?.interactionId
    if (!iid) return
    send('/app/session/'+sessionId.value+'/interaction/'+iid+'/respond', { interactionId: iid, answer, studentId: sessionInfo.value?.studentId, studentName: sessionInfo.value?.studentName })
    // 本地更新历史中的作答记录
    const idx = interactionHistory.value.findIndex(h => h.interactionId === iid)
    if (idx >= 0) interactionHistory.value[idx] = { ...interactionHistory.value[idx], myAnswer: answer }
  }
  function askQuestion(question: string) { send('/app/session/'+sessionId.value+'/qa/ask', { question }) }
  function createInteraction(dto: any) { send('/app/session/'+sessionId.value+'/interaction/create', dto) }
  function closeInteraction(iid: number) { send('/app/session/'+sessionId.value+'/interaction/'+iid+'/close', {}) }
  function answerQuestion(qaId: number, answerText: string) { send('/app/session/'+sessionId.value+'/qa/'+qaId+'/answer', { answerText }) }
  function sendReaction(emoji: string) { send('/app/session/'+sessionId.value+'/reaction', { emoji, studentId: sessionInfo.value?.studentId, studentName: sessionInfo.value?.studentName, type: 'emoji' }) }

  // === 举手队列 ===
  function raiseHand() { send('/app/session/'+sessionId.value+'/hand/raise', {}); handRaised.value = true }
  function lowerHand() { send('/app/session/'+sessionId.value+'/hand/lower', {}); handRaised.value = false }
  function callStudent(studentId?: string) { send('/app/session/'+sessionId.value+'/hand/call', studentId ? { studentId } : {}) }
  function dismissHand(studentId: string) { send('/app/session/'+sessionId.value+'/hand/dismiss', { studentId }) }

  async function fetchStudentProfile(studentId: string, classId: number) { const r = await liveApi.getStudentProfile(studentId, classId); studentProfile.value = r.data.data }

  /** 将 stats 合并到历史记录中 */
  function mergeStats(stats: LiveStats) {
    const idx = interactionHistory.value.findIndex(h => h.interactionId === stats.interactionId)
    if (idx >= 0) {
      interactionHistory.value[idx] = {
        ...interactionHistory.value[idx],
        respondedCount: stats.respondedCount,
        correctRate: stats.correctRate,
        status: stats.status,
      }
    }
  }

  /** 将互动推送追加/更新到历史 */
  function mergeInteractionPush(push: InteractionPush) {
    const idx = interactionHistory.value.findIndex(h => h.interactionId === push.interactionId)
    const item: InteractionHistoryItem = {
      interactionId: push.interactionId, type: push.type, title: push.title,
      description: push.description || null, options: push.options || null,
      correctKey: push.correctKey || null, timeLimit: push.timeLimit || null,
      status: push.status, createdAt: push.serverTime,
      totalStudents: 0, respondedCount: 0, correctRate: null,
      myAnswer: null, myCorrect: null,
    }
    if (idx >= 0) {
      interactionHistory.value[idx] = { ...interactionHistory.value[idx], ...item }
    } else {
      interactionHistory.value.unshift(item)
    }
  }

  function connect(token: string) {
    if (stomp?.connected) return
    connectionStatus.value = 'connecting'
    const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    stomp = new Client({
      brokerURL: `${wsProtocol}//${window.location.host}/ws/live`,
      connectHeaders: { Authorization: `Bearer ${token}`, 'X-Session-Id': String(sessionInfo.value?.sessionId ?? '') },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        connectionStatus.value = 'connected'
        const sid = sessionInfo.value?.sessionId
        if (!sid || !stomp) return
        if (isTeacher.value) {
          // stats 推送 → 合并到历史 + 更新 lastStats
          stomp.subscribe(`/topic/session/${sid}/stats`, msg => {
            try {
              const s: LiveStats = JSON.parse(msg.body)
              lastStats.value = s
              mergeStats(s)
            } catch {}
          })
          // 互动推送（关闭通知等）→ 更新历史
          stomp.subscribe(`/topic/session/${sid}/interaction`, msg => {
            try { mergeInteractionPush(JSON.parse(msg.body)) } catch {}
          })
          stomp.subscribe(`/topic/session/${sid}/qa`, msg => { try { topQuestions.value = JSON.parse(msg.body).topQuestions || [] } catch {} })
          stomp.subscribe(`/topic/session/${sid}/students`, msg => { try { const d = JSON.parse(msg.body); studentCount.value = d.count || 0; studentList.value = d.students || []; absentCount.value = d.absentCount || 0; absentStudents.value = d.absentStudents || [] } catch {} })
          stomp.subscribe(`/topic/session/${sid}/reactions`, msg => { try { reactions.value = [...reactions.value.slice(-19), JSON.parse(msg.body)] } catch {} })
          // 举手队列
          stomp.subscribe(`/topic/session/${sid}/hand-queue`, msg => { try { handQueue.value = JSON.parse(msg.body) } catch {} })
          // 拉取历史 + 在线学生
          liveApi.getInteractionHistory(sid).then(r => {
            if (r.data.data?.length) interactionHistory.value = r.data.data
          }).catch(() => {})
          liveApi.getOnlineStudents(sid).then(r => {
            const d = r.data.data
            if (d) { studentCount.value = d.count; studentList.value = d.students; absentCount.value = d.absentCount; absentStudents.value = d.absentStudents }
          }).catch(() => {})
        } else if (isStudent.value) {
          // 互动推送 → currentInteraction + 追加历史
          stomp.subscribe(`/topic/session/${sid}/interaction`, msg => {
            try {
              const push: InteractionPush = JSON.parse(msg.body)
              currentInteraction.value = push
              mergeInteractionPush(push)
            } catch {}
          })
          // 举手队列（学生端同步，用于判断自己被点名）
          stomp.subscribe(`/topic/session/${sid}/hand-queue`, msg => {
            try {
              const q = JSON.parse(msg.body)
              handQueue.value = q
              // 检查自己是否仍在队列中（可能被移除或点名）
              const myId = sessionInfo.value?.studentId
              if (myId) {
                const inQueue = (q.waiting || []).some((e: HandEntry) => e.studentId === myId)
                    || (q.called || []).some((e: HandEntry) => e.studentId === myId)
                if (!inQueue) handRaised.value = false
              }
            } catch {}
          })
          // 教师在线状态
          stomp.subscribe(`/topic/session/${sid}/teacher-status`, msg => { try { teacherOnline.value = !!JSON.parse(msg.body).online } catch {} })
          // 拉取历史（含自己作答）
          const sid2 = sessionInfo.value?.studentId
          liveApi.getInteractionHistory(sid, sid2).then(r => {
            if (r.data.data?.length) interactionHistory.value = r.data.data
          }).catch(() => {})
        }
      },
      onStompError: () => { connectionStatus.value = 'disconnected' },
      onWebSocketClose: () => { connectionStatus.value = 'disconnected' },
    })
    stomp.activate()
  }

  function send(dest: string, body: any) {
    if (!stomp?.connected) {
      console.warn('[LiveStore] STOMP 未连接，消息发送失败:', dest, body)
      return
    }
    stomp.publish({ destination: dest, body: JSON.stringify(body) })
    console.log('[LiveStore] STOMP 消息已发送:', dest)
  }

  function disconnect() { if (stomp) { stomp.deactivate(); stomp = null } connectionStatus.value = 'disconnected' }
  function reset() { role.value = null; sessionInfo.value = null; currentInteraction.value = null; lastStats.value = null; interactionHistory.value = []; topQuestions.value = []; studentCount.value = 0; studentList.value = []; absentCount.value = 0; absentStudents.value = []; reactions.value = []; studentProfile.value = null; handQueue.value = { waiting: [], called: [] }; handRaised.value = false; teacherOnline.value = true }

  return { role, sessionInfo, currentInteraction, lastStats, interactionHistory, topQuestions, connectionStatus, studentCount, studentList, absentCount, absentStudents, reactions, studentProfile, handQueue, handRaised, teacherOnline, isTeacher, isStudent, sessionId, createSession, checkActiveSession, endLiveSession, joinSession, submitResponse, askQuestion, createInteraction, closeInteraction, answerQuestion, sendReaction, raiseHand, lowerHand, callStudent, dismissHand, fetchStudentProfile, connect, disconnect, reset }
})
