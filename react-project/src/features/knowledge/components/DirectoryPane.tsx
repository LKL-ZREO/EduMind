import { Button, Dropdown, Empty, Input, Space, Tag, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { fileType, formatUpdatedAt } from '@/features/knowledge/model/tree'
import type { TreeNode } from '@/features/knowledge/model/types'
import styles from './DirectoryPane.module.css'

type DirectoryPaneProps = {
  title: string
  path: TreeNode[]
  items: TreeNode[]
  selectedNodeId: number | null
  search: string
  onSearch: (search: string) => void
  onSelect: (node: TreeNode) => void
  onNavigatePath: (node: TreeNode | null) => void
  onCreateFolder: (parent: TreeNode | null) => void
  onUpload: (parent: TreeNode | null) => void
  onRename: (node: TreeNode) => void
  onDelete: (node: TreeNode) => void
}

export function DirectoryPane({
  title,
  path,
  items,
  selectedNodeId,
  search,
  onSearch,
  onSelect,
  onNavigatePath,
  onCreateFolder,
  onUpload,
  onRename,
  onDelete,
}: DirectoryPaneProps) {
  const currentFolder = path[path.length - 1] || null
  const visibleItems = items.filter((item) =>
    item.label.toLocaleLowerCase('zh-CN').includes(search.trim().toLocaleLowerCase('zh-CN')),
  )

  function menu(node: TreeNode): MenuProps {
    const items: MenuProps['items'] = [
      ...(node.type === 'folder'
        ? [
            { key: 'upload', label: '上传到此目录' },
            { key: 'folder', label: '新建子文件夹' },
          ]
        : []),
      { key: 'rename', label: '重命名' },
      { type: 'divider' },
      { key: 'delete', danger: true, label: '删除' },
    ]
    return {
      items,
      onClick: ({ key, domEvent }) => {
        domEvent.stopPropagation()
        if (key === 'upload') onUpload(node)
        if (key === 'folder') onCreateFolder(node)
        if (key === 'rename') onRename(node)
        if (key === 'delete') onDelete(node)
      },
    }
  }

  return (
    <section className={styles.pane}>
      <div className={styles.breadcrumb}>
        <Button type="link" size="small" onClick={() => onNavigatePath(null)}>
          根目录
        </Button>
        {path.map((node) => (
          <span key={node.id}>
            /{' '}
            <Button type="link" size="small" onClick={() => onNavigatePath(node)}>
              {node.label}
            </Button>
          </span>
        ))}
      </div>

      <header className={styles.header}>
        <div>
          <Typography.Title level={2}>{title}</Typography.Title>
          <Typography.Text type="secondary">{items.length} 个项目</Typography.Text>
        </div>
        <Space wrap>
          <Input.Search
            allowClear
            value={search}
            placeholder="搜索当前目录"
            onChange={(event) => onSearch(event.target.value)}
          />
          <Button onClick={() => onCreateFolder(currentFolder)}>新建文件夹</Button>
          <Button type="primary" onClick={() => onUpload(currentFolder)}>
            上传文件
          </Button>
        </Space>
      </header>

      <div className={styles.list}>
        {visibleItems.length === 0 && (
          <Empty description={search ? '没有匹配的资源' : '当前目录为空'}>
            {!search && <Button onClick={() => onUpload(currentFolder)}>上传教学资料</Button>}
          </Empty>
        )}
        {visibleItems.map((node) => (
          <div
            key={node.id}
            role="button"
            tabIndex={0}
            className={`${styles.item} ${selectedNodeId === node.id ? styles.selected : ''}`}
            onClick={() => onSelect(node)}
            onDoubleClick={() => node.type === 'folder' && onSelect(node)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') onSelect(node)
            }}
          >
            <span className={`${styles.icon} ${node.type === 'folder' ? styles.folder : ''}`}>
              {node.type === 'folder' ? '📁' : '📄'}
            </span>
            <span className={styles.copy}>
              <strong>{node.label}</strong>
              <small>
                {node.type === 'folder' ? `${node.children.length} 个项目` : fileType(node.label)}
                {' · '}
                {formatUpdatedAt(node.updatedAt)}
              </small>
            </span>
            <Tag>{node.type === 'folder' ? '目录' : '已收录'}</Tag>
            <Dropdown trigger={['click']} menu={menu(node)}>
              <Button
                type="text"
                aria-label={`管理资源 ${node.label}`}
                onClick={(event) => event.stopPropagation()}
              >
                •••
              </Button>
            </Dropdown>
          </div>
        ))}
      </div>
    </section>
  )
}
