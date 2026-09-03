import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  App,
  Button,
  Card,
  Empty,
  Input,
  Modal,
  Progress,
  QRCode,
  Radio,
  Result,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'
import {
  createLiveSession,
  endLiveSession,
  extendInteraction,
  getActiveLiveSession,
  getConfusionStats,
  getInteractionDetail,
  getInteractionHistory,
  getInteractionStats,
  getLiveReport,
  getOnlineStudents,
  getQuestionBoard,
  getStudentProfile,
  sendQuestion,
} from '@/features/live/api/liveApi'
import {
  publishLiveMessage,
  useLiveSocketLifecycle,
} from '@/features/live/hooks/useLiveSocketLifecycle'
import {
  activeTeacherSessionInfo,
  formatLiveDuration,
  remainingSeconds,
  responsePercent,
  teacherSessionInfo,
} from '@/features/live/model/live'
import type {
  InteractionDetail,
  QAQuestion,
  QuestionBoardItem,
  StudentProfile,
} from '@/features/live/model/types'
import { useLiveStore } from '@/features/live/store/liveStore'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './TeacherLivePage.module.css'

type BoardFilter = 'ALL' | QuestionBoardItem['status']

const typeLabels: Record<string, string> = {
  CHOICE: '选择题',
  OPEN: '简答题',
  EXERCISE: '随堂练习',
}

function useNow(enabled: boolean) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!enabled) return
    const timer = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [enabled])
  return now
}

export function TeacherLivePage() {
  const classId = Number(useParams().classId)
  if (!Number.isInteger(classId) || classId <= 0) {
    return <Result status="404" title="班级 ID 无效" />
  }
  return <TeacherLiveSession key={classId} classId={classId} />
}

