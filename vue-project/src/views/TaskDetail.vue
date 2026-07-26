<template>
  <div class="detail-page">
    <div v-if="loading" class="loading-state">
      <p>加载中...</p>
    </div>

    <template v-else-if="task">
      <!-- 头部信息 -->
      <header class="detail-header">
        <button class="back-btn" @click="goBack">← 返回</button>
        <div class="header-info">
          <h2>📊 {{ task.taskName }}</h2>
          <div class="header-meta">
            <span>📚 {{ className }}</span>
            <span>📅 截止 {{ formatDate(task.deadline) }}</span>
            <span v-if="task.allowLate">⏰ 逾期允许，每天扣 {{ task.latePenalty }} 分</span>
          </div>
        </div>
      </header>

      <!-- 概览卡片 -->
      <div class="overview-cards">
        <div class="card">
          <div class="card-num">{{ task.submittedCount }}</div>
          <div class="card-label">已提交人数</div>
        </div>
        <div class="card">
          <div class="card-num">{{ task.totalSubmissions }}</div>
          <div class="card-label">总提交次数</div>
        </div>
        <div class="card">
          <div class="card-num">{{ task.avgScore }}</div>
          <div class="card-label">平均分</div>
        </div>
      </div>

      <!-- 成绩分布 -->
      <section class="chart-section">
        <h3>成绩分布</h3>
        <div ref="chartRef" class="chart-container"></div>
      </section>

      <!-- 提交列表 -->
      <section class="submissions-section">
        <h3>提交列表（{{ task.submissions?.length || 0 }} 人）</h3>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>#</th>
                <th>姓名</th>
                <th>得分</th>
                <th>状态</th>
                <th>提交时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in task.submissions" :key="i">
                <td>{{ i + 1 }}</td>
                <td>{{ s.studentName }}</td>
                <td>
                  <span v-if="s.score != null" :class="scoreClass(s.score)">{{
                    s.finalScore != null ? s.finalScore : s.score
                  }}</span>
                  <span v-else class="no-score">-</span>
                </td>
                <td>
                  <span v-if="s.score == null" class="tag tag-miss">未提交</span>
                  <span v-else-if="s.isLate" class="tag tag-late">晚交</span>
                  <span v-else class="tag tag-ok">正常</span>
                </td>
                <td>{{ s.submittedAt ? formatDateTime(s.submittedAt) : '-' }}</td>
                <td>
                  <button class="btn-link" @click="viewDetail(s)">查看</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <div v-else class="loading-state">
      <p>作业不存在</p>
      <button class="back-btn" @click="goBack">返回列表</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useClassStore } from '@/stores/class'
import request from '@/api/request'
import type { ApiResponse } from '@/api/types'
import { ElMessage } from 'element-plus'

interface ScoreDistribution {
  excellent: number
  good: number
  medium: number
  pass: number
  fail: number
}

interface TaskSubmission {
  submissionId: number
  studentName: string
  score: number | null
  finalScore: number | null
  isLate: boolean
  submittedAt: string | null
}

interface TaskDetail {
  classId: number
  taskName: string
  deadline: string
  allowLate: boolean
  latePenalty: number
  submittedCount: number
  totalSubmissions: number
  avgScore: number
  distribution: ScoreDistribution
  submissions: TaskSubmission[]
}

const route = useRoute()
const router = useRouter()
const classStore = useClassStore()
const task = ref<TaskDetail | null>(null)
const className = ref('')
const loading = ref(true)
const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

function goBack() {
  router.push('/teacher/tasks')
}

async function loadDetail() {
  loading.value = true
  try {
    const taskId = String(route.params.id)
    const response = await request.get<ApiResponse<TaskDetail>>(`/tasks/${taskId}`)
    if (response.data.code !== 200) {
      task.value = null
      return
    }
    task.value = response.data.data
    await classStore.fetchClassList()
    className.value =
      classStore.classList.find((item) => item.id === task.value?.classId)?.name ?? '未知'
  } catch {
    task.value = null
  } finally {
    loading.value = false
    await nextTick()
    renderChart()
  }
}

