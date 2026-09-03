import { Button, Spin, Typography } from 'antd'
import type { ContextualPrompt } from '@/features/assistant/model/prompts'
import { renderAssistantMarkdown } from '@/features/assistant/model/markdown'
import type { UiMessage } from '@/features/assistant/model/types'
import styles from './MessageList.module.css'

type MessageListProps = {
  loading: boolean
  messages: UiMessage[]
  prompts: ContextualPrompt[]
  className?: string
  knowledgeBaseCount: number
  onPrompt: (prompt: ContextualPrompt) => void
  onCopy: (text: string) => void
  onRetry: (messageIndex: number) => void
  onEdit: (message: UiMessage) => void
  onOpenCitation: () => void
  containerRef: React.RefObject<HTMLDivElement | null>
}

function MarkdownContent({ content }: { content: string }) {
  return (
    <div
      className={styles.markdown}
      dangerouslySetInnerHTML={{ __html: renderAssistantMarkdown(content) }}
    />
  )
}

export function MessageList({
  loading,
  messages,
  prompts,
  className,
  knowledgeBaseCount,
  onPrompt,
  onCopy,
  onRetry,
  onEdit,
  onOpenCitation,
  containerRef,
}: MessageListProps) {
  return (
    <main ref={containerRef} className={styles.list}>
      {loading && (
        <div className={styles.loading}>
          <Spin size="large" />
          <Typography.Text type="secondary">正在准备教学上下文……</Typography.Text>
        </div>
      )}

      {!loading && messages.length === 0 && (
        <section className={styles.welcome}>
          <div className={styles.welcomeMark}>✦</div>
          <span className={styles.kicker}>你的专属教学 Agent</span>
          <Typography.Title level={2}>今天想先处理哪项教学工作？</Typography.Title>
          <Typography.Paragraph type="secondary">
            已连接 <strong>{className || '通用课程上下文'}</strong>
            {knowledgeBaseCount > 0 && `，可检索 ${knowledgeBaseCount} 个团队知识库`}
          </Typography.Paragraph>
          <div className={styles.promptGrid}>
            {prompts.map((prompt) => (
              <button key={prompt.title} type="button" onClick={() => onPrompt(prompt)}>
                <span className={styles.promptIcon}>{prompt.icon}</span>
                <span>
                  <strong>{prompt.title}</strong>
                  <small>{prompt.description}</small>
                </span>
                <b>→</b>
              </button>
            ))}
          </div>
        </section>
      )}

      {!loading &&
        messages.map((message, index) => (
          <article
            key={message.localId}
            className={`${styles.message} ${message.role === 'user' ? styles.user : ''}`}
          >
            <div className={styles.avatar}>{message.role === 'user' ? '我' : 'AI'}</div>
            <div className={styles.messageMain}>
              {message.attachmentName && (
                <div className={styles.attachmentNote}>🖼 {message.attachmentName}</div>
              )}

              {message.toolSteps.length > 0 && (
                <div className={styles.toolSteps}>
                  {message.toolSteps.map((step, stepIndex) => (
                    <div key={`${step.tool}-${stepIndex}`} className={styles.toolStep}>
                      <span data-status={step.status}>
                        {step.status === 'running' ? '⋯' : step.status === 'success' ? '✓' : '!'}
                      </span>
                      <strong>{step.label}</strong>
                      {step.elapsedMs !== undefined && <small>{step.elapsedMs} ms</small>}
                    </div>
                  ))}
                </div>
              )}

              <div className={styles.bubble}>
                {message.role === 'user' ? (
                  <div className={styles.plainText}>{message.content}</div>
                ) : message.state === 'streaming' ? (
                  <div className={styles.streamingText}>
                    {message.content}
                    <span className={styles.cursor} />
                  </div>
                ) : (
                  <MarkdownContent content={message.content} />
                )}
              </div>

              {message.citations.length > 0 && (
                <div className={styles.citations}>
                  <strong>本回答参考了 {message.citations.length} 处知识库内容</strong>
                  {message.citations.map((citation, citationIndex) => (
                    <button
                      key={`${citation.documentId}-${citation.sectionIndex}`}
                      type="button"
                      onClick={onOpenCitation}
                    >
                      <b>{citationIndex + 1}</b>
                      <span>
                        <strong>{citation.documentName}</strong>
                        <small>
                          第 {citation.sectionIndex + 1} 节 · {citation.excerpt}
                        </small>
                      </span>
                    </button>
                  ))}
                </div>
              )}

              <div className={styles.meta}>
                <span>{message.time}</span>
                {message.role === 'assistant' && message.content && (
                  <>
                    <Button type="link" size="small" onClick={() => onCopy(message.content)}>
                      复制
                    </Button>
                    <Button type="link" size="small" onClick={() => onRetry(index)}>
                      重新生成
                    </Button>
                    {message.state === 'stopped' && <em>已停止</em>}
                    {message.state === 'error' && <em className={styles.error}>生成失败</em>}
                  </>
                )}
                {message.role === 'user' && (
                  <Button type="link" size="small" onClick={() => onEdit(message)}>
                    编辑并分支
                  </Button>
                )}
              </div>
            </div>
          </article>
        ))}
    </main>
  )
}
