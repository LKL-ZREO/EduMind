import type { LessonDraft, LessonStage, PreLessonOverview, Timeline, TimelineItem } from './types'

export function tomorrowString(now = new Date()) {
  const date = new Date(now)
  date.setDate(date.getDate() + 1)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function stage(
  id: string,
  phase: string,
  title: string,
  minutes: number,
  teacherAction: string,
  studentAction: string,
  resource: string,
): LessonStage {
  return { id, phase, title, minutes, teacherAction, studentAction, resource }
}

export function suggestedStages(mainPoint: string, secondaryPoint?: string): LessonStage[] {
  return [
    stage(
      'suggested-intro',
      '导入',
      '从典型错误进入本节课',
      5,
      `展示一条${mainPoint}的高频错误，追问错误原因。`,
      '独立判断后与同桌交流，给出修改意见。',
      '数据中心典型错题 1 条',
    ),
    stage(
      'suggested-review',
      '复习',
      `${mainPoint}关键概念回顾`,
      8,
      '用对比例子梳理前置概念，明确容易混淆的边界。',
      '完成概念对照表，并用自己的话解释差异。',
      '知识点对照卡',
    ),
    stage(
      'suggested-explain',
      '讲解',
      `${secondaryPoint || mainPoint}重点突破`,
      15,
      '分步骤讲解典型问题，显式展示思考过程与检查方法。',
      '跟随推导并在关键步骤作出预测。',
      '课件、板书与示例代码',
    ),
    stage(
      'suggested-interact',
      '互动',
      '随堂检测与即时纠偏',
      12,
      '发布两道梯度题，根据实时正确率决定是否补讲。',
      '独立作答，提交后标记仍不理解的知识点。',
      '课堂题组 2—3 题',
    ),
    stage(
      'suggested-summary',
      '总结',
      '方法归纳与课后任务',
      5,
      '总结本节判断方法，说明分层作业要求。',
      '用一句话总结最容易出错的步骤。',
      '小结卡与课后作业草稿',
    ),
  ]
}

export function createLessonDraft(
  classId: number,
  overview: PreLessonOverview,
  timeline: Timeline | undefined,
  now = new Date(),
): LessonDraft {
  const weakPoints = overview.weakPoints.slice(0, 2)
  const planned = timeline?.weeks
    .flatMap((week) => week.items)
    .find((item) => item.type === 'plan' && item.status === 'PLANNED')
  const mainPoint = weakPoints[0]?.name || '本单元核心知识'
  const secondaryPoint = weakPoints[1]?.name
  return {
    classId,
    topic: planned?.title || `${mainPoint}巩固与应用`,
    plannedDate: planned?.date || tomorrowString(now),
    duration: 45,
    knowledgePoints: weakPoints.length ? weakPoints.map((item) => item.name) : [mainPoint],
    objectives: [
      `能够解释${mainPoint}的核心概念与常见误区`,
      `能够独立完成一道${secondaryPoint || mainPoint}的典型应用题`,
    ],
    evidenceIds: [
      ...weakPoints.map((_, index) => `knowledge-${index}`),
      ...(overview.warningCount > 0 ? ['warning-students'] : []),
    ],
    stages: suggestedStages(mainPoint, secondaryPoint),
    differentiation: overview.tieredGroups.map((tier) => ({
      label: tier.label,
      range: tier.range,
      count: tier.count,
      strategy: tier.suggestion,
    })),
    materialReady: { preview: false, questions: false, homework: false },
    notes: '',
    status: 'DRAFT',
  }
}

export function lessonDraftStorageKey(classId: number) {
  return `edumind:lesson-preparation:${classId}`
}

export function restoreLessonDraft(
  classId: number,
  overview: PreLessonOverview,
  timeline?: Timeline,
) {
  const fallback = createLessonDraft(classId, overview, timeline)
  const stored = localStorage.getItem(lessonDraftStorageKey(classId))
  if (!stored) return fallback
  try {
    const parsed = JSON.parse(stored) as Partial<LessonDraft>
    return {
      ...fallback,
      ...parsed,
      classId,
      materialReady: { ...fallback.materialReady, ...parsed.materialReady },
    }
  } catch {
    return fallback
  }
}

export function totalLessonMinutes(draft: LessonDraft) {
  return draft.stages.reduce((sum, item) => sum + Math.max(0, Number(item.minutes) || 0), 0)
}

export function lessonReadiness(draft: LessonDraft, evidenceCount: number) {
  const totalMinutes = totalLessonMinutes(draft)
  const items = [
    {
      key: 'context',
      label: '课程信息',
      done: Boolean(draft.topic.trim() && draft.plannedDate && draft.duration > 0),
    },
    {
      key: 'objectives',
      label: '教学目标',
      done: draft.objectives.some((item) => item.trim()),
    },
    {
      key: 'evidence',
      label: '学情依据',
      done: draft.evidenceIds.length > 0 || evidenceCount === 0,
    },
    {
      key: 'flow',
      label: '教学流程',
      done:
        draft.stages.length > 0 &&
        draft.stages.every(
          (item) => item.title.trim() && item.teacherAction.trim() && item.studentAction.trim(),
        ),
    },
    { key: 'duration', label: '时间校验', done: totalMinutes === draft.duration },
    {
      key: 'materials',
      label: '教学材料',
      done: Object.values(draft.materialReady).some(Boolean),
    },
  ]
  const complete = items.filter((item) => item.done).length
  return { items, complete, percent: Math.round((complete / items.length) * 100), totalMinutes }
}

export function evidenceSignals(overview: PreLessonOverview) {
  const signals = overview.weakPoints.slice(0, 3).map((point, index) => ({
    id: `knowledge-${index}`,
    category: index === 0 ? '首要薄弱点' : '知识点信号',
    title: point.name,
    value: `${point.mastery}%`,
    detail: `掌握度 ${point.mastery}%，历史错误 ${point.errorCount} 次。`,
  }))
  if (overview.warningCount > 0) {
    signals.push({
      id: 'warning-students',
      category: '学生分层',
      title: `${overview.warningCount} 名学生需要额外支架`,
      value: `${overview.warningCount} 人`,
      detail: `班级共 ${overview.totalStudents} 人，建议准备基础任务和提示卡。`,
    })
  }
  if (overview.liveSessionCount > 0) {
    signals.push({
      id: 'live-feedback',
      category: '课堂反馈',
      title: '上次课堂互动表现',
      value: `${overview.liveAvgCorrectRate}%`,
      detail: `累计 ${overview.liveSessionCount} 次课堂，平均参与率 ${overview.participationRate}%。`,
    })
  }
  return signals
}

export function recentTimeline(timeline?: Timeline): TimelineItem[] {
  return (timeline?.weeks || []).flatMap((week) => week.items).slice(0, 5)
}

export function exportLessonPlan(draft: LessonDraft, className: string) {
  const objectives = draft.objectives
    .filter((item) => item.trim())
    .map((item, index) => `${index + 1}. ${item}`)
    .join('\n')
  const stages = draft.stages
    .map(
      (item, index) =>
        `${index + 1}. 【${item.phase}｜${item.minutes}分钟】${item.title}\n` +
        `   教师活动：${item.teacherAction || '待补充'}\n` +
        `   学生活动：${item.studentAction || '待补充'}\n` +
        `   材料与检查点：${item.resource || '待补充'}`,
    )
    .join('\n\n')
  return [
    `《${draft.topic || '未命名课程'}》备课方案`,
    `班级：${className}`,
    `日期：${draft.plannedDate || '待定'}｜课时：${draft.duration}分钟`,
    `知识点：${draft.knowledgePoints.join('、') || '待补充'}`,
    '',
    '一、教学目标',
    objectives || '待补充',
    '',
    '二、教学流程',
    stages || '待补充',
    '',
    '三、备课备注',
    draft.notes || '无',
  ].join('\n')
}
