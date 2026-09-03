import type { FlatNode, TreeNode } from './types'

export function findNodePath(nodes: TreeNode[], id: number, parents: TreeNode[] = []): TreeNode[] {
  for (const node of nodes) {
    const path = [...parents, node]
    if (node.id === id) return path
    const childPath = findNodePath(node.children, id, path)
    if (childPath.length) return childPath
  }
  return []
}

export function findTreeNode(nodes: TreeNode[], id: number): TreeNode | null {
  const path = findNodePath(nodes, id)
  return path[path.length - 1] || null
}

export function countTreeItems(nodes: TreeNode[], type: 'folder' | 'file'): number {
  return nodes.reduce(
    (total, node) => total + (node.type === type ? 1 : 0) + countTreeItems(node.children, type),
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
      isShared: node.isShared,
      createdAt: node.createdAt,
      updatedAt: node.updatedAt,
    })
  }

  const roots: TreeNode[] = []
  for (const item of flat) {
    const node = map.get(item.id)
    if (!node) continue
    if (item.parentId == null) roots.push(node)
    else map.get(item.parentId)?.children.push(node)
  }
  return sortTree(roots)
}

export function sortTree(nodes: TreeNode[]): TreeNode[] {
  return [...nodes]
    .sort((left, right) => {
      if (left.type !== right.type) return left.type === 'folder' ? -1 : 1
      return left.label.localeCompare(right.label, 'zh-CN')
    })
    .map((node) => ({ ...node, children: sortTree(node.children) }))
}

export function currentDirectoryItems(roots: TreeNode[], selectedNode: TreeNode | null) {
  if (!selectedNode) return roots
  if (selectedNode.type === 'folder') return selectedNode.children
  const path = findNodePath(roots, selectedNode.id)
  const parent = path.length > 1 ? path[path.length - 2] : null
  return parent?.children || roots
}

export function fileType(name: string) {
  const extension = name.split('.').pop()?.toUpperCase()
  return extension && extension !== name.toUpperCase() ? `${extension} 文档` : '文档'
}

export function isPptFile(label: string) {
  return /\.(ppt|pptx)$/i.test(label)
}

export function formatUpdatedAt(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

export function extractJoinToken(value: string) {
  const input = value.trim()
  if (!input) return ''
  try {
    const url = new URL(input)
    return url.searchParams.get('joinToken') || url.searchParams.get('token') || input
  } catch {
    const match = /[?&](?:joinToken|token)=([^&]+)/.exec(input)
    return match?.[1] ? decodeURIComponent(match[1]) : input
  }
}
