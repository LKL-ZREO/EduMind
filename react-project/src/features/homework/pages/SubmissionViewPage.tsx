import { Alert, Button, Card, Empty, Spin, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'
import { submissionContentQueryOptions } from '@/features/homework/api/homeworkQueries'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './SubmissionViewPage.module.css'

export function SubmissionViewPage() {
  const { id } = useParams()
  const submissionId = Number(id)
  const navigate = useNavigate()
  const contentQuery = useQuery({
    ...submissionContentQueryOptions(submissionId),
    enabled: Number.isInteger(submissionId) && submissionId > 0,
  })

  function closeOrReturn() {
    window.close()
    if (!window.closed) void navigate('/teacher/tasks')
  }

  if (!Number.isInteger(submissionId) || submissionId <= 0) {
    return <Empty description="提交记录编号无效" />
  }

  return (
    <main className={styles.page}>
      {contentQuery.isPending && (
        <div className={styles.loading}>
          <Spin size="large" />
          加载提交内容……
        </div>
      )}
      {contentQuery.isError && (
        <Alert
          showIcon
          type="error"
          title="无法查看提交内容"
          description={getApiErrorMessage(contentQuery.error, '记录不存在或无权访问')}
          action={<Button onClick={closeOrReturn}>关闭</Button>}
        />
      )}
      {contentQuery.data && (
        <>
          <header className={styles.header}>
            <Button onClick={closeOrReturn}>✕ 关闭</Button>
            <div>
              <Typography.Title level={2}>{contentQuery.data.fileName}</Typography.Title>
              <Tag>学生：{contentQuery.data.studentName}</Tag>
            </div>
          </header>
          <Card className={styles.content}>
            <pre>
              <code>{contentQuery.data.content}</code>
            </pre>
          </Card>
        </>
      )}
    </main>
  )
}
