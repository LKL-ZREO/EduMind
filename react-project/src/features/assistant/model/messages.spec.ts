import { describe, expect, it } from 'vitest'
import { applyStreamEvent, createUiMessage, finishAssistantMessage } from './messages'

describe('stream message reducer', () => {
  it('applies tokens and tool lifecycle without mutating the previous message', () => {
    const initial = [createUiMessage('assistant-1', 'assistant', '')]
    const token = applyStreamEvent(initial, 'assistant-1', {
      type: 'token',
      data: { content: '第一段' },
    }).messages
    const started = applyStreamEvent(token, 'assistant-1', {
      type: 'tool_started',
      data: { tool: 'rag', label: '检索知识库' },
    }).messages
    const completed = applyStreamEvent(started, 'assistant-1', {
      type: 'tool_completed',
      data: { tool: 'rag', success: true, elapsedMs: 28 },
    }).messages

    expect(initial[0]?.content).toBe('')
    expect(completed[0]).toMatchObject({
      content: '第一段',
      toolSteps: [{ tool: 'rag', label: '检索知识库', status: 'success', elapsedMs: 28 }],
    })
  })

  it('deduplicates citations and emits lesson-plan artifacts', () => {
    const initial = [createUiMessage('assistant-1', 'assistant', '教案内容')]
    const citationEvent = {
      type: 'citation',
      data: {
        documentId: 'doc-1',
        documentName: '函数讲义',
        sectionIndex: 2,
        excerpt: '函数单调性',
      },
    }
    const once = applyStreamEvent(initial, 'assistant-1', citationEvent).messages
    const twice = applyStreamEvent(once, 'assistant-1', citationEvent).messages
    const artifact = applyStreamEvent(twice, 'assistant-1', {
      type: 'artifact',
      data: { type: 'lesson_plan', title: '复习课方案' },
    }).artifact

    expect(twice[0]?.citations).toHaveLength(1)
    expect(artifact).toEqual({
      type: 'lesson_plan',
      title: '复习课方案',
      content: '教案内容',
    })
  })

  it('keeps a partial answer when generation is stopped', () => {
    const initial = [createUiMessage('assistant-1', 'assistant', '已生成部分')]
    const stopped = finishAssistantMessage(initial, 'assistant-1', 'stopped')

    expect(stopped[0]).toMatchObject({ content: '已生成部分', state: 'stopped' })
  })
})
