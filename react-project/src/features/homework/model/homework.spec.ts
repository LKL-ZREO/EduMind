import { describe, expect, it } from 'vitest'
import { buildDraftDescription, groupTasks, totalDraftScore } from './homework'
import type { HomeworkTask } from './types'

const baseTask: HomeworkTask = {
  id: 1,
  classId: 10,
  taskName: '函数作业',
  description: '<p>完成函数练习</p>',
  deadline: '2026-08-10T20:00:00',
  allowLate: true,
  latePenalty: 5,
  status: 'active',
  createdAt: '2026-08-03T00:00:00',
}

describe('homework model', () => {
  it('groups equivalent published tasks across classes without mutating the source', () => {
    const tasks = [baseTask, { ...baseTask, id: 2, classId: 11 }]
    const groups = groupTasks(tasks, [
      { id: 10, name: '高一一班' },
      { id: 11, name: '高一二班' },
    ])

    expect(groups).toHaveLength(1)
    expect(groups[0]?.classNames).toEqual(['高一一班', '高一二班'])
    expect(groups[0]?.tasks.map((task) => task.id)).toEqual([1, 2])
    expect(tasks[0]).not.toHaveProperty('classNames')
  })

  it('keeps different deadlines as separate published groups', () => {
    const groups = groupTasks(
      [baseTask, { ...baseTask, id: 2, deadline: '2026-08-11T20:00:00' }],
      [{ id: 10, name: '高一一班' }],
    )
    expect(groups).toHaveLength(2)
  })

  it('totals question scores and escapes titles in generated draft HTML', () => {
    const questions = [
      {
        title: '<img src=x onerror=alert(1)>',
        requirement: '<p>说明</p>',
        score: 40,
        uploadRequired: true,
      },
      { title: '第二题', requirement: '', score: 60, uploadRequired: false },
    ]

    expect(totalDraftScore(questions)).toBe(100)
    const html = buildDraftDescription(questions)
    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;')
    expect(html).toContain('<p>说明</p>')
  })
})
