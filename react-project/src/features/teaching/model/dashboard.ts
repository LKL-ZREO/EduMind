import type { ConfusionEvent, ConfusionStat, KnowledgeMastery, StudentOverview } from './types'

export function passRate(students: StudentOverview[]) {
  const scored = students.filter((student) => student.homeworkCount > 0)
  if (!scored.length) return 0
  const passed = scored.filter((student) => student.avgScore >= 60).length
  return Math.round((passed * 1000) / scored.length) / 10
}

export function weakKnowledgePoints(items: KnowledgeMastery[]) {
  return items
    .filter((item) => item.name !== '其他' && item.mastery < 70)
    .sort((left, right) => left.mastery - right.mastery)
}

export function otherKnowledgeRate(items: KnowledgeMastery[]) {
  const total = items.reduce((sum, item) => sum + Number(item.errorCount || 0), 0)
  const other = items.find((item) => item.name === '其他')
  return total && other ? Math.round((other.errorCount * 1000) / total) / 10 : 0
}

export function filterStudents(
  students: StudentOverview[],
  search: string,
  sortBy: 'score' | 'homework',
  showAll: boolean,
) {
  const keyword = search.trim().toLocaleLowerCase('zh-CN')
  const result = students.filter(
    (student) =>
      !keyword ||
      student.name.toLocaleLowerCase('zh-CN').includes(keyword) ||
      String(student.studentId || '')
        .toLocaleLowerCase('zh-CN')
        .includes(keyword),
  )
  result.sort((left, right) => {
    if (sortBy === 'homework') return right.homeworkCount - left.homeworkCount
    if (!left.homeworkCount && right.homeworkCount) return 1
    if (left.homeworkCount && !right.homeworkCount) return -1
    return left.avgScore - right.avgScore
  })
  return showAll || keyword
    ? result
    : result.filter((student) => student.homeworkCount > 0 && student.avgScore < 60)
}

export function combineConfusionStats(qq: ConfusionStat[], live: ConfusionStat[]) {
  const totals = new Map<string, number>()
  for (const item of [...qq, ...live]) {
    totals.set(item.name, (totals.get(item.name) || 0) + Number(item.count || 0))
  }
  return [...totals].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count)
}

export function combineConfusionEvents(qq: ConfusionEvent[], live: ConfusionEvent[]) {
  return [
    ...qq.map((event) => ({ ...event, source: 'QQ' as const })),
    ...live.map((event) => ({ ...event, source: '课堂' as const })),
  ].sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
}

export function studentStatus(student: StudentOverview) {
  if (!student.homeworkCount) return { label: '暂无成绩', color: 'default' }
  if (student.avgScore < 60) return { label: '重点关注', color: 'error' }
  if (student.avgScore < 70) return { label: '需要巩固', color: 'warning' }
  return { label: '状态正常', color: 'success' }
}