function renderChart() {
  if (!task.value?.distribution || !chartRef.value) return
  chart?.dispose()
  const dist = task.value.distribution
  chart = echarts.init(chartRef.value)

  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: ['优秀(90+)', '良好(80-89)', '中等(70-79)', '及格(60-69)', '不及格(<60)'],
      axisLabel: { color: '#bbb' },
    },
    yAxis: {
      type: 'value',
      min: 0,
      minInterval: 1,
      axisLabel: { color: '#bbb' },
    },
    series: [
      {
        type: 'bar',
        data: [
          dist.excellent || 0,
          dist.good || 0,
          dist.medium || 0,
          dist.pass || 0,
          dist.fail || 0,
        ],
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#409EFF' },
              { offset: 1, color: '#cc6400' },
            ],
          },
        },
        label: {
          show: true,
          position: 'top',
          color: '#ccc',
        },
      },
    ],
  })
}

function resizeChart() {
  chart?.resize()
}

function formatDate(date: string) {
  return formatDateTime(date)
}

function formatDateTime(date: string) {
  if (!date) return '-'
  const value = new Date(date)
  const pad = (number: number) => String(number).padStart(2, '0')
  return `${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}`
}

function scoreClass(score: number) {
  if (score >= 90) return 'score-excellent'
  if (score >= 80) return 'score-good'
  if (score >= 60) return 'score-pass'
  return 'score-fail'
}

function viewDetail(submission: TaskSubmission) {
  if (!submission.submissionId) {
    ElMessage.warning('数据未加载完成，请刷新页面后重试')
    return
  }
  window.open(`/view/submission/${submission.submissionId}`, '_blank')
}

onMounted(() => {
  window.addEventListener('resize', resizeChart)
  void loadDetail()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})
</script>

<style scoped>
.detail-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
  color: #303133;
  font-size: 14px;
}

.loading-state {
  text-align: center;
  padding: 60px;
  color: #606266;
}

/* 头部 */
.detail-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.back-btn {
  padding: 8px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: transparent;
  color: #303133;
  cursor: pointer;
  flex-shrink: 0;
  margin-top: 4px;
}

.back-btn:hover {
  background: #ebeef5;
}

.header-info h2 {
  margin: 0 0 6px 0;
  font-size: 20px;
}

.header-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #606266;
  font-size: 13px;
}

/* 概览卡片 */
.overview-cards {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.card {
  flex: 1;
  background: #ffffff;
  border-radius: 10px;
  padding: 18px;
  text-align: center;
}

.card-num {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}

.card-label {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
}

/* 图表 */
.chart-section {
  background: #ffffff;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 20px;
}

.chart-section h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
}

.chart-container {
  width: 100%;
  height: 260px;
}

/* 提交列表 */
.submissions-section {
  background: #ffffff;
  border-radius: 10px;
  padding: 20px;
}

.submissions-section h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
}

.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.data-table th {
  background: #ebeef5;
  padding: 10px 14px;
  text-align: left;
  color: #606266;
  font-weight: 500;
  border-bottom: 2px solid #e4e7ed;
  white-space: nowrap;
}

.data-table td {
  padding: 10px 14px;
  border-bottom: 1px solid #e4e7ed;
  color: #d0d0d0;
}

.data-table tr:hover td {
  background: #ebeef5;
}

.no-score {
  color: #666;
}

.tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.tag-ok {
  background: rgba(82, 196, 26, 0.15);
  color: #52c41a;
}

.tag-late {
  background: rgba(245, 34, 45, 0.15);
  color: #ff4d4f;
}

.tag-miss {
  background: rgba(153, 153, 153, 0.15);
  color: #606266;
}

.score-excellent {
  color: #52c41a;
  font-weight: 600;
}
.score-good {
  color: #1890ff;
  font-weight: 600;
}
.score-pass {
  color: #d46b08;
  font-weight: 600;
}
.score-fail {
  color: #cf1322;
  font-weight: 600;
}

.btn-link {
  background: none;
  border: 1px solid #dcdfe6;
  color: #409eff;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
}

.btn-link:hover {
  background: rgba(64, 158, 255, 0.12);
}
</style>
