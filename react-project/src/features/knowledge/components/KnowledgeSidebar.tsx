import { Button, Empty, Spin, Tree, Typography } from 'antd'
import type { DataNode, DirectoryTreeProps } from 'antd/es/tree'
import { findNodePath, findTreeNode } from '@/features/knowledge/model/tree'
import type { KnowledgeSpaces, SharedKb, TreeNode } from '@/features/knowledge/model/types'
import styles from './KnowledgeSidebar.module.css'

type KnowledgeSidebarProps = {
  spaces: KnowledgeSpaces
  activeKbId: number | null
  tree: TreeNode[]
  selectedNodeId: number | null
  loading: boolean
  onSelectSpace: (kbId: number | null) => void
  onSelectNode: (nodeId: number) => void
  onMoveNode: (nodeId: number, targetParentId: number | null) => void
  onCreateKnowledgeBase: () => void
  onJoinKnowledgeBase: () => void
  onOpenSettings: (kb: SharedKb) => void
}

function toTreeData(nodes: TreeNode[]): DataNode[] {
  return nodes.map((node) => ({
    key: node.id,
    title: (
      <span className={styles.treeTitle}>
        <span>{node.type === 'folder' ? '📁' : '📄'}</span>
        <span>{node.label}</span>
      </span>
    ),
    children: toTreeData(node.children),
  }))
}

export function KnowledgeSidebar({
  spaces,
  activeKbId,
  tree,
  selectedNodeId,
  loading,
  onSelectSpace,
  onSelectNode,
  onMoveNode,
  onCreateKnowledgeBase,
  onJoinKnowledgeBase,
  onOpenSettings,
}: KnowledgeSidebarProps) {
  const handleDrop: DirectoryTreeProps['onDrop'] = (info) => {
    const nodeId = Number(info.dragNode.key)
    const dropNodeId = Number(info.node.key)
    const dropNode = findTreeNode(tree, dropNodeId)
    if (!dropNode) return
    if (!info.dropToGap && dropNode.type === 'folder') {
      onMoveNode(nodeId, dropNode.id)
      return
    }
    const path = findNodePath(tree, dropNodeId)
    onMoveNode(nodeId, path.length > 1 ? path[path.length - 2]?.id || null : null)
  }

  return (
    <aside className={styles.sidebar}>
      <div className={styles.heading}>
        <div>
          <span className={styles.eyebrow}>KNOWLEDGE</span>
          <Typography.Title level={3}>教学知识库</Typography.Title>
        </div>
      </div>

      <Button
        block
        type={activeKbId === null ? 'primary' : 'text'}
        className={styles.spaceButton}
        onClick={() => onSelectSpace(null)}
      >
        <span>🏠 个人空间</span>
      </Button>

      <div className={styles.spaceSection}>
        <div className={styles.sectionHeading}>
          <span>我创建的团队库</span>
          <Button
            type="text"
            size="small"
            aria-label="新建团队知识库"
            onClick={onCreateKnowledgeBase}
          >
            ＋
          </Button>
        </div>
        {spaces.owned.map((kb) => (
          <div key={kb.id} className={styles.spaceRow}>
            <Button
              type={activeKbId === kb.id ? 'primary' : 'text'}
              onClick={() => onSelectSpace(kb.id)}
            >
              👥 {kb.name}
            </Button>
            <Button
              type="text"
              size="small"
              aria-label={`设置知识库 ${kb.name}`}
              onClick={() => onOpenSettings(kb)}
            >
              ⚙
            </Button>
          </div>
        ))}
      </div>

      <div className={styles.spaceSection}>
        <div className={styles.sectionHeading}>
          <span>我加入的团队库</span>
          <Button
            type="text"
            size="small"
            aria-label="加入团队知识库"
            onClick={onJoinKnowledgeBase}
          >
            ＋
          </Button>
        </div>
        {spaces.joined.map((kb) => (
          <Button
            key={kb.id}
            block
            type={activeKbId === kb.id ? 'primary' : 'text'}
            className={styles.spaceButton}
            onClick={() => onSelectSpace(kb.id)}
          >
            <span>🌐 {kb.name}</span>
          </Button>
        ))}
      </div>

      <div className={styles.treeArea}>
        <Typography.Text type="secondary">当前空间目录</Typography.Text>
        {loading ? (
          <Spin className={styles.spin} />
        ) : tree.length ? (
          <Tree.DirectoryTree
            blockNode
            draggable
            defaultExpandAll
            treeData={toTreeData(tree)}
            selectedKeys={selectedNodeId === null ? [] : [selectedNodeId]}
            allowDrop={({ dropNode, dropPosition }) =>
              dropPosition !== 0 || findTreeNode(tree, Number(dropNode.key))?.type === 'folder'
            }
            onSelect={(keys) => {
              const key = keys[0]
              if (key !== undefined) onSelectNode(Number(key))
            }}
            onDrop={handleDrop}
          />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无目录" />
        )}
      </div>
    </aside>
  )
}
