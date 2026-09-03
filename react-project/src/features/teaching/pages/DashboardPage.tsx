import { lazy, Suspense, useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Empty,
  Input,
  Modal,
  Progress,
  Select,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import {
  confusionSignalsQueryOptions,
  dashboardMetricsQueryOptions,
  frequentErrorsQueryOptions,
  knowledgeMasteryQueryOptions,
  reclassificationQueryOptions,
  scoreDistributionQueryOptions,
  studentOverviewQueryOptions,
  teachingClassesQueryOptions,
  teachingKeys,
} from '@/features/teaching/api/teachingQueries'
import {
  KnowledgeMasteryChart,
  ScoreDistributionChart,
} from '@/features/teaching/components/DashboardCharts'
import { useTeachingMutations } from '@/features/teaching/hooks/useTeachingMutations'
import {
  combineConfusionEvents,
  combineConfusionStats,
  filterStudents,
  otherKnowledgeRate,
  passRate,
  studentStatus,
  weakKnowledgePoints,
} from '@/features/teaching/model/dashboard'
import type {
  KnowledgeMastery,
  StudentOverview,
  TeacherKnowledgeItem,
} from '@/features/teaching/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'
import { sanitizeHtml } from '@/shared/utils/safeHtml'
import styles from './DashboardPage.module.css'

const StudentInsightModal = lazy(() =>
  import('@/features/teaching/components/StudentInsightModal').then((module) => ({
    default: module.StudentInsightModal,
  })),
)

const terminalStatuses = new Set(['COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'])

function KnowledgeEditor({
  classId,
  initial,
  onClose,
  onTask,
}: {
  classId: number
  initial: KnowledgeMastery[]
  onClose: () => void
  onTask: (taskId: string) => void
}) {
  const { message } = App.useApp()
  const mutations = useTeachingMutations()
  const [items, setItems] = useState<TeacherKnowledgeItem[]>(() =>
    initial
      .filter((item) => item.name !== '其他')
      .map((item, index) => ({
        id: item.id,
        name: item.name,
        color: item.color || '#3f8f87',
        sortOrder: index,
      })),
  )
  const [newName, setNewName] = useState('')

  async function addItem() {
    if (!newName.trim()) return
    try {
      const task = await mutations.addKnowledge.mutateAsync({
        classId,
        name: newName.trim(),
        color: '#3f8f87',
      })
      onTask(task.taskId)
      setNewName('')
      message.success('知识点已添加，历史错题正在重新分类')
    } catch (error) {
      message.error(getApiErrorMessage(error, '添加知识点失败'))
    }
  }

  async function save() {
    if (items.some((item) => !item.name.trim())) {
      message.warning('知识点名称不能为空')
      return
    }
    try {
      const task = await mutations.saveKnowledge.mutateAsync({
        classId,
        items: items.map((item, index) => ({ ...item, name: item.name.trim(), sortOrder: index })),
      })
      onTask(task.taskId)
      message.success('知识点已保存，后台正在重新分类')
      onClose()
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存知识点失败'))
    }
  }

  return (
    <Modal
      title="维护教师知识点"
      open
      onCancel={onClose}
      onOk={() => void save()}
      confirmLoading={mutations.saveKnowledge.isPending}
      okText="保存并重新分类"
    >
      <div className={styles.editorList}>
        {items.map((item, index) => (
          <Space.Compact key={item.id || index} block>
            <input
              aria-label={`${item.name || '新知识点'}颜色`}
              type="color"
              value={item.color}
              onChange={(event) =>
                setItems((current) =>
                  current.map((entry, entryIndex) =>
                    entryIndex === index ? { ...entry, color: event.target.value } : entry,
                  ),
                )
              }
            />
            <Input
              value={item.name}
              onChange={(event) =>
                setItems((current) =>
                  current.map((entry, entryIndex) =>
                    entryIndex === index ? { ...entry, name: event.target.value } : entry,
                  ),
                )
              }
            />
            <Button
              danger
              onClick={() =>
                setItems((current) => current.filter((_, entryIndex) => entryIndex !== index))
              }
            >
              删除
            </Button>
          </Space.Compact>
        ))}
        <Space.Compact block>
          <Input
            placeholder="新增知识点，例如：一元二次方程"
            value={newName}
            onChange={(event) => setNewName(event.target.value)}
            onPressEnter={() => void addItem()}
          />
          <Button onClick={() => void addItem()} loading={mutations.addKnowledge.isPending}>
            添加
          </Button>
        </Space.Compact>
      </div>
    </Modal>
  )
}

export function DashboardPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const classesQuery = useQuery(teachingClassesQueryOptions())
  const classes = classesQuery.data || []
  const requestedClassId = Number(searchParams.get('classId'))
  const selectedClassId = classes.some((item) => item.id === requestedClassId)
    ? requestedClassId
    : classes[0]?.id || 0

  const metricsQuery = useQuery(dashboardMetricsQueryOptions(selectedClassId))
  const distributionQuery = useQuery(scoreDistributionQueryOptions(selectedClassId))
  const knowledgeQuery = useQuery(knowledgeMasteryQueryOptions(selectedClassId))
  const errorsQuery = useQuery(frequentErrorsQueryOptions(selectedClassId))
  const studentsQuery = useQuery(studentOverviewQueryOptions(selectedClassId))
  const confusionsQuery = useQuery(confusionSignalsQueryOptions(selectedClassId))
  const [studentSearch, setStudentSearch] = useState('')
  const [studentSort, setStudentSort] = useState<'score' | 'homework'>('score')
  const [showAllStudents, setShowAllStudents] = useState(false)
  const [selectedStudent, setSelectedStudent] = useState<StudentOverview | null>(null)
  const [knowledgeEditorOpen, setKnowledgeEditorOpen] = useState(false)
  const [taskId, setTaskId] = useState('')
  const [planOpen, setPlanOpen] = useState(false)
  const [goals, setGoals] = useState<string[]>(['巩固薄弱知识点', '提升课堂参与度'])
  const [planHtml, setPlanHtml] = useState('')
  const mutations = useTeachingMutations()

  const reclassificationQuery = useQuery({
    ...reclassificationQueryOptions(selectedClassId, taskId),
    refetchInterval: (query) =>
      query.state.data && terminalStatuses.has(query.state.data.status) ? false : 2_000,
  })
  const reclassificationStatus = reclassificationQuery.data?.status
  useEffect(() => {
    if (!reclassificationStatus || !terminalStatuses.has(reclassificationStatus)) return
    void queryClient.invalidateQueries({ queryKey: teachingKeys.dashboard(selectedClassId) })
  }, [queryClient, reclassificationStatus, selectedClassId])

  const students = useMemo(() => studentsQuery.data || [], [studentsQuery.data])
  const visibleStudents = useMemo(
    () => filterStudents(students, studentSearch, studentSort, showAllStudents),
    [showAllStudents, studentSearch, studentSort, students],
  )
  const knowledge = useMemo(() => knowledgeQuery.data || [], [knowledgeQuery.data])
  const weakPoints = useMemo(() => weakKnowledgePoints(knowledge), [knowledge])
  const confusionStats = useMemo(
    () =>
      combineConfusionStats(
        confusionsQuery.data?.stats || [],
        confusionsQuery.data?.live.stats || [],
      ),
    [confusionsQuery.data],
  )
  const confusionEvents = useMemo(
    () =>
      combineConfusionEvents(
        confusionsQuery.data?.events || [],
        confusionsQuery.data?.live.events || [],
      ),
    [confusionsQuery.data],
  )
  const metrics = metricsQuery.data
  const isLoading = [metricsQuery, distributionQuery, knowledgeQuery, studentsQuery].some(
    (query) => query.isPending,
  )

  function selectClass(classId: number) {
    setTaskId('')
    setSearchParams({ classId: String(classId) })
  }

  async function generatePlan() {
    try {
      const html = await mutations.generatePlan.mutateAsync({
        classId: selectedClassId,
        goals,
        weakKnowledgePoints: weakPoints.map((item) => item.name),
        planType: 'targeted',
      })
      setPlanHtml(sanitizeHtml(html))
    } catch (error) {
      message.error(getApiErrorMessage(error, '生成教学方案失败'))
    }
  }

  if (classesQuery.isPending)
    return (
      <div className={styles.center}>
        <Spin size="large" />
      </div>
    )
  if (classesQuery.isError) return <Alert type="error" message="班级列表加载失败" showIcon />
  if (!classes.length)
    return (
      <div className={styles.page}>
        <Empty description="请先创建班级，再查看教学数据" />
      </div>
    )

  const reclassification = reclassificationQuery.data
  const progress = reclassification?.total
    ? Math.round((reclassification.processed * 100) / reclassification.total)
    : 0

  return (
    <main className={styles.page}>
      <header className={styles.hero}>
        <div>
          <span className={styles.eyebrow}>TEACHING INTELLIGENCE</span>
          <Typography.Title level={2}>教学数据中心</Typography.Title>
          <Typography.Paragraph>
            从成绩、知识点和课堂困惑中找到下一步教学动作。
          </Typography.Paragraph>
        </div>
        <Space wrap>
          <Select
            aria-label="选择班级"
            value={selectedClassId}
            options={classes.map((item) => ({ value: item.id, label: item.name }))}
            onChange={selectClass}
          />
          <Button
            onClick={() =>
              void Promise.all([
                metricsQuery.refetch(),
                distributionQuery.refetch(),
                knowledgeQuery.refetch(),
                errorsQuery.refetch(),
                studentsQuery.refetch(),
                confusionsQuery.refetch(),
              ])
            }
          >
            刷新数据
          </Button>
          <Button type="primary" onClick={() => setPlanOpen(true)}>
            生成教学方案
          </Button>
        </Space>
      </header>

      {isLoading ? (
        <div className={styles.center}>
          <Spin />
        </div>
      ) : (
        <>
          <section className={styles.metrics} aria-label="核心指标">
            <Card>
              <Statistic title="学生人数" value={metrics?.totalStudents || 0} suffix="人" />
            </Card>
            <Card>
              <Statistic title="班级平均分" value={metrics?.avgScore || 0} precision={1} />
            </Card>
            <Card>
              <Statistic title="及格率" value={passRate(students)} precision={1} suffix="%" />
            </Card>
            <Card>
              <Statistic
                title="预警学生"
                value={metrics?.warningStudents || 0}
                styles={{ content: { color: '#cf4b55' } }}
                suffix="人"
              />
            </Card>
          </section>

          {reclassification && (
            <Alert
              showIcon
              type={
                reclassification.status === 'FAILED'
                  ? 'error'
                  : reclassification.status.startsWith('COMPLETED')
                    ? 'success'
                    : 'info'
              }
              message={`知识点重新分类：${reclassification.status}`}
              description={
                <Progress
                  percent={progress}
                  size="small"
                  status={reclassification.status === 'FAILED' ? 'exception' : undefined}
                />
              }
            />
          )}

          <section className={styles.twoColumns}>
            <Card title="成绩分布">
              <ScoreDistributionChart data={distributionQuery.data || []} />
            </Card>
            <Card
              title="知识点掌握度"
              extra={
                <Button size="small" onClick={() => setKnowledgeEditorOpen(true)}>
                  维护知识点
                </Button>
              }
            >
              <KnowledgeMasteryChart data={knowledge} />
              <Space wrap>
                {weakPoints.slice(0, 4).map((item) => (
                  <Tag color="volcano" key={item.name}>
                    {item.name} {item.mastery}%
                  </Tag>
                ))}
                {otherKnowledgeRate(knowledge) > 20 && (
                  <Tag color="gold">“其他”错题占比 {otherKnowledgeRate(knowledge)}%</Tag>
                )}
              </Space>
            </Card>
          </section>

          <section className={styles.twoColumns}>
            <Card title="高频错题">
              <Table
                size="small"
                pagination={false}
                rowKey={(item) => `${item.question}-${item.knowledgePoint}`}
                dataSource={(errorsQuery.data || []).slice(0, 6)}
                columns={[
                  {
                    title: '知识点',
                    dataIndex: 'knowledgePoint',
                    render: (value: string) => <Tag>{value}</Tag>,
                  },
                  { title: '题目', dataIndex: 'question', ellipsis: true },
                  {
                    title: '错误率',
                    dataIndex: 'errorRate',
                    render: (value: number) => `${value}%`,
                  },
                ]}
                locale={{ emptyText: '暂无高频错题' }}
              />
            </Card>
            <Card title="学生困惑信号" extra={<Tag color="blue">QQ + 实时课堂</Tag>}>
              <Space wrap>
                {confusionStats.slice(0, 6).map((item) => (
                  <Tag color="geekblue" key={item.name}>
                    {item.name} · {item.count}
                  </Tag>
                ))}
              </Space>
              <div className={styles.eventList}>
                {confusionEvents.slice(0, 5).map((event) => (
                  <div key={`${event.source}-${event.id}`}>
                    <b>{event.knowledgePoint}</b>
                    <span>
                      {event.studentName || '匿名学生'} · {event.source} ·{' '}
                      {event.question || '发出困惑信号'}
                    </span>
                  </div>
                ))}
              </div>
            </Card>
          </section>

          <Card
            title="需要关注的学生"
            extra={
              <Space wrap>
                <Input.Search
                  allowClear
                  placeholder="姓名或学号"
                  value={studentSearch}
                  onChange={(event) => setStudentSearch(event.target.value)}
                />
                <Select
                  value={studentSort}
                  onChange={setStudentSort}
                  options={[
                    { value: 'score', label: '按成绩' },
                    { value: 'homework', label: '按作业数' },
                  ]}
                />
                <Checkbox
                  checked={showAllStudents}
                  onChange={(event) => setShowAllStudents(event.target.checked)}
                >
                  显示全部
                </Checkbox>
              </Space>
            }
          >
            <Table
              size="small"
              rowKey={(item) => item.id || item.studentId || item.name}
              dataSource={visibleStudents}
              columns={[
                {
                  title: '学生',
                  dataIndex: 'name',
                  render: (value: string, record: StudentOverview) => (
                    <Button type="link" onClick={() => setSelectedStudent(record)}>
                      {value}
                    </Button>
                  ),
                },
                { title: '平均分', dataIndex: 'avgScore' },
                { title: '作业数', dataIndex: 'homeworkCount' },
                { title: '错误数', dataIndex: 'errorCount' },
                {
                  title: '状态',
                  render: (_: unknown, record: StudentOverview) => {
                    const status = studentStatus(record)
                    return <Tag color={status.color}>{status.label}</Tag>
                  },
                },
              ]}
            />
          </Card>
        </>
      )}

      {knowledgeEditorOpen && (
        <KnowledgeEditor
          key={`${selectedClassId}-${knowledge.length}`}
          classId={selectedClassId}
          initial={knowledge}
          onClose={() => setKnowledgeEditorOpen(false)}
          onTask={setTaskId}
        />
      )}
      {selectedStudent && (
        <Suspense fallback={null}>
          <StudentInsightModal
            classId={selectedClassId}
            student={selectedStudent}
            onClose={() => setSelectedStudent(null)}
          />
        </Suspense>
      )}
      <Modal
        title="AI 定向教学方案"
        open={planOpen}
        onCancel={() => setPlanOpen(false)}
        width={760}
        footer={
          <Space>
            <Button onClick={() => setPlanOpen(false)}>关闭</Button>
            <Button
              type="primary"
              loading={mutations.generatePlan.isPending}
              onClick={() => void generatePlan()}
            >
              生成方案
            </Button>
          </Space>
        }
      >
        <Checkbox.Group
          options={['巩固薄弱知识点', '提升课堂参与度', '分层教学', '错题专项训练']}
          value={goals}
          onChange={(values) => setGoals(values)}
        />
        {planHtml ? (
          <article className={styles.plan} dangerouslySetInnerHTML={{ __html: planHtml }} />
        ) : (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="选择目标后生成基于当前班级数据的方案"
          />
        )}
      </Modal>
    </main>
  )
}
