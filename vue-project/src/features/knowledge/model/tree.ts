import type { FlatNode, TreeNode } from './types'

export function findNodePath(nodes: TreeNode[], id: number, parents: TreeNode[] = []): TreeNode[] {
  for (const node of nodes) {
    const path = [...parents, node]
    if (node.id === id) return path
    const childPath = findNodePath(node.children ?? [], id, path)
    if (childPath.length) return childPath
  }
  return []
}

export function countTreeItems(nodes: TreeNode[], type: 'folder' | 'file'): number {
  return nodes.reduce(
    (total, node) =>
      total + (node.type === type ? 1 : 0) + countTreeItems(node.children ?? [], type),
    0,
  )
}

export function buildTree(flat: FlatNode[]): TreeNode[] {
  const map = new Map<number, TreeNode>()
  for (const node of flat) {
    map.set(node.id, {
      id: node.id,
      label: node.label,
      type: node.nodeType,
      docId: node.docId ?? undefined,
      children: [],
      kbId: node.kbId,
      createdAt: node.createdAt,
      updatedAt: node.updatedAt,
    })
  }

  const roots: TreeNode[] = []
  for (const item of flat) {
    const node = map.get(item.id)!
    if (item.parentId == null) {
      roots.push(node)
    } else {
      map.get(item.parentId)?.children?.push(node)
    }
  }
  return roots
}

export function fileType(name: string) {
  const extension = name.split('.').pop()?.toUpperCase()
  return extension && extension !== name.toUpperCase() ? `${extension} 文档` : '文档'
}

export function resourceStatus(node: TreeNode) {
  if (node.type === 'folder') return '目录'
  if (node.loadState === 'ready') return '可预览'
  if (node.loadState === 'loading') return '读取中'
  if (node.loadState === 'processing') return '处理中'
  if (node.loadState === 'error') return '加载失败'
  return '已收录'
}

export function resourceStatusType(node: TreeNode): 'success' | 'warning' | 'danger' | 'info' {
  const status = resourceStatus(node)
  if (status === '可预览') return 'success'
  if (status === '读取中' || status === '处理中') return 'warning'
  if (status === '加载失败') return 'danger'
  return 'info'
}

export function formatUpdatedAt(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}
