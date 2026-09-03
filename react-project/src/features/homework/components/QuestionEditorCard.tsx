import { Button, Card, Checkbox, Input, InputNumber, Space, Tag, Typography } from 'antd'
import { questionTypeLabel } from '@/features/homework/model/homework'
import type { DraftQuestion } from '@/features/homework/model/types'
import { RichTextEditor } from '@/shared/ui/RichTextEditor'
import styles from './QuestionEditorCard.module.css'

type QuestionEditorCardProps = {
  index: number
  question: DraftQuestion
  removable: boolean
  onChange: (question: DraftQuestion) => void
  onRemove: () => void
}

export function QuestionEditorCard({
  index,
  question,
  removable,
  onChange,
  onRemove,
}: QuestionEditorCardProps) {
  const patch = (next: Partial<DraftQuestion>) => onChange({ ...question, ...next })

  return (
    <Card
      className={styles.card}
      title={
        <Space>
          <span>第 {index + 1} 题</span>
          <Tag>{questionTypeLabel(question.type)}</Tag>
        </Space>
      }
      extra={
        <Button danger type="link" disabled={!removable} onClick={onRemove}>
          移除
        </Button>
      }
    >
      <div className={styles.fields}>
        <label>
          <Typography.Text strong>题目标题</Typography.Text>
          <Input
            value={question.title}
            placeholder="题目标题"
            onChange={(event) => patch({ title: event.target.value })}
          />
        </label>
        <label>
          <Typography.Text strong>分值</Typography.Text>
          <InputNumber
            min={0}
            max={1000}
            value={question.score}
            onChange={(value) => patch({ score: value ?? 0 })}
          />
        </label>
      </div>
      <div className={styles.requirement}>
        <Typography.Text strong>题目要求</Typography.Text>
        <RichTextEditor
          value={question.requirement}
          onChange={(requirement) => patch({ requirement })}
        />
      </div>
      {question.options?.length ? (
        <div className={styles.options}>
          {question.options.map((option) => (
            <span key={option.key}>
              <strong>{option.key}.</strong> {option.text}
            </span>
          ))}
        </div>
      ) : null}
      <Checkbox
        checked={question.uploadRequired}
        onChange={(event) => patch({ uploadRequired: event.target.checked })}
      >
        需要学生上传附件
      </Checkbox>
    </Card>
  )
}
