import { useMemo } from 'react'
import { Alert, Descriptions, Empty, Modal, Space, Spin, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { studentInsightQueryOptions } from '@/features/teaching/api/teachingQueries'
import type { StudentOverview } from '@/features/teaching/model/types'
import { EChart } from '@/shared/charts/EChart'
import styles from './StudentInsightModal.module.css'

type Props = {
  classId: number
  student: StudentOverview | null
  onClose: () => void
}

export function StudentInsightModal({ classId, student, onClose }: Props) {
  const query = useQuery(studentInsightQueryOptions(classId, student))
  const history = useMemo(() => query.data?.scoreHistory || [], [query.data?.scoreHistory])
  const option = useMemo(
    () => ({
      grid: { left: 38, right: 18, top: 24, bottom: 54 },
      tooltip: { trigger: 'axis' },
      xAxis: {
        type: 'category',
        data: history.map((item) => item.assignmentName),
        axisLabel: { rotate: history.length > 4 ? 25 : 0 },
      },
      yAxis: { type: 'value', min: 0, max: 100 },
      series: [
        {
          type: 'line',
          smooth: true,
          data: history.map((item) => item.score),
          lineStyle: { color: '#247e77' },
          itemStyle: { color: '#247e77' },
          areaStyle: { color: 'rgba(36, 126, 119, .12)' },
          markLine: { silent: true, symbol: 'none', data: [{ yAxis: 60, name: '及格线' }] },
        },
      ],
    }),
    [history],
  )

  const insight = query.data
  const riskColor =
    insight?.risk.level === 'HIGH' ? 'red' : insight?.risk.level === 'MEDIUM' ? 'orange' : 'green'

  return (
    <Modal
      title={`${student?.name || ''} · 学情诊断`}
      open={Boolean(student)}
      onCancel={onClose}
      footer={null}
      width={820}
      destroyOnHidden
    >
      {query.isPending ? (
        <div className={styles.center}>
          <Spin />
        </div>
      ) : query.isError ? (
        <Alert type="error" showIcon message="学情诊断加载失败" />
      ) : !insight ? (
        <Empty />
      ) : (
        <div className={styles.content}>
          <Space wrap>
            <Tag color={riskColor}>风险 {insight.risk.level}</Tag>
            {insight.risk.reasons.map((reason) => (
              <Tag key={reason}>{reason}</Tag>
            ))}
          </Space>
          <Descriptions size="small" bordered column={{ xs: 2, sm: 4 }}>
            <Descriptions.Item label="平均分">{insight.summary.avgScore}</Descriptions.Item>
            <Descriptions.Item label="最近得分">{insight.summary.latestScore}</Descriptions.Item>
            <Descriptions.Item label="已完成">{insight.summary.completedCount}</Descriptions.Item>
            <Descriptions.Item label="严重错误">
              {insight.summary.criticalErrorCount}
            </Descriptions.Item>
          </Descriptions>
          <section>
            <Typography.Title level={5}>成绩趋势</Typography.Title>
            {history.length ? (
              <EChart
                option={option}
                className={styles.chart}
                ariaLabel={`${student?.name || ''}的成绩趋势折线图`}
              />
            ) : (
              <Empty description="暂无成绩趋势" />
            )}
          </section>
          <section>
            <Typography.Title level={5}>薄弱知识点</Typography.Title>
            <Space wrap>
              {insight.weakKnowledgePoints.map((item) => (
                <Tag color="volcano" key={item.name}>
                  {item.name} · {item.errorCount} 次
                </Tag>
              ))}
            </Space>
          </section>
          <Alert
            type="info"
            showIcon
            message="教学建议"
            description={
              insight.risk.suggestions.join('；') || '保持跟踪，结合下一次作业结果调整。'
            }
          />
        </div>
      )}
    </Modal>
  )
}
