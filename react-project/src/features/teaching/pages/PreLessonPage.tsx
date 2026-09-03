import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App,
  Button,
  Card,
  Checkbox,
  Empty,
  Input,
  InputNumber,
  Modal,
  Progress,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import {
  preLessonQueryOptions,
  teachingClassesQueryOptions,
  timelineQueryOptions,
} from '@/features/teaching/api/teachingQueries'
import { useTeachingMutations } from '@/features/teaching/hooks/useTeachingMutations'
import {
  evidenceSignals,
  exportLessonPlan,
  lessonDraftStorageKey,
  lessonReadiness,
  recentTimeline,
  restoreLessonDraft,
} from '@/features/teaching/model/lesson'
import type {
  LessonDraft,
  LessonStage,
  PreLessonOverview,
  Timeline,
} from '@/features/teaching/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './PreLessonPage.module.css'

function moveItem<T>(items: T[], from: number, to: number) {
  if (to < 0 || to >= items.length) return items
  const next = [...items]
  const [item] = next.splice(from, 1)
  if (item === undefined) return items
  next.splice(to, 0, item)
  return next
}

function PreLessonWorkspace({
  classId,
  className,
  overview,
  timeline,
}: {
  classId: number
  className: string
  overview: PreLessonOverview
  timeline?: Timeline
}) {
  const { message } = App.useApp()
  const [draft, setDraft] = useState<LessonDraft>(() =>
    restoreLessonDraft(classId, overview, timeline),
  )
  const [savedAt, setSavedAt] = useState<Date | null>(null)
  const [suggestion, setSuggestion] = useState(overview.aiSuggestion)
  const [exportOpen, setExportOpen] = useState(false)
  const mutations = useTeachingMutations()
  const signals = useMemo(() => evidenceSignals(overview), [overview])
  const readiness = useMemo(() => lessonReadiness(draft, signals.length), [draft, signals.length])
  const planText = useMemo(() => exportLessonPlan(draft, className), [className, draft])
  const timelineItems = useMemo(() => recentTimeline(timeline), [timeline])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      localStorage.setItem(lessonDraftStorageKey(classId), JSON.stringify(draft))
      setSavedAt(new Date())
    }, 700)
    return () => window.clearTimeout(timer)
  }, [classId, draft])

  function updateDraft(patch: Partial<LessonDraft>) {
    setDraft((current) => ({ ...current, ...patch }))
  }

  function updateStage(index: number, patch: Partial<LessonStage>) {
    setDraft((current) => ({
      ...current,
      stages: current.stages.map((stage, stageIndex) =>
        stageIndex === index ? { ...stage, ...patch } : stage,
      ),
    }))
  }

  async function requestSuggestion() {
    try {
      const response = await mutations.getSuggestion.mutateAsync(classId)
      setSuggestion(response.suggestion)
    } catch (error) {
      message.error(getApiErrorMessage(error, '获取 AI 建议失败'))
    }
  }

  async function addToCalendar() {
    if (!draft.topic.trim()) {
      message.warning('请先填写本节课主题')
      return
    }
    try {
      await mutations.addCalendar.mutateAsync({
        classId,
        weekNumber: 0,
        plannedDate: draft.plannedDate,
        topic: draft.topic.trim(),
        knowledgePoints: draft.knowledgePoints.join('、'),
        status: 'PLANNED',
      })
      message.success('已加入教学日历')
    } catch (error) {
      message.error(getApiErrorMessage(error, '加入教学日历失败'))
    }
  }

  async function copyPlan() {
    await navigator.clipboard.writeText(planText)
    message.success('备课方案已复制')
  }

  return (
    <>
      <section className={styles.summary}>
        <Card>
          <span>班级平均分</span>
          <strong>{overview.avgScore}</strong>
        </Card>
        <Card>
          <span>预警学生</span>
          <strong>{overview.warningCount}</strong>
        </Card>
        <Card>
          <span>课堂参与率</span>
          <strong>{overview.participationRate}%</strong>
        </Card>
        <Card>
          <span>备课完成度</span>
          <Progress type="circle" size={64} percent={readiness.percent} />
        </Card>
      </section>

      <section className={styles.workspace}>
        <aside className={styles.side}>
          <Card title="学情依据">
            <Checkbox.Group
              value={draft.evidenceIds}
              onChange={(values) => updateDraft({ evidenceIds: values })}
            >
              <div className={styles.signalList}>
                {signals.map((signal) => (
                  <Checkbox key={signal.id} value={signal.id}>
                    <b>{signal.title}</b>
                    <small>
                      {signal.category} · {signal.detail}
                    </small>
                  </Checkbox>
                ))}
              </div>
            </Checkbox.Group>
          </Card>
          <Card title="最近教学活动">
            <div className={styles.timeline}>
              {timelineItems.map((item) => (
                <div key={`${item.type}-${item.id}`}>
                  <Tag>{item.typeLabel}</Tag>
                  <b>{item.title}</b>
                  <small>
                    {item.date} {item.time}
                  </small>
                </div>
              ))}
              {!timelineItems.length && (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无记录" />
              )}
            </div>
          </Card>
        </aside>

        <div className={styles.editor}>
          <Card
            title="课程信息"
            extra={
              <span className={styles.saved}>
                {savedAt ? `${savedAt.toLocaleTimeString('zh-CN')} 已自动保存` : '等待自动保存'}
              </span>
            }
          >
            <div className={styles.courseGrid}>
              <label>
                课题
                <Input
                  value={draft.topic}
                  onChange={(event) => updateDraft({ topic: event.target.value })}
                />
              </label>
              <label>
                上课日期
                <Input
                  type="date"
                  value={draft.plannedDate}
                  onChange={(event) => updateDraft({ plannedDate: event.target.value })}
                />
              </label>
              <label>
                课时
                <InputNumber
                  min={10}
                  max={180}
                  value={draft.duration}
                  addonAfter="分钟"
                  onChange={(value) => updateDraft({ duration: value || 45 })}
                />
              </label>
            </div>
          </Card>

          <Card
            title="教学目标"
            extra={
              <Button
                size="small"
                onClick={() => updateDraft({ objectives: [...draft.objectives, ''] })}
              >
                添加目标
              </Button>
            }
          >
            <div className={styles.objectives}>
              {draft.objectives.map((objective, index) => (
                <Space.Compact block key={index}>
                  <Input
                    value={objective}
                    onChange={(event) =>
                      updateDraft({
                        objectives: draft.objectives.map((item, itemIndex) =>
                          itemIndex === index ? event.target.value : item,
                        ),
                      })
                    }
                  />
                  <Button
                    danger
                    onClick={() =>
                      updateDraft({
                        objectives: draft.objectives.filter((_, itemIndex) => itemIndex !== index),
                      })
                    }
                  >
                    删除
                  </Button>
                </Space.Compact>
              ))}
            </div>
          </Card>

          <Card
            title="课堂流程"
            extra={
              <Space>
                <Tag color={readiness.totalMinutes === draft.duration ? 'green' : 'orange'}>
                  {readiness.totalMinutes} / {draft.duration} 分钟
                </Tag>
                <Button
                  size="small"
                  onClick={() =>
                    updateDraft({
                      stages: [
                        ...draft.stages,
                        {
                          id: `custom-${Date.now()}`,
                          phase: '活动',
                          title: '新教学活动',
                          minutes: 5,
                          teacherAction: '',
                          studentAction: '',
                          resource: '',
                        },
                      ],
                    })
                  }
                >
                  添加环节
                </Button>
              </Space>
            }
          >
            <div className={styles.stages}>
              {draft.stages.map((stage, index) => (
                <article key={stage.id}>
                  <div className={styles.stageHead}>
                    <Input
                      value={stage.phase}
                      onChange={(event) => updateStage(index, { phase: event.target.value })}
                    />
                    <Input
                      value={stage.title}
                      onChange={(event) => updateStage(index, { title: event.target.value })}
                    />
                    <InputNumber
                      min={1}
                      max={draft.duration}
                      value={stage.minutes}
                      addonAfter="分"
                      onChange={(value) => updateStage(index, { minutes: value || 1 })}
                    />
                    <Space.Compact>
                      <Button
                        disabled={!index}
                        onClick={() =>
                          updateDraft({ stages: moveItem(draft.stages, index, index - 1) })
                        }
                      >
                        ↑
                      </Button>
                      <Button
                        disabled={index === draft.stages.length - 1}
                        onClick={() =>
                          updateDraft({ stages: moveItem(draft.stages, index, index + 1) })
                        }
                      >
                        ↓
                      </Button>
                      <Button
                        danger
                        onClick={() =>
                          updateDraft({
                            stages: draft.stages.filter((_, itemIndex) => itemIndex !== index),
                          })
                        }
                      >
                        ×
                      </Button>
                    </Space.Compact>
                  </div>
                  <div className={styles.stageBody}>
                    <label>
                      教师活动
                      <Input.TextArea
                        autoSize
                        value={stage.teacherAction}
                        onChange={(event) =>
                          updateStage(index, { teacherAction: event.target.value })
                        }
                      />
                    </label>
                    <label>
                      学生活动
                      <Input.TextArea
                        autoSize
                        value={stage.studentAction}
                        onChange={(event) =>
                          updateStage(index, { studentAction: event.target.value })
                        }
                      />
                    </label>
                    <label>
                      材料与检查点
                      <Input.TextArea
                        autoSize
                        value={stage.resource}
                        onChange={(event) => updateStage(index, { resource: event.target.value })}
                      />
                    </label>
                  </div>
                </article>
              ))}
            </div>
          </Card>

          <Card title="分层策略与备课备注">
            <div className={styles.tiers}>
              {draft.differentiation.map((tier) => (
                <div key={`${tier.label}-${tier.range}`}>
                  <Tag>{tier.label}</Tag>
                  <b>
                    {tier.count} 人 · {tier.range}
                  </b>
                  <span>{tier.strategy}</span>
                </div>
              ))}
            </div>
            <Input.TextArea
              className={styles.notes}
              rows={4}
              value={draft.notes}
              placeholder="记录板书设计、课堂观察点或其他提醒……"
              onChange={(event) => updateDraft({ notes: event.target.value })}
            />
          </Card>
        </div>

        <aside className={styles.side}>
          <Card
            title="AI 备课建议"
            extra={
              <Button
                type="link"
                loading={mutations.getSuggestion.isPending}
                onClick={() => void requestSuggestion()}
              >
                重新生成
              </Button>
            }
          >
            <Typography.Paragraph className={styles.suggestion}>
              {suggestion || '暂无建议'}
            </Typography.Paragraph>
          </Card>
          <Card title="教学材料">
            <div className={styles.materials}>
              <Checkbox
                checked={draft.materialReady.preview}
                onChange={(event) =>
                  updateDraft({
                    materialReady: { ...draft.materialReady, preview: event.target.checked },
                  })
                }
              >
                预习任务已准备
              </Checkbox>
              <Link to={`/teacher/preview/create?classId=${classId}`}>创建预习任务</Link>
              <Checkbox
                checked={draft.materialReady.questions}
                onChange={(event) =>
                  updateDraft({
                    materialReady: { ...draft.materialReady, questions: event.target.checked },
                  })
                }
              >
                课堂题组已准备
              </Checkbox>
              <Link to="/teacher/tasks">前往题库</Link>
              <Checkbox
                checked={draft.materialReady.homework}
                onChange={(event) =>
                  updateDraft({
                    materialReady: { ...draft.materialReady, homework: event.target.checked },
                  })
                }
              >
                课后作业已准备
              </Checkbox>
              <Link to="/teacher/tasks">编辑作业</Link>
            </div>
          </Card>
          <Card title="完成检查">
            <div className={styles.checkList}>
              {readiness.items.map((item) => (
                <div key={item.key}>
                  <Tag color={item.done ? 'green' : 'default'}>{item.done ? '完成' : '待完善'}</Tag>
                  {item.label}
                </div>
              ))}
            </div>
          </Card>
          <Space direction="vertical" className={styles.actions}>
            <Button block onClick={() => setExportOpen(true)}>
              预览与导出
            </Button>
            <Button
              block
              onClick={() => void addToCalendar()}
              loading={mutations.addCalendar.isPending}
            >
              加入教学日历
            </Button>
            <Button block type="primary" onClick={() => updateDraft({ status: 'READY' })}>
              标记备课完成
            </Button>
          </Space>
        </aside>
      </section>

      <Modal
        title="备课方案预览"
        open={exportOpen}
        onCancel={() => setExportOpen(false)}
        width={760}
        footer={
          <Space>
            <Button onClick={() => setExportOpen(false)}>关闭</Button>
            <Button type="primary" onClick={() => void copyPlan()}>
              复制方案
            </Button>
          </Space>
        }
      >
        <pre className={styles.planPreview}>{planText}</pre>
      </Modal>
    </>
  )
}

