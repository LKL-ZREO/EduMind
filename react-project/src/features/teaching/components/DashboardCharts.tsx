import { useMemo } from 'react'
import { EChart } from '@/shared/charts/EChart'
import type { KnowledgeMastery, ScoreDistribution } from '@/features/teaching/model/types'
import styles from './DashboardCharts.module.css'

export function ScoreDistributionChart({ data }: { data: ScoreDistribution[] }) {
  const option = useMemo(
    () => ({
      grid: { left: 36, right: 16, top: 22, bottom: 32 },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.map((item) => item.range), axisTick: { show: false } },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        {
          type: 'bar',
          data: data.map((item) => ({ value: item.count, itemStyle: { color: item.color } })),
          barMaxWidth: 42,
          borderRadius: [6, 6, 0, 0],
        },
      ],
    }),
    [data],
  )
  return <EChart option={option} className={styles.chart} ariaLabel="班级成绩分布柱状图" />
}

export function KnowledgeMasteryChart({ data }: { data: KnowledgeMastery[] }) {
  const visible = useMemo(() => data.filter((item) => item.name !== '其他').slice(0, 8), [data])
  const option = useMemo(
    () => ({
      grid: { left: 94, right: 28, top: 18, bottom: 28 },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      yAxis: { type: 'category', inverse: true, data: visible.map((item) => item.name) },
      series: [
        {
          type: 'bar',
          data: visible.map((item) => ({
            value: item.mastery,
            itemStyle: { color: item.color || (item.mastery < 60 ? '#ef6b73' : '#42a89d') },
          })),
          barMaxWidth: 22,
          markLine: { silent: true, symbol: 'none', data: [{ xAxis: 60, name: '及格线' }] },
        },
      ],
    }),
    [visible],
  )
  return <EChart option={option} className={styles.chart} ariaLabel="知识点掌握度条形图" />
}
