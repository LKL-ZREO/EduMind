import { useRef } from 'react'
import { Button, Input, Typography } from 'antd'
import styles from './ChatComposer.module.css'

type ChatComposerProps = {
  value: string
  responding: boolean
  selectedFile: File | null
  selectedFileUrl: string
  onChange: (value: string) => void
  onFileSelect: (file: File) => void
  onClearFile: () => void
  onSend: () => void
  onStop: () => void
}

export function ChatComposer({
  value,
  responding,
  selectedFile,
  selectedFileUrl,
  onChange,
  onFileSelect,
  onClearFile,
  onSend,
  onStop,
}: ChatComposerProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)

  return (
    <footer className={styles.area}>
      {selectedFile && (
        <div className={styles.filePreview}>
          <img src={selectedFileUrl} alt="待分析图片" />
          <span>
            <strong>{selectedFile.name}</strong>
            <small>将作为视觉材料发送</small>
          </span>
          <Button type="text" aria-label="移除待发送图片" onClick={onClearFile}>
            ×
          </Button>
        </div>
      )}

      <div className={styles.composer}>
        <Button
          className={styles.attach}
          aria-label="上传图片"
          title="上传试题、作业或代码截图"
          onClick={() => fileInputRef.current?.click()}
        >
          ＋
        </Button>
        <input
          ref={fileInputRef}
          hidden
          type="file"
          accept="image/*"
          onChange={(event) => {
            const file = event.currentTarget.files?.[0]
            if (file) onFileSelect(file)
            event.currentTarget.value = ''
          }}
        />
        <Input.TextArea
          autoSize={{ minRows: 1, maxRows: 6 }}
          value={value}
          placeholder={
            selectedFile
              ? '告诉 Agent 如何分析这张图片……'
              : '向教学 Agent 描述你的目标，或选择一个快捷任务……'
          }
          onChange={(event) => onChange(event.target.value)}
          onPressEnter={(event) => {
            if (event.shiftKey || event.nativeEvent.isComposing) return
            event.preventDefault()
            if (!responding && (value.trim() || selectedFile)) onSend()
          }}
        />
        {responding ? (
          <Button danger type="primary" aria-label="停止生成" onClick={onStop}>
            ■
          </Button>
        ) : (
          <Button
            type="primary"
            aria-label="发送消息"
            disabled={!value.trim() && !selectedFile}
            onClick={onSend}
          >
            ↑
          </Button>
        )}
      </div>
      <div className={styles.hint}>
        <Typography.Text type="secondary">Enter 发送 · Shift + Enter 换行</Typography.Text>
        <Typography.Text type="secondary">重要教学操作仍需教师确认</Typography.Text>
      </div>
    </footer>
  )
}
