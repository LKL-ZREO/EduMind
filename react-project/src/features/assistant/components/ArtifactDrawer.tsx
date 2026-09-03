import { useState } from 'react'
import { Button, DatePicker, Drawer, Empty, Form, Input, Segmented, Space, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { renderAssistantMarkdown } from '@/features/assistant/model/markdown'
import type { CalendarPlanPayload, LessonArtifact } from '@/features/assistant/model/types'
import styles from './ArtifactDrawer.module.css'

type ArtifactDrawerProps = {
  open: boolean
  artifact: LessonArtifact | null
  classId: number | null
  className?: string
  sessionTitle?: string
  saving: boolean
  onClose: () => void
  onApplyEdit: (content: string) => void
  onCopy: (content: string) => void
  onSaveCalendar: (payload: CalendarPlanPayload) => Promise<void>
  onOpenPreLesson: () => void
}

type CalendarFields = {
  topic: string
  knowledgePoints?: string
  plannedDate: Dayjs
}

export function ArtifactDrawer({
  open,
  artifact,
  classId,
  className,
  sessionTitle,
  saving,
  onClose,
  onApplyEdit,
  onCopy,
  onSaveCalendar,
  onOpenPreLesson,
}: ArtifactDrawerProps) {
  const [view, setView] = useState<'preview' | 'edit'>('preview')
  const [draft, setDraft] = useState(artifact?.content || '')

  return (
    <Drawer
      open={open}
      size={440}
      title={artifact ? '教学成果' : '教学上下文'}
      destroyOnHidden
      onClose={onClose}
    >
      {!artifact ? (
        <Empty description="完成一次备课方案生成后，成果会出现在这里">
          <Typography.Text type="secondary">
            当前班级：{className || '通用课程上下文'}
          </Typography.Text>
        </Empty>
      ) : (
        <Space direction="vertical" size={18} style={{ width: '100%' }}>
          <div className={styles.artifactHeading}>
            <span>📝</span>
            <div>
              <Typography.Title level={4}>{artifact.title}</Typography.Title>
              <Typography.Text type="secondary">可编辑 · 可加入备课日历</Typography.Text>
            </div>
          </div>

          <Space>
            <Segmented
              value={view}
              options={[
                { label: '预览', value: 'preview' },
                { label: '编辑', value: 'edit' },
              ]}
              onChange={(value) => setView(value as 'preview' | 'edit')}
            />
            <Button onClick={() => onCopy(artifact.content)}>复制</Button>
          </Space>

          {view === 'edit' ? (
            <>
              <Input.TextArea
                rows={15}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
              />
              <Button
                block
                type="primary"
                onClick={() => {
                  onApplyEdit(draft)
                  setView('preview')
                }}
              >
                应用修改
              </Button>
            </>
          ) : (
            <div
              className={styles.markdown}
              dangerouslySetInnerHTML={{ __html: renderAssistantMarkdown(artifact.content) }}
            />
          )}

          <div className={styles.calendar}>
            <Typography.Title level={5}>加入备课日历</Typography.Title>
            <Form<CalendarFields>
              layout="vertical"
              initialValues={{ topic: sessionTitle || artifact.title, plannedDate: dayjs() }}
              onFinish={(values) => {
                if (!classId) return
                void onSaveCalendar({
                  classId,
                  weekNumber: 0,
                  plannedDate: values.plannedDate.format('YYYY-MM-DD'),
                  topic: values.topic.trim(),
                  knowledgePoints: values.knowledgePoints?.trim() || null,
                })
              }}
            >
              <Form.Item
                label="主题"
                name="topic"
                rules={[{ required: true, whitespace: true, message: '请输入备课主题' }]}
              >
                <Input placeholder="例如：指针与数组复习课" />
              </Form.Item>
              <Form.Item label="知识点" name="knowledgePoints">
                <Input placeholder="指针、数组、内存管理" />
              </Form.Item>
              <Form.Item label="计划日期" name="plannedDate" rules={[{ required: true }]}>
                <DatePicker style={{ width: '100%' }} />
              </Form.Item>
              <Button block type="primary" htmlType="submit" loading={saving} disabled={!classId}>
                {classId ? '确认加入备课日历' : '请先选择班级'}
              </Button>
            </Form>
            <Button block onClick={onOpenPreLesson}>
              打开备课工作台
            </Button>
          </div>
        </Space>
      )}
    </Drawer>
  )
}
