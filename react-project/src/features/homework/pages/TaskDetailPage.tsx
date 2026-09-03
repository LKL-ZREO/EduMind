import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { TableColumnsType } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'
import { classGroupsQueryOptions } from '@/features/classroom/api/classroomQueries'
import { homeworkTaskDetailQueryOptions } from '@/features/homework/api/homeworkQueries'
import { formatDateTime } from '@/features/homework/model/homework'
import type { TaskSubmission } from '@/features/homework/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './TaskDetailPage.module.css'

const distributionMeta = [
  { key: 'excellent', label: '优秀', range: '90+', color: '#1677ff' },
  { key: 'good', label: '良好', range: '80–89', color: '#36cfc9' },
  { key: 'medium', label: '中等', range: '70–79', color: '#73d13d' },
  { key: 'pass', label: '及格', range: '60–69', color: '#faad14' },
  { key: 'fail', label: '不及格', range: '<60', color: '#ff4d4f' },
] as const

function scoreColor(score: number) {
  if (score >= 90) return 'blue'
  if (score >= 80) return 'cyan'
  if (score >= 60) return 'green'
  return 'red'
}

export function TaskDetailPage() {
  const { id } = useParams()
  const taskId = Number(id)
  const navigate = useNavigate()
  const taskQuery = useQuery({
    ...homeworkTaskDetailQueryOptions(taskId),
    enabled: Number.isInteger(taskId) && taskId > 0,
  })
  const classGroupsQuery = useQuery(classGroupsQueryOptions())
  const task = taskQuery.data
  const className = (classGroupsQuery.data || [])
    .flatMap((group) => group.classes)
    .find((item) => item.id === task?.classId)?.name

  const columns: TableColumnsType<TaskSubmission> = [
    { title: '姓名', dataIndex: 'studentName' },
    { title: '学号', dataIndex: 'studentId', render: (value?: string) => value || '—' },
    {
      title: '得分',
      render: (_, submission) => {
        const score = submission.finalScore ?? submission.score
        return score == null ? '—' : <Tag color={scoreColor(score)}>{score} 分</Tag>
      },
    },
    {
      title: '状态',
      render: (_, submission) =>
        submission.score == null ? (
          <Tag>批改中</Tag>
        ) : submission.isLate ? (
          <Tag color="orange">晚交</Tag>
        ) : (
          <Tag color="green">正常</Tag>
        ),
    },
    {
      title: '提交时间',
      dataIndex: 'submittedAt',
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      render: (_, submission) => (
        <Button
          type="link"
          onClick={() =>
            window.open(
              `/view/submission/${submission.submissionId}`,
              '_blank',
              'noopener,noreferrer',
            )
          }
        >
          查看原文
        </Button>
      ),
    },
  ]

  if (!Number.isInteger(taskId) || taskId <= 0) {
    return <Empty description="作业编号无效" />
  }

  if (taskQuery.isPending) {
    return (
      <div className={styles.loading}>
        <Spin size="large" />
        正在加载作业统计……
      </div>
    )
  }

  if (taskQuery.isError || !task) {
    return (
      <Alert
        showIcon
        type="error"
        title="作业详情加载失败"
        description={getApiErrorMessage(taskQuery.error, '作业不存在或无权查看')}
        action={<Button onClick={() => void navigate('/teacher/tasks')}>返回作业列表</Button>}
      />
    )
  }

  const maxDistribution = Math.max(
    1,
    ...distributionMeta.map((item) => task.distribution[item.key] || 0),
  )

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <Button onClick={() => void navigate('/teacher/tasks')}>← 返回</Button>
        <div>
          <Typography.Title level={2}>{task.taskName}</Typography.Title>
          <Space wrap>
            <Tag color="blue">{className || `班级 ${task.classId}`}</Tag>
            <span>截止 {formatDateTime(task.deadline)}</span>
            <span>{task.allowLate ? `允许迟交，每天扣 ${task.latePenalty} 分` : '不允许迟交'}</span>
          </Space>
        </div>
      </header>

      <section className={styles.metrics}>
        <Card>
          <Statistic title="已提交人数" value={task.submittedCount} />
        </Card>
        <Card>
          <Statistic title="总提交次数" value={task.totalSubmissions} />
        </Card>
        <Card>
          <Statistic title="平均分" value={task.avgScore} precision={1} />
        </Card>
      </section>

      <Card title="成绩分布" className={styles.chartCard}>
        <div className={styles.chart} role="img" aria-label="作业成绩分布柱状图">
          {distributionMeta.map((item) => {
            const value = task.distribution[item.key] || 0
            return (
              <div key={item.key} className={styles.barColumn}>
                <strong>{value}</strong>
                <div className={styles.barTrack}>
                  <span
                    style={{
                      height: `${Math.max(value ? 10 : 0, (value / maxDistribution) * 100)}%`,
                      background: item.color,
                    }}
                  />
                </div>
                <span>{item.label}</span>
                <small>{item.range}</small>
              </div>
            )
          })}
        </div>
      </Card>

      <Card title={`提交列表（${task.submissions.length} 人）`}>
        <Table<TaskSubmission>
          rowKey="submissionId"
          columns={columns}
          dataSource={task.submissions}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
          scroll={{ x: 760 }}
        />
      </Card>

      <Descriptions bordered column={1} size="small">
        <Descriptions.Item label="作业状态">{task.status}</Descriptions.Item>
        <Descriptions.Item label="班级">{className || task.classId}</Descriptions.Item>
      </Descriptions>
    </main>
  )
}
