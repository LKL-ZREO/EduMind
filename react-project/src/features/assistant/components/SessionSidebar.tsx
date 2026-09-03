import { useMemo, useState } from 'react'
import { Button, Dropdown, Empty, Input, Spin, Tag, Typography } from 'antd'
import type { MenuProps } from 'antd'
import type { ChatSession } from '@/features/assistant/model/types'
import styles from './SessionSidebar.module.css'

type SessionSidebarProps = {
  sessions: ChatSession[]
  activeSessionId: string
  loading: boolean
  connected: boolean
  mobileOpen: boolean
  onMobileClose: () => void
  onCreate: () => void
  onSelect: (sessionId: string) => void
  onRename: (session: ChatSession) => void
  onTogglePin: (session: ChatSession) => void
  onDelete: (session: ChatSession) => void
}

function formatSessionTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function sessionIcon(mode: ChatSession['mode']) {
  if (mode === 'lesson_plan') return '📝'
  if (mode === 'learning_analysis') return '📊'
  if (mode === 'grading') return '✓'
  return '✦'
}

export function SessionSidebar({
  sessions,
  activeSessionId,
  loading,
  connected,
  mobileOpen,
  onMobileClose,
  onCreate,
  onSelect,
  onRename,
  onTogglePin,
  onDelete,
}: SessionSidebarProps) {
  const [search, setSearch] = useState('')
  const filteredSessions = useMemo(() => {
    const keyword = search.trim().toLocaleLowerCase('zh-CN')
    if (!keyword) return sessions
    return sessions.filter((session) =>
      `${session.title} ${session.className || ''}`.toLocaleLowerCase('zh-CN').includes(keyword),
    )
  }, [search, sessions])

  function menuItems(session: ChatSession): MenuProps['items'] {
    return [
      { key: 'pin', label: session.pinned ? '取消置顶' : '置顶' },
      { key: 'rename', label: '重命名' },
      { type: 'divider' },
      { key: 'delete', label: <Typography.Text type="danger">删除</Typography.Text> },
    ]
  }

  function handleMenu(session: ChatSession, key: string) {
    if (key === 'pin') onTogglePin(session)
    if (key === 'rename') onRename(session)
    if (key === 'delete') onDelete(session)
  }

  return (
    <aside className={`${styles.sidebar} ${mobileOpen ? styles.mobileOpen : ''}`}>
      <div className={styles.heading}>
        <div>
          <span className={styles.eyebrow}>EDUMIND AGENT</span>
          <Typography.Title level={3}>教学工作台</Typography.Title>
        </div>
        <Button className={styles.mobileClose} type="text" onClick={onMobileClose}>
          ×
        </Button>
      </div>

      <Button block type="primary" className={styles.createButton} onClick={onCreate}>
        ＋ 新建对话
      </Button>
      <Input.Search
        allowClear
        value={search}
        placeholder="搜索对话"
        onChange={(event) => setSearch(event.target.value)}
      />

      <div className={styles.list}>
        {loading && <Spin className={styles.loading} />}
        {!loading && filteredSessions.length === 0 && (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有历史对话" />
        )}
        {filteredSessions.map((session) => (
          <div
            key={session.sessionId}
            role="button"
            tabIndex={0}
            className={`${styles.item} ${session.sessionId === activeSessionId ? styles.active : ''}`}
            onClick={() => onSelect(session.sessionId)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') onSelect(session.sessionId)
            }}
          >
            <span className={styles.icon}>{sessionIcon(session.mode)}</span>
            <span className={styles.copy}>
              <strong>{session.title || '新对话'}</strong>
              <small>
                {session.className || '通用教学对话'} · {formatSessionTime(session.updatedAt)}
              </small>
            </span>
            {session.pinned && <Tag color="blue">置顶</Tag>}
            <Dropdown
              trigger={['click']}
              menu={{
                items: menuItems(session),
                onClick: ({ key, domEvent }) => {
                  domEvent.stopPropagation()
                  handleMenu(session, key)
                },
              }}
            >
              <Button
                type="text"
                aria-label={`管理会话 ${session.title}`}
                onClick={(event) => event.stopPropagation()}
              >
                •••
              </Button>
            </Dropdown>
          </div>
        ))}
      </div>

      <div className={styles.footer}>
        <span className={`${styles.statusDot} ${connected ? '' : styles.off}`} />
        {connected ? 'Agent 服务已就绪' : '连接异常'}
      </div>
    </aside>
  )
}
