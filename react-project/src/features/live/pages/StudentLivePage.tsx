import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Alert,
  App,
  Avatar,
  Button,
  Card,
  Empty,
  Input,
  Progress,
  Radio,
  Result,
  Segmented,
  Space,
  Spin,
  Statistic,
  Tag,
  Typography,
} from 'antd'
import { Link, useParams } from 'react-router'
import {
  getInteractionHistory,
  joinLiveSession,
  markConfusion,
  previewLiveSession,
  quickJoinLiveSession,
  unbindStudentDevice,
} from '@/features/live/api/liveApi'
import {
  publishLiveMessage,
  useLiveSocketLifecycle,
} from '@/features/live/hooks/useLiveSocketLifecycle'
import { normalizeLiveCode, remainingSeconds } from '@/features/live/model/live'
import type {
  ConfusionResult,
  InteractionHistoryItem,
  LiveSessionInfo,
} from '@/features/live/model/types'
import { useLiveStore } from '@/features/live/store/liveStore'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './StudentLivePage.module.css'

const reactionOptions = [
  { emoji: '👍', label: '听懂了' },
  { emoji: '👏', label: '很清楚' },
  { emoji: '🤔', label: '再讲讲' },
  { emoji: '⚡', label: '有启发' },
]

type HistoryFilter = 'ALL' | 'CORRECT' | 'REVIEW'
type ConfusionState = { pending: boolean; result?: ConfusionResult }

function useNow(enabled: boolean) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!enabled) return
    const timer = window.setInterval(() => setNow(Date.now()), 250)
    return () => window.clearInterval(timer)
  }, [enabled])
  return now
}

export function StudentLivePage() {
  const code = normalizeLiveCode(String(useParams().sessionCode || ''))
  if (code.length !== 6) {
    return (
      <Result status="404" title="课堂码无效" extra={<Link to="/live/join">重新输入课堂码</Link>} />
    )
  }
  return <StudentLiveSession key={code} code={code} />
}

