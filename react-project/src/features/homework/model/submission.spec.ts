import { describe, expect, it } from 'vitest'
import {
  countdownLabel,
  parseHomeworkFileName,
  renderAssignmentHtml,
  validateHomeworkFileName,
} from './submission'

describe('student submission model', () => {
  it('parses the four filename identity fields and rejects malformed names', () => {
    expect(parseHomeworkFileName('2026001_张三_高一一班_函数作业.pdf')).toEqual({
      studentId: '2026001',
      studentName: '张三',
      className: '高一一班',
      assignmentName: '函数作业',
    })
    expect(parseHomeworkFileName('函数作业.pdf')).toBeNull()
  })

  it('reports class and task mismatches before uploading', () => {
    const warnings = validateHomeworkFileName(
      '2026001_张三_高一二班_几何作业.pdf',
      { id: 10, name: '高一一班' },
      {
        id: 20,
        taskName: '函数作业',
        description: '',
        deadline: '2026-08-10T20:00:00',
        allowLate: true,
        latePenalty: 0,
      },
    )
    expect(warnings).toHaveLength(2)
    expect(warnings[0]).toContain('班级不匹配')
    expect(warnings[1]).toContain('作业不匹配')
  })

  it('formats active and expired deadlines from a supplied clock', () => {
    const now = new Date('2026-08-03T10:00:00').getTime()
    expect(countdownLabel('2026-08-03T11:01:02', now)).toBe('距离截止还有 1 小时 1 分 2 秒')
    expect(countdownLabel('2026-08-03T09:00:00', now)).toBe('已截止')
  })

  it('renders math while removing event handlers from assignment HTML', () => {
    const html = renderAssignmentHtml(
      '<p><span class="math-inline" data-latex="x^2"></span><img src="x" onerror="alert(1)"></p>',
    )
    expect(html).toContain('katex')
    expect(html).not.toContain('onerror')
  })
})
