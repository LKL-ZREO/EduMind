import type { ClassOption, DraftQuestion, HomeworkTask, TaskGroup, TeachingQuestion } from './types'

export function createBlankQuestion(): DraftQuestion {
  return {
    type: 'HOMEWORK',
    title: '',
    requirement: '',
    score: 20,
    uploadRequired: true,
  }
}

export function cloneQuestion(question: DraftQuestion | TeachingQuestion): DraftQuestion {
  return {
    id: question.id,
    type: question.type,
    title: question.title,
    requirement: question.requirement || '',
    options: question.options,
    correctKey: question.correctKey,
    explanation: question.explanation,
    knowledgePoint: question.knowledgePoint,
    difficulty: question.difficulty,
    timeLimit: question.timeLimit,
    score: question.score,
    uploadRequired: question.uploadRequired,
  }
}

export function groupTasks(tasks: HomeworkTask[], classes: ClassOption[]): TaskGroup[] {
  const classNames = new Map(classes.map((item) => [item.id, item.name]))
  const grouped = new Map<string, TaskGroup>()
  for (const task of tasks) {
    const key = [
      task.taskName,
      normalizeDescription(task.description),
      task.deadline || '',
      task.allowLate ? 'late' : 'strict',
      task.latePenalty ?? 0,
    ].join('::')
    const className = classNames.get(task.classId) || `班级 ${task.classId}`
    const existing = grouped.get(key)
    if (existing) {
      existing.tasks.push(task)
      if (!existing.classNames.includes(className)) existing.classNames.push(className)
      if (task.status === 'active') existing.status = 'active'
      continue
    }
    grouped.set(key, {
      key,
      taskName: task.taskName,
      description: task.description,
      deadline: task.deadline,
      allowLate: task.allowLate,
      latePenalty: task.latePenalty,
      status: task.status,
      createdAt: task.createdAt,
      tasks: [task],
      classNames: [className],
    })
  }
  return [...grouped.values()].sort((left, right) => {
    if (left.status !== right.status) return left.status === 'active' ? -1 : 1
    return new Date(right.createdAt || 0).getTime() - new Date(left.createdAt || 0).getTime()
  })
}

export function totalDraftScore(questions: DraftQuestion[]) {
  return questions.reduce((total, question) => total + Number(question.score || 0), 0)
}

export function questionTypeLabel(type?: string) {
  return (
    {
      CHOICE: '选择题',
      OPEN: '简答题',
      EXERCISE: '随堂练习',
      HOMEWORK: '作业题',
    }[type || 'HOMEWORK'] || '作业题'
  )
}

export function buildDraftDescription(questions: DraftQuestion[]) {
  return questions
    .map(
      (question, index) => `
        <section class="assignment-question">
          <h3>题目 ${index + 1}: ${escapeHtml(question.title || '未命名题目')}</h3>
          <div class="assignment-question-body">${question.requirement || ''}</div>
          <p><strong>分值:</strong>${Number(question.score || 0)}; <strong>提交方式:</strong>${question.uploadRequired ? '按题上传附件' : '在线作答'}</p>
        </section>`,
    )
    .join('')
}

export function formatDateTime(value?: string | null) {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function toDatetimeLocal(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function normalizeDescription(value?: string) {
  return (value || '').replace(/\s+/g, ' ').trim()
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