function StudentLiveSession({ code }: { code: string }) {
  const { message, modal } = App.useApp()
  const role = useLiveStore((state) => state.role)
  const sessionInfo = useLiveStore((state) => state.sessionInfo)
  const connectionStatus = useLiveStore((state) => state.connectionStatus)
  const current = useLiveStore((state) => state.currentInteraction)
  const history = useLiveStore((state) => state.interactionHistory)
  const handRaised = useLiveStore((state) => state.handRaised)
  const teacherOnline = useLiveStore((state) => state.teacherOnline)
  const sessionEnded = useLiveStore((state) => state.sessionEnded)
  const startSession = useLiveStore((state) => state.startSession)
  const hydrateStudent = useLiveStore((state) => state.hydrateStudent)
  const rememberAnswer = useLiveStore((state) => state.rememberAnswer)
  const reset = useLiveStore((state) => state.reset)
  const [preview, setPreview] = useState<LiveSessionInfo | null>(null)
  const [identifying, setIdentifying] = useState(true)
  const [previewError, setPreviewError] = useState('')
  const [joining, setJoining] = useState(false)
  const [identity, setIdentity] = useState(() => ({
    studentId: localStorage.getItem('live_student_id') || '',
    studentName: localStorage.getItem('live_student_name') || '',
  }))
  const initialIdentity = useRef(identity)
  const [draftAnswers, setDraftAnswers] = useState<Record<number, string>>({})
  const [submittedAnswers, setSubmittedAnswers] = useState<Record<number, string>>({})
  const [questionText, setQuestionText] = useState('')
  const [historyFilter, setHistoryFilter] = useState<HistoryFilter>('ALL')
  const [historyExpanded, setHistoryExpanded] = useState(false)
  const [confusions, setConfusions] = useState<Record<number, ConfusionState>>({})
  const activeTimed = Boolean(current?.status === 'ACTIVE' && current.deadlineEpochMs)
  const now = useNow(activeTimed)

  useLiveSocketLifecycle()

  useEffect(() => {
    let cancelled = false

    async function finishJoin(data: LiveSessionInfo) {
      const nextHistory = await getInteractionHistory(data.sessionId, data.studentId, data.token)
      if (cancelled) return
      localStorage.removeItem('live_student_id')
      localStorage.removeItem('live_student_name')
      setIdentity({ studentId: data.studentId, studentName: data.studentName })
      startSession('student', data)
      hydrateStudent(nextHistory)
    }

    async function initialize() {
      try {
        const sessionPreview = await previewLiveSession(code)
        if (cancelled) return
        setPreview(sessionPreview)
        try {
          await finishJoin(await quickJoinLiveSession(code))
          return
        } catch {
          // 未绑定个人设备时继续尝试旧版暂存身份。
        }
        const rememberedId = initialIdentity.current.studentId.trim()
        if (rememberedId) {
          try {
            await finishJoin(
              await joinLiveSession({
                code,
                studentId: rememberedId,
                studentName: initialIdentity.current.studentName.trim() || undefined,
              }),
            )
          } catch {
            // 暂存身份不匹配时保留表单供学生修正。
          }
        }
      } catch (error) {
        if (!cancelled) setPreviewError(getApiErrorMessage(error, '课堂不存在或已经结束'))
      } finally {
        if (!cancelled) setIdentifying(false)
      }
    }

    void initialize()
    return () => {
      cancelled = true
      reset()
    }
  }, [code, hydrateStudent, reset, startSession])

  async function join() {
    const studentId = identity.studentId.trim()
    const studentName = identity.studentName.trim()
    if (!studentId) {
      message.warning('请输入学号')
      return
    }
    if (preview?.requiresStudentName && !studentName) {
      message.warning('班级尚无花名册，请输入姓名')
      return
    }
    setJoining(true)
    try {
      const data = await joinLiveSession({ code, studentId, studentName: studentName || undefined })
      const nextHistory = await getInteractionHistory(data.sessionId, data.studentId, data.token)
      localStorage.removeItem('live_student_id')
      localStorage.removeItem('live_student_name')
      setIdentity({ studentId: data.studentId, studentName: data.studentName })
      startSession('student', data)
      hydrateStudent(nextHistory)
    } catch (error) {
      message.error(getApiErrorMessage(error, '身份确认失败，请检查学号'))
    } finally {
      setJoining(false)
    }
  }

  function switchIdentity() {
    modal.confirm({
      title: '切换学生身份？',
      content: '这台设备保存的学生身份将被清除。',
      okText: '确认切换',
      async onOk() {
        await unbindStudentDevice()
        reset()
        localStorage.removeItem('live_student_id')
        localStorage.removeItem('live_student_name')
        setIdentity({ studentId: '', studentName: '' })
      },
    })
  }

  const currentHistory = history.find((item) => item.interactionId === current?.interactionId)
  const currentId = current?.interactionId || 0
  const answer = currentId
    ? draftAnswers[currentId] || submittedAnswers[currentId] || currentHistory?.myAnswer || ''
    : ''
  const remaining = remainingSeconds(current?.deadlineEpochMs || null, now)
  const canCommunicate = connectionStatus === 'connected' && !sessionEnded
  const canSubmit = Boolean(
    canCommunicate &&
    current?.status === 'ACTIVE' &&
    answer.trim() &&
    (remaining === null || remaining > 0),
  )
  const submitted = Boolean(currentId && (submittedAnswers[currentId] || currentHistory?.myAnswer))
  const completed = useMemo(() => history.filter((item) => item.status !== 'ACTIVE'), [history])
  const answeredCount = completed.filter((item) => item.myAnswer).length
  const correctCount = completed.filter((item) => item.myCorrect === true).length
  const filteredHistory = useMemo(() => {
    const items = completed.filter((item) => item.interactionId !== currentId)
    if (historyFilter === 'CORRECT') return items.filter((item) => item.myCorrect === true)
    if (historyFilter === 'REVIEW') return items.filter((item) => item.myCorrect !== true)
    return items
  }, [completed, currentId, historyFilter])
  const visibleHistory = historyExpanded ? filteredHistory : filteredHistory.slice(0, 3)

  function setCurrentAnswer(value: string) {
    if (!currentId) return
    setDraftAnswers((values) => ({ ...values, [currentId]: value }))
  }

  function submitAnswer() {
    if (!canSubmit || !current || !sessionInfo) return
    const cleanAnswer = answer.trim()
    const sent = publishLiveMessage(
      `/app/session/${sessionInfo.sessionId}/interaction/${current.interactionId}/respond`,
      {
        interactionId: current.interactionId,
        answer: cleanAnswer,
        studentId: sessionInfo.studentId,
        studentName: sessionInfo.studentName,
      },
    )
    if (!sent) {
      message.error('课堂连接尚未恢复，答案未提交')
      return
    }
    setSubmittedAnswers((values) => ({ ...values, [current.interactionId]: cleanAnswer }))
    rememberAnswer(current.interactionId, cleanAnswer)
    message.success('答案已提交，截止前仍可修改')
  }

  function askQuestion() {
    if (!sessionInfo || !questionText.trim()) return
    if (
      !publishLiveMessage(`/app/session/${sessionInfo.sessionId}/qa/ask`, {
        question: questionText.trim(),
      })
    ) {
      message.error('课堂连接尚未恢复，问题未发送')
      return
    }
    setQuestionText('')
    message.success('问题已匿名发送给老师')
  }

  function sendReaction(emoji: string) {
    if (!sessionInfo) return
    if (
      !publishLiveMessage(`/app/session/${sessionInfo.sessionId}/reaction`, {
        emoji,
        studentId: sessionInfo.studentId,
        studentName: sessionInfo.studentName,
        type: 'emoji',
      })
    )
      message.error('连接尚未恢复，反馈未发送')
  }

  function toggleHand() {
    if (!sessionInfo) return
    const action = handRaised ? 'lower' : 'raise'
    if (!publishLiveMessage(`/app/session/${sessionInfo.sessionId}/hand/${action}`, {})) {
      message.error('连接尚未恢复，请稍后再试')
    }
  }

  async function confused(interactionId: number) {
    if (!sessionInfo || confusions[interactionId]?.result) return
    setConfusions((state) => ({ ...state, [interactionId]: { pending: true } }))
    try {
      const result = await markConfusion(sessionInfo.sessionId, interactionId, sessionInfo.token)
      setConfusions((state) => ({ ...state, [interactionId]: { pending: false, result } }))
    } catch (error) {
      setConfusions((state) => ({ ...state, [interactionId]: { pending: false } }))
      message.error(getApiErrorMessage(error, '解析生成失败'))
    }
  }

  if (identifying)
    return (
      <div className={styles.center}>
        <Spin size="large" description="正在识别课堂和设备身份…" />
      </div>
    )
  if (previewError)
    return (
      <Result
        status="warning"
        title="无法进入课堂"
        subTitle={previewError}
        extra={
          <Link to="/live/join">
            <Button type="primary">重新输入课堂码</Button>
          </Link>
        }
      />
    )
  if (role !== 'student' || !sessionInfo) {
    return (
      <IdentityCard
        preview={preview}
        identity={identity}
        joining={joining}
        onChange={setIdentity}
        onJoin={() => void join()}
      />
    )
  }

  const currentCorrect =
    current?.type === 'CHOICE' &&
    current.correctKey &&
    (submittedAnswers[currentId] || currentHistory?.myAnswer)
      ? current.correctKey === (submittedAnswers[currentId] || currentHistory?.myAnswer)
      : currentHistory?.myCorrect

  return (
    <main className={styles.classroom}>
      <header className={styles.header}>
        <div>
          <span className={styles.brand}>EM</span>
          <div>
            <b>{sessionInfo.title}</b>
            <small>
              {sessionInfo.className} · {sessionInfo.teacherName}
            </small>
          </div>
        </div>
        <Space wrap>
          <Tag
            color={
              sessionEnded
                ? 'default'
                : connectionStatus === 'connected'
                  ? 'green'
                  : connectionStatus === 'connecting'
                    ? 'gold'
                    : 'red'
            }
          >
            {sessionEnded
              ? '课堂已结束'
              : connectionStatus === 'connected'
                ? '实时连接'
                : connectionStatus === 'connecting'
                  ? '正在连接'
                  : '连接中断'}
          </Tag>
          <Avatar>{sessionInfo.studentName.trim().slice(0, 1) || '同'}</Avatar>
          <Button type="text" onClick={switchIdentity}>
            切换身份
          </Button>
        </Space>
      </header>

      {connectionStatus !== 'connected' && !sessionEnded && (
        <Alert
          type="warning"
          showIcon
          title="正在重新连接课堂"
          description="恢复后题目会自动同步，不需要刷新页面。"
        />
      )}
      {!teacherOnline && !sessionEnded && (
        <Alert type="info" showIcon title="老师暂时离开，请稍等" />
      )}

      <div className={styles.content}>
        <section className={styles.main}>
          {current && current.status === 'ACTIVE' && !sessionEnded ? (
            <Card className={styles.interaction}>
              <div className={styles.interactionMeta}>
                <Space>
                  <Tag color="blue">
                    {current.type === 'CHOICE'
                      ? '选择题'
                      : current.type === 'OPEN'
                        ? '简答题'
                        : '随堂练习'}
                  </Tag>
                  {submitted && <Tag color="green">已提交，可修改</Tag>}
                </Space>
                {remaining !== null && (
                  <div className={remaining <= 30 ? styles.urgent : ''}>
                    <b>{remaining}</b> 秒
                  </div>
                )}
              </div>
              <Typography.Title level={2}>{current.title}</Typography.Title>
              {current.description && (
                <Typography.Paragraph>{current.description}</Typography.Paragraph>
              )}
              {current.options?.length ? (
                <Radio.Group
                  className={styles.choice}
                  value={answer}
                  onChange={(event) => setCurrentAnswer(String(event.target.value))}
                >
                  {current.options.map((option) => (
                    <Radio.Button value={option.key} key={option.key}>
                      <b>{option.key}</b> {option.text}
                    </Radio.Button>
                  ))}
                </Radio.Group>
              ) : (
                <Input.TextArea
                  rows={5}
                  value={answer}
                  onChange={(event) => setCurrentAnswer(event.target.value)}
                  placeholder="输入你的思路或答案…"
                />
              )}
              {remaining !== null && (
                <Progress
                  percent={
                    current.timeLimit
                      ? Math.max(
                          0,
                          Math.min(100, Math.round((remaining / current.timeLimit) * 100)),
                        )
                      : 100
                  }
                  showInfo={false}
                  status={remaining <= 30 ? 'exception' : 'active'}
                />
              )}
              <Space wrap>
                <Button size="large" type="primary" disabled={!canSubmit} onClick={submitAnswer}>
                  {submitted ? '更新答案' : '提交答案'}
                </Button>
                <Button
                  loading={confusions[current.interactionId]?.pending}
                  disabled={Boolean(confusions[current.interactionId]?.result)}
                  onClick={() => void confused(current.interactionId)}
                >
                  我不懂，生成解析
                </Button>
              </Space>
              {confusions[current.interactionId]?.result && (
                <Alert
                  type="info"
                  showIcon
                  title={`知识点：${confusions[current.interactionId]?.result?.knowledgePoint}`}
                  description={confusions[current.interactionId]?.result?.explanation}
                />
              )}
              {submitted && (
                <Alert
                  type={
                    currentCorrect === true
                      ? 'success'
                      : currentCorrect === false
                        ? 'warning'
                        : 'info'
                  }
                  showIcon
                  title={
                    currentCorrect === true
                      ? '回答正确'
                      : currentCorrect === false
                        ? '这道题需要再复习'
                        : '答案已提交'
                  }
                  description={
                    current.type === 'CHOICE' && current.correctKey
                      ? `参考答案：${current.correctKey}`
                      : '老师结束作答后可在记录中回顾。'
                  }
                />
              )}
            </Card>
          ) : (
            <Card className={styles.waiting}>
              <div className={styles.waitIcon}>
                {sessionEnded ? '✓' : connectionStatus !== 'connected' ? '↻' : '…'}
              </div>
              <Typography.Title level={2}>
                {sessionEnded
                  ? '本次课堂已结束'
                  : connectionStatus !== 'connected'
                    ? '正在重新连接课堂'
                    : teacherOnline
                      ? '等待老师发起下一项互动'
                      : '老师暂时离开，请稍等'}
              </Typography.Title>
              <Typography.Paragraph>
                {sessionEnded
                  ? '你可以在下方回顾本节课的作答记录。'
                  : '题目发布后会自动显示在这里，不需要手动刷新。'}
              </Typography.Paragraph>
            </Card>
          )}

          <Card title="本节课学习记录">
            <div className={styles.stats}>
              <Statistic title="已结束互动" value={completed.length} />
              <Statistic title="参与作答" value={answeredCount} />
              <Statistic title="回答正确" value={correctCount} />
              <Statistic
                title="参与率"
                value={completed.length ? Math.round((answeredCount / completed.length) * 100) : 0}
                suffix="%"
              />
            </div>
            <Segmented
              value={historyFilter}
              onChange={(value) => setHistoryFilter(value as HistoryFilter)}
              options={[
                { label: '全部', value: 'ALL' },
                { label: '答对', value: 'CORRECT' },
                { label: '待复习', value: 'REVIEW' },
              ]}
            />
            <div className={styles.history}>
              {visibleHistory.map((item) => (
                <HistoryItem
                  key={item.interactionId}
                  item={item}
                  confusion={confusions[item.interactionId]}
                  onConfused={() => void confused(item.interactionId)}
                />
              ))}
              {!visibleHistory.length && (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无匹配记录" />
              )}
            </div>
            {filteredHistory.length > 3 && (
              <Button type="link" onClick={() => setHistoryExpanded((value) => !value)}>
                {historyExpanded ? '收起记录' : `查看全部 ${filteredHistory.length} 条`}
              </Button>
            )}
          </Card>
        </section>

        <aside className={styles.tools}>
          <Card title="课堂沟通">
            <Input.TextArea
              rows={3}
              value={questionText}
              onChange={(event) => setQuestionText(event.target.value)}
              placeholder="匿名向老师提问…"
            />
            <Button
              block
              type="primary"
              disabled={!canCommunicate || !questionText.trim()}
              onClick={askQuestion}
            >
              发送匿名问题
            </Button>
            <Button
              block
              className={handRaised ? styles.handRaised : ''}
              disabled={!canCommunicate}
              onClick={toggleHand}
            >
              {handRaised ? '放下手 ✋' : '举手 ✋'}
            </Button>
          </Card>
          <Card title="即时反馈">
            <div className={styles.reactions}>
              {reactionOptions.map((reaction) => (
                <Button
                  key={reaction.emoji}
                  disabled={!canCommunicate}
                  onClick={() => sendReaction(reaction.emoji)}
                >
                  <span>{reaction.emoji}</span>
                  {reaction.label}
                </Button>
              ))}
            </div>
          </Card>
          <Card title="我的身份">
            <p>
              <b>{sessionInfo.studentName}</b>
            </p>
            <p>{sessionInfo.studentId}</p>
            <p>课堂码：{sessionInfo.sessionCode}</p>
          </Card>
        </aside>
      </div>
    </main>
  )
}

