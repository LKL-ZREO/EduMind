import { useEffect, useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Input,
  Modal,
  Progress,
  Select,
  Space,
  Spin,
  Statistic,
  Tag,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import 'katex/dist/katex.min.css'
import {
  gradingResultQueryOptions,
  publicClassesQueryOptions,
  publicTasksQueryOptions,
} from '@/features/homework/api/homeworkQueries'
import { useStudentHomeworkMutations } from '@/features/homework/hooks/useHomeworkMutations'
import {
  countdownLabel,
  parseHomeworkFileName,
  renderAssignmentHtml,
  validateHomeworkFileName,
} from '@/features/homework/model/submission'
import type { SubmitResponseData } from '@/features/homework/model/types'
import { getApiErrorData, getApiErrorMessage } from '@/shared/api/errors'
import type { ApiResponse } from '@/shared/api/types'
import styles from './StudentSubmitPage.module.css'

const MAX_SIZE = 20 * 1024 * 1024
const ACCEPT = '.pdf,.doc,.docx,.txt,.jpg,.jpeg,.png,.zip,.rar'

export function StudentSubmitPage() {
  const classesQuery = useQuery(publicClassesQueryOptions())
  const [selectedClassId, setSelectedClassId] = useState<number | null>(null)
  const tasksQuery = useQuery(publicTasksQueryOptions(selectedClassId || 0))
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null)
  const [now, setNow] = useState<number | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [warnings, setWarnings] = useState<string[]>([])
  const [serverConfirmation, setServerConfirmation] = useState(false)
  const [progress, setProgress] = useState(0)
  const [error, setError] = useState('')
  const [submissionId, setSubmissionId] = useState<number | null>(null)
  const [submitInfo, setSubmitInfo] = useState<SubmitResponseData | null>(null)
  const [bindIdentity, setBindIdentity] = useState<{
    studentId: string
    studentName: string
  } | null>(null)
  const [qqNumber, setQqNumber] = useState('')
  const mutations = useStudentHomeworkMutations()

  const selectedClass =
    classesQuery.data?.find((classItem) => classItem.id === selectedClassId) || null
  const selectedTask = tasksQuery.data?.find((task) => task.id === selectedTaskId) || null
  const renderedDescription = useMemo(
    () => renderAssignmentHtml(selectedTask?.description),
    [selectedTask?.description],
  )
  const countdown = now === null ? '' : countdownLabel(selectedTask?.deadline, now)
  const gradingQuery = useQuery({
    ...gradingResultQueryOptions(submissionId || 0),
    enabled: submissionId !== null,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'COMPLETED' || status === 'FAILED' ? false : 2_000
    },
  })
  const grading = gradingQuery.data

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [])

  function selectClass(classId: number | null) {
    setSelectedClassId(classId)
    setSelectedTaskId(null)
    setWarnings([])
    setServerConfirmation(false)
    setSubmitInfo(null)
    setSubmissionId(null)
  }

  function selectTask(taskId: number | null) {
    setSelectedTaskId(taskId)
    const nextTask = tasksQuery.data?.find((task) => task.id === taskId) || null
    setWarnings(file ? validateHomeworkFileName(file.name, selectedClass, nextTask) : [])
    setServerConfirmation(false)
    setSubmitInfo(null)
    setSubmissionId(null)
    setNow(Date.now())
  }

  function acceptFile(nextFile: File) {
    setError('')
    setSubmissionId(null)
    setSubmitInfo(null)
    setServerConfirmation(false)
    if (nextFile.size > MAX_SIZE) {
      setFile(null)
      setWarnings([])
      setError('文件超过 20MB 限制，请压缩后重试')
      return
    }
    setFile(nextFile)
    setWarnings(validateHomeworkFileName(nextFile.name, selectedClass, selectedTask))
  }

  function recoverableResult(result: ApiResponse<SubmitResponseData> | undefined) {
    if (!result) return false
    if (result.code === 428 && result.data?.needBind) {
      const parsed = file ? parseHomeworkFileName(file.name) : null
      setBindIdentity({
        studentId: result.data.studentId || parsed?.studentId || '',
        studentName: result.data.studentName || parsed?.studentName || '',
      })
      setProgress(0)
      return true
    }
    if (result.code === 300) {
      const nextWarnings: string[] = []
      const mismatch = result.data?.warnings
      if (mismatch?.classMismatch) {
        nextWarnings.push(
          `班级不匹配：文件名写的是「${mismatch.classMismatch.fileNameValue}」，你选的是「${mismatch.classMismatch.selectedValue}」`,
        )
      }
      if (mismatch?.taskMismatch) {
        nextWarnings.push(
          `作业不匹配：文件名写的是「${mismatch.taskMismatch.fileNameValue}」，你选的是「${mismatch.taskMismatch.selectedValue}」`,
        )
      }
      setWarnings(nextWarnings)
      setServerConfirmation(true)
      setProgress(0)
      return true
    }
    if (result.code === 429) {
      setError(result.message || '请勿重复提交，请稍后再试')
      setProgress(0)
      return true
    }
    return false
  }

  async function performSubmit(confirm: boolean) {
    if (!file) return setError('请先选择文件')
    if (!selectedClassId || !selectedTaskId) return setError('请先选择班级和作业')
    if (warnings.length && !confirm) return
    setError('')
    setProgress(0)
    try {
      const result = await mutations.submit.mutateAsync({
        file,
        classId: selectedClassId,
        taskId: selectedTaskId,
        confirm,
        onProgress: setProgress,
      })
      if (recoverableResult(result)) return
      setProgress(100)
      setSubmitInfo(result.data)
      setSubmissionId(result.data?.submissionId || null)
      setFile(null)
      setWarnings([])
      setServerConfirmation(false)
    } catch (submitError: unknown) {
      const result = getApiErrorData<ApiResponse<SubmitResponseData>>(submitError)
      if (recoverableResult(result)) return
      setProgress(0)
      setError(getApiErrorMessage(submitError, '上传失败，请检查网络后重试'))
    }
  }

  async function bindQqAndRetry() {
    if (!bindIdentity) return
    if (!/^\d{5,11}$/.test(qqNumber)) {
      setError('QQ 号应为 5–11 位数字')
      return
    }
    try {
      await mutations.bindQq.mutateAsync({ ...bindIdentity, qqNumber })
      setBindIdentity(null)
      setQqNumber('')
      await performSubmit(true)
    } catch (bindError: unknown) {
      setError(getApiErrorMessage(bindError, '绑定 QQ 失败'))
    }
  }

  const gradingPending = grading?.status === 'PENDING' || grading?.status === 'PROCESSING'
  const remainingAttempts = submitInfo?.remainingAttempts ?? 3

  return (
    <main className={styles.page}>
      <nav className={styles.nav}>
        <Link to="/live/join">加入课堂</Link>
        <Link to="/login">教师入口</Link>
      </nav>

      <header className={styles.hero}>
        <Typography.Text className={styles.eyebrow}>SUBMIT DESK</Typography.Text>
        <Typography.Title>学生提交台</Typography.Title>
        <Typography.Paragraph>
          选择班级和作业，按命名规范上传文件即可提交，无需注册。
        </Typography.Paragraph>
      </header>

      <Card className={styles.selector}>
        <div className={styles.selectGrid}>
          <label>
            <Typography.Text strong>班级</Typography.Text>
            <Select<number>
              allowClear
              showSearch
              value={selectedClassId ?? undefined}
              loading={classesQuery.isPending}
              placeholder="请选择班级"
              optionFilterProp="label"
              options={(classesQuery.data || []).map((item) => ({
                label: item.name,
                value: item.id,
              }))}
              onChange={(value) => selectClass(value ?? null)}
            />
          </label>
          <label>
            <Typography.Text strong>作业</Typography.Text>
            <Select<number>
              allowClear
              value={selectedTaskId ?? undefined}
              loading={tasksQuery.isFetching}
              disabled={!selectedClassId}
              placeholder="请选择作业"
              options={(tasksQuery.data || []).map((item) => ({
                label: item.taskName,
                value: item.id,
              }))}
              onChange={(value) => selectTask(value ?? null)}
            />
          </label>
        </div>
        {selectedTask?.description && (
          <section className={styles.description}>
            <strong>作业要求</strong>
            <div dangerouslySetInnerHTML={{ __html: renderedDescription }} />
          </section>
        )}
        {countdown && (
          <Alert
            showIcon
            type={countdown === '已截止' ? 'error' : 'info'}
            title={countdown}
            description={
              countdown === '已截止' && selectedTask?.allowLate
                ? `该作业允许迟交，每天扣 ${selectedTask.latePenalty} 分`
                : undefined
            }
          />
        )}
      </Card>

      <section
        className={`${styles.dropZone} ${file ? styles.hasFile : ''}`}
        onDragOver={(event) => event.preventDefault()}
        onDrop={(event) => {
          event.preventDefault()
          const dropped = event.dataTransfer.files[0]
          if (dropped) acceptFile(dropped)
        }}
      >
        {mutations.submit.isPending ? (
          <div className={styles.uploading}>
            <Spin size="large" />
            <strong>正在上传作业……</strong>
            <Progress percent={progress} />
          </div>
        ) : file ? (
          <div className={styles.fileArea}>
            <div className={styles.fileBadge}>
              {file.name.split('.').pop()?.toUpperCase() || 'FILE'}
            </div>
            <div className={styles.fileInfo}>
              <strong>{file.name}</strong>
              <span>{(file.size / 1024 / 1024).toFixed(2)} MB</span>
            </div>
            <Space wrap>
              <Button
                onClick={() => {
                  setFile(null)
                  setWarnings([])
                  setServerConfirmation(false)
                }}
              >
                移除
              </Button>
              <Button
                type="primary"
                disabled={remainingAttempts <= 0 || warnings.length > 0}
                onClick={() => void performSubmit(false)}
              >
                {remainingAttempts <= 0 ? '已达提交上限' : '提交作业'}
              </Button>
            </Space>
          </div>
        ) : (
          <div className={styles.dropHint}>
            <div className={styles.uploadMark}>UPLOAD</div>
            <Typography.Title level={3}>将作业文件拖到这里</Typography.Title>
            <span>或</span>
            <label className={styles.fileButton}>
              选择文件
              <input
                hidden
                type="file"
                accept={ACCEPT}
                onChange={(event) => {
                  const selected = event.target.files?.[0]
                  if (selected) acceptFile(selected)
                  event.target.value = ''
                }}
              />
            </label>
            <small>支持 PDF、Word、TXT、图片、压缩包，最大 20MB</small>
          </div>
        )}
        {file && warnings.length > 0 && !mutations.submit.isPending && (
          <Alert
            showIcon
            className={styles.warning}
            type="warning"
            title="文件名与选择项不匹配"
            description={
              <Space direction="vertical">
                {warnings.map((warning) => (
                  <span key={warning}>{warning}</span>
                ))}
                <Button danger onClick={() => void performSubmit(true)}>
                  {serverConfirmation ? '确认忽略后端校验并提交' : '确认提交'}
                </Button>
              </Space>
            }
          />
        )}
      </section>

      {submitInfo && (
        <Alert
          showIcon
          type="success"
          title="作业提交成功"
          description={`学号 ${submitInfo.studentId || '—'}，本次为第 ${submitInfo.submitCount || 1} 次提交，剩余 ${submitInfo.remainingAttempts ?? 0} 次`}
        />
      )}

      {submissionId && (
        <Card className={styles.gradingCard}>
          {gradingQuery.isPending || gradingPending ? (
            <div className={styles.gradingPending}>
              <Spin size="large" />
              <Typography.Title level={3}>
                {grading?.status === 'PROCESSING' ? 'AI 正在批改……' : '批改任务排队中……'}
              </Typography.Title>
              <span>提交编号：{submissionId} · 每 2 秒自动刷新</span>
            </div>
          ) : grading?.status === 'COMPLETED' ? (
            <div className={styles.gradingDone}>
              <Statistic
                title="批改完成"
                value={grading.totalScore ?? 0}
                suffix="分"
                valueStyle={{ color: (grading.totalScore || 0) >= 60 ? '#1677ff' : '#cf1322' }}
              />
              {grading.finalScore != null && grading.finalScore !== grading.totalScore && (
                <Tag color="orange">迟交扣分后：{grading.finalScore} 分</Tag>
              )}
              {grading.overallComment && (
                <p>
                  <strong>综合评语：</strong>
                  {grading.overallComment}
                </p>
              )}
              {grading.strengths?.length ? (
                <p>
                  <strong>优点：</strong>
                  {grading.strengths.join('、')}
                </p>
              ) : null}
              {grading.weaknesses?.length ? (
                <p>
                  <strong>不足：</strong>
                  {grading.weaknesses.join('、')}
                </p>
              ) : null}
              {grading.suggestions && (
                <p>
                  <strong>建议：</strong>
                  {grading.suggestions}
                </p>
              )}
            </div>
          ) : (
            <Alert
              showIcon
              type="error"
              title="批改失败"
              description={
                grading?.errorMessage || getApiErrorMessage(gradingQuery.error, '请联系教师')
              }
            />
          )}
        </Card>
      )}

      {error && <Alert showIcon closable type="error" title={error} onClose={() => setError('')} />}

      <Card className={styles.usage} title="文件名命名规范">
        <p>请按以下格式命名文件，用于识别身份和统计提交次数：</p>
        <code>学号_姓名_班级_作业名称.pdf</code>
        <p>
          例如：<strong>202103001_张三_计科2101_第三次作业.pdf</strong>
        </p>
      </Card>

      <Modal
        open={bindIdentity !== null}
        title="首次提交，请绑定 QQ 号"
        okText="绑定并提交"
        cancelText="取消"
        confirmLoading={mutations.bindQq.isPending}
        onCancel={() => {
          setBindIdentity(null)
          setQqNumber('')
        }}
        onOk={() => void bindQqAndRetry()}
      >
        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          <Typography.Paragraph type="secondary">
            绑定后，当作业成绩不理想时，系统可以通过 QQ 私聊提醒你。
          </Typography.Paragraph>
          <Input value={bindIdentity?.studentId} disabled addonBefore="学号" />
          <Input value={bindIdentity?.studentName} disabled addonBefore="姓名" />
          <Input
            value={qqNumber}
            maxLength={11}
            placeholder="请输入 5–11 位 QQ 号"
            addonBefore="QQ号"
            onChange={(event) => setQqNumber(event.target.value.replace(/\D/g, ''))}
            onPressEnter={() => void bindQqAndRetry()}
          />
        </Space>
      </Modal>
    </main>
  )
}
