import { create } from 'zustand'
import {
  mergeBoardPush,
  mergeHistoryPush,
  mergeInteractionTiming,
  mergeLiveStats,
} from '@/features/live/model/live'
import type {
  ConnectionStatus,
  HandQueue,
  InteractionHistoryItem,
  InteractionPush,
  LiveRole,
  LiveSessionInfo,
  LiveSocketEvent,
  LiveStats,
  OnlineStudents,
  QAQuestion,
  QuestionBoardItem,
  ReactionMessage,
} from '@/features/live/model/types'

type LiveData = {
  role: LiveRole | null
  sessionInfo: LiveSessionInfo | null
  connectionStatus: ConnectionStatus
  currentInteraction: InteractionPush | null
  lastStats: LiveStats | null
  interactionHistory: InteractionHistoryItem[]
  questionBoard: QuestionBoardItem[]
  topQuestions: QAQuestion[]
  studentCount: number
  studentList: OnlineStudents['students']
  absentCount: number
  absentStudents: OnlineStudents['absentStudents']
  reactions: ReactionMessage[]
  handQueue: HandQueue
  handRaised: boolean
  teacherOnline: boolean
  sessionEnded: boolean
}

type LiveActions = {
  startSession: (role: LiveRole, sessionInfo: LiveSessionInfo) => void
  setConnectionStatus: (status: ConnectionStatus) => void
  hydrateTeacher: (data: {
    history: InteractionHistoryItem[]
    board: QuestionBoardItem[]
    presence: OnlineStudents
    stats?: LiveStats | null
  }) => void
  hydrateStudent: (history: InteractionHistoryItem[]) => void
  setQuestionBoard: (board: QuestionBoardItem[]) => void
  applySocketEvent: (event: LiveSocketEvent) => void
  rememberAnswer: (interactionId: number, answer: string) => void
  clearReactions: () => void
  reset: () => void
}

function initialData(): LiveData {
  return {
    role: null,
    sessionInfo: null,
    connectionStatus: 'disconnected',
    currentInteraction: null,
    lastStats: null,
    interactionHistory: [],
    questionBoard: [],
    topQuestions: [],
    studentCount: 0,
    studentList: [],
    absentCount: 0,
    absentStudents: [],
    reactions: [],
    handQueue: { waiting: [], called: [] },
    handRaised: false,
    teacherOnline: true,
    sessionEnded: false,
  }
}

export const useLiveStore = create<LiveData & LiveActions>((set) => ({
  ...initialData(),
  startSession: (role, sessionInfo) =>
    set({
      ...initialData(),
      role,
      sessionInfo,
      currentInteraction: sessionInfo.currentInteraction,
    }),
  setConnectionStatus: (connectionStatus) => set({ connectionStatus }),
  hydrateTeacher: ({ history, board, presence, stats }) =>
    set({
      interactionHistory: history,
      questionBoard: board,
      studentCount: presence.count,
      studentList: presence.students,
      absentCount: presence.absentCount,
      absentStudents: presence.absentStudents,
      lastStats: stats || null,
    }),
  hydrateStudent: (interactionHistory) => set({ interactionHistory }),
  setQuestionBoard: (questionBoard) => set({ questionBoard }),
  applySocketEvent: (event) =>
    set((state) => {
      switch (event.type) {
        case 'interaction':
          return {
            currentInteraction: state.role === 'student' ? event.payload : state.currentInteraction,
            interactionHistory: mergeHistoryPush(state.interactionHistory, event.payload),
            questionBoard: mergeBoardPush(state.questionBoard, event.payload),
          }
        case 'stats': {
          const merged = mergeLiveStats(
            state.interactionHistory,
            state.questionBoard,
            event.payload,
          )
          return {
            lastStats: event.payload,
            interactionHistory: merged.history,
            questionBoard: merged.board,
          }
        }
        case 'timing': {
          const merged = mergeInteractionTiming(
            state.questionBoard,
            state.currentInteraction,
            event.payload,
          )
          return { questionBoard: merged.board, currentInteraction: merged.current }
        }
        case 'qa':
          return { topQuestions: event.payload.topQuestions || [] }
        case 'presence':
          return {
            studentCount: event.payload.count,
            studentList: event.payload.students,
            absentCount: event.payload.absentCount,
            absentStudents: event.payload.absentStudents,
          }
        case 'reaction':
          return { reactions: [...state.reactions.slice(-19), event.payload] }
        case 'handQueue': {
          const studentId = state.sessionInfo?.studentId
          return {
            handQueue: event.payload,
            handRaised: Boolean(
              studentId &&
              [...event.payload.waiting, ...event.payload.called].some(
                (item) => item.studentId === studentId,
              ),
            ),
          }
        }
        case 'teacherStatus':
          return {
            teacherOnline: event.payload.online,
            sessionEnded: state.sessionEnded || Boolean(event.payload.sessionEnded),
          }
      }
    }),
  rememberAnswer: (interactionId, answer) =>
    set((state) => ({
      interactionHistory: state.interactionHistory.map((item) =>
        item.interactionId === interactionId ? { ...item, myAnswer: answer } : item,
      ),
    })),
  clearReactions: () => set({ reactions: [] }),
  reset: () => set(initialData()),
}))
