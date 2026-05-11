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
                  <span v-if="s.score != null" :class="scoreClass(s.score)">{{ s.finalScore != null ? s.finalScore : s.score }}</span>
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

<script>
import * as echarts from 'echarts'

export default {
  name: 'TaskDetail',
  data() {
    return {
      apiBaseUrl: 'http://localhost:8080/api',
      taskId: null,
      task: null,
      className: '',
      loading: true
    }
  },

  mounted() {
    this.taskId = this.$route.params.id
    this.loadDetail()
  },

  beforeUnmount() {
    // 清理 chart 实例
    if (this.chart) {
      this.chart.dispose()
    }
  },

  methods: {
    getToken() {
      return localStorage.getItem('token') || ''
    },

    goBack() {
      this.$router.push('/teacher/tasks')
    },

    async loadDetail() {
      this.loading = true
      try {
        const response = await fetch(`${this.apiBaseUrl}/tasks/${this.taskId}`, {
          headers: { 'Authorization': `Bearer ${this.getToken()}` }
        })
        const result = await response.json()
        if (result.code === 200) {
          this.task = result.data
          // 获取班级名称
          try {
            const clsResponse = await fetch(`${this.apiBaseUrl}/dashboard/classes`, {
              headers: { 'Authorization': `Bearer ${this.getToken()}` }
            })
            const clsResult = await clsResponse.json()
            if (clsResult.code === 200) {
              const cls = clsResult.data.find(c => c.id === this.task.classId)
              this.className = cls ? cls.name : '未知'
            }
          } catch (e) {
            this.className = '未知'
          }
          this.$nextTick(() => this.renderChart())
        } else {
          this.task = null
        }
      } catch (e) {
        console.error('加载作业详情失败', e)
        this.task = null
      } finally {
        this.loading = false
      }
    },

    renderChart() {
      if (!this.task?.distribution) return
      const container = this.$refs.chartRef
      if (!container) return

      if (this.chart) this.chart.dispose()

      const dist = this.task.distribution
      const chart = echarts.init(container)
      this.chart = chart

      chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: '3%', right: '4%', bottom: '10%', containLabel: true },
        xAxis: {
          type: 'category',
          data: ['优秀(90+)', '良好(80-89)', '中等(70-79)', '及格(60-69)', '不及格(<60)'],
          axisLabel: { color: '#bbb' }
        },
        yAxis: {
          type: 'value',
          axisLabel: { color: '#bbb' }
        },
        series: [{
          type: 'bar',
          data: [
            dist.excellent || 0,
            dist.good || 0,
            dist.medium || 0,
            dist.pass || 0,
            dist.fail || 0
          ],
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: '#ff7d00' },
                { offset: 1, color: '#cc6400' }
              ]
            }
          },
          label: {
            show: true,
            position: 'top',
            color: '#ccc'
          }
        }]
      })

      window.addEventListener('resize', () => chart.resize())
    },

    formatDate(dateStr) {
      if (!dateStr) return ''
      const d = new Date(dateStr)
      const pad = n => String(n).padStart(2, '0')
      return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    },

    formatDateTime(dateStr) {
      if (!dateStr) return '-'
      const d = new Date(dateStr)
      const pad = n => String(n).padStart(2, '0')
      return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    },

    scoreClass(score) {
      if (score >= 90) return 'score-excellent'
      if (score >= 80) return 'score-good'
      if (score >= 60) return 'score-pass'
      return 'score-fail'
    },

    viewDetail(s) {
      alert(`学生：${s.studentName}\n得分：${s.finalScore != null ? s.finalScore : s.score}\n${s.isLate ? '⚠️ 晚交提交' : '✅ 正常提交'}\n时间：${s.submittedAt || '-'}`)
    }
  }
}
</script>

<style scoped>
.detail-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
  color: #e0e0e0;
  font-size: 14px;
}

.loading-state {
  text-align: center;
  padding: 60px;
  color: #999;
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
  border: 1px solid #555;
  border-radius: 6px;
  background: transparent;
  color: #ccc;
  cursor: pointer;
  flex-shrink: 0;
  margin-top: 4px;
}

.back-btn:hover {
  background: #333;
}

.header-info h2 {
  margin: 0 0 6px 0;
  font-size: 20px;
}

.header-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #999;
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
  background: #2a2a2a;
  border-radius: 10px;
  padding: 18px;
  text-align: center;
}

.card-num {
  font-size: 28px;
  font-weight: bold;
  color: #ff7d00;
}

.card-label {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}

/* 图表 */
.chart-section {
  background: #2a2a2a;
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
  background: #2a2a2a;
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
  background: #333;
  padding: 10px 14px;
  text-align: left;
  color: #aaa;
  font-weight: 500;
  border-bottom: 2px solid #444;
  white-space: nowrap;
}

.data-table td {
  padding: 10px 14px;
  border-bottom: 1px solid #3a3a3a;
  color: #d0d0d0;
}

.data-table tr:hover td {
  background: #333;
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
  color: #999;
}

.score-excellent { color: #52c41a; font-weight: 600; }
.score-good { color: #1890ff; font-weight: 600; }
.score-pass { color: #d46b08; font-weight: 600; }
.score-fail { color: #cf1322; font-weight: 600; }

.btn-link {
  background: none;
  border: 1px solid #555;
  color: #ff7d00;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
}

.btn-link:hover {
  background: rgba(255, 125, 0, 0.1);
}
</style>
