import katex from 'katex'
import { sanitizeHtml, sanitizeRenderedMathHtml } from '@/shared/utils/safeHtml'
import type { ClassOption, PublicTask } from './types'

export type ParsedFileName = {
  studentId: string
  studentName: string
  className: string
  assignmentName: string
}

export function parseHomeworkFileName(name: string): ParsedFileName | null {
  const match = /^(.+)_(.+)_(.+)_(.+)\.\w+$/.exec(name)
  if (!match) return null
  const [, studentId, studentName, className, assignmentName] = match
  if (!studentId || !studentName || !className || !assignmentName) return null
  return {
    studentId: studentId.trim(),
    studentName: studentName.trim(),
    className: className.trim(),
    assignmentName: assignmentName.trim(),
  }
}

export function validateHomeworkFileName(
  name: string,
  selectedClass: ClassOption | null,
  selectedTask: PublicTask | null,
) {
  const parsed = parseHomeworkFileName(name)
  if (!parsed) {
    return [
      '文件名格式不正确，请使用「学号_姓名_班级_作业名.扩展名」格式，例如：202103001_张三_计科2101_第三次作业.pdf',
    ]
  }
  const warnings: string[] = []
  if (selectedClass && parsed.className !== selectedClass.name) {
    warnings.push(
      `班级不匹配：文件名写的是「${parsed.className}」，你选的是「${selectedClass.name}」`,
    )
  }
  if (
    selectedTask &&
    !parsed.assignmentName.includes(selectedTask.taskName) &&
    !selectedTask.taskName.includes(parsed.assignmentName)
  ) {
    warnings.push(
      `作业不匹配：文件名写的是「${parsed.assignmentName}」，你选的是「${selectedTask.taskName}」`,
    )
  }
  return warnings
}

export function countdownLabel(deadline: string | undefined, now = Date.now()) {
  if (!deadline) return ''
  const difference = new Date(deadline).getTime() - now
  if (Number.isNaN(difference)) return ''
  if (difference <= 0) return '已截止'
  const days = Math.floor(difference / 86_400_000)
  const hours = Math.floor((difference % 86_400_000) / 3_600_000)
  const minutes = Math.floor((difference % 3_600_000) / 60_000)
  const seconds = Math.floor((difference % 60_000) / 1_000)
  if (days > 0) return `距离截止还有 ${days} 天 ${hours} 小时 ${minutes} 分`
  if (hours > 0) return `距离截止还有 ${hours} 小时 ${minutes} 分 ${seconds} 秒`
  return `距离截止还有 ${minutes} 分 ${seconds} 秒`
}

export function renderAssignmentHtml(input?: string | null) {
  const html = sanitizeHtml(input)
  if (!html) return ''
  const root = document.createElement('div')
  root.innerHTML = html
  root.querySelectorAll<HTMLElement>('.math-inline[data-latex]').forEach((element) => {
    const latex = element.dataset.latex
    if (!latex) return
    element.innerHTML = katex.renderToString(latex, { throwOnError: false, displayMode: false })
  })
  return sanitizeRenderedMathHtml(root.innerHTML)
}