function TeacherLiveSession({ classId }: { classId: number }) {
  const navigate = useNavigate()
  const { message, modal } = App.useApp()
  const role = useLiveStore((state) => state.role)
  const sessionInfo = useLiveStore((state) => state.sessionInfo)
  const connectionStatus = useLiveStore((state) => state.connectionStatus)
  const board = useLiveStore((state) => state.questionBoard)
  const history = useLiveStore((state) => state.interactionHistory)
  const students = useLiveStore((state) => state.studentList)
  const studentCount = useLiveStore((state) => state.studentCount)
  const absentStudents = useLiveStore((state) => state.absentStudents)
  const absentCount = useLiveStore((state) => state.absentCount)
  const questions = useLiveStore((state) => state.topQuestions)
  const reactions = useLiveStore((state) => state.reactions)
  const handQueue = useLiveStore((state) => state.handQueue)
  const startSession = useLiveStore((state) => state.startSession)
  const hydrateTeacher = useLiveStore((state) => state.hydrateTeacher)
  const setQuestionBoard = useLiveStore((state) => state.setQuestionBoard)
  const applySocketEvent = useLiveStore((state) => state.applySocketEvent)
  const clearReactions = useLiveStore((state) => state.clearReactions)
  const reset = useLiveStore((state) => state.reset)
  const [initializing, setInitializing] = useState(true)
  const [initialError, setInitialError] = useState('')
  const [filter, setFilter] = useState<BoardFilter>('ALL')
  const [pendingQuestionId, setPendingQuestionId] = useState<number | null>(null)
  const [selectedInteractionId, setSelectedInteractionId] = useState<number | null>(null)
  const [answeringQuestion, setAnsweringQuestion] = useState<QAQuestion | null>(null)
  const [answerText, setAnswerText] = useState('')
  const [selectedStudent, setSelectedStudent] = useState<{ id: string; name: string } | null>(null)
  const [summary, setSummary] = useState<{
    title: string
    duration: string
    interactions: number
    online: number
    absent: number
    qa: number
  } | null>(null)
  const now = useNow(board.some((item) => item.status === 'ACTIVE'))

  useLiveSocketLifecycle()

  useEffect(() => {
    if (!Number.isInteger(classId) || classId <= 0) return
    let cancelled = false
    async function initialize() {
      try {
        const active = await getActiveLiveSession(classId)
        if (cancelled) return
        const info =
          active.hasActive && active.sessionId && active.sessionCode && active.title
            ? activeTeacherSessionInfo({
                sessionId: active.sessionId,
                sessionCode: active.sessionCode,
                title: active.title,
                startedAt: active.startedAt,
              })
            : teacherSessionInfo(await createLiveSession({ classId }))
        if (cancelled) return
        const [nextHistory, nextBoard, presence, stats] = await Promise.all([
          getInteractionHistory(info.sessionId),
          getQuestionBoard(info.sessionId),
          getOnlineStudents(info.sessionId),
          getInteractionStats(info.sessionId).catch(() => null),
        ])
        if (cancelled) return
        startSession('teacher', info)
        hydrateTeacher({ history: nextHistory, board: nextBoard, presence, stats })
      } catch (error) {
        if (!cancelled) setInitialError(getApiErrorMessage(error, '课堂初始化失败'))
      } finally {
        if (!cancelled) setInitializing(false)
      }
    }

    void initialize()
    return () => {
      cancelled = true
      reset()
    }
  }, [classId, hydrateTeacher, reset, startSession])

  const sessionId = sessionInfo?.sessionId || 0
  const confusionQuery = useQuery({
    queryKey: ['live', sessionId, 'confusions'],
    queryFn: () => getConfusionStats(sessionId),
    enabled: sessionId > 0,
    refetchInterval: 30_000,
  })
  const detailQuery = useQuery({
    queryKey: ['live', sessionId, 'detail', selectedInteractionId],
    queryFn: () => getInteractionDetail(sessionId, selectedInteractionId!),
    enabled: sessionId > 0 && selectedInteractionId !== null,
  })
  const profileQuery = useQuery({
    queryKey: ['live', classId, 'profile', selectedStudent?.id],
    queryFn: () => getStudentProfile(selectedStudent!.id, classId),
    enabled: classId > 0 && selectedStudent !== null,
  })
  const visibleBoard = useMemo(
    () => (filter === 'ALL' ? board : board.filter((item) => item.status === filter)),
    [board, filter],
  )
  const hasActiveQuestion = board.some((item) => item.status === 'ACTIVE')
  const liveUrl = sessionInfo ? `${window.location.origin}/live/${sessionInfo.sessionCode}` : ''

  async function refreshBoard() {
    if (!sessionId) return
    try {
      setQuestionBoard(await getQuestionBoard(sessionId))
      message.success('题目看板已刷新')
    } catch (error) {
      message.error(getApiErrorMessage(error, '题目看板刷新失败'))
    }
  }

  async function activateQuestion(questionId: number) {
    setPendingQuestionId(questionId)
    try {
      const push = await sendQuestion(sessionId, questionId)
      applySocketEvent({ type: 'interaction', payload: push })
      message.success('题目已发送，学生端开始作答')
    } catch (error) {
      message.error(getApiErrorMessage(error, '发送失败，请确认当前没有其他题目正在作答'))
      await refreshBoard()
    } finally {
      setPendingQuestionId(null)
    }
  }

  async function extend(interactionId: number, seconds: number) {
    try {
      const timing = await extendInteraction(sessionId, interactionId, seconds)
      applySocketEvent({ type: 'timing', payload: timing })
      message.success(`已延时${formatLiveDuration(seconds)}`)
    } catch (error) {
      message.error(getApiErrorMessage(error, '延时失败，题目可能已经结束'))
    }
  }

  function closeInteraction(interactionId: number) {
    const sent = publishLiveMessage(
      `/app/session/${sessionId}/interaction/${interactionId}/close`,
      {},
    )
    if (sent) message.success('已请求结束作答')
    else message.error('课堂连接尚未恢复')
  }

  function answerQuestion() {
    if (!answeringQuestion || !answerText.trim()) return
    const sent = publishLiveMessage(`/app/session/${sessionId}/qa/${answeringQuestion.id}/answer`, {
      answerText: answerText.trim(),
    })
    if (!sent) {
      message.error('课堂连接尚未恢复')
      return
    }
    setAnsweringQuestion(null)
    setAnswerText('')
  }

  function handAction(action: 'call' | 'dismiss', studentId: string) {
    if (!publishLiveMessage(`/app/session/${sessionId}/hand/${action}`, { studentId })) {
      message.error('课堂连接尚未恢复')
    }
  }

  async function exportReport() {
    if (!sessionInfo) return
    try {
      const startedAt = sessionInfo.startedAt
        ? new Date(sessionInfo.startedAt).getTime()
        : Date.now()
      const duration = formatLiveDuration(Math.max(0, Math.round((Date.now() - startedAt) / 1_000)))
      const report = await getLiveReport(sessionId, {
        title: sessionInfo.title,
        duration,
        online: studentCount,
        absent: absentCount,
        qa: questions.length,
      })
      const url = URL.createObjectURL(new Blob([report.html], { type: 'text/html;charset=utf-8' }))
      const link = document.createElement('a')
      link.href = url
      link.download = `${sessionInfo.title || '课堂报告'}.html`
      link.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      message.error(getApiErrorMessage(error, '报告导出失败'))
    }
  }

  function requestEndSession() {
    modal.confirm({
      title: '确定结束本次课堂吗？',
      content: '学生端会收到结课通知，实时连接随后关闭。',
      okText: '结束课堂',
      okButtonProps: { danger: true },
      async onOk() {
        if (!sessionInfo) return
        const startedAt = sessionInfo.startedAt
          ? new Date(sessionInfo.startedAt).getTime()
          : Date.now()
        const nextSummary = {
          title: sessionInfo.title,
          duration: formatLiveDuration(Math.max(0, Math.round((Date.now() - startedAt) / 1_000))),
          interactions: history.length,
          online: studentCount,
          absent: absentCount,
          qa: questions.length,
        }
        await endLiveSession(sessionId)
        setSummary(nextSummary)
        reset()
      },
    })
  }

  if (initializing)
    return (
      <div className={styles.center}>
        <Spin size="large" description="正在恢复实时课堂…" />
      </div>
    )
  if (initialError)
    return (
      <Result
        status="error"
        title="课堂启动失败"
        subTitle={initialError}
        extra={
          <Button onClick={() => void navigate(`/teacher/classes/${classId}`)}>返回班级</Button>
        }
      />
    )
  if (role !== 'teacher' || !sessionInfo)
    return (
      <div className={styles.center}>
        <Spin />
      </div>
    )

  return (
    <main className={styles.page}>
      <header className={styles.header}>
        <div>
          <Button type="text" onClick={() => void navigate(`/teacher/classes/${classId}`)}>
            ← 返回班级
          </Button>
          <Typography.Title level={3}>{sessionInfo.title || '课堂实时互动'}</Typography.Title>
          <Tag
            color={
              connectionStatus === 'connected'
                ? 'green'
                : connectionStatus === 'connecting'
                  ? 'gold'
                  : 'red'
            }
          >
            {connectionStatus === 'connected'
              ? '实时连接正常'
              : connectionStatus === 'connecting'
                ? '正在连接'
                : '连接中断，自动重试中'}
          </Tag>
        </div>
        <Space wrap>
          <span className={styles.code}>
            加入码 <b>{sessionInfo.sessionCode}</b>
          </span>
          <Button
            onClick={() =>
              void navigator.clipboard
                .writeText(sessionInfo.sessionCode)
                .then(() => message.success('课堂码已复制'))
            }
          >
            复制课堂码
          </Button>
          <ModalButton title="学生扫码加入" button="二维码">
            <QRCode value={liveUrl} size={230} />
            <p>首次加入需要确认学号，之后同一设备可快速进入。</p>
          </ModalButton>
        </Space>
        <Space wrap>
          <Tag color="blue">{studentCount} 人在线</Tag>
          <Button onClick={() => void exportReport()}>导出报告</Button>
          <Button danger type="primary" onClick={requestEndSession}>
            结束课堂
          </Button>
        </Space>
      </header>

      {connectionStatus !== 'connected' && (
        <Alert
          type="warning"
          showIcon
          title="实时连接暂时中断"
          description="STOMP 客户端会每 5 秒自动重连；恢复后会重新订阅当前课堂频道。"
        />
      )}

      <div className={styles.layout}>
        <aside className={styles.sidebar}>
          <Card
            title={`在线学生（${studentCount}）`}
            extra={
              <Button
                size="small"
                disabled={!students.length}
                onClick={() => {
                  const selected = students[Math.floor(Math.random() * students.length)]
                  if (selected) message.success(`随机点名：${selected.studentName}`)
                }}
              >
                随机点名
              </Button>
            }
          >
            <div className={styles.people}>
              {students.map((student) => (
                <button
                  key={student.studentId}
                  onClick={() =>
                    setSelectedStudent({ id: student.studentId, name: student.studentName })
                  }
                >
                  <i />
                  {student.studentName}
                  <small>{student.studentId}</small>
                </button>
              ))}
              {!students.length && (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无学生加入" />
              )}
            </div>
          </Card>
          {(handQueue.waiting.length > 0 || handQueue.called.length > 0) && (
            <Card title={`举手队列（${handQueue.waiting.length}）`}>
              <div className={styles.hands}>
                {handQueue.waiting.map((entry, index) => (
                  <div key={entry.studentId}>
                    <span>
                      {index + 1}. {entry.studentName}
                    </span>
                    <Space.Compact>
                      <Button
                        size="small"
                        type="primary"
                        onClick={() => handAction('call', entry.studentId)}
                      >
                        点名
                      </Button>
                      <Button
                        size="small"
                        danger
                        onClick={() => handAction('dismiss', entry.studentId)}
                      >
                        移除
                      </Button>
                    </Space.Compact>
                  </div>
                ))}
                {handQueue.called.map((entry) => (
                  <div key={entry.studentId}>
                    <Tag color="green">已点名</Tag>
                    <span>{entry.studentName}</span>
                    <Button
                      size="small"
                      danger
                      onClick={() => handAction('dismiss', entry.studentId)}
                    >
                      结束
                    </Button>
                  </div>
                ))}
              </div>
            </Card>
          )}
          {absentStudents.length > 0 && (
            <Card title={`未加入（${absentCount}）`}>
              <div className={styles.absent}>
                {absentStudents.map((student) => (
                  <span key={student.studentId}>
                    {student.studentName} · {student.studentId}
                  </span>
                ))}
              </div>
            </Card>
          )}
        </aside>

        <section className={styles.board}>
          <Card
            title="课堂题目"
            extra={
              <Space wrap>
                <Radio.Group
                  size="small"
                  value={filter}
                  onChange={(event) => setFilter(event.target.value as BoardFilter)}
                  options={[
                    { label: `全部 ${board.length}`, value: 'ALL' },
                    {
                      label: `未发送 ${board.filter((item) => item.status === 'UNSENT').length}`,
                      value: 'UNSENT',
                    },
                    {
                      label: `作答中 ${board.filter((item) => item.status === 'ACTIVE').length}`,
                      value: 'ACTIVE',
                    },
                    {
                      label: `已结束 ${board.filter((item) => item.status === 'CLOSED').length}`,
                      value: 'CLOSED',
                    },
                  ]}
                />
                <Button size="small" onClick={() => void refreshBoard()}>
                  刷新
                </Button>
              </Space>
            }
          >
            <div className={styles.questionList}>
              {visibleBoard.map((item) => (
                <QuestionCard
                  key={item.questionId}
                  item={item}
                  now={now}
                  hasActive={hasActiveQuestion}
                  pending={pendingQuestionId === item.questionId}
                  expanded={selectedInteractionId === item.interactionId}
                  detail={
                    selectedInteractionId === item.interactionId ? detailQuery.data : undefined
                  }
                  detailLoading={detailQuery.isPending}
                  onSend={() => void activateQuestion(item.questionId)}
                  onExtend={(seconds) =>
                    item.interactionId && void extend(item.interactionId, seconds)
                  }
                  onClose={() => item.interactionId && closeInteraction(item.interactionId)}
                  onToggleDetail={() =>
                    setSelectedInteractionId((current) =>
                      current === item.interactionId ? null : item.interactionId,
                    )
                  }
                />
              ))}
              {!visibleBoard.length && <Empty description="当前筛选下暂无题目" />}
            </div>
          </Card>
          <Card title={`学生标记“不懂”（${confusionQuery.data?.total || 0} 次）`}>
            <Space wrap>
              {(confusionQuery.data?.stats || []).map((item) => (
                <Tag color="purple" key={item.name}>
                  {item.name} · {item.count}
                </Tag>
              ))}
            </Space>
            {!confusionQuery.data?.stats.length && (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无不懂标记" />
            )}
          </Card>
          {reactions.length > 0 && (
            <Card
              title="即时反馈"
              extra={
                <Button size="small" onClick={clearReactions}>
                  清除
                </Button>
              }
            >
              <Space wrap>
                {reactions.map((reaction, index) => (
                  <Tag key={`${reaction.studentId}-${reaction.timestamp}-${index}`}>
                    {reaction.emoji} {reaction.studentName}
                  </Tag>
                ))}
              </Space>
            </Card>
          )}
        </section>

        <aside className={styles.qa}>
          <Card title="匿名提问">
            <div className={styles.qaList}>
              {questions.map((question) => (
                <article key={question.id}>
                  <Space>
                    <b>{question.question}</b>
                    {question.similarCount > 0 && <Tag>×{question.similarCount + 1}</Tag>}
                  </Space>
                  {question.answered ? (
                    <p>{question.answerText}</p>
                  ) : (
                    <Button
                      size="small"
                      type="primary"
                      onClick={() => {
                        setAnsweringQuestion(question)
                        setAnswerText('')
                      }}
                    >
                      回答
                    </Button>
                  )}
                </article>
              ))}
              {!questions.length && (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无提问" />
              )}
            </div>
          </Card>
        </aside>
      </div>

      <Modal
        title="回答匿名提问"
        open={Boolean(answeringQuestion)}
        onCancel={() => setAnsweringQuestion(null)}
        onOk={answerQuestion}
        okButtonProps={{ disabled: !answerText.trim() }}
      >
        <Typography.Paragraph>{answeringQuestion?.question}</Typography.Paragraph>
        <Input.TextArea
          rows={4}
          value={answerText}
          onChange={(event) => setAnswerText(event.target.value)}
        />
      </Modal>
      <Modal
        title={`学生画像 · ${selectedStudent?.name || ''}`}
        open={Boolean(selectedStudent)}
        onCancel={() => setSelectedStudent(null)}
        footer={null}
      >
        {profileQuery.isPending ? (
          <div className={styles.modalCenter}>
            <Spin />
          </div>
        ) : profileQuery.data ? (
          <StudentProfileView profile={profileQuery.data} />
        ) : (
          <Empty description="暂无画像" />
        )}
      </Modal>
      <Modal
        title="课程总结"
        open={Boolean(summary)}
        closable={false}
        maskClosable={false}
        footer={
          <Button type="primary" onClick={() => void navigate(`/teacher/classes/${classId}`)}>
            返回班级
          </Button>
        }
      >
        {summary && (
          <div className={styles.summary}>
            <Typography.Title level={4}>{summary.title}</Typography.Title>
            <p>课堂时长：{summary.duration}</p>
            <p>互动题目：{summary.interactions} 道</p>
            <p>
              在线 / 未加入：{summary.online} / {summary.absent} 人
            </p>
            <p>匿名提问：{summary.qa} 条</p>
          </div>
        )}
      </Modal>
    </main>
  )
}

