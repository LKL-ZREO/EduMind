import { Button, Empty, Input, Space, Typography } from 'antd'
import { questionTypeLabel } from '@/features/homework/model/homework'
import type { TeachingQuestion } from '@/features/homework/model/types'
import styles from './QuestionBankPanel.module.css'

type QuestionBankPanelProps = {
  questions: TeachingQuestion[]
  input: string
  loading: boolean
  onInputChange: (value: string) => void
  onSearch: () => void
  onAdd: (question: TeachingQuestion) => void
}

export function QuestionBankPanel({
  questions,
  input,
  loading,
  onInputChange,
  onSearch,
  onAdd,
}: QuestionBankPanelProps) {
  return (
    <section className={styles.panel}>
      <Typography.Title level={4}>题库</Typography.Title>
      <Space.Compact block>
        <Input
          value={input}
          placeholder="搜索题目"
          onChange={(event) => onInputChange(event.target.value)}
          onPressEnter={onSearch}
        />
        <Button loading={loading} onClick={onSearch}>
          搜索
        </Button>
      </Space.Compact>
      <div className={styles.list}>
        {questions.map((question) => (
          <Button key={question.id} className={styles.row} onClick={() => onAdd(question)}>
            <strong>{question.title}</strong>
            <small>
              {questionTypeLabel(question.type)} · {question.score} 分 ·{' '}
              {question.uploadRequired ? '附件提交' : '在线作答'}
            </small>
          </Button>
        ))}
        {!questions.length && (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="题库中暂无匹配题目" />
        )}
      </div>
    </section>
  )
}
