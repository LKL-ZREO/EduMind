import { Button, Empty, Input, Segmented, Spin, Typography } from 'antd'
import { formatDateTime } from '@/features/homework/model/homework'
import type { HomeworkDraft, TaskGroup } from '@/features/homework/model/types'
import styles from './TaskLibrarySidebar.module.css'

type TaskLibrarySidebarProps = {
  mode: 'draft' | 'published'
  drafts: HomeworkDraft[]
  groups: TaskGroup[]
  activeDraftId: number | null
  activePublishedKey: string
  loading: boolean
  draftSearch: string
  publishedSearch: string
  onModeChange: (mode: 'draft' | 'published') => void
  onDraftSearch: (value: string) => void
  onPublishedSearch: (value: string) => void
  onSelectDraft: (draft: HomeworkDraft) => void
  onSelectPublished: (group: TaskGroup) => void
}

export function TaskLibrarySidebar({
  mode,
  drafts,
  groups,
  activeDraftId,
  activePublishedKey,
  loading,
  draftSearch,
  publishedSearch,
  onModeChange,
  onDraftSearch,
  onPublishedSearch,
  onSelectDraft,
  onSelectPublished,
}: TaskLibrarySidebarProps) {
  const visibleDrafts = drafts.filter((draft) =>
    draft.taskName.toLocaleLowerCase('zh-CN').includes(draftSearch.toLocaleLowerCase('zh-CN')),
  )
  const visibleGroups = groups.filter((group) =>
    group.taskName.toLocaleLowerCase('zh-CN').includes(publishedSearch.toLocaleLowerCase('zh-CN')),
  )

  return (
    <aside className={styles.panel}>
      <Segmented
        block
        value={mode}
        options={[
          { label: '草稿', value: 'draft' },
          { label: '已发布', value: 'published' },
        ]}
        onChange={(value) => onModeChange(value as 'draft' | 'published')}
      />
      {mode === 'draft' ? (
        <>
          <Input.Search
            allowClear
            value={draftSearch}
            placeholder="搜索草稿"
            onChange={(event) => onDraftSearch(event.target.value)}
          />
          <div className={styles.list}>
            {visibleDrafts.map((draft) => (
              <Button
                key={draft.id}
                className={styles.row}
                type={activeDraftId === draft.id ? 'primary' : 'text'}
                onClick={() => onSelectDraft(draft)}
              >
                <strong>{draft.taskName || '未命名作业'}</strong>
                <small>
                  {draft.questions.length} 题 · {formatDateTime(draft.updatedAt)}
                </small>
              </Button>
            ))}
            {!visibleDrafts.length && (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无草稿" />
            )}
          </div>
        </>
      ) : (
        <>
          <Input.Search
            allowClear
            value={publishedSearch}
            placeholder="搜索已发布作业"
            onChange={(event) => onPublishedSearch(event.target.value)}
          />
          {loading ? (
            <Spin />
          ) : (
            <div className={styles.list}>
              {visibleGroups.map((group) => (
                <Button
                  key={group.key}
                  className={styles.row}
                  type={activePublishedKey === group.key ? 'primary' : 'text'}
                  onClick={() => onSelectPublished(group)}
                >
                  <strong>{group.taskName}</strong>
                  <small>
                    {group.classNames.join('、')} · {formatDateTime(group.deadline)}
                  </small>
                </Button>
              ))}
              {!visibleGroups.length && (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已发布作业" />
              )}
            </div>
          )}
        </>
      )}
      <Typography.Text type="secondary" className={styles.hint}>
        发布到多个班级的相同作业会合并展示。
      </Typography.Text>
    </aside>
  )
}
