import { useMemo, useState } from 'react'
import {
  Alert,
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import {
  knowledgeMasteryQueryOptions,
  previewTasksQueryOptions,
  teachingClassesQueryOptions,
} from '@/features/teaching/api/teachingQueries'
import { useTeachingMutations } from '@/features/teaching/hooks/useTeachingMutations'
import type { PreviewTask } from '@/features/teaching/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './PreviewCreatePage.module.css'

type FormValues = { knowledgePoint: string; topic?: string; docId?: string }

export function PreviewCreatePage() {
  const { message } = App.useApp()
  const [searchParams, setSearchParams] = useSearchParams()
  const [form] = Form.useForm<FormValues>()
  const [created, setCreated] = useState<PreviewTask | null>(null)
  const classesQuery = useQuery(teachingClassesQueryOptions())
  const classes = classesQuery.data || []
  const requestedClassId = Number(searchParams.get('classId'))
  const classId = classes.some((item) => item.id === requestedClassId)
    ? requestedClassId
    : classes[0]?.id || 0
  const knowledgeQuery = useQuery(knowledgeMasteryQueryOptions(classId))
  const tasksQuery = useQuery(previewTasksQueryOptions(classId))
  const mutations = useTeachingMutations()
  const knowledgeOptions = useMemo(
    () =>
      (knowledgeQuery.data || [])
        .filter((item) => item.name !== '其他')
        .map((item) => ({ value: item.name, label: `${item.name} · 掌握度 ${item.mastery}%` })),
    [knowledgeQuery.data],
  )

  async function create(values: FormValues) {
    try {
      const task = await mutations.createPreview.mutateAsync({ classId, ...values })
      setCreated(task)
      message.success('预习任务已生成')
    } catch (error) {
      message.error(getApiErrorMessage(error, '生成预习任务失败'))
    }
  }

  async function close(task: PreviewTask) {
    try {
      await mutations.closePreview.mutateAsync({ taskId: task.id, classId })
      message.success('预习任务已关闭')
    } catch (error) {
      message.error(getApiErrorMessage(error, '关闭任务失败'))
    }
  }

  async function copyLink(taskId: number) {
    await navigator.clipboard.writeText(`${window.location.origin}/preview/${taskId}`)
    message.success('学生访问链接已复制')
  }

  if (classesQuery.isPending)
    return (
      <div className={styles.center}>
        <Spin size="large" />
      </div>
    )
  if (!classes.length)
    return (
      <div className={styles.page}>
        <Empty description="请先创建班级" />
      </div>
    )

  return (
    <main className={styles.page}>
      <header className={styles.hero}>
        <div>
          <span>AI PREVIEW</span>
          <Typography.Title level={2}>生成课前预习任务</Typography.Title>
          <Typography.Paragraph>
            输入一个知识点，由 AI 生成导学材料、自测题和讨论问题。
          </Typography.Paragraph>
        </div>
        <Select
          aria-label="选择班级"
          value={classId}
          options={classes.map((item) => ({ value: item.id, label: item.name }))}
          onChange={(value) => {
            setCreated(null)
            form.resetFields()
            setSearchParams({ classId: String(value) })
          }}
        />
      </header>

      <section className={styles.columns}>
        <Card title="生成配置">
          <Alert type="info" showIcon message="AI 生成通常需要几十秒，请勿重复提交。" />
          <Form
            form={form}
            layout="vertical"
            onFinish={(values) => void create(values)}
            className={styles.form}
          >
            <Form.Item
              name="knowledgePoint"
              label="核心知识点"
              rules={[{ required: true, message: '请选择或输入知识点' }]}
            >
              <Select
                showSearch
                allowClear
                options={knowledgeOptions}
                placeholder="选择知识点"
                optionFilterProp="label"
              />
            </Form.Item>
            <Form.Item name="topic" label="本课主题（可选）">
              <Input placeholder="例如：一元二次方程的实际应用" />
            </Form.Item>
            <Form.Item name="docId" label="参考文档 ID（可选）">
              <Input placeholder="让 AI 优先参考指定知识库文档" />
            </Form.Item>
            <Button
              block
              type="primary"
              htmlType="submit"
              loading={mutations.createPreview.isPending}
            >
              生成预习任务
            </Button>
          </Form>
        </Card>

        <Card title="生成结果">
          {created ? (
            <div className={styles.result}>
              <Tag color="green">已生成</Tag>
              <Typography.Title level={3}>{created.title}</Typography.Title>
              <Typography.Paragraph>{created.guideText}</Typography.Paragraph>
              <div className={styles.resultMeta}>
                <span>{created.questions.length} 道自测题</span>
                <span>讨论题 1 道</span>
              </div>
              <Space wrap>
                <Link to={`/preview/${created.id}`} target="_blank">
                  <Button type="primary">打开学生页面</Button>
                </Link>
                <Button onClick={() => void copyLink(created.id)}>复制链接</Button>
              </Space>
            </div>
          ) : (
            <Empty description="生成完成后会在这里显示任务摘要" />
          )}
        </Card>
      </section>

      <Card title="该班级的预习任务">
        <div className={styles.taskList}>
          {(tasksQuery.data || []).map((task) => (
            <article key={task.id}>
              <div>
                <Space wrap>
                  <Typography.Text strong>{task.title}</Typography.Text>
                  <Tag color={task.status === 'ACTIVE' ? 'green' : 'default'}>{task.status}</Tag>
                </Space>
                <p>
                  {task.knowledgePoint} · {new Date(task.createdAt).toLocaleString('zh-CN')}
                </p>
              </div>
              <Space>
                <Link to={`/preview/${task.id}`} target="_blank">
                  <Button>预览</Button>
                </Link>
                <Button onClick={() => void copyLink(task.id)}>复制链接</Button>
                {task.status === 'ACTIVE' && (
                  <Button
                    danger
                    loading={mutations.closePreview.isPending}
                    onClick={() => void close(task)}
                  >
                    关闭
                  </Button>
                )}
              </Space>
            </article>
          ))}
          {!tasksQuery.isPending && !tasksQuery.data?.length && (
            <Empty description="暂无预习任务" />
          )}
        </div>
      </Card>
    </main>
  )
}
