import * as echarts from 'echarts'
import request from '@/shared/api/request'
import { sanitizeHtml } from '@/shared/utils/safeHtml'

export default {
  name: 'TeacherDashboard',
  data() {
    return {
      loading: false,
      selectedClass: null,
      studentFilter: '',
      sortBy: 'score',
      showAllStudents: false,
      lastUpdatedAt: null,
      showTeachingPlanModal: false,
      generatingPlan: false,
      planType: 'review',
      selectedGoals: [],
      generatedPlan: '',

      // 班级列表
      classList: [],

      // 教学目标
      teachingGoals: [
        { label: '巩固基础', value: 'basic' },
        { label: '突破难点', value: 'difficult' },
        { label: '举一反三', value: 'extend' },
        { label: '查漏补缺', value: 'review' },
      ],

      // 热力图编辑
      isEditing: false,
      savingKnowledge: false,
      selectedKp: '',
      showAddKpDialog: false,
      showKpErrorModal: false,
      kpErrorLoading: false,
      kpErrorList: [],
      kpErrorTitle: '',
      kpErrorSearch: '',
      kpErrorFilter: 'all',
      backupKnowledge: [],
      newKpItem: {
        name: '',
        color: '#1890ff',
      },
      knowledgeColorOptions: ['#655bd7', '#1890ff', '#13a47a', '#e39a24', '#e35d68', '#8b5fc7'],
      reclassificationTask: null,
      reclassificationPollTimer: null,

      // 核心指标
      metrics: {
        totalStudents: 0,
        studentTrend: 5,
        totalHomework: 0,
        newHomework: 0,
        avgScore: 0,
        scoreTrend: 3.2,
        warningStudents: 0,
      },

      // 成绩分布
      scoreDistribution: [],

      // 知识点掌握度
      knowledgeMastery: [],

      // 高频错题
      frequentErrors: [],

      // 学生列表
      students: [],

      // 学生诊断详情
      showStudentDetailModal: false,
      studentDetailLoading: false,
      selectedStudentDetail: null,
      studentDetail: {
        studentName: '',
        totalCount: 0,
        avgScore: 0,
        maxScore: 0,
        minScore: 0,
        trend: 0,
        points: [],
        summary: null,
        risk: null,
        weakKnowledgePoints: [],
        recentErrors: [],
      },

      // 成长曲线
      showProgressModal: false,
      progressData: {
        studentName: '',
        totalCount: 0,
        avgScore: 0,
        maxScore: 0,
        minScore: 0,
        trend: 0,
        points: [],
      },
      progressChartRef: null,
      progressChart: null,
      progressChartResizeHandler: null,

      // 学生"不懂"标记
      confusions: [],
      confusionStats: [],
      liveEvents: [],
      liveStats: [],
    }
  },

  mounted() {
    this.loadClassList()
  },

  beforeUnmount() {
    this.disposeChart()
    if (this.reclassificationPollTimer) clearTimeout(this.reclassificationPollTimer)
  },

  watch: {
    selectedClass(newVal) {
      if (newVal && newVal !== 'null') {
        this.showAllStudents = false
        this.studentFilter = ''
        this.loadAllData()
      }
    },
    // 弹窗关闭时清理图表
    showProgressModal(val) {
      if (!val) {
        this.$nextTick(() => this.disposeChart())
      }
    },
  },

  computed: {
    safeGeneratedPlan() {
      return sanitizeHtml(this.generatedPlan)
    },

    isKnowledgeNameDuplicate() {
      const normalized = this.newKpItem.name.trim().toLowerCase()
      if (!normalized) return false
      return this.knowledgeMastery.some((item) => item.name.trim().toLowerCase() === normalized)
    },

    selectedKnowledgeItem() {
      return this.knowledgeMastery.find((item) => item.name === this.kpErrorTitle) || null
    },

    kpErrorTotalOccurrences() {
      return this.kpErrorList.reduce((sum, item) => sum + Number(item.errorCount || 0), 0)
    },

    kpErrorHighCount() {
      return this.kpErrorList
        .filter((item) => item.difficulty === 'high')
        .reduce((sum, item) => sum + Number(item.errorCount || 0), 0)
    },

    kpErrorFilterOptions() {
      const countByDifficulty = (difficulty) =>
        this.kpErrorList.filter((item) => item.difficulty === difficulty).length
      return [
        { label: '全部', value: 'all', count: this.kpErrorList.length },
        { label: '高严重度', value: 'high', count: countByDifficulty('high') },
        { label: '中等', value: 'medium', count: countByDifficulty('medium') },
        { label: '一般', value: 'low', count: countByDifficulty('low') },
      ]
    },

    filteredKpErrors() {
      const keyword = this.kpErrorSearch.trim().toLowerCase()
      return this.kpErrorList.filter((item) => {
        const matchesFilter = this.kpErrorFilter === 'all' || item.difficulty === this.kpErrorFilter
        const matchesKeyword =
          !keyword ||
          String(item.question || '')
            .toLowerCase()
            .includes(keyword)
        return matchesFilter && matchesKeyword
      })
    },

    studentDetailLatest() {
      const points = this.studentDetail.points || []
      return points.length ? points[points.length - 1] : null
    },

    studentDetailLatestChange() {
      return Number(this.studentDetailLatest?.change || 0)
    },

    studentDetailRecentPoints() {
      return [...(this.studentDetail.points || [])].slice(-6).reverse()
    },

    studentDetailAssessment() {
      if (this.studentDetail.risk) {
        const level = this.studentDetail.risk.level
        return {
          tone: level === 'HIGH' ? 'danger' : level === 'MEDIUM' ? 'warning' : 'success',
          title:
            level === 'HIGH'
              ? '建议优先跟进'
              : level === 'MEDIUM'
                ? '需要继续巩固'
                : '当前表现稳定',
          description: this.studentDetail.risk.reasons?.join('；') || '当前未发现明确风险信号。',
        }
      }
      const latest = Number(this.studentDetailLatest?.score)
      const change = this.studentDetailLatestChange
      if (!this.studentDetailLatest) {
        return {
          tone: 'muted',
          title: '暂无足够数据',
          description: '至少完成一次有效提交后，系统才能形成学习判断。',
        }
      }
      if (latest < 60 || change <= -10) {
        return {
          tone: 'danger',
          title: '建议优先跟进',
          description:
            latest < 60
              ? `最近一次成绩为 ${latest} 分，尚未达到及格线。`
              : `最近一次成绩下降 ${Math.abs(change)} 分，波动较为明显。`,
        }
      }
      if (latest < 70 || change < 0) {
        return {
          tone: 'warning',
          title: '需要继续巩固',
          description:
            latest < 70
              ? `最近一次成绩为 ${latest} 分，基础掌握仍有提升空间。`
              : `最近一次成绩下降 ${Math.abs(change)} 分，建议观察下一次表现。`,
        }
      }
      return {
        tone: 'success',
        title: change > 0 ? '近期表现向好' : '当前表现稳定',
        description:
          change > 0
            ? `最近一次成绩提升 ${change} 分，可以继续保持当前学习节奏。`
            : `最近一次成绩为 ${latest} 分，暂未发现明显风险。`,
      }
    },

    studentDetailSuggestions() {
      if (this.studentDetail.risk?.suggestions?.length) {
        return this.studentDetail.risk.suggestions
      }
      const suggestions = []
      const latest = Number(this.studentDetailLatest?.score)
      const change = this.studentDetailLatestChange
      if (!this.studentDetailLatest) return ['等待产生有效作业记录后再进行判断']
      if (latest < 60) suggestions.push('优先回看最近一次作业中的基础性错误')
      else if (latest < 70) suggestions.push('安排一组基础巩固练习，确认核心概念掌握情况')
      else suggestions.push('保持当前任务难度，并逐步加入迁移应用题')
      if (change < 0) suggestions.push('对比最近两次作业，确认下降集中在哪类题目')
      else if (change > 0) suggestions.push('肯定近期进步，并继续观察下一次作业是否保持')
      if (Number(this.studentDetail.totalCount) < 3) {
        suggestions.push('当前样本较少，建议至少积累 3 次作业后再判断长期趋势')
      } else {
        suggestions.push('结合完整成长曲线判断波动是偶发还是持续')
      }
      return suggestions
    },

    reclassificationProgress() {
      if (!this.reclassificationTask?.total) {
        return this.reclassificationTask?.status === 'RUNNING' ? 8 : 0
      }
      return Math.min(
        100,
        Math.round(
          ((this.reclassificationTask.processed || 0) * 100) / this.reclassificationTask.total,
        ),
      )
    },

    selectedClassName() {
      return this.classList.find((item) => item.id === this.selectedClass)?.name || '尚未选择班级'
    },

    lastUpdatedLabel() {
      if (!this.lastUpdatedAt) return '等待数据更新'
      return `更新于 ${new Intl.DateTimeFormat('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(this.lastUpdatedAt)}`
    },

    scoredStudents() {
      return this.students.filter((student) => Number(student.homeworkCount) > 0)
    },

    passRate() {
      if (!this.scoredStudents.length) return 0
      const passed = this.scoredStudents.filter((student) => Number(student.avgScore) >= 60).length
      return Math.round((passed * 1000) / this.scoredStudents.length) / 10
    },

    riskStudents() {
      return this.scoredStudents
        .filter((student) => Number(student.avgScore) < 60)
        .sort((a, b) => Number(a.avgScore) - Number(b.avgScore))
    },

    knowledgeMasterySorted() {
      return [...this.knowledgeMastery].sort((a, b) => {
        if (a.name === '其他') return 1
        if (b.name === '其他') return -1
        return Number(a.mastery) - Number(b.mastery)
      })
    },

    knowledgePointCount() {
      return this.knowledgeMastery.filter((item) => item.name !== '其他').length
    },

    otherKnowledge() {
      return this.knowledgeMastery.find((item) => item.name === '其他') || null
    },

    totalKnowledgeErrors() {
      return this.knowledgeMastery.reduce((sum, item) => sum + Number(item.errorCount || 0), 0)
    },

    otherRate() {
      if (!this.otherKnowledge || !this.totalKnowledgeErrors) return 0
      return (
        Math.round(
          (Number(this.otherKnowledge.errorCount || 0) * 1000) / this.totalKnowledgeErrors,
        ) / 10
      )
    },

    otherCoverage() {
      const count = Number(this.otherKnowledge?.errorCount || 0)
      if (!count) {
        return {
          tone: 'good',
          icon: '✓',
          title: '知识点词表覆盖良好',
          description: '当前没有错误落入“其他”，教师定义的分类口径能够覆盖现有错误。',
        }
      }
      if (this.otherRate > 25) {
        return {
          tone: 'danger',
          icon: '!',
          title: `“其他”占比 ${this.otherRate}%，建议完善知识点`,
          description: `${count} 条错误尚未匹配到教师定义的知识点，可查看明细后新增分类。`,
        }
      }
      if (this.otherRate >= 10) {
        return {
          tone: 'warning',
          icon: 'i',
          title: `“其他”占比 ${this.otherRate}%，可以检查分类口径`,
          description: `${count} 条错误暂未归类，新增知识点后系统会重新分析这些记录。`,
        }
      }
      return {
        tone: 'good',
        icon: '✓',
        title: `知识点词表覆盖率约 ${Math.round((100 - this.otherRate) * 10) / 10}%`,
        description: `仅 ${count} 条错误进入“其他”，当前分类口径较为稳定。`,
      }
    },

    primaryWeakPoint() {
      return (
        this.knowledgeMasterySorted.find(
          (item) => item.name !== '其他' && Number(item.mastery) < 70,
        ) || null
      )
    },

    topConfusion() {
      return this.allConfusionStats[0] || null
    },

    dashboardStatus() {
      if (Number(this.metrics.warningStudents) > 0 || this.otherRate > 25) {
        return { label: '当前需要关注', tone: 'attention' }
      }
      if (this.primaryWeakPoint || this.topConfusion) {
        return { label: '存在待跟进信号', tone: 'watch' }
      }
      return { label: '班级状态稳定', tone: 'stable' }
    },

    dashboardHeadline() {
      if (!this.metrics.totalHomework && !this.students.length) {
        return '数据还在积累，完成批改后会形成班级学情结论'
      }
      if (Number(this.metrics.warningStudents) > 0 && this.primaryWeakPoint) {
        return `${this.metrics.warningStudents} 名学生需要关注，${this.primaryWeakPoint.name}是当前首要薄弱点`
      }
      if (Number(this.metrics.warningStudents) > 0) {
        return `${this.metrics.warningStudents} 名学生平均成绩低于 60 分，建议优先跟进`
      }
      if (this.primaryWeakPoint) {
        return `${this.primaryWeakPoint.name}掌握度偏低，建议安排针对性巩固`
      }
      return '当前班级整体表现稳定，可以继续按教学计划推进'
    },

    dashboardSummary() {
      const parts = []
      if (this.scoredStudents.length) {
        parts.push(`${this.scoredStudents.length} 名学生已有成绩记录，达标率 ${this.passRate}%`)
      }
      if (this.topConfusion) {
        parts.push(`“${this.topConfusion.name}”收到 ${this.topConfusion.count} 次不懂反馈`)
      }
      if (this.otherKnowledge?.errorCount) {
        parts.push(`${this.otherKnowledge.errorCount} 条错误暂归入“其他”`)
      }
      return parts.length
        ? `${parts.join('；')}。`
        : '目前还没有足够的作业、错题或课堂反馈数据，系统不会生成无依据的趋势判断。'
    },

    priorityActions() {
      const items = []
      if (Number(this.metrics.warningStudents) > 0) {
        items.push({
          type: 'students',
          tone: 'danger',
          title: `${this.metrics.warningStudents} 名学生需要重点关注`,
          description: '累计平均成绩低于 60 分，建议先查看个人成长轨迹与作业记录。',
          action: 'students',
          actionLabel: '查看重点学生',
        })
      }
      if (this.primaryWeakPoint) {
        items.push({
          type: 'knowledge',
          tone: 'warning',
          title: `${this.primaryWeakPoint.name}掌握度仅 ${this.primaryWeakPoint.mastery}%`,
          description: `已累计 ${this.primaryWeakPoint.errorCount || 0} 条相关错误，可查看高频问题。`,
          action: 'knowledge',
          actionLabel: '查看错误明细',
        })
      }
      if (this.otherKnowledge?.errorCount) {
        items.push({
          type: 'taxonomy',
          tone: this.otherRate > 25 ? 'danger' : 'neutral',
          title: `${this.otherKnowledge.errorCount} 条错误尚未准确归类`,
          description: `“其他”占当前错误的 ${this.otherRate}%，可据此补充教师知识点词表。`,
          action: 'other',
          actionLabel: '检查未归类错误',
        })
      }
      if (this.topConfusion) {
        items.push({
          type: 'confusion',
          tone: 'violet',
          title: `${this.topConfusion.name}出现集中“不懂”反馈`,
          description: `来自 QQ 与课堂的合并记录共 ${this.topConfusion.count} 次。`,
          action: 'confusions',
          actionLabel: '查看学生反馈',
        })
      }
      if (!items.length) {
        items.push({
          type: 'stable',
          tone: 'success',
          title: '暂无高优先级教学风险',
          description: '当前数据没有触发关注条件，可以继续观察后续作业与课堂反馈。',
          action: null,
          actionLabel: '',
        })
      }
      return items.slice(0, 3)
    },

    // 合并 QQ + 活课堂的不懂统计
    allConfusionStats() {
      const map = {}
      for (const s of this.confusionStats) map[s.name] = (map[s.name] || 0) + (s.count || 0)
      for (const s of this.liveStats) map[s.name] = (map[s.name] || 0) + (s.count || 0)
      return Object.entries(map)
        .map(([name, count]) => ({ name, count }))
        .sort((a, b) => b.count - a.count)
    },
    allConfusionEvents() {
      const qq = (this.confusions || []).map((c) => ({ ...c, _source: 'QQ', _key: 'qq-' + c.id }))
      const live = (this.liveEvents || []).map((c) => ({
        ...c,
        _source: '课堂',
        _key: 'live-' + c.id,
      }))
      return [...qq, ...live].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    },

    // 薄弱知识点（掌握度 < 70%）
    weakKnowledgePoints() {
      return this.knowledgeMastery
        .filter((item) => item.name !== '其他' && item.mastery < 70)
        .map((item) => item.name)
    },

    // 筛选和排序后的学生列表
    filteredStudents() {
      let result = [...this.students]

      if (this.studentFilter) {
        const keyword = this.studentFilter.trim().toLowerCase()
        result = result.filter(
          (student) =>
            student.name.toLowerCase().includes(keyword) ||
            String(student.studentId || '')
              .toLowerCase()
              .includes(keyword),
        )
      }
      if (this.sortBy === 'homework') {
        result.sort((a, b) => Number(b.homeworkCount) - Number(a.homeworkCount))
      } else {
        result.sort((a, b) => {
          if (!a.homeworkCount && b.homeworkCount) return 1
          if (a.homeworkCount && !b.homeworkCount) return -1
          return Number(a.avgScore) - Number(b.avgScore)
        })
      }
      return result
    },

    visibleStudents() {
      if (this.studentFilter || this.showAllStudents) return this.filteredStudents
      return this.filteredStudents.filter(
        (student) => Number(student.homeworkCount) > 0 && Number(student.avgScore) < 60,
      )
    },
  },

  methods: {
    // 加载班级列表
    async loadClassList() {
      try {
        const res = await request.get('/dashboard/classes')
        const result = res.data
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
        this.loadStudents(),
        this.loadConfusions(),
        this.loadLiveConfusions(),
      ])
      this.lastUpdatedAt = new Date()
      this.loading = false
    },

    // 加载核心指标
    async loadMetrics() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const res = await request.get('/dashboard/metrics', {
          params: { classId: this.selectedClass },
        })
        if (res.data.code === 200) {
          this.metrics = res.data.data
        }
      } catch (error) {
        console.error('加载指标失败:', error)
      }
    },

    // 加载成绩分布
    async loadScoreDistribution() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const res = await request.get('/dashboard/score-distribution', {
          params: { classId: this.selectedClass },
        })
        if (res.data.code === 200) {
          this.scoreDistribution = res.data.data
        }
      } catch (error) {
        console.error('加载成绩分布失败:', error)
      }
    },

    // 加载知识点掌握度
    async loadKnowledgeMastery() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const res = await request.get('/dashboard/knowledge-mastery', {
          params: { classId: this.selectedClass },
        })
        console.log('知识点掌握度接口返回:', res.data)
        if (res.data.code === 200) {
          this.knowledgeMastery = res.data.data
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
        const params = { classId: this.selectedClass }
        if (this.selectedKp) params.knowledgePoint = this.selectedKp
        const res = await request.get('/dashboard/frequent-errors', { params })
        if (res.data.code === 200) {
          this.frequentErrors = res.data.data
        }
      } catch (error) {
        console.error('加载高频错题失败:', error)
      }
    },

    // 加载学生列表
    async loadStudents() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const res = await request.get('/dashboard/students', {
          params: { classId: this.selectedClass, sortBy: this.sortBy },
        })
        if (res.data.code === 200) {
          this.students = res.data.data
        }
      } catch (error) {
        console.error('加载学生列表失败:', error)
      }
    },

    // 加载活课堂"不懂"标记
    async loadLiveConfusions() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const res = await request.get('/dashboard/live-confusions', {
          params: { classId: this.selectedClass },
        })
        if (res.data.code === 200) {
          this.liveStats = res.data.data.stats || []
          this.liveEvents = res.data.data.events || []
        }
      } catch (error) {
        console.error('加载活课堂不懂标记失败:', error)
      }
    },

    // 加载学生"不懂"标记（QQ）
    async loadConfusions() {
      if (!this.selectedClass || this.selectedClass === 'null') return
      try {
        const [logRes, statsRes] = await Promise.all([
          request.get('/dashboard/student-confusions', { params: { classId: this.selectedClass } }),
          request.get('/dashboard/student-confusions/stats', {
            params: { classId: this.selectedClass },
          }),
        ])
        if (logRes.data.code === 200) this.confusions = logRes.data.data || []
        if (statsRes.data.code === 200) this.confusionStats = statsRes.data.data || []
      } catch (error) {
        console.error('加载不懂标记失败:', error)
      }
    },

    // 相对时间格式化
    formatRelativeTime(dateStr) {
      if (!dateStr) return ''
      const now = Date.now()
      const t = new Date(dateStr).getTime()
      const diff = now - t
      const mins = Math.floor(diff / 60000)
      if (mins < 1) return '刚刚'
      if (mins < 60) return mins + '分钟前'
      const hours = Math.floor(mins / 60)
      if (hours < 24) return hours + '小时前'
      const days = Math.floor(hours / 24)
      if (days < 30) return days + '天前'
      return Math.floor(days / 30) + '个月前'
    },

    formatDateTime(dateStr) {
      if (!dateStr) return '时间未知'
      const date = new Date(dateStr)
      if (Number.isNaN(date.getTime())) return '时间未知'
      return new Intl.DateTimeFormat('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(date)
    },

    normalizeSeverity(severity) {
      if (severity === 'critical' || severity === 'high') return 'high'
      if (severity === 'major' || severity === 'medium') return 'medium'
      return 'low'
    },

    handleInsightAction(action) {
      if (action === 'students') {
        this.showAllStudents = false
        this.studentFilter = ''
        this.scrollToSection('studentSection')
        return
      }
      if (action === 'knowledge' && this.primaryWeakPoint) {
        this.showKpErrors(this.primaryWeakPoint)
        return
      }
      if (action === 'other' && this.otherKnowledge) {
        this.showKpErrors(this.otherKnowledge)
        return
      }
      if (action === 'confusions') {
        this.scrollToSection('confusionSection')
      }
    },

    scrollToSection(refName) {
      const target = this.$refs[refName]
      target?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
    },

    getStudentStatus(student) {
      if (!Number(student.homeworkCount)) return { label: '暂无成绩', tone: 'muted' }
      if (Number(student.avgScore) < 60) return { label: '重点关注', tone: 'danger' }
      if (Number(student.avgScore) < 70) return { label: '需要巩固', tone: 'warning' }
      return { label: '状态正常', tone: 'success' }
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
      if (this.isKnowledgeNameDuplicate) {
        this.$message?.warning('已存在同名知识点')
        return
      }
      if (!this.selectedClass || this.selectedClass === 'null') {
        this.$message?.error('请先选择班级')
        return
      }

      try {
        this.savingKnowledge = true
        const res = await request.post('/dashboard/teacher-knowledge/add', {
          classId: this.selectedClass,
          name: this.newKpItem.name.trim(),
          color: this.newKpItem.color,
        })
        if (res.data.code === 200) {
          this.$message?.success('添加成功，正在后台重归类历史错误...')
          this.newKpItem = { name: '', color: '#1890ff' }
          this.showAddKpDialog = false
          await this.loadKnowledgeMastery()
          this.trackReclassificationTask(res.data.data)
        } else {
          throw new Error(res.data.message || '添加失败')
        }
      } catch (error) {
        console.error('添加知识点失败:', error)
        this.$message?.error('添加失败: ' + error.message)
      } finally {
        this.savingKnowledge = false
      }
    },

    closeAddKnowledgeDialog() {
      if (this.savingKnowledge) return
      this.showAddKpDialog = false
      this.newKpItem = { name: '', color: '#1890ff' }
    },

    // 编辑模式只暂存删除，统一在“保存”时由批量同步接口迁移历史错误。
    removeKnowledge(index) {
      const item = this.knowledgeMastery[index]
      if (!item || item.name === '其他') return
      this.knowledgeMastery.splice(index, 1)
      this.$message?.info('已标记删除，保存后会将相关历史错误移入“其他”并重新归类')
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
          id: k.id,
          name: k.name,
          color: k.color || '#1890ff',
          sortOrder: i,
        }))

        const res = await request.post('/dashboard/teacher-knowledge/batch', {
          classId: this.selectedClass,
          items: items,
        })
        if (res.data.code === 200) {
          this.$message?.success('保存成功，正在后台重归类历史错误...')
          this.isEditing = false
          await this.loadKnowledgeMastery()
          this.trackReclassificationTask(res.data.data)
        } else {
          throw new Error(res.data.message || '保存失败')
        }
      } catch (error) {
        console.error('保存知识点失败:', error)
        this.$message?.error('保存失败: ' + error.message)
      } finally {
        this.savingKnowledge = false
      }
    },

    // ========== 重归类轮询 ==========

    trackReclassificationTask(task) {
      if (!task?.taskId) return
      if (this.reclassificationPollTimer) clearTimeout(this.reclassificationPollTimer)
      this.reclassificationTask = task

      const poll = async (attempt = 0) => {
        if (attempt >= 60 || !this.reclassificationTask) return
        try {
          const res = await request.get(
            `/dashboard/knowledge-reclassification/${this.reclassificationTask.taskId}`,
            { params: { classId: this.selectedClass } },
          )
          if (res.data.code !== 200) return
          this.reclassificationTask = res.data.data
          const status = this.reclassificationTask.status
          if (status === 'COMPLETED' || status === 'COMPLETED_WITH_ERRORS') {
            await Promise.all([this.loadKnowledgeMastery(), this.loadFrequentErrors()])
            const failedText = this.reclassificationTask.failed
              ? `，${this.reclassificationTask.failed} 条处理失败并保留在“其他”`
              : ''
            this.$message?.success(
              `重归类完成：${this.reclassificationTask.reclassified || 0} 条已重新分配，${this.reclassificationTask.remainingOther || 0} 条仍在“其他”${failedText}`,
            )
            return
          }
          if (status === 'FAILED') {
            this.$message?.error('历史错误重归类失败，原有分类数据未丢失')
            return
          }
          this.reclassificationPollTimer = setTimeout(() => poll(attempt + 1), 2000)
        } catch (error) {
          console.error('查询重归类任务失败:', error)
          this.reclassificationPollTimer = setTimeout(() => poll(attempt + 1), 3000)
        }
      }
      poll()
    },

    // ========== 知识点错误弹窗 ==========

    // 点击热力图格子 → 弹窗展示该知识点的高频错误
    async showKpErrors(item) {
      this.selectedKp = item.name
      this.kpErrorTitle = item.name
      this.kpErrorSearch = ''
      this.kpErrorFilter = 'all'
      this.kpErrorList = []
      this.kpErrorLoading = true
      this.showKpErrorModal = true

      try {
        const res = await request.get('/dashboard/frequent-errors', {
          params: { classId: this.selectedClass, knowledgePoint: item.name },
        })
        if (res.data.code === 200) {
          this.kpErrorList = res.data.data
        }
      } catch (error) {
        console.error('加载知识点错误详情失败:', error)
        this.kpErrorList = []
      } finally {
        this.kpErrorLoading = false
      }
    },

    getDifficultyLabel(difficulty) {
      if (difficulty === 'high') return '高严重度'
      if (difficulty === 'low') return '一般'
      return '中等'
    },

    getErrorFrequencyWidth(count) {
      const maxCount = Math.max(...this.kpErrorList.map((item) => Number(item.errorCount || 0)), 1)
      return Math.max(6, Math.round((Number(count || 0) * 100) / maxCount))
    },

    openAddKnowledgeFromErrors() {
      this.showKpErrorModal = false
      this.newKpItem = { name: '', color: '#1890ff' }
      this.showAddKpDialog = true
    },

    generatePlanFromErrorDialog() {
      this.showKpErrorModal = false
      this.generateTeachingPlan()
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
      alert(
        `错题详情：${error.question}\n\n错误率：${error.errorRate}%\n错误人数：${error.errorCount}人`,
      )
    },

    // 查看学生诊断详情
    async viewStudentDetail(student) {
      this.selectedStudentDetail = student
      this.studentDetail = {
        studentName: student.name,
        totalCount: 0,
        avgScore: 0,
        maxScore: 0,
        minScore: 0,
        trend: 0,
        points: [],
        summary: null,
        risk: null,
        weakKnowledgePoints: [],
        recentErrors: [],
      }
      this.studentDetailLoading = true
      this.showStudentDetailModal = true

      if (!Number(student.homeworkCount)) {
        this.studentDetailLoading = false
        return
      }

      try {
        const params = { studentName: student.name, classId: String(this.selectedClass) }
        if (student.studentId) params.studentId = student.studentId
        const res = await request.get('/dashboard/student-insight', { params })
        if (res.data.code === 200) {
          const insight = res.data.data
          const summary = insight.summary || {}
          this.studentDetail = {
            studentName: insight.student?.name || student.name,
            totalCount: summary.completedCount || 0,
            avgScore: summary.avgScore || 0,
            maxScore: summary.highestScore || 0,
            minScore: summary.lowestScore || 0,
            trend: summary.latestChange || 0,
            points: (insight.scoreHistory || []).map((point) => ({
              no: point.no,
              submissionId: point.submissionId,
              assignmentName: point.assignmentName,
              date: point.date,
              score: point.score,
              change: point.change,
              late: point.late,
            })),
            summary,
            risk: insight.risk,
            weakKnowledgePoints: insight.weakKnowledgePoints || [],
            recentErrors: insight.recentErrors || [],
          }
        }
      } catch (error) {
        console.error('加载学生诊断详情失败:', error)
        this.$message?.error('学生作业轨迹加载失败')
      } finally {
        this.studentDetailLoading = false
      }
    },

    closeStudentDetail() {
      this.showStudentDetailModal = false
    },

    openProgressFromDetail() {
      if (!this.studentDetail.points?.length) return
      this.progressData = JSON.parse(JSON.stringify(this.studentDetail))
      this.showStudentDetailModal = false
      this.showProgressModal = true
      this.$nextTick(() => this.renderProgressChart(this.progressData.points))
    },

    formatScoreChange(change) {
      const value = Number(change || 0)
      if (value > 0) return `+${value}`
      if (value < 0) return String(value)
      return '持平'
    },

    // 📈 查看学生成长曲线
    async showProgress(student) {
      this.progressData = {
        studentName: student.name,
        totalCount: 0,
        avgScore: 0,
        maxScore: 0,
        minScore: 0,
        trend: 0,
        points: [],
      }
      this.showProgressModal = true

      try {
        const params = { studentName: student.name, classId: String(this.selectedClass) }
        if (student.studentId) params.studentId = student.studentId
        const res = await request.get('/dashboard/student-progress', { params })
        if (res.data.code === 200) {
          this.progressData = res.data.data
          // 等 DOM 更新后渲染图表
          this.$nextTick(() => this.renderProgressChart(res.data.data.points))
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
          formatter: function (params) {
            const p = points[params[0].dataIndex]
            return `<div style="padding:8px;font-size:14px">
              <b>第${p.no}次作业</b><br/>
              ${p.assignmentName}<br/>
              得分: <span style="color:#667eea;font-weight:bold;">${p.score}</span> 分<br/>
              提交: ${p.date}
            </div>`
          },
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '10%',
          containLabel: true,
        },
        xAxis: {
          type: 'category',
          data: points.map((p) => `第${p.no}次`),
          axisLabel: { color: '#666' },
        },
        yAxis: {
          type: 'value',
          min: 0,
          max: 100,
          name: '分数',
          nameTextStyle: { color: '#666' },
          axisLabel: { color: '#666' },
        },
        series: [
          {
            data: points.map((p) => p.score),
            type: 'line',
            smooth: true,
            symbol: 'circle',
            symbolSize: 10,
            lineStyle: { width: 3, color: '#667eea' },
            areaStyle: {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 0,
                y2: 1,
                colorStops: [
                  { offset: 0, color: 'rgba(102,126,234,0.3)' },
                  { offset: 1, color: 'rgba(102,126,234,0.05)' },
                ],
              },
            },
            markLine: {
              silent: true,
              data: [
                {
                  type: 'average',
                  name: '平均分',
                  lineStyle: { color: '#faad14', type: 'dashed' },
                },
                {
                  yAxis: 60,
                  lineStyle: { color: '#f5222d', type: 'dashed' },
                  label: { formatter: '及格线' },
                },
              ],
            },
          },
        ],
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
      if (!this.weakKnowledgePoints.length) {
        this.$message?.info('当前还没有掌握度低于 70% 的教师定义知识点')
        return
      }
      this.showTeachingPlanModal = true
      this.selectedGoals = ['difficult', 'review']
      this.generatedPlan = ''

      // 加载薄弱知识点
      try {
        const res = await request.get('/dashboard/weak-points', {
          params: { classId: this.selectedClass },
        })
        if (res.data.code === 200) {
          console.log('薄弱知识点:', res.data.data)
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
        const res = await request.post('/dashboard/teaching-plan/generate', {
          classId: this.selectedClass,
          goals: this.selectedGoals,
          planType: this.planType,
          weakKnowledgePoints: this.weakKnowledgePoints.slice(0, 3),
        })
        if (res.data.code === 200) {
          this.generatedPlan = res.data.data
        }
      } catch (error) {
        console.error('生成教案失败:', error)
        this.$message?.error('生成教案失败')
      } finally {
        this.generatingPlan = false
      }
    },
  },
}
