import { describe, expect, it } from 'vitest'
import {
  combineConfusionEvents,
  combineConfusionStats,
  filterStudents,
  otherKnowledgeRate,
  passRate,
  weakKnowledgePoints,
} from './dashboard'
import type { StudentOverview } from './types'

const students: StudentOverview[] = [
  {
    name: '张三',
    studentId: 'S01',
    avgScore: 55,
    homeworkCount: 3,
    errorCount: 8,
    trend: -3,
    needAttention: true,
  },
  {
    name: '李四',
    studentId: 'S02',
    avgScore: 88,
    homeworkCount: 4,
    errorCount: 2,
    trend: 5,
    needAttention: false,
  },
  {
    name: '王五',
    studentId: 'S03',
    avgScore: 0,
    homeworkCount: 0,
    errorCount: 0,
    trend: 0,
    needAttention: true,
  },
]

describe('teaching dashboard model', () => {
  it('derives pass rate and the default attention list without mutating server data', () => {
    expect(passRate(students)).toBe(50)
    expect(filterStudents(students, '', 'score', false).map((item) => item.name)).toEqual(['张三'])
    expect(students.map((item) => item.name)).toEqual(['张三', '李四', '王五'])
  })

  it('filters, sorts and derives weak knowledge points', () => {
    expect(filterStudents(students, 'S02', 'homework', false).map((item) => item.name)).toEqual([
      '李四',
    ])
    const knowledge = [
      { name: '函数', mastery: 72, errorCount: 4, criticalCount: 0 },
      { name: '集合', mastery: 52, errorCount: 6, criticalCount: 2 },
      { name: '其他', mastery: 30, errorCount: 10, criticalCount: 1 },
    ]
    expect(weakKnowledgePoints(knowledge).map((item) => item.name)).toEqual(['集合'])
    expect(otherKnowledgeRate(knowledge)).toBe(50)
  })

  it('merges QQ and classroom confusion signals by knowledge point and time', () => {
    expect(
      combineConfusionStats(
        [{ name: '函数', count: 2 }],
        [
          { name: '函数', count: 3 },
          { name: '集合', count: 1 },
        ],
      ),
    ).toEqual([
      { name: '函数', count: 5 },
      { name: '集合', count: 1 },
    ])
    const events = combineConfusionEvents(
      [{ id: 1, knowledgePoint: '函数', createdAt: '2026-08-01T10:00:00' }],
      [{ id: 2, knowledgePoint: '集合', createdAt: '2026-08-01T11:00:00' }],
    )
    expect(events.map((item) => item.id)).toEqual([2, 1])
    expect(events.map((item) => item.source)).toEqual(['课堂', 'QQ'])
  })
})
