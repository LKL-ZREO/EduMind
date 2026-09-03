import { Button, Select, Space, Tag } from 'antd'
import { CHAT_MODE_OPTIONS } from '@/features/assistant/model/prompts'
import type {
  ChatMode,
  ClassroomOption,
  KnowledgeBaseOption,
} from '@/features/assistant/model/types'
import styles from './ContextHeader.module.css'

type ContextHeaderProps = {
  classes: ClassroomOption[]
  knowledgeBases: KnowledgeBaseOption[]
  classId: number | null
  kbIds: number[]
  mode: ChatMode
  disabled: boolean
  onClassChange: (classId: number | null) => void
  onKnowledgeBasesChange: (kbIds: number[]) => void
  onModeChange: (mode: ChatMode) => void
  onOpenSessions: () => void
  onOpenArtifact: () => void
}

export function ContextHeader({
  classes,
  knowledgeBases,
  classId,
  kbIds,
  mode,
  disabled,
  onClassChange,
  onKnowledgeBasesChange,
  onModeChange,
  onOpenSessions,
  onOpenArtifact,
}: ContextHeaderProps) {
  return (
    <header className={styles.header}>
      <Button className={styles.mobileMenu} onClick={onOpenSessions} aria-label="打开会话列表">
        ☰
      </Button>
      <Space wrap className={styles.selectors}>
        <div className={styles.control}>
          <span>班级</span>
          <Select<number>
            aria-label="班级"
            allowClear
            disabled={disabled}
            value={classId ?? undefined}
            placeholder="不限定班级"
            options={classes.map((item) => ({ label: item.name, value: item.id }))}
            onChange={(value) => onClassChange(value ?? null)}
          />
        </div>
        <div className={`${styles.control} ${styles.sources}`}>
          <span>知识源</span>
          <Select<number[]>
            aria-label="知识源"
            mode="multiple"
            maxTagCount="responsive"
            disabled={disabled}
            value={kbIds}
            placeholder="仅个人知识库"
            options={knowledgeBases.map((item) => ({ label: item.name, value: item.id }))}
            onChange={onKnowledgeBasesChange}
          />
        </div>
        <div className={styles.control}>
          <span>模式</span>
          <Select<ChatMode>
            aria-label="模式"
            disabled={disabled}
            value={mode}
            options={CHAT_MODE_OPTIONS}
            onChange={onModeChange}
          />
        </div>
      </Space>
      <Space>
        {mode !== 'auto' && (
          <Tag color="blue">{CHAT_MODE_OPTIONS.find((x) => x.value === mode)?.label}</Tag>
        )}
        <Button onClick={onOpenArtifact}>成果</Button>
      </Space>
    </header>
  )
}
