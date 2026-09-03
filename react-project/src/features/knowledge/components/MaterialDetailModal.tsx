import { useState } from 'react'
import { Alert, Button, Descriptions, List, Modal, Space, Spin, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { materialDetailQueryOptions } from '@/features/knowledge/api/knowledgeQueries'
import {
  difficultyLabel,
  draftStatusLabel,
  formatTimeLimit,
  typeLabel,
} from '@/features/knowledge/model/presentation'
import { getApiErrorMessage } from '@/shared/api/errors'
import { renderMarkdown } from '@/shared/utils/safeHtml'

type MaterialDetailModalProps = {
  type: 'preview' | 'quiz'
  id: number
  questionIds?: number[]
  onClose: () => void
}

export function MaterialDetailModal({
  type,
  id,
  questionIds = [],
  onClose,
}: MaterialDetailModalProps) {
  const [activeId, setActiveId] = useState(id)
  const detailQuery = useQuery(materialDetailQueryOptions(type, activeId))
  const detail = detailQuery.data
  const questionIndex = type === 'quiz' ? questionIds.indexOf(activeId) : -1
  const hasPrevious = questionIndex > 0
  const hasNext = questionIndex >= 0 && questionIndex < questionIds.length - 1

  const footer =
    type === 'quiz' ? (
      <Space style={{ justifyContent: 'space-between', width: '100%' }}>
        <Button
          disabled={!hasPrevious}
          onClick={() => setActiveId(questionIds[questionIndex - 1] ?? activeId)}
        >
          上一题
        </Button>
        <span>
          {questionIndex >= 0 ? `第 ${questionIndex + 1} / ${questionIds.length} 题` : '课堂试题'}
        </span>
        <Button
          disabled={!hasNext}
          onClick={() => setActiveId(questionIds[questionIndex + 1] ?? activeId)}
        >
          下一题
        </Button>
      </Space>
    ) : (
      <Button type="primary" onClick={onClose}>
        完成查看
      </Button>
    )

  return (
    <Modal
      open
      width={760}
      title={type === 'preview' ? '预习材料详情' : '课堂试题详情'}
      footer={footer}
      onCancel={onClose}
    >
      {detailQuery.isPending && <Spin />}
      {detailQuery.isError && (
        <Alert
          showIcon
          type="error"
          message="材料加载失败"
          description={getApiErrorMessage(detailQuery.error, '无法加载材料')}
        />
      )}
      {detail?.type === 'preview' && (
        <div>
          <Typography.Title level={3}>{detail.data.title || detail.data.topic}</Typography.Title>
          <Tag>{draftStatusLabel(detail.data)}</Tag>
          <Typography.Title level={5}>预习引导</Typography.Title>
          <div dangerouslySetInnerHTML={{ __html: renderMarkdown(detail.data.guideText || '') }} />
          <Typography.Title level={5}>讨论问题</Typography.Title>
          <Typography.Paragraph>{detail.data.discussionQuestion || '—'}</Typography.Paragraph>
          {detail.data.questions?.length ? (
            <>
              <Typography.Title level={5}>配套自测</Typography.Title>
              <List
                bordered
                dataSource={detail.data.questions}
                renderItem={(question, index) => (
                  <List.Item>
                    <div style={{ width: '100%' }}>
                      <Typography.Text strong>
                        {index + 1}. {question.question || question.title}
                      </Typography.Text>
                      {question.options?.map((option) => (
                        <Typography.Paragraph key={option.key} style={{ margin: '8px 0 0' }}>
                          <Tag color={option.key === question.correctKey ? 'success' : undefined}>
                            {option.key}
                          </Tag>
                          {option.text}
                        </Typography.Paragraph>
                      ))}
                      {question.correctKey && (
                        <Typography.Text type="success">
                          参考答案：{question.correctKey}
                        </Typography.Text>
                      )}
                    </div>
                  </List.Item>
                )}
              />
            </>
          ) : null}
        </div>
      )}
      {detail?.type === 'quiz' && (
        <div>
          <Typography.Title level={3}>{detail.data.title}</Typography.Title>
          <Descriptions bordered column={2} size="small">
            <Descriptions.Item label="题型">{typeLabel(detail.data.type)}</Descriptions.Item>
            <Descriptions.Item label="难度">
              {difficultyLabel(detail.data.difficulty)}
            </Descriptions.Item>
            <Descriptions.Item label="知识点">
              {detail.data.knowledgePoint || '—'}
            </Descriptions.Item>
            <Descriptions.Item label="建议时间">
              {formatTimeLimit(detail.data.timeLimit)}
            </Descriptions.Item>
          </Descriptions>
          {detail.data.options?.map((option) => (
            <Typography.Paragraph key={option.key}>
              <strong>{option.key}.</strong> {option.text}
            </Typography.Paragraph>
          ))}
          <Alert
            type="success"
            message={`参考答案：${detail.data.correctKey || '—'}`}
            description={detail.data.explanation}
          />
        </div>
      )}
    </Modal>
  )
}
