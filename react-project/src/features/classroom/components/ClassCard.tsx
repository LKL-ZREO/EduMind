import { Button, Card, Space, Statistic, Tag, Typography } from 'antd'
import type { ClassItem } from '@/features/classroom/model/types'
import { formatClassDate } from '@/features/classroom/model/groupClasses'
import styles from './ClassCard.module.css'

type ClassCardProps = {
  classItem: ClassItem
  onManage: (classId: number) => void
  onInvite: (classItem: ClassItem) => void
}

export function ClassCard({ classItem, onManage, onInvite }: ClassCardProps) {
  const archived = classItem.status === 'ARCHIVED'

  return (
    <Card className={styles.card} variant="borderless">
      <div className={styles.heading}>
        <div className={styles.avatar}>{classItem.name.charAt(0)}</div>
        <div className={styles.title}>
          <Space wrap>
            <Typography.Title level={4}>{classItem.name}</Typography.Title>
            <Tag color={archived ? 'default' : 'success'}>{archived ? '已归档' : '进行中'}</Tag>
          </Space>
          <Typography.Text type="secondary">
            创建于 {formatClassDate(classItem.createdAt)}
          </Typography.Text>
        </div>
      </div>

      <Typography.Paragraph className={styles.description} ellipsis={{ rows: 2 }}>
        {classItem.description || '暂无班级描述'}
      </Typography.Paragraph>

      <div className={styles.stats}>
        <Statistic title="学生" value={classItem.studentCount} suffix="人" />
        <button type="button" className={styles.invite} onClick={() => onInvite(classItem)}>
          <span>邀请码</span>
          <strong>{classItem.inviteCode}</strong>
        </button>
      </div>

      <Button block type={archived ? 'default' : 'primary'} onClick={() => onManage(classItem.id)}>
        {archived ? '查看详情' : '管理班级'}
      </Button>
    </Card>
  )
}