function IdentityCard({
  preview,
  identity,
  joining,
  onChange,
  onJoin,
}: {
  preview: LiveSessionInfo | null
  identity: { studentId: string; studentName: string }
  joining: boolean
  onChange: (value: { studentId: string; studentName: string }) => void
  onJoin: () => void
}) {
  return (
    <main className={styles.identityPage}>
      <Card className={styles.identityCard}>
        <Tag color="green">课堂进行中</Tag>
        <Typography.Title>{preview?.title || '实时课堂'}</Typography.Title>
        <Typography.Paragraph>
          {preview?.className} · {preview?.teacherName}
        </Typography.Paragraph>
        <label>
          学号
          <Input
            autoFocus
            value={identity.studentId}
            onChange={(event) => onChange({ ...identity, studentId: event.target.value })}
            onPressEnter={onJoin}
          />
        </label>
        {preview?.requiresStudentName && (
          <label>
            姓名
            <Input
              value={identity.studentName}
              onChange={(event) => onChange({ ...identity, studentName: event.target.value })}
              onPressEnter={onJoin}
            />
          </label>
        )}
        <Button size="large" type="primary" block loading={joining} onClick={onJoin}>
          确认身份并加入
        </Button>
        <Link to="/live/join">返回课堂码输入页</Link>
      </Card>
    </main>
  )
}

function HistoryItem({
  item,
  confusion,
  onConfused,
}: {
  item: InteractionHistoryItem
  confusion?: ConfusionState
  onConfused: () => void
}) {
  const label = !item.myAnswer
    ? '未作答'
    : item.myCorrect === true
      ? '回答正确'
      : item.myCorrect === false
        ? '需要复习'
        : '已提交'
  return (
    <article>
      <div>
        <Tag
          color={item.myCorrect === true ? 'green' : item.myCorrect === false ? 'red' : 'default'}
        >
          {label}
        </Tag>
        <b>{item.title}</b>
      </div>
      <p>我的答案：{item.myAnswer || '—'}</p>
      <Space wrap>
        <Button
          size="small"
          loading={confusion?.pending}
          disabled={Boolean(confusion?.result)}
          onClick={onConfused}
        >
          我不懂
        </Button>
        {confusion?.result && <Tag color="purple">{confusion.result.knowledgePoint}</Tag>}
      </Space>
      {confusion?.result && <Alert type="info" description={confusion.result.explanation} />}
    </article>
  )
}