export function PreLessonPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const classesQuery = useQuery(teachingClassesQueryOptions())
  const classes = classesQuery.data || []
  const requestedClassId = Number(searchParams.get('classId'))
  const classId = classes.some((item) => item.id === requestedClassId)
    ? requestedClassId
    : classes[0]?.id || 0
  const overviewQuery = useQuery(preLessonQueryOptions(classId))
  const timelineQuery = useQuery(timelineQueryOptions(classId))
  const activeClass = classes.find((item) => item.id === classId)

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
          <span>PRE-LESSON WORKSPACE</span>
          <Typography.Title level={2}>课前备课工作台</Typography.Title>
          <Typography.Paragraph>
            让学情数据成为教学设计的输入，让每一步调整都有证据。
          </Typography.Paragraph>
        </div>
        <Select
          value={classId}
          aria-label="选择班级"
          options={classes.map((item) => ({ value: item.id, label: item.name }))}
          onChange={(value) => setSearchParams({ classId: String(value) })}
        />
      </header>
      {overviewQuery.isPending ? (
        <div className={styles.center}>
          <Spin />
        </div>
      ) : overviewQuery.isError || !overviewQuery.data ? (
        <Alert type="error" showIcon message="备课学情加载失败" />
      ) : (
        <PreLessonWorkspace
          key={classId}
          classId={classId}
          className={activeClass?.name || overviewQuery.data.className}
          overview={overviewQuery.data}
          timeline={timelineQuery.data}
        />
      )}
    </main>
  )
}
