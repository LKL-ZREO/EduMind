import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useChatStore } from '@/stores/chat'

/**
 * Chat Store 测试 — AI 响应状态管理。
 */
describe('useChatStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('AI responding state', () => {
    it('初始状态 → aiResponding=false, partialContent=""', () => {
      const store = useChatStore()
      expect(store.aiResponding).toBe(false)
      expect(store.partialContent).toBe('')
    })

    it('setResponding → 设置 AI 响应状态', () => {
      const store = useChatStore()

      store.setResponding(true)
      expect(store.aiResponding).toBe(true)

      store.setResponding(false)
      expect(store.aiResponding).toBe(false)
    })
  })

  describe('partial content', () => {
    it('setPartial → 追加流式内容', () => {
      const store = useChatStore()

      store.setPartial('Hello')
      expect(store.partialContent).toBe('Hello')

      store.setPartial('Hello World')
      expect(store.partialContent).toBe('Hello World')
    })

    it('clearPartial → 清空流式内容', () => {
      const store = useChatStore()

      store.setPartial('Hello World')
      store.clearPartial()

      expect(store.partialContent).toBe('')
    })
  })
})
