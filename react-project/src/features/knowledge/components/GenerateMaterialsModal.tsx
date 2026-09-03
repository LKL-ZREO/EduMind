import { useState } from 'react'
import {
  Alert,
  Button,
  Empty,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { difficultyLabel, typeLabel } from '@/features/knowledge/model/presentation'
import type {
  ClassroomOption,
  GeneratedQuestion,
  GenResult,
  SavePreviewPayload,
  TreeNode,
} from '@/features/knowledge/model/types'
import { renderMarkdown } from '@/shared/utils/safeHtml'
import styles from './GenerateMaterialsModal.module.css'

type GenerateMaterialsModalProps = {
  document: TreeNode
  classes: ClassroomOption[]
  generating: boolean
  savingPreview: boolean
  savingQuestion: boolean
  onClose: () => void
  onGenerate: (docId: string) => Promise<GenResult | null>
  onSavePreview: (payload: SavePreviewPayload) => Promise<void>
  onSaveQuestion: (question: GeneratedQuestion, docId: string) => Promise<void>
}

export function GenerateMaterialsModal({
  document,
  classes,
  generating,
  savingPreview,
  savingQuestion,
  onClose,
  onGenerate,
  onSavePreview,
  onSaveQuestion,
}: GenerateMaterialsModalProps) {
  const [result, setResult] = useState<GenResult | null>(null)
  const [classId, setClassId] = useState<number | null>(null)
  const [topic, setTopic] = useState('')
  const [guideText, setGuideText] = useState('')
  const [discussionQuestion, setDiscussionQuestion] = useState('')
  const docId = document.docId || ''

  async function generate() {
    const generated = await onGenerate(docId)
    if (!generated) return
    setResult(generated)
    setTopic(generated.preview?.topic || '')
    setGuideText(generated.preview?.guideText || '')
    setDiscussionQuestion(generated.preview?.discussionQuestion || '')
  }

  async function savePreview() {
    if (!result?.preview || !classId) return
    await onSavePreview({
      ...result.preview,
      topic,
      guideText,
      discussionQuestion,
      classId,
      docId,
    })
    setResult((current) =>
      current?.preview ? { ...current, preview: { ...current.preview, published: true } } : current,
    )
  }

  async function saveQuestion(question: GeneratedQuestion, index: number) {
    await onSaveQuestion(question, docId)
    setResult((current) =>
      current
        ? {
            ...current,
            quizzes: current.quizzes.map((item, itemIndex) =>
              itemIndex === index ? { ...item, published: true } : item,
            ),
          }
        : current,
    )
  }

  return (
    <Modal
      open
      width={900}
      title={`AI 生成教学材料 · ${document.label}`}
      footer={null}
      onCancel={onClose}
    >
      {!result && !generating && (
        <div className={styles.start}>
          <div>✦</div>
          <Typography.Title level={3}>从 PPT 生成预习任务和课堂试题</Typography.Title>
          <Typography.Paragraph type="secondary">
            Agent
            会读取当前文档，生成一份可编辑的预习材料和多道课堂题目。生成结果需要教师审核后才能保存。
          </Typography.Paragraph>
          <Button type="primary" size="large" disabled={!docId} onClick={() => void generate()}>
            开始生成
          </Button>
        </div>
      )}

      {generating && (
        <div className={styles.generating}>
          <Spin size="large" />
          <Typography.Title level={4}>AI 正在分析文档内容……</Typography.Title>
          <Typography.Text type="secondary">
            较长的 PPT 可能需要一到三分钟，请保持页面打开。
          </Typography.Text>
        </div>
      )}

      {result && !generating && (
        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          {(result.previewError || result.quizError) && (
            <Alert
              showIcon
              type="warning"
              message="部分材料生成失败"
              description={[result.previewError, result.quizError].filter(Boolean).join('；')}
            />
          )}
          <label className={styles.classSelect}>
            <span>保存/发布到班级</span>
            <Select<number>
              allowClear
              value={classId ?? undefined}
              placeholder="保存预习材料前请选择班级"
              options={classes.map((item) => ({ label: item.name, value: item.id }))}
              onChange={(value) => setClassId(value ?? null)}
            />
          </label>
          <Tabs
            items={[
              {
                key: 'preview',
                label: '预习材料',
                children: result.preview ? (
                  <div className={styles.previewEditor}>
                    <Typography.Text strong>知识点主题</Typography.Text>
                    <Input value={topic} onChange={(event) => setTopic(event.target.value)} />
                    <Typography.Text strong>预习引导</Typography.Text>
                    <Input.TextArea
                      rows={7}
                      value={guideText}
                      onChange={(event) => setGuideText(event.target.value)}
                    />
                    <div
                      className={styles.markdown}
                      dangerouslySetInnerHTML={{ __html: renderMarkdown(guideText) }}
                    />
                    <Typography.Text strong>讨论问题</Typography.Text>
                    <Input.TextArea
                      rows={3}
                      value={discussionQuestion}
                      onChange={(event) => setDiscussionQuestion(event.target.value)}
                    />
                    <Button
                      type="primary"
                      loading={savingPreview}
                      disabled={!classId || result.preview.published}
                      onClick={() => void savePreview()}
                    >
                      {result.preview.published ? '已保存' : '审核并保存预习材料'}
                    </Button>
                  </div>
                ) : (
                  <Empty description={result.previewError || '没有生成预习材料'} />
                ),
              },
              {
                key: 'quiz',
                label: `课堂试题 (${result.quizzes.length})`,
                children: result.quizzes.length ? (
                  <div className={styles.questions}>
                    {result.quizzes.map((question, index) => (
                      <div key={`${question.title}-${index}`} className={styles.question}>
                        <Space wrap>
                          <Tag color="blue">{typeLabel(question.type)}</Tag>
                          <Tag>{difficultyLabel(question.difficulty)}</Tag>
                          <Tag>{question.knowledgePoint || '未标知识点'}</Tag>
                        </Space>
                        <Typography.Title level={5}>{question.title}</Typography.Title>
                        {question.options?.map((option) => (
                          <p key={option.key}>
                            <strong>{option.key}.</strong> {option.text}
                          </p>
                        ))}
                        <Typography.Paragraph type="secondary">
                          参考答案：{question.correctKey || '—'}
                        </Typography.Paragraph>
                        <Button
                          type="primary"
                          loading={savingQuestion}
                          disabled={question.published}
                          onClick={() => void saveQuestion(question, index)}
                        >
                          {question.published ? '已保存到题库' : '审核并保存到题库'}
                        </Button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <Empty description={result.quizError || '没有生成课堂试题'} />
                ),
              },
            ]}
          />
          <Space style={{ justifyContent: 'center', width: '100%' }}>
            <Button onClick={onClose}>关闭</Button>
            <Button onClick={() => void generate()}>重新生成</Button>
          </Space>
        </Space>
      )}
    </Modal>
  )
}
