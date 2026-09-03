import { create } from 'zustand'

type ChatRunState = {
  responding: boolean
  setResponding: (responding: boolean) => void
}

export const useChatRunStore = create<ChatRunState>((set) => ({
  responding: false,
  setResponding: (responding) => set({ responding }),
}))
