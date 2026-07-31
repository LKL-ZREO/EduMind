import type { GeneratedPreview, GeneratedQuestion, KbRole, TreeNode } from './types'

const typeLabels: Record<string, string> = {
  CHOICE: '选择题',
  OPEN: '简答题',
  EXERCISE: '随堂练习',
}

const difficultyLabels: Record<string, string> = {
  easy: '简单',
  medium: '中等',
  hard: '困难',
}

export function isPptFile(label: string) {
  return /\.(ppt|pptx)$/i.test(label)
}

export function typeLabel(type: string) {
  return typeLabels[type] ?? type
}

export const typeLabelZh = typeLabel

export function diffLabel(difficulty: string) {
  return difficultyLabels[difficulty] ?? difficulty
}

export function memberRoleLabel(role: KbRole) {
  return ({ owner: '创建者', admin: '管理员', member: '成员' } satisfies Record<KbRole, string>)[
    role
  ]
}

export function isCorrectOption(question: GeneratedQuestion, optionKey: string) {
  return question.correctKey?.trim().toUpperCase() === optionKey.trim().toUpperCase()
}

export function draftStatusLabel(item: GeneratedQuestion | GeneratedPreview) {
  if ('type' in item && item.type) return item.archived ? '已归档' : '已保存到题库'
  const status = item.status?.toUpperCase()
  const labels: Record<string, string> = {
    ACTIVE: '课堂作答中',
    CLOSED: '已发送',
    PUBLISHED: '已发布',
    COMPLETED: '已完成',
  }
  if (status) return labels[status] ?? status
  return item.published ? '已保存' : '预习材料'
}

export function draftStatusType(
  item: GeneratedQuestion | GeneratedPreview,
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if ('type' in item && item.type) return item.archived ? 'info' : 'success'
  const status = item.status?.toUpperCase()
  if (status === 'ACTIVE') return 'warning'
  if (status === 'PUBLISHED' || status === 'COMPLETED') return 'success'
  if (status === 'CLOSED') return 'primary'
  return 'info'
}

export function formatTimeLimit(seconds?: number) {
  if (!seconds || seconds <= 0) return '未设置'
  if (seconds < 60) return `${seconds} 秒`
  if (seconds % 60 === 0) return `${seconds / 60} 分钟`
  return `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`
}

export function countItems(node: TreeNode, type: 'folder' | 'file') {
  return node.children?.filter((child) => child.type === type).length ?? 0
}
