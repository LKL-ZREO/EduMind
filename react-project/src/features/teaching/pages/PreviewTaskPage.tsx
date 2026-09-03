import { useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Empty,
  Progress,
  Radio,
  Result,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { previewTaskQueryOptions } from '@/features/teaching/api/teachingQueries'
import { renderMarkdown } from '@/shared/utils/safeHtml'
import styles from './PreviewTaskPage.module.css'

export function PreviewTaskPage() {
  const taskId = Number(useParams().taskId)
  const taskQuery = useQuery(previewTaskQueryOptions(taskId))
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const [submitted, setSubmitted] = useState(false)
  const task = taskQuery.data
  const scorableQuestions =
    task?.questions.flatMap((question, index) =>
      question.options?.length ? [{ question, index }] : [],
    ) || []
  const answeredCount = scorableQuestions.filter(({ index }) => Boolean(answers[index])).length
  const correctCount = scorableQuestions.filter(
    ({ question, index }) => answers[index] === question.correctKey,
  ).length
  const guideHtml = useMemo(() => renderMarkdown(task?.guideText), [task?.guideText])
  const discussionHtml = useMemo(
    () => renderMarkdown(task?.discussionQuestion),
    [task?.discussionQuestion],
  )

  if (!Number.isInteger(taskId) || taskId <= 0)
    return <Result status="404" title="无效的预习任务地址" />
  if (taskQuery.isPending)
    return (
      <div className={styles.center}>
        <Spin size="large" />
      </div>
    )
  if (taskQuery.isError)
    return (
      <Result
        status="error"
        title="预习任务加载失败"
        subTitle="请确认链接是否正确，或稍后重试。"
        extra={<Button onClick={() => void taskQuery.refetch()}>重新加载</Button>}
      />
    )
  if (!task) return <Empty description="任务不存在" />

  return (
    <main className={styles.page}>
      <header className={styles.hero}>
        <Tag color={task.status === 'ACTIVE' ? 'green' : 'default'}>
          {task.status === 'ACTIVE' ? '进行中' : '已关闭'}
        </Tag>
        <Typography.Title>{task.title}</Typography.Title>
        <Typography.Paragraph>{task.knowledgePoint} · 课前自主学习</Typography.Paragraph>
        <Progress
          percent={Math.round((answeredCount * 100) / Math.max(scorableQuestions.length, 1))}
          showInfo={false}
        />
      </header>
      {task.status !== 'ACTIVE' && (
        <Alert type="warning" showIcon message="该预习任务已经关闭，你仍可以查看内容。" />
      )}
      <Card title="导学材料">
        <article className={styles.markdown} dangerouslySetInnerHTML={{ __html: guideHtml }} />
      </Card>
      <section className={styles.questions}>
        <Typography.Title level={2}>课前自测</Typography.Title>
        {task.questions.map((question, index) => {
          const correct = answers[index] === question.correctKey
          return (
            <Card
              key={`${question.question}-${index}`}
              title={`${index + 1}. ${question.question}`}
              extra={
                submitted && (
                  <Tag color={correct ? 'green' : 'red'}>{correct ? '正确' : '再想一想'}</Tag>
                )
              }
            >
              {question.options?.length ? (
                <Radio.Group
                  value={answers[index]}
                  onChange={(event) => {
                    setAnswers((current) => ({ ...current, [index]: event.target.value as string }))
                    setSubmitted(false)
                  }}
                >
                  <Space direction="vertical">
                    {question.options.map((option) => (
                      <Radio value={option.key} key={option.key}>
                        {option.key}. {option.text}
                      </Radio>
                    ))}
                  </Space>
                </Radio.Group>
              ) : (
                <Alert
                  type="info"
                  showIcon
                  message={`参考答案：${question.correctKey}`}
                  description={question.explanation}
                />
              )}
              {question.options?.length && submitted && (
                <Alert
                  className={styles.explanation}
                  type={correct ? 'success' : 'info'}
                  showIcon
                  message={`参考答案：${question.correctKey}`}
                  description={question.explanation}
                />
              )}
            </Card>
          )
        })}
        {scorableQuestions.length > 0 && (
          <Button
            size="large"
            type="primary"
            disabled={answeredCount < scorableQuestions.length}
            onClick={() => setSubmitted(true)}
          >
            提交自测
          </Button>
        )}
        {submitted && scorableQuestions.length > 0 && (
          <Result
            status={correctCount === scorableQuestions.length ? 'success' : 'info'}
            title={`答对 ${correctCount} / ${scorableQuestions.length} 题`}
            subTitle="理解解析比一次答对更重要，带着仍不确定的问题进入课堂。"
          />
        )}
      </section>
      <Card title="带到课堂讨论">
        <article className={styles.markdown} dangerouslySetInnerHTML={{ __html: discussionHtml }} />
      </Card>
    </main>
  )
}