function ModalButton({
  title,
  button,
  children,
}: {
  title: string
  button: string
  children: React.ReactNode
}) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <Button onClick={() => setOpen(true)}>{button}</Button>
      <Modal title={title} open={open} onCancel={() => setOpen(false)} footer={null}>
        <div className={styles.qr}>{children}</div>
      </Modal>
    </>
  )
}

function StudentProfileView({ profile }: { profile: StudentProfile }) {
  return (
    <div className={styles.profile}>
      <p>
        参与课堂：<b>{profile.totalSessions}</b> 次
      </p>
      <p>
        总互动：<b>{profile.totalInteractions}</b> 题
      </p>
      <p>
        已作答：<b>{profile.totalAnswers}</b> 题（{profile.participationRate}%）
      </p>
      <p>
        正确率：<b>{profile.correctRate}%</b>（{profile.correctAnswers}/{profile.totalAnswers}）
      </p>
    </div>
  )
}

function QuestionCard({
  item,
  now,
  hasActive,
  pending,
  expanded,
  detail,
  detailLoading,
  onSend,
  onExtend,
  onClose,
  onToggleDetail,
}: {
  item: QuestionBoardItem
  now: number
  hasActive: boolean
  pending: boolean
  expanded: boolean
  detail?: InteractionDetail
  detailLoading: boolean
  onSend: () => void
  onExtend: (seconds: number) => void
  onClose: () => void
  onToggleDetail: () => void
}) {
  const remaining = remainingSeconds(item.deadlineEpochMs, now)
  return (
    <article className={`${styles.question} ${styles[item.status.toLowerCase()]}`}>
      <div className={styles.questionHead}>
        <Space wrap>
          <Tag
            color={item.type === 'OPEN' ? 'green' : item.type === 'EXERCISE' ? 'orange' : 'blue'}
          >
            {typeLabels[item.type]}
          </Tag>
          {item.knowledgePoint && <Tag>{item.knowledgePoint}</Tag>}
          <Tag
            color={
              item.status === 'ACTIVE' ? 'red' : item.status === 'CLOSED' ? 'green' : 'default'
            }
          >
            {item.status === 'ACTIVE' ? '作答中' : item.status === 'CLOSED' ? '已结束' : '未发送'}
          </Tag>
        </Space>
        <b>
          {item.status === 'ACTIVE'
            ? `剩余 ${formatLiveDuration(remaining)}`
            : item.timeLimit
              ? `计划 ${formatLiveDuration(item.timeLimit)}`
              : '不限时'}
        </b>
      </div>
      <Typography.Title level={4}>{item.title}</Typography.Title>
      {item.description && <Typography.Paragraph>{item.description}</Typography.Paragraph>}
      {item.options?.length ? (
        <div className={styles.options}>
          {item.options.map((option) => (
            <div className={option.key === item.correctKey ? styles.correct : ''} key={option.key}>
              <b>{option.key}</b>
              <span>{option.text}</span>
            </div>
          ))}
        </div>
      ) : (
        item.correctKey && <Alert type="info" title={`参考答案：${item.correctKey}`} />
      )}
      {item.status !== 'UNSENT' && (
        <div className={styles.response}>
          <span>
            <b>{item.respondedCount}</b> / {item.totalStudents} 人已作答
          </span>
          <Progress percent={responsePercent(item)} showInfo={false} />
          {item.correctRate !== null && <Tag color="green">正确率 {item.correctRate}%</Tag>}
        </div>
      )}
      {item.status === 'UNSENT' ? (
        <Button type="primary" disabled={hasActive} loading={pending} onClick={onSend}>
          {hasActive ? '当前题结束后可发送' : '发送到课堂'}
        </Button>
      ) : (
        <Space wrap>
          {item.status === 'ACTIVE' && (
            <>
              <Button onClick={() => onExtend(30)}>+30秒</Button>
              <Button onClick={() => onExtend(60)}>+1分钟</Button>
              <Button danger onClick={onClose}>
                提前结束
              </Button>
            </>
          )}
          <Button type="link" onClick={onToggleDetail}>
            {expanded ? '收起明细' : '查看作答明细'}
          </Button>
        </Space>
      )}
      {expanded && (
        <div className={styles.detail}>
          {detailLoading ? (
            <Spin />
          ) : detail ? (
            <>
              {detail.responses.map((response) => (
                <div key={response.studentId}>
                  <span>
                    {response.studentName}（{response.studentId}）
                  </span>
                  <Tag
                    color={
                      response.isCorrect === true
                        ? 'green'
                        : response.isCorrect === false
                          ? 'red'
                          : 'default'
                    }
                  >
                    {response.answer || '未作答'}
                  </Tag>
                </div>
              ))}
              {detail.unrespondedStudents.length > 0 && (
                <p>未作答：{detail.unrespondedStudents.join('、')}</p>
              )}
            </>
          ) : (
            <Empty description="暂无作答明细" />
          )}
        </div>
      )}
    </article>
  )
}
