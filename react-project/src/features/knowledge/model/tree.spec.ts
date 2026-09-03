import { describe, expect, it } from 'vitest'
import {
  buildTree,
  countTreeItems,
  currentDirectoryItems,
  extractJoinToken,
  findNodePath,
} from './tree'
import type { FlatNode } from './types'

const nodes: FlatNode[] = [
  {
    id: 3,
    userId: 1,
    parentId: 1,
    label: '函数笔记.md',
    nodeType: 'file',
    docId: 'doc-3',
    sortOrder: 0,
    createdAt: '2026-08-01T00:00:00',
    updatedAt: '2026-08-03T00:00:00',
  },
  {
    id: 2,
    userId: 1,
    parentId: null,
    label: '根目录说明.md',
    nodeType: 'file',
    docId: 'doc-2',
    sortOrder: 0,
    createdAt: '2026-08-01T00:00:00',
    updatedAt: '2026-08-03T00:00:00',
  },
  {
    id: 1,
    userId: 1,
    parentId: null,
    label: '高一数学',
    nodeType: 'folder',
    docId: null,
    sortOrder: 0,
    createdAt: '2026-08-01T00:00:00',
    updatedAt: '2026-08-03T00:00:00',
  },
]

describe('knowledge tree model', () => {
  it('normalizes a flat response into a folder-first tree without mutating the response', () => {
    const tree = buildTree(nodes)

    expect(tree.map((node) => node.label)).toEqual(['高一数学', '根目录说明.md'])
    expect(tree[0]?.children.map((node) => node.label)).toEqual(['函数笔记.md'])
    expect(nodes[0]).not.toHaveProperty('children')
  })

  it('derives paths, directory contents and recursive totals from the normalized tree', () => {
    const tree = buildTree(nodes)
    const nestedFile = tree[0]?.children[0]
    expect(nestedFile).toBeDefined()

    expect(findNodePath(tree, 3).map((node) => node.id)).toEqual([1, 3])
    expect(currentDirectoryItems(tree, nestedFile || null).map((node) => node.id)).toEqual([3])
    expect(countTreeItems(tree, 'folder')).toBe(1)
    expect(countTreeItems(tree, 'file')).toBe(2)
  })

  it('accepts both invitation links and raw invitation tokens', () => {
    expect(extractJoinToken('https://example.test/teacher/docs?joinToken=team-123')).toBe(
      'team-123',
    )
    expect(extractJoinToken('/teacher/docs?token=encoded%20token')).toBe('encoded token')
    expect(extractJoinToken('raw-token')).toBe('raw-token')
  })
})
