<template>
  <div class="dashboard-page">
    <!-- 页面头部 -->
    <header class="dashboard-header">
      <div class="header-left">
        <h1>📊 教学数据中心</h1>
        <p class="subtitle">实时监控班级学习情况，智能分析教学数据</p>
      </div>
      <div class="header-right">
        <select v-model="selectedClass" class="class-selector">
          <option v-for="cls in classList" :key="cls.id" :value="cls.id">
            {{ cls.name }}
          </option>
        </select>
        <button class="refresh-btn" @click="refreshData" :disabled="loading">
          <span v-if="!loading">🔄 刷新</span>
          <span v-else>加载中...</span>
        </button>
      </div>
    </header>

    <!-- 核心指标卡片 -->
    <section class="metrics-section">
      <div class="metric-card">
        <div class="metric-icon">👨‍🎓</div>
        <div class="metric-content">
          <span class="metric-value">{{ metrics.totalStudents }}</span>
          <span class="metric-label">学生总数</span>
          <span class="metric-trend" :class="metrics.studentTrend > 0 ? 'up' : 'down'">
            {{ metrics.studentTrend > 0 ? '↑' : '↓' }} {{ Math.abs(metrics.studentTrend) }}%
          </span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon">📝</div>
        <div class="metric-content">
          <span class="metric-value">{{ metrics.totalHomework }}</span>
          <span class="metric-label">作业总数</span>
          <span class="metric-trend up">本周 +{{ metrics.newHomework }}</span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon">✅</div>
        <div class="metric-content">
          <span class="metric-value">{{ metrics.avgScore }}%</span>
          <span class="metric-label">平均正确率</span>
          <span class="metric-trend" :class="metrics.scoreTrend >= 0 ? 'up' : 'down'">
            {{ metrics.scoreTrend >= 0 ? '↑' : '↓' }} {{ Math.abs(metrics.scoreTrend) }}%
          </span>
        </div>
      </div>
      <div class="metric-card">
        <div class="metric-icon">⚠️</div>
        <div class="metric-content">
          <span class="metric-value">{{ metrics.warningStudents }}</span>
          <span class="metric-label">需关注学生</span>
          <span class="metric-trend down">低于平均 {{ metrics.avgScore - 60 }}%</span>
        </div>
      </div>
    </section>

    <!-- 图表区域 -->
    <section class="charts-section">
      <!-- 班级成绩分布 -->
      <div class="chart-card">
        <div class="chart-header">
          <h3>📈 班级成绩分布</h3>
          <div class="chart-actions">
            <button
              v-for="period in periods"
              :key="period.value"
              :class="['period-btn', { active: selectedPeriod === period.value }]"
              @click="selectedPeriod = period.value"
            >
              {{ period.label }}
            </button>
          </div>
        </div>
        <div class="chart-body">
          <div class="score-distribution">
            <div
              v-for="(item, index) in scoreDistribution"
              :key="index"
              class="distribution-bar"
            >
              <div class="bar-label">{{ item.range }}</div>
              <div class="bar-track">
                <div
                  class="bar-fill"
                  :style="{ width: item.percentage + '%', background: item.color }"
                ></div>
              </div>
              <div class="bar-value">{{ item.count }}人 ({{ item.percentage }}%)</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 知识点掌握度 -->
      <div class="chart-card">
        <div class="chart-header">
          <h3>🎯 知识点掌握度热力图</h3>
          <div class="chart-actions">
            <button v-if="!isEditing" class="action-btn" @click="showAddKpDialog = true">✚ 添加知识点</button>
            <button v-if="!isEditing" class="action-btn edit-btn" @click="toggleEdit">✏️ 编辑</button>
            <button v-if="isEditing" class="action-btn save-btn" @click="saveKnowledge" :disabled="savingKnowledge">
              {{ savingKnowledge ? '保存中...' : '💾 保存' }}
            </button>
            <button v-if="isEditing" class="action-btn cancel-edit-btn" @click="cancelEdit">取消</button>
            <button class="action-btn teaching-btn" @click="generateTeachingPlan">生成针对性教案</button>
          </div>
        </div>
        <div class="chart-body">
          <div class="knowledge-heatmap">
            <div
              v-for="(item, index) in knowledgeMastery"
              :key="index"
              class="heatmap-item"
              :class="{ editing: isEditing, active: selectedKp === item.name && !isEditing }"
              :style="{ background: isEditing ? '#e8e8e8' : getHeatmapColor(item.mastery) }"
              :title="isEditing ? '' : `${item.name}: ${item.errorCount} 个错误`"
              @click="isEditing ? null : showKpErrors(item)"
            >
              <!-- 编辑模式 -->
              <template v-if="isEditing">
                <button v-if="item.name !== '其他'" class="heatmap-remove" @click.stop="removeKnowledge(index)">×</button>
                <span v-else class="heatmap-remove-placeholder"></span>
                <div class="heatmap-edit-content">
                  <input v-model="item.name" class="heatmap-name-input" placeholder="知识点名称" />
                  <div class="heatmap-color-row">
                    <input type="color" v-model="item.color" class="heatmap-color-picker" />
                    <span class="color-preview" :style="{ background: item.color }">{{ item.color }}</span>
                  </div>
                </div>
              </template>
              <!-- 展示模式 -->
              <template v-else>
                <span class="heatmap-name">{{ item.name }}</span>
                <span class="heatmap-value">{{ item.errorCount || 0 }}</span>
                <span class="heatmap-sub">个错误</span>
              </template>
            </div>
          </div>
          <!-- 空状态 -->
          <div v-if="knowledgeMastery.length === 0" class="empty-hint heatmap-empty">
            暂无知识点，请点击「添加知识点」创建
          </div>
        </div>
      </div>
    </section>

    <!-- 学生学情列表 -->
    <section class="details-section">
      <div class="detail-card">
        <div class="card-header">
          <h3>👥 学生学情概览</h3>
          <div class="filter-bar">
            <input
              v-model="studentFilter"
              type="text"
              placeholder="搜索学生姓名..."
              class="search-input"
            />
            <select v-model="sortBy" class="sort-select">
              <option value="score">按成绩排序</option>
              <option value="progress">按进步排序</option>
              <option value="homework">按作业完成排序</option>
            </select>
          </div>
        </div>
        <div class="student-list">
          <div
            v-for="student in filteredStudents"
            :key="student.id"
            class="student-item"
            :class="{ 'need-attention': student.needAttention }"
          >
            <div class="student-avatar">{{ student.name[0] }}</div>
            <div class="student-info">
              <div class="student-name">{{ student.name }}</div>
              <div class="student-progress">
                <div class="progress-bar">
                  <div
                    class="progress-fill"
                    :style="{ width: student.avgScore + '%', background: getScoreColor(student.avgScore) }"
                  ></div>
                </div>
                <span class="progress-text">{{ student.avgScore }}分</span>
              </div>
            </div>
            <div class="student-stats">
              <div class="stat">
                <span class="stat-label">作业</span>
                <span class="stat-value">{{ student.homeworkCount }}</span>
              </div>
              <div class="stat">
                <span class="stat-label">错题</span>
                <span class="stat-value" :class="{ high: student.errorCount > 10 }">{{ student.errorCount }}</span>
              </div>
              <div class="stat">
                <span class="stat-label">趋势</span>
                <span class="stat-value trend" :class="student.trend > 0 ? 'up' : 'down'">
                  {{ student.trend > 0 ? '↑' : '↓' }}
                </span>
              </div>
            </div>
            <button class="trend-btn" @click="showProgress(student)">📈 趋势</button>
            <button class="student-action" @click="viewStudentDetail(student)">详情</button>
          </div>
        </div>
      </div>
    </section>

    <!-- 📈 学生成长曲线弹窗 -->
    <div v-if="showProgressModal" class="modal-overlay" @click.self="showProgressModal = false">
      <div class="modal-content wide">
        <div class="modal-header">
          <h3>📈 {{ progressData.studentName }} 的学习成长曲线</h3>
          <button class="close-btn" @click="showProgressModal = false">×</button>
        </div>
        <div class="modal-body">
          <!-- 统计小卡片 -->
          <div class="progress-stats">
            <div class="stat-card">
              <span class="stat-num">{{ progressData.totalCount }}</span>
              <span class="stat-label">完成作业</span>
            </div>
            <div class="stat-card">
              <span class="stat-num">{{ progressData.avgScore }}</span>
              <span class="stat-label">平均分</span>
            </div>
            <div class="stat-card" :class="progressData.trend > 0 ? 'trend-up' : 'trend-down'">
              <span class="stat-num">{{ progressData.trend > 0 ? '+' : '' }}{{ progressData.trend }}</span>
              <span class="stat-label">成长趋势</span>
            </div>
            <div class="stat-card">
              <span class="stat-num">{{ progressData.maxScore }}</span>
              <span class="stat-label">最高分</span>
            </div>
          </div>
          <!-- 曲线图 -->
          <div ref="progressChartRef" class="progress-chart"></div>
          <!-- 作业历史 -->
          <div class="progress-table-wrap" v-if="progressData.points && progressData.points.length > 0">
            <table class="progress-table">
              <thead>
                <tr><th>次数</th><th>作业名称</th><th>日期</th><th>得分</th><th>环比</th></tr>
              </thead>
              <tbody>
                <tr v-for="p in progressData.points" :key="p.no">
                  <td>第{{ p.no }}次</td>
                  <td>{{ p.assignmentName }}</td>
                  <td>{{ p.date }}</td>
                  <td :class="scoreColorClass(p.score)">{{ p.score }}</td>
                  <td :class="changeClass(p.change)">
                    {{ p.change !== 0 ? (p.change > 0 ? '↑+' : '↓') + Math.abs(p.change) : '-' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-hint">暂无成长数据</div>
        </div>
      </div>
    </div>

    <!-- 添加知识点弹窗 -->
    <div v-if="showAddKpDialog" class="modal-overlay" @click.self="showAddKpDialog = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>✚ 添加知识点</h3>
          <button class="close-btn" @click="showAddKpDialog = false">×</button>
        </div>
        <div class="modal-body">
          <div class="config-item">
            <label>知识点名称</label>
            <input v-model="newKpItem.name" class="config-input" placeholder="例如：指针、数组、循环结构" />
          </div>
          <div class="config-item">
            <label>热力图颜色</label>
            <div class="color-picker-row">
              <input type="color" v-model="newKpItem.color" class="color-picker" />
              <span class="color-preview" :style="{ background: newKpItem.color }">{{ newKpItem.color }}</span>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="showAddKpDialog = false">取消</button>
          <button class="btn-primary" @click="addKnowledge" :disabled="!newKpItem.name.trim()">确认添加</button>
        </div>
      </div>
    </div>

    <!-- 知识点错误详情弹窗 -->
    <div v-if="showKpErrorModal" class="modal-overlay" @click.self="showKpErrorModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>🔥 {{ kpErrorTitle }} — 高频错误</h3>
          <button class="close-btn" @click="showKpErrorModal = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="kpErrorList.length === 0" class="empty-hint">暂无错误数据</div>
          <div v-else class="error-list">
            <div
              v-for="(error, index) in kpErrorList"
              :key="index"
              class="error-item"
              :class="{ 'high-frequency': index < 3 }"
            >
              <div class="error-rank">{{ index + 1 }}</div>
              <div class="error-content">
                <div class="error-title">{{ error.question }}</div>
                <div class="error-meta">
                  <span class="error-tag" :class="error.difficulty">{{ error.difficultyLabel }}</span>
                  <span class="error-count">{{ error.errorCount }} 次出现</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="showKpErrorModal = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- AI 教案生成弹窗 -->
    <div v-if="showTeachingPlanModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal-content">
        <div class="modal-header">
          <h3>🤖 AI 智能教案生成</h3>
          <button class="close-btn" @click="closeModal">×</button>
        </div>
        <div class="modal-body">
          <div class="plan-config">
            <div class="config-item">
              <label>教学目标</label>
              <div class="checkbox-group">
                <label v-for="goal in teachingGoals" :key="goal.value" class="checkbox-label">
                  <input type="checkbox" v-model="selectedGoals" :value="goal.value">
                  {{ goal.label }}
                </label>
              </div>
            </div>
            <div class="config-item">
              <label>针对薄弱知识点</label>
              <div class="tag-list">
                <span
                  v-for="tag in weakKnowledgePoints"
                  :key="tag"
                  class="tag-item"
                >
                  {{ tag }}
                </span>
              </div>
            </div>
            <div class="config-item">
              <label>教案类型</label>
              <select v-model="planType" class="config-select">
                <option value="review">复习课</option>
                <option value="practice">练习课</option>
                <option value="test">测试卷</option>
              </select>
            </div>
          </div>
          <div v-if="generatedPlan" class="plan-preview">
            <h4>生成结果预览</h4>
            <div class="plan-content" v-html="generatedPlan"></div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeModal">取消</button>
          <button
            class="btn-primary"
            @click="generatePlan"
            :disabled="generatingPlan || selectedGoals.length === 0"
          >
            {{ generatingPlan ? '生成中...' : '生成教案' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

export default {
  name: 'TeacherDashboard',
  data() {
    return {
      loading: false,
      selectedClass: null,
      selectedPeriod: 'week',
      studentFilter: '',
      sortBy: 'score',
      showTeachingPlanModal: false,
      generatingPlan: false,
      planType: 'review',
      selectedGoals: [],
      generatedPlan: '',
      apiBaseUrl: 'http://localhost:8080/api',

      // 班级列表
      classList: [],

      // 时间周期
      periods: [
        { label: '本周', value: 'week' },
        { label: '本月', value: 'month' },
        { label: '本学期', value: 'semester' }
      ],

      // 教学目标
      teachingGoals: [
        { label: '巩固基础', value: 'basic' },
        { label: '突破难点', value: 'difficult' },
        { label: '举一反三', value: 'extend' },
        { label: '查漏补缺', value: 'review' }
      ],

      // 热力图编辑
      isEditing: false,
      savingKnowledge: false,
      selectedKp: '',
      showAddKpDialog: false,
      showKpErrorModal: false,
      kpErrorList: [],
      kpErrorTitle: '',
      backupKnowledge: [],
      newKpItem: {
        name: '',
        color: '#1890ff'
      },

      // 核心指标
      metrics: {
        totalStudents: 0,
        studentTrend: 5,
        totalHomework: 0,
        newHomework: 0,
        avgScore: 0,
        scoreTrend: 3.2,
        warningStudents: 0
      },

      // 成绩分布
      scoreDistribution: [],

      // 知识点掌握度
      knowledgeMastery: [],

      // 高频错题
      frequentErrors: [],

      // 学生列表
      students: [],

      // 成长曲线
      showProgressModal: false,
      progressData: {
        studentName: '',
        totalCount: 0,
        avgScore: 0,
        maxScore: 0,
        trend: 0,
        points: []
      },
      progressChartRef: null,
      progressChart: null,
      progressChartResizeHandler: null
    }
  },

  mounted() {
    this.loadClassList()
  },

  beforeDestroy() {
    this.disposeChart()
  },

  watch: {
    selectedClass(newVal) {
      if (newVal && newVal !== 'null') {
        this.loadAllData()
      }
    },
    // 弹窗关闭时清理图表
    showProgressModal(val) {
      if (!val) {
        this.$nextTick(() => this.disposeChart())
      }
    }
  },

  computed: {
    // 薄弱知识点（掌握度 < 70%）
    weakKnowledgePoints() {
      return this.knowledgeMastery
        .filter(k => k.mastery < 70)
        .map(k => k.name)
    },

    // 筛选和排序后的学生列表
    filteredStudents() {
      let result = this.students

      // 搜索筛选
      if (this.studentFilter) {
        result = result.filter(s => s.name.includes(this.studentFilter))
      }

      return result
    }
  },

  methods: {
    // 获取 Token
    getToken() {
      return localStorage.getItem('token') || ''
    },

    // 加载班级列表
    async loadClassList() {
      try {
        const response = await fetch(`${this.apiBaseUrl}/dashboard/classes`, {
          headers: {
            'Authorization': `Bearer ${this.getToken()}`
          }
        })

        if (!response.ok) {
          throw new Error('获取班级列表失败')
        }

        const result = await response.json()
        if (result.code === 200) {
          this.classList = result.data
          if (this.classList.length > 0) {
            this.selectedClass = this.classList[0].id
          }
        }
      } catch (error) {
        console.error('加载班级列表失败:', error)
        this.$message?.error('加载班级列表失败')
      }
    },

    // 加载所有数据
    async loadAllData() {
      this.loading = true
      await Promise.all([
        this.loadMetrics(),
        this.loadScoreDistribution(),
        this.loadKnowledgeMastery(),
        this.loadFrequentErrors(),
        this.loadStudents()
      ])
      this.loading = false
    },

    // 加载核心指标
    async loadMetrics() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const response = await fetch(`${this.apiBaseUrl}/dashboard/metrics?classId=${this.selectedClass}`, {
          headers: {
            'Authorization': `Bearer ${this.getToken()}`
          }
        })

        if (!response.ok) throw new Error('获取指标失败')

        const result = await response.json()
        if (result.code === 200) {
          this.metrics = result.data
        }
      } catch (error) {
        console.error('加载指标失败:', error)
      }
    },

    // 加载成绩分布
    async loadScoreDistribution() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const response = await fetch(`${this.apiBaseUrl}/dashboard/score-distribution?classId=${this.selectedClass}`, {
          headers: {
            'Authorization': `Bearer ${this.getToken()}`
          }
        })

        if (!response.ok) throw new Error('获取成绩分布失败')

        const result = await response.json()
        if (result.code === 200) {
          this.scoreDistribution = result.data
        }
      } catch (error) {
        console.error('加载成绩分布失败:', error)
      }
    },

    // 加载知识点掌握度
    async loadKnowledgeMastery() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const response = await fetch(`${this.apiBaseUrl}/dashboard/knowledge-mastery?classId=${this.selectedClass}`, {
          headers: {
            'Authorization': `Bearer ${this.getToken()}`
          }
        })

        if (!response.ok) throw new Error('获取知识点掌握度失败')

        const result = await response.json()
        console.log('知识点掌握度接口返回:', result)
        if (result.code === 200) {
          this.knowledgeMastery = result.data
          console.log('热力图数据:', this.knowledgeMastery)
        }
      } catch (error) {
        console.error('加载知识点掌握度失败:', error)
      }
    },

    // 加载高频错题（支持按知识点筛选）
    async loadFrequentErrors() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        let url = `${this.apiBaseUrl}/dashboard/frequent-errors?classId=${this.selectedClass}`
        if (this.selectedKp) {
          url += `&knowledgePoint=${encodeURIComponent(this.selectedKp)}`
        }
        const response = await fetch(url, {
          headers: {
            'Authorization': `Bearer ${this.getToken()}`
          }
        })

        if (!response.ok) throw new Error('获取高频错题失败')

        const result = await response.json()
        if (result.code === 200) {
          this.frequentErrors = result.data
        }
      } catch (error) {
        console.error('加载高频错题失败:', error)
      }
    },

    // 加载学生列表
    async loadStudents() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const response = await fetch(
          `${this.apiBaseUrl}/dashboard/students?classId=${this.selectedClass}&sortBy=${this.sortBy}`,
          {
            headers: {
              'Authorization': `Bearer ${this.getToken()}`
            }
          }
        )

        if (!response.ok) throw new Error('获取学生列表失败')

        const result = await response.json()
        if (result.code === 200) {
          this.students = result.data
        }
      } catch (error) {
        console.error('加载学生列表失败:', error)
      }
    },

    // 刷新数据
    async refreshData() {
      this.loading = true
      await this.loadAllData()
      this.loading = false
      this.$message?.success('数据已刷新')
    },

    // ========== 热力图编辑 ==========

    // 进入编辑模式
    toggleEdit() {
      this.isEditing = true
      this.backupKnowledge = JSON.parse(JSON.stringify(this.knowledgeMastery))
    },

    // 取消编辑
    cancelEdit() {
      this.knowledgeMastery = this.backupKnowledge
      this.isEditing = false
    },

    // 添加知识点 → POST 单条到后端
    async addKnowledge() {
      if (!this.newKpItem.name.trim()) return
      if (!this.selectedClass || this.selectedClass === 'null') {
        this.$message?.error('请先选择班级')
        return
      }

      try {
        this.savingKnowledge = true
        const response = await fetch(`${this.apiBaseUrl}/dashboard/teacher-knowledge/add`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${this.getToken()}`
          },
          body: JSON.stringify({
            classId: this.selectedClass,
            name: this.newKpItem.name.trim(),
            color: this.newKpItem.color
          })
        })
        const result = await response.json()
        if (response.ok && result.code === 200) {
          this.$message?.success('添加成功，正在后台重归类历史错误...')
          this.newKpItem = { name: '', color: '#1890ff' }
          this.showAddKpDialog = false
          await this.loadKnowledgeMastery()
          this.pollReclassifyResult()
        } else {
          throw new Error(result.message || '添加失败')
        }
      } catch (error) {
        console.error('添加知识点失败:', error)
        this.$message?.error('添加失败: ' + error.message)
      } finally {
        this.savingKnowledge = false
      }
    },

    // 移除知识点 → DELETE 从后端删除
    async removeKnowledge(index) {
      const item = this.knowledgeMastery[index]
      if (!item || !item.id) {
        this.knowledgeMastery.splice(index, 1)
        return
      }

      try {
        const response = await fetch(`${this.apiBaseUrl}/dashboard/teacher-knowledge/${item.id}`, {
          method: 'DELETE',
          headers: {
            'Authorization': `Bearer ${this.getToken()}`
          }
        })
        const result = await response.json()
        if (response.ok && result.code === 200) {
          this.knowledgeMastery.splice(index, 1)
          this.$message?.success('删除成功')
        } else {
          throw new Error(result.message || '删除失败')
        }
      } catch (error) {
        console.error('删除知识点失败:', error)
        this.$message?.error('删除失败: ' + error.message)
      }
    },

    // 编辑模式保存 → 批量覆盖后端
    async saveKnowledge() {
      if (!this.selectedClass || this.selectedClass === 'null') {
        this.$message?.error('请先选择班级')
        return
      }

      this.savingKnowledge = true
      try {
        const items = this.knowledgeMastery.map((k, i) => ({
            name: k.name,
            color: k.color || '#1890ff',
            sortOrder: i
          }))

        const response = await fetch(`${this.apiBaseUrl}/dashboard/teacher-knowledge/batch`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${this.getToken()}`
          },
          body: JSON.stringify({
            classId: this.selectedClass,
            items: items
          })
        })

        const result = await response.json()
        if (response.ok && result.code === 200) {
          this.$message?.success('保存成功，正在后台重归类历史错误...')
          this.isEditing = false
          await this.loadKnowledgeMastery()
          this.pollReclassifyResult()
        } else {
          throw new Error(result.message || '保存失败')
        }
      } catch (error) {
        console.error('保存知识点失败:', error)
        this.$message?.error('保存失败: ' + error.message)
      } finally {
        this.savingKnowledge = false
      }
    },

    // ========== 重归类轮询 ==========

    pollReclassifyResult() {
      // 记录当前「其他」的错误数
      const otherItem = this.knowledgeMastery.find(k => k.name === '其他')
      const beforeCount = otherItem ? otherItem.errorCount : 0

      const check = (attempt) => {
        if (attempt > 5) return
        setTimeout(async () => {
          await this.loadKnowledgeMastery()
          const current = this.knowledgeMastery.find(k => k.name === '其他')
          const afterCount = current ? current.errorCount : 0
          if (afterCount < beforeCount) {
            this.$message?.success(`重归类完成，${beforeCount - afterCount}条错误已重新分配`)
            await this.loadFrequentErrors()
          } else if (attempt < 5) {
            check(attempt + 1)
          }
        }, 3000)
      }
      check(1)
    },

    // ========== 知识点错误弹窗 ==========

    // 点击热力图格子 → 弹窗展示该知识点的高频错误
    async showKpErrors(item) {
      this.selectedKp = item.name
      this.kpErrorTitle = item.name
      this.showKpErrorModal = true

      try {
        const url = `${this.apiBaseUrl}/dashboard/frequent-errors?classId=${this.selectedClass}&knowledgePoint=${encodeURIComponent(item.name)}`
        const response = await fetch(url, {
          headers: { 'Authorization': `Bearer ${this.getToken()}` }
        })
        const result = await response.json()
        if (result.code === 200) {
          this.kpErrorList = result.data
        }
      } catch (error) {
        console.error('加载知识点错误详情失败:', error)
        this.kpErrorList = []
      }
    },

    // ========== 原有方法 ==========

    // 获取热力图颜色（仅展示模式用）
    getHeatmapColor(mastery) {
      if (mastery >= 80) return '#52c41a'
      if (mastery >= 60) return '#faad14'
      return '#f5222d'
    },

    // 获取成绩颜色
    getScoreColor(score) {
      if (score >= 80) return '#52c41a'
      if (score >= 60) return '#faad14'
      return '#f5222d'
    },

    // 查看错题详情
    viewErrorDetail(error) {
      alert(`错题详情：${error.question}\n\n错误率：${error.errorRate}%\n错误人数：${error.errorCount}人`)
    },

    // 查看学生详情
    viewStudentDetail(student) {
      alert(`学生详情：${student.name}\n\n平均分：${student.avgScore}\n作业数：${student.homeworkCount}\n错题数：${student.errorCount}`)
    },

    // 📈 查看学生成长曲线
    async showProgress(student) {
      this.progressData = {
        studentName: student.name,
        totalCount: 0,
        avgScore: 0,
        maxScore: 0,
        trend: 0,
        points: []
      }
      this.showProgressModal = true

      try {
        const params = new URLSearchParams({ studentName: student.name, classId: this.selectedClass })
        if (student.studentId) params.append('studentId', student.studentId)
        const response = await fetch(
          `${this.apiBaseUrl}/dashboard/student-progress?${params}`,
          { headers: { 'Authorization': `Bearer ${this.getToken()}` } }
        )

        if (!response.ok) {
          const err = await response.json()
          this.$message?.error(err.message || '获取成长数据失败')
          return
        }

        const result = await response.json()
        if (result.code === 200) {
          this.progressData = result.data
          // 等 DOM 更新后渲染图表
          this.$nextTick(() => this.renderProgressChart(result.data.points))
        }
      } catch (error) {
        console.error('加载成长曲线失败:', error)
        this.$message?.error('加载成长曲线失败')
      }
    },

    // 📈 渲染曲线图
    renderProgressChart(points) {
      if (!points || points.length === 0) return

      // 先清理旧图表实例
      this.disposeChart()

      const container = this.$refs.progressChartRef
      if (!container) return

      const chart = echarts.init(container)
      this.progressChart = chart

      chart.setOption({
        tooltip: {
          trigger: 'axis',
          formatter: function(params) {
            const p = points[params[0].dataIndex]
            return `<div style="padding:8px;font-size:14px">
              <b>第${p.no}次作业</b><br/>
              ${p.assignmentName}<br/>
              得分: <span style="color:#667eea;font-weight:bold;">${p.score}</span> 分<br/>
              提交: ${p.date}
            </div>`
          }
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '10%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: points.map(p => `第${p.no}次`),
          axisLabel: { color: '#666' }
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 100,
          name: '分数',
          nameTextStyle: { color: '#666' },
          axisLabel: { color: '#666' }
        },
        series: [{
          data: points.map(p => p.score),
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 10,
          lineStyle: { width: 3, color: '#667eea' },
          areaStyle: {
            color: {
              type: 'linear',
              x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [
                { offset: 0, color: 'rgba(102,126,234,0.3)' },
                { offset: 1, color: 'rgba(102,126,234,0.05)' }
              ]
            }
          },
          markLine: {
            silent: true,
            data: [
              { type: 'average', name: '平均分', lineStyle: { color: '#faad14', type: 'dashed' } },
              { yAxis: 60, lineStyle: { color: '#f5222d', type: 'dashed' }, label: { formatter: '及格线' } }
            ]
          }
        }]
      })

      // 窗口自适应
      const resizeHandler = () => chart.resize()
      window.addEventListener('resize', resizeHandler)
      this.progressChartResizeHandler = resizeHandler
    },

    // 清理 ECharts 实例和事件监听，防止内存泄漏
    disposeChart() {
      if (this.progressChart) {
        this.progressChart.dispose()
        this.progressChart = null
      }
      if (this.progressChartResizeHandler) {
        window.removeEventListener('resize', this.progressChartResizeHandler)
        this.progressChartResizeHandler = null
      }
    },

    // 分数颜色
    scoreColorClass(score) {
      if (score >= 90) return 'score-excellent'
      if (score >= 80) return 'score-good'
      if (score >= 60) return 'score-pass'
      return 'score-fail'
    },

    // 环比变化颜色
    changeClass(change) {
      if (change > 0) return 'change-up'
      if (change < 0) return 'change-down'
      return ''
    },

    // 打开教案生成弹窗
    async generateTeachingPlan() {
      this.showTeachingPlanModal = true
      this.selectedGoals = ['difficult', 'review']
      this.generatedPlan = ''

      // 加载薄弱知识点
      try {
        const response = await fetch(`${this.apiBaseUrl}/dashboard/weak-points?classId=${this.selectedClass}`, {
          headers: {
            'Authorization': `Bearer ${this.getToken()}`
          }
        })

        if (response.ok) {
          const result = await response.json()
          if (result.code === 200) {
            // 使用后端返回的薄弱知识点
            console.log('薄弱知识点:', result.data)
          }
        }
      } catch (error) {
        console.error('加载薄弱知识点失败:', error)
      }
    },

    // 关闭弹窗
    closeModal() {
      this.showTeachingPlanModal = false
    },

    // 生成教案
    async generatePlan() {
      this.generatingPlan = true

      try {
        const response = await fetch(`${this.apiBaseUrl}/dashboard/teaching-plan/generate`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${this.getToken()}`
          },
          body: JSON.stringify({
            classId: this.selectedClass,
            goals: this.selectedGoals,
            planType: this.planType,
            weakKnowledgePoints: this.weakKnowledgePoints.slice(0, 3)
          })
        })

        if (!response.ok) throw new Error('生成教案失败')

        const result = await response.json()
        if (result.code === 200) {
          this.generatedPlan = result.data
        }
      } catch (error) {
        console.error('生成教案失败:', error)
        this.$message?.error('生成教案失败')
      } finally {
        this.generatingPlan = false
      }
    }
  }
}
</script>

<style scoped>
.dashboard-page {
  padding: 1.5rem;
  max-width: 1400px;
  margin: 0 auto;
  background: #f5f7fa;
  min-height: 100vh;
}

/* 头部样式 */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.5rem;
  background: white;
  padding: 1.5rem;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}

.header-left h1 {
  margin: 0 0 0.5rem 0;
  font-size: 1.5rem;
  color: #1a202c;
}

.subtitle {
  color: #718096;
  font-size: 0.9rem;
  margin: 0;
}

.header-right {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}

.class-selector {
  padding: 0.5rem 1rem;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 0.9rem;
  background: white;
  cursor: pointer;
}

.refresh-btn {
  padding: 0.5rem 1rem;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.refresh-btn:hover:not(:disabled) {
  background: #5568d3;
}

.refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 指标卡片 */
.metrics-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.metric-card {
  background: white;
  padding: 1.25rem;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 1rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

.metric-icon {
  font-size: 2rem;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f7fafc;
  border-radius: 12px;
}

.metric-content {
  display: flex;
  flex-direction: column;
}

.metric-value {
  font-size: 1.75rem;
  font-weight: 700;
  color: #1a202c;
}

.metric-label {
  font-size: 0.85rem;
  color: #718096;
  margin-bottom: 0.25rem;
}

.metric-trend {
  font-size: 0.8rem;
  font-weight: 500;
}

.metric-trend.up {
  color: #52c41a;
}

.metric-trend.down {
  color: #f5222d;
}

/* 图表区域 */
.charts-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.chart-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  overflow: hidden;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e2e8f0;
}

.chart-header h3 {
  margin: 0;
  font-size: 1rem;
  color: #1a202c;
}

.chart-actions {
  display: flex;
  gap: 0.5rem;
}

.period-btn {
  padding: 0.375rem 0.75rem;
  border: 1px solid #e2e8f0;
  background: white;
  border-radius: 6px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s;
}

.period-btn.active,
.period-btn:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

.action-btn {
  padding: 0.375rem 0.75rem;
  background: #52c41a;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover:not(:disabled) {
  background: #45a049;
}

.action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.action-btn.edit-btn {
  background: #1890ff;
}

.action-btn.edit-btn:hover {
  background: #096dd9;
}

.action-btn.save-btn {
  background: #52c41a;
}

.action-btn.cancel-edit-btn {
  background: #ff4d4f;
}

.action-btn.cancel-edit-btn:hover {
  background: #cf1322;
}

.action-btn.teaching-btn {
  background: #722ed1;
}

.action-btn.teaching-btn:hover {
  background: #531dab;
}



.chart-body {
  padding: 1.25rem;
}

/* 成绩分布图 */
.score-distribution {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.distribution-bar {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.bar-label {
  width: 80px;
  font-size: 0.85rem;
  color: #4a5568;
  text-align: right;
}

.bar-track {
  flex: 1;
  height: 24px;
  background: #edf2f7;
  border-radius: 4px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.bar-value {
  width: 100px;
  font-size: 0.8rem;
  color: #718096;
}

/* 热力图 */
.knowledge-heatmap {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0.5rem;
}

.heatmap-item {
  position: relative;
  padding: 0.75rem;
  border-radius: 8px;
  text-align: center;
  color: white;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.heatmap-item:hover:not(.editing) {
  transform: scale(1.05);
}

.heatmap-item.active {
  box-shadow: 0 0 0 3px white, 0 0 0 5px currentColor;
}

.heatmap-item.editing {
  background: #e8e8e8 !important;
  color: #333;
  cursor: default;
  border: 2px dashed #d9d9d9;
}

.heatmap-name {
  display: block;
  font-size: 0.8rem;
  margin-bottom: 0.25rem;
}

.heatmap-value {
  display: block;
  font-size: 1.1rem;
  font-weight: 600;
}

.heatmap-sub {
  display: block;
  font-size: 0.7rem;
  margin-top: 0.25rem;
  opacity: 0.8;
}

.heatmap-remove {
  position: absolute;
  top: -8px;
  right: -8px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: #f5222d;
  color: white;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
  cursor: pointer;
  padding: 0;
  z-index: 1;
}

.heatmap-remove:hover {
  background: #cf1322;
}

.heatmap-remove-placeholder {
  display: inline-block;
  width: 20px;
  height: 20px;
}

.heatmap-edit-content {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.heatmap-name-input {
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 0.3rem 0.4rem;
  font-size: 0.75rem;
  text-align: center;
  width: 100%;
  box-sizing: border-box;
}

.heatmap-name-input:focus {
  outline: none;
  border-color: #1890ff;
}

.heatmap-color-row {
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.heatmap-color-picker {
  width: 24px;
  height: 24px;
  border: none;
  padding: 0;
  cursor: pointer;
  border-radius: 4px;
}

.heatmap-color-preview {
  font-size: 0.75rem;
  font-family: monospace;
  color: #666;
}

.heatmap-empty {
  grid-column: 1 / -1;
  padding: 2rem;
}

/* 弹窗表单 */
.config-input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 0.9rem;
  box-sizing: border-box;
}

.config-input:focus {
  outline: none;
  border-color: #1890ff;
}

.mastery-input-row {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.mastery-value {
  font-size: 1.1rem;
  font-weight: 600;
  color: #1890ff;
  min-width: 3rem;
}

.color-picker-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.color-picker {
  width: 40px;
  height: 40px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 2px;
  cursor: pointer;
}

.color-preview {
  padding: 0.25rem 0.75rem;
  border-radius: 4px;
  color: white;
  font-size: 0.8rem;
  font-family: monospace;
}

/* 详情区域 */
.details-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.detail-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e2e8f0;
}

.card-header h3 {
  margin: 0;
  font-size: 1rem;
  color: #1a202c;
}

.tag {
  padding: 0.25rem 0.5rem;
  background: #e6f7ff;
  color: #1890ff;
  border-radius: 4px;
  font-size: 0.75rem;
}

/* 错题列表 */
.error-list {
  padding: 0.75rem;
  max-height: 400px;
  overflow-y: auto;
}

.error-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border-radius: 8px;
  margin-bottom: 0.5rem;
  background: #f7fafc;
  transition: background 0.2s;
}

.error-item:hover {
  background: #edf2f7;
}

.error-item.high-frequency {
  background: #fff2f0;
  border: 1px solid #ffccc7;
}

.error-rank {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #667eea;
  color: white;
  border-radius: 50%;
  font-size: 0.8rem;
  font-weight: 600;
}

.error-item.high-frequency .error-rank {
  background: #f5222d;
}

.error-content {
  flex: 1;
}

.error-title {
  font-size: 0.9rem;
  color: #1a202c;
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error-meta {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.error-tag {
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  font-size: 0.7rem;
}

.error-tag.easy {
  background: #d4edda;
  color: #155724;
}

.error-tag.medium {
  background: #fff3cd;
  color: #856404;
}

.error-tag.hard {
  background: #f8d7da;
  color: #721c24;
}

.error-rate {
  font-size: 0.75rem;
  color: #f5222d;
  font-weight: 500;
}

.error-count {
  font-size: 0.75rem;
  color: #718096;
}

.error-action {
  padding: 0.375rem 0.75rem;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s;
}

.error-action:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 0.5rem;
}

.search-input {
  padding: 0.375rem 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.85rem;
  width: 150px;
}

.sort-select {
  padding: 0.375rem 0.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.85rem;
  background: white;
}

/* 学生列表 */
.student-list {
  padding: 0.75rem;
  max-height: 400px;
  overflow-y: auto;
}

.student-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  border-radius: 8px;
  margin-bottom: 0.5rem;
  background: #f7fafc;
  transition: all 0.2s;
}

.student-item:hover {
  background: #edf2f7;
}

.student-item.need-attention {
  background: #fff2f0;
  border: 1px solid #ffccc7;
}

.student-avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #667eea;
  color: white;
  border-radius: 50%;
  font-size: 1rem;
  font-weight: 600;
}

.student-info {
  flex: 1;
}

.student-name {
  font-size: 0.9rem;
  font-weight: 500;
  color: #1a202c;
  margin-bottom: 0.25rem;
}

.student-progress {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.progress-bar {
  width: 100px;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}

.progress-text {
  font-size: 0.8rem;
  color: #4a5568;
  font-weight: 500;
}

.student-stats {
  display: flex;
  gap: 1rem;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-label {
  font-size: 0.7rem;
  color: #718096;
}

.stat-value {
  font-size: 0.9rem;
  font-weight: 600;
  color: #1a202c;
}

.stat-value.high {
  color: #f5222d;
}

.stat-value.trend.up {
  color: #52c41a;
}

.stat-value.trend.down {
  color: #f5222d;
}

.student-action {
  padding: 0.375rem 0.75rem;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.8rem;
  cursor: pointer;
  transition: all 0.2s;
}

.student-action:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
}

/* 弹窗样式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 12px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e2e8f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.1rem;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #718096;
}

.modal-body {
  padding: 1.25rem;
  overflow-y: auto;
  flex: 1;
}

.plan-config {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.config-item label {
  display: block;
  font-size: 0.9rem;
  font-weight: 500;
  color: #1a202c;
  margin-bottom: 0.5rem;
}

.checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.85rem;
  cursor: pointer;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.tag-item {
  padding: 0.375rem 0.75rem;
  background: #fff2f0;
  color: #cf1322;
  border-radius: 4px;
  font-size: 0.8rem;
}

.config-select {
  padding: 0.5rem;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 0.9rem;
  width: 100%;
}

.plan-preview {
  margin-top: 1.5rem;
  padding: 1rem;
  background: #f7fafc;
  border-radius: 8px;
}

.plan-preview h4 {
  margin: 0 0 0.75rem 0;
  font-size: 0.95rem;
}

.plan-content {
  font-size: 0.9rem;
  line-height: 1.6;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-top: 1px solid #e2e8f0;
}

.btn-secondary {
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.9rem;
}

.btn-primary {
  padding: 0.5rem 1rem;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.btn-primary:hover:not(:disabled) {
  background: #5568d3;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式 */
@media (max-width: 1200px) {
  .metrics-section {
    grid-template-columns: repeat(2, 1fr);
  }

  .charts-section,
  .details-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .metrics-section {
    grid-template-columns: 1fr;
  }

  .dashboard-header {
    flex-direction: column;
    gap: 1rem;
  }

  .header-right {
    width: 100%;
    justify-content: flex-end;
  }

  .knowledge-heatmap {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* ========== 📈 成长曲线弹窗样式 ========== */
.modal-content.wide {
  max-width: 720px;
  width: 90%;
}

.progress-stats {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  flex: 1;
  background: #f5f7fa;
  border-radius: 10px;
  padding: 14px;
  text-align: center;
}

.stat-card .stat-num {
  display: block;
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

.stat-card .stat-label {
  display: block;
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}

.stat-card.trend-up .stat-num {
  color: #52c41a;
}

.stat-card.trend-down .stat-num {
  color: #f5222d;
}

.progress-chart {
  width: 100%;
  height: 300px;
  margin-bottom: 20px;
}

.progress-table-wrap {
  max-height: 300px;
  overflow-y: auto;
}

.progress-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.progress-table th {
  background: #f5f7fa;
  padding: 10px 12px;
  text-align: left;
  color: #666;
  font-weight: 600;
  border-bottom: 2px solid #e8e8e8;
}

.progress-table td {
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
  color: #333;
}

.progress-table tr:hover td {
  background: #fafafa;
}

.score-excellent { color: #52c41a; font-weight: 600; }
.score-good { color: #1890ff; font-weight: 600; }
.score-pass { color: #d46b08; font-weight: 600; }
.score-fail { color: #cf1322; font-weight: 600; }

.change-up { color: #52c41a; font-weight: 600; }
.change-down { color: #cf1322; font-weight: 600; }

.empty-hint {
  text-align: center;
  padding: 30px;
  color: #999;
}

.trend-btn {
  padding: 6px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  background: white;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
  margin-right: 6px;
}

.trend-btn:hover {
  border-color: #667eea;
  background: #f0f2ff;
  color: #667eea;
}
</style>
