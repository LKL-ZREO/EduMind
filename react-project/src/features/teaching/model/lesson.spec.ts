import { beforeEach, describe, expect, it } from 'vitest'
import {
  createLessonDraft,
  exportLessonPlan,
  lessonDraftStorageKey,
  lessonReadiness,
  restoreLessonDraft,
  tomorrowString,
  totalLessonMinutes,
} from './lesson'
import type { PreLessonOverview, Timeline } from './types'

const overview: PreLessonOverview = {
  classId: 10,
  className: '高一一班',
  avgScore: 76,
  totalStudents: 30,
  warningCount: 3,
  weakPoints: [
    { name: '函数单调性', errorCount: 12, mastery: 52, severity: 'HIGH' },
    { name: '定义域', errorCount: 8, mastery: 63, severity: 'MEDIUM' },
  ],
  liveSessionCount: 2,
  liveAvgCorrectRate: 68,
  participationRate: 90,
  aiSuggestion: '先复习定义域，再处理单调性。',
  tieredGroups: [{ label: '基础组', range: '0-59', count: 3, suggestion: '提供步骤卡' }],
}

const timeline: Timeline = {
  weeks: [
    {
      weekNumber: 1,
      label: '本周',
      items: [
        {
          type: 'plan',
          typeLabel: '计划',
          icon: '',
          id: 1,
          title: '函数复习',
          date: '2026-08-06',
          time: '09:00',
          status: 'PLANNED',
          detail: null,
        },
      ],
    },
  ],
}

describe('pre-lesson model', () => {
  beforeEach(() => localStorage.clear())

  it('creates a stable lesson draft from server evidence', () => {
    const draft = createLessonDraft(10, overview, timeline, new Date(2026, 7, 3, 12))
    expect(draft.topic).toBe('函数复习')
    expect(draft.plannedDate).toBe('2026-08-06')
    expect(draft.knowledgePoints).toEqual(['函数单调性', '定义域'])
    expect(totalLessonMinutes(draft)).toBe(45)
    expect(lessonReadiness(draft, 3).totalMinutes).toBe(draft.duration)
  })

  it('restores a local draft while keeping the current class identity', () => {
    localStorage.setItem(
      lessonDraftStorageKey(10),
      JSON.stringify({ classId: 99, topic: '本地修改主题', materialReady: { preview: true } }),
    )
    const restored = restoreLessonDraft(10, overview, timeline)
    expect(restored.classId).toBe(10)
    expect(restored.topic).toBe('本地修改主题')
    expect(restored.materialReady).toEqual({ preview: true, questions: false, homework: false })
  })

  it('exports readable text and calculates tomorrow without UTC date drift', () => {
    expect(tomorrowString(new Date(2026, 11, 31, 23))).toBe('2027-01-01')
    const text = exportLessonPlan(createLessonDraft(10, overview, timeline), '高一一班')
    expect(text).toContain('高一一班')
    expect(text).toContain('教学目标')
    expect(text).toContain('教学流程')
  })
})
