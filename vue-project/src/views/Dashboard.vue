<template>
  <div class="dashboard-page">
    <header class="dashboard-header">
      <div class="header-left">
        <span class="page-eyebrow">TEACHING INSIGHTS</span>
        <h1>班级学情概览</h1>
        <p class="subtitle">把作业、错题与课堂反馈，整理成老师可以立即处理的教学信号</p>
      </div>
      <div class="header-right">
        <label class="class-field">
          <span>当前班级</span>
          <select v-model="selectedClass" class="class-selector">
            <option v-for="cls in classList" :key="cls.id" :value="cls.id">
              {{ cls.name }}
            </option>
          </select>
        </label>
        <button class="refresh-btn" @click="refreshData" :disabled="loading">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20 11a8 8 0 1 0-2.34 5.66M20 4v7h-7" />
          </svg>
          <span>{{ loading ? '正在更新' : '刷新数据' }}</span>
        </button>
      </div>
    </header>

    <section class="insight-hero" :class="`is-${dashboardStatus.tone}`">
      <div class="insight-copy">
        <div class="insight-status-row">
          <span class="status-dot"></span>
          <span>{{ dashboardStatus.label }}</span>
          <span class="insight-updated">{{ lastUpdatedLabel }}</span>
        </div>
        <h2>{{ dashboardHeadline }}</h2>
        <p>{{ dashboardSummary }}</p>
        <div class="insight-meta">
          <span>{{ selectedClassName }}</span>
          <span>{{ metrics.totalHomework || 0 }} 份累计批改记录</span>
          <span>{{ knowledgePointCount }} 个教师知识点</span>
        </div>
      </div>
      <div class="insight-actions">
        <button class="btn-secondary" @click="handleInsightAction('students')">查看重点学生</button>
        <button
          class="btn-primary"
          :disabled="weakKnowledgePoints.length === 0"
          @click="generateTeachingPlan"
        >
          生成针对性教案
        </button>
      </div>
    </section>

    <section class="metrics-section" aria-label="班级核心指标">
      <article class="metric-card metric-blue">
        <div class="metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
            <circle cx="9" cy="7" r="4" />
            <path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" />
          </svg>
        </div>
        <div class="metric-content">
          <span class="metric-label">有成绩记录学生</span>
          <strong class="metric-value">{{ metrics.totalStudents || 0 }}</strong>
          <span class="metric-note">统计范围内至少提交过一次</span>
        </div>
      </article>
      <article class="metric-card metric-violet">
        <div class="metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 19V9M10 19V5M16 19v-7M22 19V2" />
          </svg>
        </div>
        <div class="metric-content">
          <span class="metric-label">累计平均成绩</span>
          <strong class="metric-value">{{ metrics.avgScore || 0 }}<small>分</small></strong>
          <span class="metric-note">基于当前全部有效批改记录</span>
        </div>
      </article>
      <article class="metric-card metric-teal">
        <div class="metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <path d="m8 12 2.5 2.5L16 9" />
          </svg>
        </div>
        <div class="metric-content">
          <span class="metric-label">已提交学生达标率</span>
          <strong class="metric-value">{{ passRate }}<small>%</small></strong>
          <span class="metric-note">平均成绩达到 60 分</span>
        </div>
      </article>
      <article class="metric-card metric-red">
        <div class="metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M10.3 3.7 2.7 17a2 2 0 0 0 1.73 3h15.14A2 2 0 0 0 21.3 17L13.7 3.7a2 2 0 0 0-3.4 0Z"
            />
            <path d="M12 9v4M12 17h.01" />
          </svg>
        </div>
        <div class="metric-content">
          <span class="metric-label">需重点关注</span>
          <strong class="metric-value">{{ metrics.warningStudents || 0 }}<small>人</small></strong>
          <span class="metric-note">学生累计平均成绩低于 60 分</span>
        </div>
      </article>
    </section>

    <section class="dashboard-primary-grid">
      <article ref="knowledgeSection" class="dashboard-card knowledge-diagnosis-card">
        <div class="section-header">
          <div>
            <span class="section-eyebrow">KNOWLEDGE DIAGNOSIS</span>
            <h3>知识点掌握度</h3>
            <p>教师定义分类口径，AI 只负责从标准词表中匹配；无法匹配的错误统一进入“其他”。</p>
          </div>
          <div class="section-actions">
            <template v-if="!isEditing">
              <button class="btn-ghost" @click="showAddKpDialog = true">添加知识点</button>
              <button class="btn-ghost" @click="toggleEdit">管理知识点</button>
              <button
                class="btn-soft-primary"
                :disabled="weakKnowledgePoints.length === 0"
                @click="generateTeachingPlan"
              >
                生成教案
              </button>
            </template>
            <template v-else>
              <button class="btn-ghost" @click="cancelEdit">取消</button>
              <button class="btn-soft-primary" :disabled="savingKnowledge" @click="saveKnowledge">
                {{ savingKnowledge ? '保存中...' : '保存并重新归类' }}
              </button>
            </template>
          </div>
        </div>

        <div v-if="knowledgeMasterySorted.length" class="knowledge-heatmap">
          <article
            v-for="item in knowledgeMasterySorted"
            :key="item.id || item.name"
            class="heatmap-item"
            :class="{
              editing: isEditing,
              active: selectedKp === item.name && !isEditing,
              'is-other': item.name === '其他',
            }"
            :style="{ '--mastery-color': getHeatmapColor(item.mastery) }"
            @click="isEditing ? null : showKpErrors(item)"
          >
            <template v-if="isEditing">
              <template v-if="item.name !== '其他'">
                <button
                  class="heatmap-remove"
                  aria-label="删除知识点"
                  @click.stop="removeKnowledge(knowledgeMastery.indexOf(item))"
                >
                  ×
                </button>
                <div class="heatmap-edit-content">
                  <input v-model="item.name" class="heatmap-name-input" placeholder="知识点名称" />
                  <div class="heatmap-color-row">
                    <input v-model="item.color" type="color" class="heatmap-color-picker" />
                    <span>教师定义分类</span>
                  </div>
                </div>
              </template>
              <div v-else class="other-locked">
                <span class="heatmap-name">其他</span>
                <small>系统兜底分类，不可修改</small>
              </div>
            </template>
            <template v-else>
              <div class="heatmap-topline">
                <span class="heatmap-name">{{ item.name }}</span>
                <span class="knowledge-origin">{{
                  item.name === '其他' ? '兜底项' : '教师定义'
                }}</span>
              </div>
              <div class="mastery-row">
                <strong>{{ item.mastery || 0 }}<small>%</small></strong>
                <span>估算掌握度</span>
              </div>
              <div class="mastery-track">
                <span :style="{ width: `${Math.max(0, item.mastery || 0)}%` }"></span>
              </div>
              <div class="heatmap-meta">
                <span>{{ item.errorCount || 0 }} 条错误</span>
                <span v-if="item.name === '其他'">占错误 {{ otherRate }}%</span>
                <span v-else>{{ item.criticalCount || 0 }} 条严重</span>
              </div>
            </template>
          </article>
        </div>
        <div v-else class="empty-state">
          <div class="empty-state-icon">+</div>
          <strong>还没有教师定义知识点</strong>
          <p>先建立本班的标准知识点词表，后续 AI 批改才能稳定归类。</p>
          <button class="btn-soft-primary" @click="showAddKpDialog = true">添加第一个知识点</button>
        </div>

        <div v-if="otherKnowledge" class="taxonomy-coverage" :class="`is-${otherCoverage.tone}`">
          <div class="coverage-icon">{{ otherCoverage.icon }}</div>
          <div>
            <strong>{{ otherCoverage.title }}</strong>
            <p>{{ otherCoverage.description }}</p>
          </div>
          <button class="text-button" @click="showKpErrors(otherKnowledge)">查看未归类错误</button>
        </div>
        <div
          v-if="
            reclassificationTask && ['PENDING', 'RUNNING'].includes(reclassificationTask.status)
          "
          class="reclassification-progress"
        >
          <span class="loading-spinner"></span>
          <div>
            <strong>正在后台重新归类历史错误</strong>
            <p>
              已处理 {{ reclassificationTask.processed || 0 }} /
              {{ reclassificationTask.total || 0 }}， 已重新归类
              {{ reclassificationTask.reclassified || 0 }} 条
            </p>
            <div class="reclassification-track">
              <span :style="{ width: `${reclassificationProgress}%` }"></span>
            </div>
          </div>
        </div>
      </article>

      <aside class="dashboard-card action-queue-card">
        <div class="section-header compact">
          <div>
            <span class="section-eyebrow">ACTION QUEUE</span>
            <h3>优先处理</h3>
          </div>
          <span class="queue-count">{{ priorityActions.length }}</span>
        </div>
        <div class="action-queue">
          <article
            v-for="(item, index) in priorityActions"
            :key="`${item.type}-${index}`"
            class="queue-item"
            :class="`is-${item.tone}`"
          >
            <div class="queue-marker">{{ index + 1 }}</div>
            <div class="queue-copy">
              <strong>{{ item.title }}</strong>
              <p>{{ item.description }}</p>
              <button v-if="item.action" @click="handleInsightAction(item.action)">
                {{ item.actionLabel }}
                <span>→</span>
              </button>
            </div>
          </article>
        </div>
        <div class="queue-footer">
          <span class="queue-footer-dot"></span>
          结论来自当前数据库统计，AI 不参与核心指标计算
        </div>
      </aside>
    </section>

    <section class="dashboard-secondary-grid">
      <article class="dashboard-card distribution-card">
        <div class="section-header compact">
          <div>
            <span class="section-eyebrow">SCORE PROFILE</span>
            <h3>班级成绩分布</h3>
            <p>按学生累计平均成绩统计</p>
          </div>
          <span class="data-scope-tag">累计数据</span>
        </div>
        <div v-if="scoreDistribution.length" class="score-distribution">
          <div v-for="(item, index) in scoreDistribution" :key="index" class="distribution-bar">
            <div class="bar-label">{{ item.range }}</div>
            <div class="bar-track">
              <div
                class="bar-fill"
                :style="{ width: item.percentage + '%', background: item.color }"
              ></div>
            </div>
            <div class="bar-value">
              <strong>{{ item.count }}</strong> 人
              <span>{{ item.percentage }}%</span>
            </div>
          </div>
        </div>
        <div v-else class="empty-state compact-empty">暂无有效成绩分布</div>
      </article>

      <article ref="confusionSection" class="dashboard-card confusion-insight-card">
        <div class="section-header compact">
          <div>
            <span class="section-eyebrow">STUDENT SIGNALS</span>
            <h3>学生“不懂”信号</h3>
            <p>合并 QQ 私聊与课堂实时反馈</p>
          </div>
          <span class="data-scope-tag">{{ allConfusionEvents.length }} 条记录</span>
        </div>
        <div v-if="allConfusionStats.length" class="confusion-insight-body">
          <div class="confusion-ranking">
            <div v-for="(item, index) in allConfusionStats.slice(0, 4)" :key="item.name">
              <span class="confusion-rank">{{ index + 1 }}</span>
              <span class="confusion-rank-name">{{ item.name }}</span>
              <span class="confusion-rank-bar">
                <span
                  :style="{
                    width: `${Math.max(8, (item.count / allConfusionStats[0].count) * 100)}%`,
                  }"
                ></span>
              </span>
              <strong>{{ item.count }} 次</strong>
            </div>
          </div>
          <div class="recent-signals">
            <div v-for="item in allConfusionEvents.slice(0, 4)" :key="item._key">
              <span class="signal-avatar">{{ (item.studentName || '未')[0] }}</span>
              <span class="signal-copy">
                <strong>{{ item.studentName || '未知学生' }}</strong>
                <small>{{ item.knowledgePoint }} · {{ item._source }}</small>
              </span>
              <time>{{ formatRelativeTime(item.createdAt) }}</time>
            </div>
          </div>
        </div>
        <div v-else class="empty-state compact-empty">暂时没有学生主动标记“不懂”</div>
      </article>
    </section>

    <section ref="studentSection" class="dashboard-card student-focus-card">
      <div class="section-header">
        <div>
          <span class="section-eyebrow">STUDENT FOLLOW-UP</span>
          <h3>{{ showAllStudents ? '全部学生学情' : '重点关注学生' }}</h3>
          <p>只展示有真实成绩依据的状态，暂不使用尚未计算的错题数和趋势占位值。</p>
        </div>
        <div class="student-toolbar">
          <label class="student-search">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-4-4" />
            </svg>
            <input v-model="studentFilter" type="search" placeholder="搜索学生姓名" />
          </label>
          <select v-model="sortBy" class="sort-select">
            <option value="score">按成绩从低到高</option>
            <option value="homework">按提交次数排序</option>
          </select>
          <button class="btn-ghost" @click="showAllStudents = !showAllStudents">
            {{ showAllStudents ? '只看重点' : '查看全部' }}
          </button>
        </div>
      </div>

      <div v-if="visibleStudents.length" class="student-table-wrap">
        <table class="student-table">
          <thead>
            <tr>
              <th>学生</th>
              <th>累计平均分</th>
              <th>提交记录</th>
              <th>当前状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="student in visibleStudents"
              :key="student.studentId || student.id || student.name"
            >
              <td>
                <div class="student-identity">
                  <span class="student-avatar">{{ student.name[0] }}</span>
                  <div>
                    <strong>{{ student.name }}</strong>
                    <small>{{ student.studentId || '暂无学号' }}</small>
                  </div>
                </div>
              </td>
              <td>
                <div class="student-score-cell">
                  <strong>{{ student.homeworkCount ? student.avgScore : '--' }}</strong>
                  <div class="progress-bar">
                    <span
                      :style="{
                        width: `${student.homeworkCount ? student.avgScore : 0}%`,
                        background: getScoreColor(student.avgScore),
                      }"
                    ></span>
                  </div>
                </div>
              </td>
              <td>{{ student.homeworkCount || 0 }} 份</td>
              <td>
                <span class="student-status" :class="`is-${getStudentStatus(student).tone}`">
                  {{ getStudentStatus(student).label }}
                </span>
              </td>
              <td>
                <div class="table-actions">
                  <button :disabled="!student.homeworkCount" @click="showProgress(student)">
                    成长轨迹
                  </button>
                  <button @click="viewStudentDetail(student)">查看详情</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-state">
        <strong>{{ studentFilter ? '没有匹配的学生' : '当前没有需要重点关注的学生' }}</strong>
        <p>
          {{ studentFilter ? '请尝试更换搜索关键词。' : '可以切换到“查看全部”浏览完整学生列表。' }}
        </p>
      </div>
    </section>

    <!-- 学生诊断详情 -->
    <div v-if="showStudentDetailModal" class="modal-overlay" @click.self="closeStudentDetail">
      <div class="modal-content modal-xl student-detail-modal">
        <div class="modal-header modal-header-rich">
          <div class="modal-person-title">
            <span class="modal-person-avatar">{{ selectedStudentDetail?.name?.[0] || '学' }}</span>
            <div>
              <span class="modal-eyebrow">STUDENT DIAGNOSIS</span>
              <h3>{{ selectedStudentDetail?.name || '学生' }} · 学习详情</h3>
              <p>{{ selectedStudentDetail?.studentId || '暂无学号' }} · {{ selectedClassName }}</p>
            </div>
          </div>
          <button class="close-btn" aria-label="关闭" @click="closeStudentDetail">×</button>
        </div>

        <div class="modal-body student-detail-body">
          <div v-if="studentDetailLoading" class="modal-loading-state">
            <span class="loading-spinner"></span>
            <strong>正在整理该学生的作业轨迹</strong>
            <p>系统正在读取历次成绩并生成可解释的诊断。</p>
          </div>

          <div v-else-if="studentDetail.points?.length" class="student-detail-content">
            <div class="detail-metric-grid">
              <article>
                <span>累计平均分</span>
                <strong>{{ studentDetail.avgScore }}<small>分</small></strong>
                <p>共 {{ studentDetail.totalCount }} 次有效提交</p>
              </article>
              <article>
                <span>最近一次</span>
                <strong>{{ studentDetailLatest?.score ?? '--' }}<small>分</small></strong>
                <p>{{ studentDetailLatest?.assignmentName || '暂无作业' }}</p>
              </article>
              <article>
                <span>历史最高</span>
                <strong>{{ studentDetail.maxScore }}<small>分</small></strong>
                <p>最低 {{ studentDetail.minScore ?? '--' }} 分</p>
              </article>
              <article :class="`is-${studentDetailAssessment.tone}`">
                <span>最近变化</span>
                <strong>
                  {{ studentDetailLatestChange > 0 ? '+' : '' }}{{ studentDetailLatestChange }}
                  <small>分</small>
                </strong>
                <p>相较上一次提交</p>
              </article>
            </div>

            <div class="student-detail-grid">
              <section class="student-history-panel">
                <div class="subsection-heading">
                  <div>
                    <h4>近期作业表现</h4>
                    <p>按提交时间展示最近 6 次成绩</p>
                  </div>
                  <button class="text-button" @click="openProgressFromDetail">
                    查看完整曲线 →
                  </button>
                </div>
                <div class="student-history-list">
                  <article
                    v-for="point in studentDetailRecentPoints"
                    :key="`${point.no}-${point.assignmentName}`"
                  >
                    <span class="history-sequence">{{ point.no }}</span>
                    <div class="history-copy">
                      <strong>{{ point.assignmentName }}</strong>
                      <small>{{ point.date || '日期未知' }}</small>
                    </div>
                    <div class="history-score">
                      <strong :class="scoreColorClass(point.score)">{{ point.score }}</strong>
                      <span :class="changeClass(point.change)">
                        {{ formatScoreChange(point.change) }}
                      </span>
                    </div>
                  </article>
                </div>
              </section>

              <aside class="student-diagnosis-panel" :class="`is-${studentDetailAssessment.tone}`">
                <span class="diagnosis-label">当前判断</span>
                <h4>{{ studentDetailAssessment.title }}</h4>
                <p>{{ studentDetailAssessment.description }}</p>
                <div class="diagnosis-divider"></div>
                <span class="diagnosis-label">建议下一步</span>
                <ul>
                  <li v-for="suggestion in studentDetailSuggestions" :key="suggestion">
                    <span>✓</span>{{ suggestion }}
                  </li>
                </ul>
                <div class="diagnosis-basis">
                  判断依据：{{ studentDetail.totalCount }} 次作业记录、最近一次成绩及相邻成绩变化
                </div>
              </aside>
            </div>

            <div class="student-evidence-grid">
              <section>
                <div class="subsection-heading">
                  <div>
                    <h4>个人薄弱知识点</h4>
                    <p>根据该学生历史错误按教师词表聚合</p>
                  </div>
                  <span>{{ studentDetail.weakKnowledgePoints?.length || 0 }} 项</span>
                </div>
                <div v-if="studentDetail.weakKnowledgePoints?.length" class="student-weak-list">
                  <article
                    v-for="point in studentDetail.weakKnowledgePoints.slice(0, 5)"
                    :key="point.name"
                  >
                    <div>
                      <strong>{{ point.name }}</strong>
                      <small>{{ point.criticalCount || 0 }} 条高严重度错误</small>
                    </div>
                    <span>{{ point.errorCount }} 条</span>
                  </article>
                </div>
                <div v-else class="evidence-empty">暂无可归类的个人错误记录</div>
              </section>

              <section>
                <div class="subsection-heading">
                  <div>
                    <h4>近期典型错误</h4>
                    <p>展示最近的 AI 批改错误记录</p>
                  </div>
                  <span>{{ studentDetail.summary?.totalErrorCount || 0 }} 条累计</span>
                </div>
                <div v-if="studentDetail.recentErrors?.length" class="student-recent-errors">
                  <article v-for="error in studentDetail.recentErrors.slice(0, 4)" :key="error.id">
                    <span :class="`is-${normalizeSeverity(error.severity)}`">{{
                      error.knowledgePoint || '其他'
                    }}</span>
                    <div>
                      <strong>{{ error.errorText }}</strong>
                      <small
                        >{{ error.assignmentName }} · {{ formatDateTime(error.createdAt) }}</small
                      >
                    </div>
                  </article>
                </div>
                <div v-else class="evidence-empty">暂无近期错误记录</div>
              </section>
            </div>
          </div>

          <div v-else class="empty-state student-detail-empty">
            <div class="empty-state-icon">—</div>
            <strong>暂时没有可以形成诊断的作业记录</strong>
            <p>该学生完成至少一次有效提交后，这里会显示成绩轨迹与跟进建议。</p>
          </div>
        </div>

        <div class="modal-footer">
          <span class="modal-footer-note">这里只展示数据库中的真实成绩，不使用占位趋势。</span>
          <div>
            <button class="btn-secondary" @click="closeStudentDetail">关闭</button>
            <button
              class="btn-primary"
              :disabled="!studentDetail.points?.length"
              @click="openProgressFromDetail"
            >
              查看成长曲线
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 学生成长曲线弹窗 -->
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
              <span class="stat-num"
                >{{ progressData.trend > 0 ? '+' : '' }}{{ progressData.trend }}</span
              >
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
          <div
            class="progress-table-wrap"
            v-if="progressData.points && progressData.points.length > 0"
          >
            <table class="progress-table">
              <thead>
                <tr>
                  <th>次数</th>
                  <th>作业名称</th>
                  <th>日期</th>
                  <th>得分</th>
                  <th>环比</th>
                </tr>
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

    <!-- 添加标准知识点 -->
    <div v-if="showAddKpDialog" class="modal-overlay" @click.self="closeAddKnowledgeDialog">
      <div class="modal-content modal-lg knowledge-create-modal">
        <div class="modal-header modal-header-rich">
          <div>
            <span class="modal-eyebrow">CLASSIFICATION STANDARD</span>
            <h3>新增标准知识点</h3>
            <p>这个名称会成为本班 AI 批改与学情统计的固定分类口径。</p>
          </div>
          <button class="close-btn" aria-label="关闭" @click="closeAddKnowledgeDialog">×</button>
        </div>

        <div class="modal-body knowledge-create-body">
          <div class="classification-rule-card">
            <div>
              <span>1</span>
              <strong>教师定义名称</strong>
              <small>确定稳定、清晰的标准词表</small>
            </div>
            <i>→</i>
            <div>
              <span>2</span>
              <strong>AI 受限匹配</strong>
              <small>只能从教师词表中选择</small>
            </div>
            <i>→</i>
            <div>
              <span>3</span>
              <strong>无法匹配归其他</strong>
              <small>避免模型自行创造分类名称</small>
            </div>
          </div>

          <div class="knowledge-form-grid">
            <section class="knowledge-form-main">
              <label class="form-label" for="knowledge-name">知识点名称</label>
              <div class="knowledge-name-field" :class="{ 'has-error': isKnowledgeNameDuplicate }">
                <input
                  id="knowledge-name"
                  v-model="newKpItem.name"
                  maxlength="30"
                  autocomplete="off"
                  placeholder="例如：循环结构、数组与字符串"
                  @keyup.enter="addKnowledge"
                />
                <span>{{ newKpItem.name.trim().length }}/30</span>
              </div>
              <p v-if="isKnowledgeNameDuplicate" class="form-error">
                已存在同名知识点，请直接使用现有分类。
              </p>
              <p v-else class="form-help">
                建议使用教材或课程标准中的正式名称，不要使用“问题一”“其他错误”等临时名称。
              </p>

              <label class="form-label">识别颜色</label>
              <div class="knowledge-color-options">
                <button
                  v-for="color in knowledgeColorOptions"
                  :key="color"
                  type="button"
                  :class="{ active: newKpItem.color === color }"
                  :style="{ '--option-color': color }"
                  :aria-label="`选择颜色 ${color}`"
                  @click="newKpItem.color = color"
                ></button>
                <label class="custom-color-picker" title="自定义颜色">
                  <input v-model="newKpItem.color" type="color" />
                  <span>+</span>
                </label>
                <code>{{ newKpItem.color }}</code>
              </div>
            </section>

            <aside class="taxonomy-preview-panel">
              <div class="taxonomy-preview-heading">
                <strong>当前知识点词表</strong>
                <span>{{ knowledgePointCount }} 个</span>
              </div>
              <div class="taxonomy-chip-list">
                <span
                  v-for="item in knowledgeMasterySorted.filter((kp) => kp.name !== '其他')"
                  :key="item.id || item.name"
                >
                  <i :style="{ background: item.color || getHeatmapColor(item.mastery) }"></i>
                  {{ item.name }}
                </span>
                <em v-if="!knowledgePointCount">暂未定义知识点</em>
              </div>
              <div class="reclassify-preview">
                <span class="reclassify-count">{{ otherKnowledge?.errorCount || 0 }}</span>
                <div>
                  <strong>条“其他”错误等待重新匹配</strong>
                  <p>新增成功后，系统会自动尝试将历史未归类错误匹配到新知识点。</p>
                </div>
              </div>
            </aside>
          </div>
        </div>

        <div class="modal-footer">
          <span class="modal-footer-note">“其他”为系统兜底项，不需要手动创建。</span>
          <div>
            <button class="btn-secondary" @click="closeAddKnowledgeDialog">取消</button>
            <button
              class="btn-primary"
              :disabled="!newKpItem.name.trim() || isKnowledgeNameDuplicate || savingKnowledge"
              @click="addKnowledge"
            >
              {{ savingKnowledge ? '正在添加...' : '添加并重新归类' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 知识点错误诊断 -->
    <div v-if="showKpErrorModal" class="modal-overlay" @click.self="showKpErrorModal = false">
      <div class="modal-content modal-xl knowledge-error-modal">
        <div class="modal-header modal-header-rich">
          <div>
            <span class="modal-eyebrow">ERROR DIAGNOSIS</span>
            <div class="knowledge-error-title-row">
              <h3>{{ kpErrorTitle }} · 错误诊断</h3>
              <span :class="{ 'is-other': kpErrorTitle === '其他' }">
                {{ kpErrorTitle === '其他' ? '待归类' : '教师定义知识点' }}
              </span>
            </div>
            <p>查看高频错误、严重程度和出现次数，定位最值得优先讲解的问题。</p>
          </div>
          <button class="close-btn" aria-label="关闭" @click="showKpErrorModal = false">×</button>
        </div>

        <div class="modal-body knowledge-error-body">
          <div v-if="kpErrorLoading" class="modal-loading-state">
            <span class="loading-spinner"></span>
            <strong>正在读取知识点错误记录</strong>
            <p>系统正在聚合同类错误与出现频次。</p>
          </div>

          <template v-else-if="kpErrorList.length">
            <div class="error-summary-grid">
              <article>
                <span>高频错误类型</span>
                <strong>{{ kpErrorList.length }}</strong>
                <small>按错误内容去重</small>
              </article>
              <article>
                <span>累计出现次数</span>
                <strong>{{ kpErrorTotalOccurrences }}</strong>
                <small>当前接口返回范围内</small>
              </article>
              <article class="is-danger">
                <span>高严重度错误</span>
                <strong>{{ kpErrorHighCount }}</strong>
                <small>建议优先讲解</small>
              </article>
              <article>
                <span>估算掌握度</span>
                <strong>{{ selectedKnowledgeItem?.mastery ?? '--' }}<small>%</small></strong>
                <small>基于累计错误数估算</small>
              </article>
            </div>

            <div class="error-toolbar">
              <label class="error-search">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="11" cy="11" r="7" />
                  <path d="m20 20-4-4" />
                </svg>
                <input v-model="kpErrorSearch" type="search" placeholder="搜索错误内容" />
              </label>
              <div class="error-filter-tabs">
                <button
                  v-for="option in kpErrorFilterOptions"
                  :key="option.value"
                  :class="{ active: kpErrorFilter === option.value }"
                  @click="kpErrorFilter = option.value"
                >
                  {{ option.label }} <span>{{ option.count }}</span>
                </button>
              </div>
            </div>

            <div v-if="filteredKpErrors.length" class="diagnostic-error-list">
              <article
                v-for="(error, index) in filteredKpErrors"
                :key="`${error.question}-${index}`"
                :class="{ 'is-priority': error.difficulty === 'high' }"
              >
                <span class="diagnostic-rank">{{ String(index + 1).padStart(2, '0') }}</span>
                <div class="diagnostic-error-copy">
                  <div class="diagnostic-error-heading">
                    <span class="error-tag" :class="error.difficulty">
                      {{ error.difficultyLabel || getDifficultyLabel(error.difficulty) }}
                    </span>
                    <span class="error-occurrences">出现 {{ error.errorCount || 0 }} 次</span>
                  </div>
                  <p>{{ error.question }}</p>
                  <div class="error-evidence-row">
                    <span>影响 {{ error.affectedStudentCount || 0 }} 名学生</span>
                    <span>{{ error.affectedStudentRate || 0 }}%</span>
                    <span>涉及 {{ error.assignmentCount || 0 }} 次作业</span>
                    <time>{{ formatDateTime(error.latestSeenAt) }}</time>
                  </div>
                  <div class="frequency-track">
                    <span :style="{ width: `${getErrorFrequencyWidth(error.errorCount)}%` }"></span>
                  </div>
                </div>
              </article>
            </div>
            <div v-else class="empty-state compact-empty">当前筛选条件下没有匹配的错误。</div>
          </template>

          <div v-else class="empty-state knowledge-error-empty">
            <div class="empty-state-icon">✓</div>
            <strong>当前知识点暂无错误记录</strong>
            <p>完成更多作业批改后，这里会自动汇总相关错误。</p>
          </div>
        </div>

        <div class="modal-footer">
          <span class="modal-footer-note">
            数据来自该班级已完成的 AI 批改记录，并按相同错误内容聚合。
          </span>
          <div>
            <button class="btn-secondary" @click="showKpErrorModal = false">关闭</button>
            <button
              v-if="kpErrorTitle === '其他'"
              class="btn-primary"
              @click="openAddKnowledgeFromErrors"
            >
              新增标准知识点
            </button>
            <button
              v-else
              class="btn-primary"
              :disabled="!weakKnowledgePoints.includes(kpErrorTitle)"
              @click="generatePlanFromErrorDialog"
            >
              生成针对性教案
            </button>
          </div>
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
                  <input type="checkbox" v-model="selectedGoals" :value="goal.value" />
                  {{ goal.label }}
                </label>
              </div>
            </div>
            <div class="config-item">
              <label>针对薄弱知识点</label>
              <div class="tag-list">
                <span v-for="tag in weakKnowledgePoints" :key="tag" class="tag-item">
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
            <div class="plan-content" v-html="safeGeneratedPlan"></div>
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

<script lang="js">
import * as echarts from 'echarts'
import request from '@/api/request'
import { sanitizeHtml } from '@/utils/safeHtml'

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
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
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
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
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
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
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
  transition:
    transform 0.2s,
    box-shadow 0.2s;
}

.heatmap-item:hover:not(.editing) {
  transform: scale(1.05);
}

.heatmap-item.active {
  box-shadow:
    0 0 0 3px white,
    0 0 0 5px currentColor;
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
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
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
  background: rgba(0, 0, 0, 0.5);
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

/* 学生"不懂"标记 */
.confusions-section {
  margin-bottom: 1.5rem;
}

.confusions-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
  padding: 1.25rem;
}

.confusions-body h4 {
  margin: 0 0 0.75rem 0;
  font-size: 0.9rem;
  color: #4a5568;
}

.stat-bars {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.stat-bar-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
}

.stat-bar-name {
  width: 100px;
  text-align: right;
  color: #4a5568;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-bar-wrap {
  flex: 1;
  height: 14px;
  background: #edf2f7;
  border-radius: 4px;
  overflow: hidden;
}

.stat-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #faad14, #f5222d);
  border-radius: 4px;
  transition: width 0.3s;
  min-width: 2px;
}

.stat-bar-count {
  width: 35px;
  font-weight: 600;
  color: #f5222d;
  font-size: 0.8rem;
}

.confusion-list {
  max-height: 320px;
  overflow-y: auto;
}

.confusion-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f0f0f0;
  font-size: 0.85rem;
}

.confusion-item:last-child {
  border-bottom: none;
}

.confusion-name {
  color: #4a5568;
  min-width: 60px;
}

.confusion-kp {
  flex: 1;
  color: #f5222d;
  font-weight: 500;
}

.confusion-time {
  color: #a0aec0;
  font-size: 0.8rem;
  white-space: nowrap;
}

.confusion-source {
  background: #f0f0f0;
  color: #888;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 0.7rem;
}

/* 响应式 */
@media (max-width: 1200px) {
  .metrics-section {
    grid-template-columns: repeat(2, 1fr);
  }

  .confusions-body {
    grid-template-columns: 1fr;
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

.change-up {
  color: #52c41a;
  font-weight: 600;
}
.change-down {
  color: #cf1322;
  font-weight: 600;
}

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

/* ========== 教学决策台新版布局 ========== */
.dashboard-page {
  width: min(100%, 1480px);
  max-width: 1480px;
  min-height: auto;
  margin: 0 auto;
  padding: 0 0 48px;
  background: transparent;
  color: #172033;
}

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin: 0 0 20px;
  padding: 4px 2px 0;
  background: transparent;
  border-radius: 0;
  box-shadow: none;
}

.page-eyebrow,
.section-eyebrow {
  display: block;
  margin-bottom: 7px;
  color: #6d63dd;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.header-left h1 {
  margin: 0 0 6px;
  color: #111827;
  font-size: clamp(24px, 2vw, 30px);
  font-weight: 750;
  letter-spacing: -0.04em;
}

.subtitle {
  margin: 0;
  color: #758095;
  font-size: 13px;
}

.header-right {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}

.class-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.class-field > span {
  color: #8a94a6;
  font-size: 11px;
  font-weight: 600;
}

.class-selector {
  min-width: 170px;
  height: 40px;
  padding: 0 34px 0 12px;
  color: #283246;
  font-size: 13px;
  font-weight: 600;
  background: #fff;
  border: 1px solid #dfe4ee;
  border-radius: 9px;
  outline: none;
  cursor: pointer;
}

.class-selector:focus {
  border-color: #8b82ec;
  box-shadow: 0 0 0 3px rgba(109, 99, 221, 0.12);
}

.refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 40px;
  padding: 0 14px;
  color: #4b5568;
  font-size: 12px;
  font-weight: 650;
  background: #fff;
  border: 1px solid #dfe4ee;
  border-radius: 9px;
}

.refresh-btn svg,
.student-search svg,
.metric-icon svg {
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.refresh-btn svg {
  width: 15px;
  height: 15px;
}

.refresh-btn:hover:not(:disabled) {
  color: #5b52cf;
  background: #f8f7ff;
  border-color: #c9c5f8;
}

.insight-hero {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  min-height: 190px;
  margin-bottom: 16px;
  padding: 30px 32px;
  overflow: hidden;
  background:
    radial-gradient(circle at 92% 16%, rgba(156, 148, 255, 0.34), transparent 32%),
    linear-gradient(132deg, #25224e 0%, #39346f 53%, #4c4593 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  box-shadow: 0 14px 34px rgba(39, 35, 83, 0.16);
}

.insight-hero::after {
  position: absolute;
  top: -84px;
  right: 12%;
  width: 220px;
  height: 220px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  content: '';
}

.insight-copy,
.insight-actions {
  position: relative;
  z-index: 1;
}

.insight-copy {
  max-width: 820px;
}

.insight-status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 13px;
  color: #d9d7f6;
  font-size: 12px;
  font-weight: 650;
}

.status-dot {
  width: 7px;
  height: 7px;
  background: #f6c768;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(246, 199, 104, 0.13);
}

.insight-hero.is-stable .status-dot {
  background: #63d5ae;
  box-shadow: 0 0 0 4px rgba(99, 213, 174, 0.13);
}

.insight-hero.is-attention .status-dot {
  background: #ff8f93;
  box-shadow: 0 0 0 4px rgba(255, 143, 147, 0.13);
}

.insight-updated {
  margin-left: 4px;
  padding-left: 12px;
  color: #a9a6cc;
  font-weight: 500;
  border-left: 1px solid rgba(255, 255, 255, 0.18);
}

.insight-copy h2 {
  max-width: 760px;
  margin: 0 0 10px;
  color: #fff;
  font-size: clamp(23px, 2.3vw, 34px);
  font-weight: 720;
  line-height: 1.25;
  letter-spacing: -0.035em;
}

.insight-copy > p {
  margin: 0;
  color: #c8c5e5;
  font-size: 13px;
  line-height: 1.7;
}

.insight-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.insight-meta span {
  padding: 5px 9px;
  color: #d8d5f2;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 6px;
}

.insight-actions {
  display: flex;
  flex-shrink: 0;
  gap: 9px;
}

.insight-actions .btn-secondary,
.insight-actions .btn-primary {
  height: 38px;
  padding: 0 14px;
  font-size: 12px;
  font-weight: 650;
  border-radius: 8px;
}

.insight-actions .btn-secondary {
  color: #eeecff;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.insight-actions .btn-primary {
  color: #342f69;
  background: #fff;
  border: 1px solid #fff;
}

.insight-actions .btn-primary:hover:not(:disabled) {
  color: #342f69;
  background: #f1efff;
}

.metrics-section {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-card {
  min-width: 0;
  min-height: 124px;
  padding: 19px;
  gap: 14px;
  background: #fff;
  border: 1px solid #e7eaf1;
  border-radius: 13px;
  box-shadow: 0 3px 14px rgba(31, 42, 68, 0.045);
}

.metric-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 22px rgba(31, 42, 68, 0.07);
}

.metric-icon {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  color: #5e66d8;
  background: #eef0ff;
  border-radius: 10px;
}

.metric-icon svg {
  width: 20px;
  height: 20px;
}

.metric-violet .metric-icon {
  color: #7654c8;
  background: #f2edff;
}

.metric-teal .metric-icon {
  color: #14856f;
  background: #e8f8f3;
}

.metric-red .metric-icon {
  color: #cf4e5a;
  background: #fff0f1;
}

.metric-content {
  min-width: 0;
}

.metric-label {
  order: 0;
  margin: 0 0 3px;
  color: #707b90;
  font-size: 11px;
  font-weight: 650;
}

.metric-value {
  order: 1;
  color: #172033;
  font-size: 27px;
  font-weight: 760;
  line-height: 1.15;
  letter-spacing: -0.035em;
}

.metric-value small {
  margin-left: 3px;
  color: #657087;
  font-size: 12px;
  font-weight: 600;
}

.metric-note {
  order: 2;
  display: block;
  margin-top: 7px;
  overflow: hidden;
  color: #9aa2b1;
  font-size: 10px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dashboard-primary-grid,
.dashboard-secondary-grid {
  display: grid;
  gap: 16px;
  margin-bottom: 16px;
}

.dashboard-primary-grid {
  grid-template-columns: minmax(0, 1fr) 330px;
  align-items: start;
}

.dashboard-secondary-grid {
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
}

.dashboard-card {
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e7eaf1;
  border-radius: 14px;
  box-shadow: 0 3px 14px rgba(31, 42, 68, 0.045);
  scroll-margin-top: 78px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 22px;
  padding: 22px 23px 18px;
  border-bottom: 1px solid #edf0f5;
}

.section-header.compact {
  align-items: center;
  padding-bottom: 16px;
}

.section-header h3 {
  margin: 0;
  color: #182135;
  font-size: 17px;
  font-weight: 720;
  letter-spacing: -0.02em;
}

.section-header p {
  max-width: 700px;
  margin: 6px 0 0;
  color: #818b9e;
  font-size: 11px;
  line-height: 1.55;
}

.section-actions,
.student-toolbar {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 8px;
}

.btn-ghost,
.btn-soft-primary,
.text-button,
.table-actions button {
  font-family: inherit;
  cursor: pointer;
  transition: all 0.18s ease;
}

.btn-ghost,
.btn-soft-primary {
  min-height: 34px;
  padding: 0 11px;
  font-size: 11px;
  font-weight: 650;
  border-radius: 7px;
}

.btn-ghost {
  color: #566176;
  background: #fff;
  border: 1px solid #dfe4ec;
}

.btn-ghost:hover {
  color: #5f55d4;
  background: #f8f7ff;
  border-color: #c9c5f8;
}

.btn-soft-primary {
  color: #fff;
  background: #655bd7;
  border: 1px solid #655bd7;
}

.btn-soft-primary:hover:not(:disabled) {
  background: #554bc5;
  border-color: #554bc5;
}

.btn-soft-primary:disabled,
.table-actions button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.knowledge-heatmap {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(176px, 1fr));
  gap: 10px;
  padding: 18px 22px 20px;
}

.heatmap-item {
  position: relative;
  min-height: 140px;
  padding: 15px;
  color: #1d273a;
  text-align: left;
  background: #fff;
  border: 1px solid #e4e8ef;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.heatmap-item::before {
  position: absolute;
  top: 0;
  left: 15px;
  width: 34px;
  height: 3px;
  background: var(--mastery-color);
  border-radius: 0 0 4px 4px;
  content: '';
}

.heatmap-item:hover:not(.editing) {
  z-index: 1;
  border-color: #c9c5f1;
  box-shadow: 0 8px 22px rgba(51, 47, 109, 0.09);
  transform: translateY(-2px);
}

.heatmap-item.active {
  border-color: #746adc;
  box-shadow: 0 0 0 3px rgba(116, 106, 220, 0.1);
}

.heatmap-item.is-other {
  background: #fafbfc;
  border-style: dashed;
}

.heatmap-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.heatmap-name {
  margin: 0;
  overflow: hidden;
  color: #232c3e;
  font-size: 13px;
  font-weight: 720;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-origin {
  flex-shrink: 0;
  padding: 3px 5px;
  color: #7167cf;
  font-size: 9px;
  font-weight: 650;
  background: #f0effd;
  border-radius: 4px;
}

.is-other .knowledge-origin {
  color: #7b8494;
  background: #eceff3;
}

.mastery-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 18px 0 8px;
}

.mastery-row strong {
  color: var(--mastery-color);
  font-size: 25px;
  line-height: 1;
  letter-spacing: -0.04em;
}

.mastery-row strong small {
  margin-left: 1px;
  font-size: 11px;
}

.mastery-row > span {
  color: #929bab;
  font-size: 9px;
}

.mastery-track {
  height: 5px;
  overflow: hidden;
  background: #edf0f4;
  border-radius: 999px;
}

.mastery-track > span {
  display: block;
  height: 100%;
  background: var(--mastery-color);
  border-radius: inherit;
  transition: width 0.4s ease;
}

.heatmap-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 11px;
  color: #7f899c;
  font-size: 9px;
}

.heatmap-item.editing {
  min-height: 140px;
  padding: 20px 15px 15px;
  color: #333;
  background: #fafbfc !important;
  border: 1px dashed #bfc5d1;
}

.heatmap-item.editing::before {
  display: none;
}

.heatmap-edit-content {
  justify-content: center;
  height: 100%;
  gap: 12px;
}

.heatmap-name-input {
  height: 34px;
  padding: 0 10px;
  font-size: 12px;
  text-align: left;
  background: #fff;
  border-color: #dfe3ea;
  border-radius: 7px;
}

.heatmap-color-row {
  justify-content: flex-start;
  color: #818b9d;
  font-size: 10px;
}

.heatmap-color-picker {
  width: 28px;
  height: 28px;
}

.heatmap-remove {
  top: 8px;
  right: 8px;
  width: 20px;
  height: 20px;
  color: #b64b55;
  line-height: 18px;
  background: #fff0f1;
}

.other-locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #858e9f;
  text-align: center;
}

.other-locked small {
  margin-top: 8px;
  font-size: 10px;
}

.taxonomy-coverage {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: 11px;
  margin: 0 22px 20px;
  padding: 12px 14px;
  background: #f4faf7;
  border: 1px solid #d8eee4;
  border-radius: 9px;
}

.taxonomy-coverage.is-warning {
  background: #fffbf1;
  border-color: #f4e5b8;
}

.taxonomy-coverage.is-danger {
  background: #fff5f5;
  border-color: #f4d4d7;
}

.coverage-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  color: #278266;
  font-size: 12px;
  font-weight: 800;
  background: #def3e9;
  border-radius: 50%;
}

.is-warning .coverage-icon {
  color: #9a6810;
  background: #faeabe;
}

.is-danger .coverage-icon {
  color: #b3424c;
  background: #f8dfe1;
}

.taxonomy-coverage strong {
  display: block;
  margin-bottom: 3px;
  color: #334052;
  font-size: 11px;
}

.taxonomy-coverage p {
  margin: 0;
  color: #788396;
  font-size: 10px;
  line-height: 1.5;
}

.text-button {
  padding: 4px 0;
  color: #655bd7;
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
  background: transparent;
  border: 0;
}

.text-button:hover {
  color: #453aae;
}

.action-queue-card {
  position: sticky;
  top: 74px;
}

.queue-count {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 25px;
  height: 25px;
  color: #655bd7;
  font-size: 11px;
  font-weight: 750;
  background: #f0effd;
  border-radius: 50%;
}

.action-queue {
  display: flex;
  flex-direction: column;
  padding: 8px 18px 10px;
}

.queue-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
  padding: 14px 0;
  border-bottom: 1px solid #eef0f4;
}

.queue-item:last-child {
  border-bottom: 0;
}

.queue-marker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 23px;
  height: 23px;
  color: #b74b56;
  font-size: 10px;
  font-weight: 750;
  background: #fff0f1;
  border-radius: 7px;
}

.queue-item.is-warning .queue-marker {
  color: #a46b0e;
  background: #fff4d7;
}

.queue-item.is-neutral .queue-marker {
  color: #667085;
  background: #f0f2f5;
}

.queue-item.is-violet .queue-marker {
  color: #655bd7;
  background: #f0effd;
}

.queue-item.is-success .queue-marker {
  color: #278266;
  background: #e5f6ee;
}

.queue-copy strong {
  display: block;
  color: #293448;
  font-size: 11px;
  line-height: 1.45;
}

.queue-copy p {
  margin: 4px 0 7px;
  color: #8992a2;
  font-size: 10px;
  line-height: 1.55;
}

.queue-copy button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  color: #655bd7;
  font-family: inherit;
  font-size: 10px;
  font-weight: 650;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.queue-copy button:hover {
  color: #443aaf;
}

.queue-footer {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin: 0 18px 17px;
  padding: 10px;
  color: #8b94a3;
  font-size: 9px;
  line-height: 1.5;
  background: #f8f9fb;
  border-radius: 7px;
}

.queue-footer-dot {
  width: 6px;
  height: 6px;
  flex: 0 0 6px;
  margin-top: 3px;
  background: #65b99b;
  border-radius: 50%;
}

.data-scope-tag {
  padding: 5px 8px;
  color: #697387;
  font-size: 9px;
  font-weight: 650;
  background: #f3f5f8;
  border-radius: 5px;
}

.distribution-card .score-distribution {
  gap: 13px;
  padding: 20px 23px 24px;
}

.distribution-card .distribution-bar {
  gap: 10px;
}

.distribution-card .bar-label {
  width: 64px;
  color: #606b7e;
  font-size: 10px;
  text-align: left;
}

.distribution-card .bar-track {
  height: 8px;
  background: #edf0f4;
  border-radius: 999px;
}

.distribution-card .bar-fill {
  min-width: 2px;
  border-radius: inherit;
}

.distribution-card .bar-value {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 3px;
  width: 78px;
  color: #6f7a8e;
  font-size: 10px;
}

.distribution-card .bar-value strong {
  color: #273246;
  font-size: 12px;
}

.distribution-card .bar-value span {
  width: 33px;
  margin-left: 3px;
  color: #a0a7b4;
  text-align: right;
}

.confusion-insight-body {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  min-height: 220px;
}

.confusion-ranking,
.recent-signals {
  padding: 18px 20px;
}

.confusion-ranking {
  border-right: 1px solid #edf0f5;
}

.confusion-ranking > div {
  display: grid;
  grid-template-columns: 20px minmax(58px, 0.8fr) minmax(70px, 1fr) 38px;
  align-items: center;
  gap: 7px;
  min-height: 38px;
}

.confusion-rank {
  color: #8e96a5;
  font-size: 9px;
  font-weight: 750;
}

.confusion-rank-name {
  overflow: hidden;
  color: #465166;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.confusion-rank-bar {
  height: 5px;
  overflow: hidden;
  background: #f0eefc;
  border-radius: 999px;
}

.confusion-rank-bar > span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #8b82e8, #655bd7);
  border-radius: inherit;
}

.confusion-ranking strong {
  color: #655bd7;
  font-size: 9px;
  text-align: right;
}

.recent-signals > div {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 42px;
  border-bottom: 1px solid #f0f2f5;
}

.recent-signals > div:last-child {
  border-bottom: 0;
}

.signal-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 27px;
  height: 27px;
  flex: 0 0 27px;
  color: #655bd7;
  font-size: 10px;
  font-weight: 720;
  background: #eeecfb;
  border-radius: 8px;
}

.signal-copy {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.signal-copy strong {
  color: #3d485b;
  font-size: 10px;
}

.signal-copy small {
  overflow: hidden;
  color: #929aaa;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-signals time {
  color: #a1a8b5;
  font-size: 8px;
  white-space: nowrap;
}

.student-focus-card {
  margin-bottom: 0;
}

.student-toolbar {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.student-search {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 168px;
  height: 34px;
  padding: 0 10px;
  background: #fff;
  border: 1px solid #dfe4ec;
  border-radius: 7px;
}

.student-search svg {
  width: 14px;
  height: 14px;
  color: #9ba3b0;
}

.student-search input {
  width: 100%;
  min-width: 0;
  color: #394458;
  font-family: inherit;
  font-size: 10px;
  background: transparent;
  border: 0;
  outline: 0;
}

.student-search input::placeholder {
  color: #a7aeba;
}

.sort-select {
  height: 34px;
  padding: 0 28px 0 9px;
  color: #5f697c;
  font-size: 10px;
  background: #fff;
  border: 1px solid #dfe4ec;
  border-radius: 7px;
  outline: 0;
}

.student-table-wrap {
  max-height: 520px;
  overflow: auto;
}

.student-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.student-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 10px 18px;
  color: #929aa9;
  font-size: 9px;
  font-weight: 700;
  text-align: left;
  background: #fafbfc;
  border-bottom: 1px solid #e9ecf1;
}

.student-table td {
  padding: 12px 18px;
  color: #596477;
  font-size: 10px;
  border-bottom: 1px solid #eff1f5;
}

.student-table tbody tr:last-child td {
  border-bottom: 0;
}

.student-table tbody tr:hover td {
  background: #fbfbfe;
}

.student-table th:first-child {
  width: 27%;
}

.student-table th:nth-child(2) {
  width: 22%;
}

.student-table th:nth-child(3),
.student-table th:nth-child(4) {
  width: 14%;
}

.student-table th:last-child {
  width: 23%;
}

.student-identity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.student-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  color: #5f56ca;
  font-size: 11px;
  font-weight: 720;
  background: #eceafb;
  border-radius: 9px;
}

.student-identity > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.student-identity strong {
  overflow: hidden;
  color: #313c50;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-identity small {
  color: #9ca4b2;
  font-size: 8px;
}

.student-score-cell {
  display: flex;
  align-items: center;
  gap: 9px;
}

.student-score-cell > strong {
  width: 24px;
  color: #303b4f;
  font-size: 11px;
}

.student-score-cell .progress-bar {
  width: min(90px, 60%);
  height: 5px;
  background: #edf0f4;
  border-radius: 999px;
}

.student-score-cell .progress-bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.student-status {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 7px;
  font-size: 9px;
  font-weight: 650;
  border-radius: 5px;
}

.student-status.is-danger {
  color: #b8444e;
  background: #fff0f1;
}

.student-status.is-warning {
  color: #966314;
  background: #fff4d8;
}

.student-status.is-success {
  color: #237a60;
  background: #e7f7ef;
}

.student-status.is-muted {
  color: #7f8898;
  background: #f0f2f5;
}

.table-actions {
  display: flex;
  gap: 6px;
}

.table-actions button {
  min-height: 28px;
  padding: 0 8px;
  color: #5f697c;
  font-size: 9px;
  background: #fff;
  border: 1px solid #dfe4ec;
  border-radius: 6px;
}

.table-actions button:hover:not(:disabled) {
  color: #5d53cf;
  background: #f8f7ff;
  border-color: #c9c5f8;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 190px;
  padding: 28px;
  flex-direction: column;
  color: #8a93a3;
  text-align: center;
}

.empty-state-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  margin-bottom: 10px;
  color: #675dd4;
  font-size: 20px;
  background: #efedfd;
  border-radius: 10px;
}

.empty-state strong {
  color: #4d586b;
  font-size: 12px;
}

.empty-state p {
  max-width: 420px;
  margin: 7px 0 14px;
  font-size: 10px;
  line-height: 1.6;
}

.empty-state.compact-empty,
.compact-empty {
  min-height: 200px;
  font-size: 11px;
}

@media (max-width: 1240px) {
  .dashboard-primary-grid {
    grid-template-columns: minmax(0, 1fr) 300px;
  }

  .knowledge-heatmap {
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  }

  .section-header {
    gap: 14px;
  }

  .section-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}

@media (max-width: 1060px) {
  .dashboard-primary-grid,
  .dashboard-secondary-grid {
    grid-template-columns: 1fr;
  }

  .action-queue-card {
    position: static;
  }

  .action-queue {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    padding: 16px 18px;
  }

  .queue-item {
    align-content: start;
    padding: 12px;
    border: 1px solid #eceef3;
    border-radius: 9px;
  }

  .metrics-section {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .dashboard-header,
  .insight-hero,
  .section-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-right,
  .insight-actions,
  .student-toolbar {
    width: 100%;
  }

  .class-field,
  .class-selector,
  .student-search {
    min-width: 0;
    flex: 1;
  }

  .insight-hero {
    min-height: auto;
    padding: 24px;
  }

  .insight-actions button {
    flex: 1;
  }

  .metrics-section,
  .action-queue,
  .confusion-insight-body {
    grid-template-columns: 1fr;
  }

  .metric-card {
    min-height: 105px;
  }

  .section-actions {
    justify-content: flex-start;
  }

  .taxonomy-coverage {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .taxonomy-coverage .text-button {
    grid-column: 2;
    justify-self: start;
  }

  .confusion-ranking {
    border-right: 0;
    border-bottom: 1px solid #edf0f5;
  }

  .student-table {
    min-width: 760px;
  }
}

@media (max-width: 520px) {
  .metrics-section {
    grid-template-columns: 1fr;
  }

  .header-right,
  .student-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .refresh-btn,
  .student-search,
  .student-toolbar .sort-select,
  .student-toolbar .btn-ghost {
    width: 100%;
  }

  .knowledge-heatmap {
    grid-template-columns: 1fr;
    padding: 14px;
  }

  .taxonomy-coverage {
    margin: 0 14px 16px;
  }
}

/* ========== 诊断弹窗新版 ========== */
.modal-overlay {
  z-index: 2200;
  padding: 24px;
  background: rgba(18, 24, 38, 0.56);
  backdrop-filter: blur(5px);
}

.modal-content {
  width: min(92vw, 620px);
  max-width: 620px;
  max-height: min(88vh, 860px);
  overflow: hidden;
  background: #fff;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 16px;
  box-shadow: 0 28px 80px rgba(17, 24, 39, 0.24);
}

.modal-content.modal-lg {
  width: min(92vw, 840px);
  max-width: 840px;
}

.modal-content.modal-xl {
  width: min(94vw, 1040px);
  max-width: 1040px;
}

.modal-content.wide {
  width: min(92vw, 780px);
  max-width: 780px;
}

.modal-header {
  flex: 0 0 auto;
  padding: 20px 22px;
  background: #fff;
  border-bottom: 1px solid #eceff4;
}

.modal-header-rich {
  align-items: flex-start;
  padding: 23px 26px 20px;
}

.modal-header-rich h3 {
  margin: 0;
  color: #182135;
  font-size: 19px;
  font-weight: 750;
  letter-spacing: -0.025em;
}

.modal-header-rich p {
  margin: 6px 0 0;
  color: #818b9d;
  font-size: 11px;
  line-height: 1.55;
}

.modal-eyebrow {
  display: block;
  margin-bottom: 7px;
  color: #6a60d7;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.close-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  color: #8a93a3;
  font-size: 19px;
  line-height: 1;
  background: #f5f6f8;
  border: 1px solid transparent;
  border-radius: 8px;
  transition: all 0.18s ease;
}

.close-btn:hover {
  color: #4f596b;
  background: #eceef2;
}

.modal-body {
  min-height: 0;
  padding: 22px 26px;
  overscroll-behavior: contain;
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  gap: 18px;
  padding: 15px 26px;
  background: #fafbfc;
  border-top: 1px solid #eceff4;
}

.modal-footer > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.modal-footer-note {
  color: #929aa8;
  font-size: 9px;
  line-height: 1.5;
}

.modal-footer .btn-primary,
.modal-footer .btn-secondary {
  min-height: 34px;
  padding: 0 13px;
  font-family: inherit;
  font-size: 11px;
  font-weight: 650;
  border-radius: 7px;
}

.modal-footer .btn-secondary {
  color: #596477;
  background: #fff;
  border-color: #dce1e9;
}

.modal-footer .btn-primary {
  color: #fff;
  background: #655bd7;
}

.modal-footer .btn-primary:hover:not(:disabled) {
  background: #554bc5;
}

.modal-loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 330px;
  flex-direction: column;
  color: #818b9c;
  text-align: center;
}

.modal-loading-state strong {
  margin-top: 14px;
  color: #465165;
  font-size: 12px;
}

.modal-loading-state p {
  margin: 6px 0 0;
  font-size: 10px;
}

.loading-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid #eceafb;
  border-top-color: #655bd7;
  border-radius: 50%;
  animation: dashboard-modal-spin 0.8s linear infinite;
}

@keyframes dashboard-modal-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 学生诊断 */
.modal-person-title {
  display: flex;
  align-items: center;
  gap: 13px;
}

.modal-person-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  color: #5f56ca;
  font-size: 15px;
  font-weight: 760;
  background: #eceafb;
  border-radius: 12px;
}

.student-detail-body {
  padding: 20px 24px 24px;
  background: #f7f8fb;
}

.detail-metric-grid,
.error-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.detail-metric-grid > article,
.error-summary-grid > article {
  min-width: 0;
  padding: 14px 15px;
  background: #fff;
  border: 1px solid #e8ebf1;
  border-radius: 10px;
}

.detail-metric-grid article > span,
.error-summary-grid article > span {
  display: block;
  margin-bottom: 7px;
  color: #838d9f;
  font-size: 9px;
  font-weight: 650;
}

.detail-metric-grid article > strong,
.error-summary-grid article > strong {
  display: block;
  overflow: hidden;
  color: #253044;
  font-size: 22px;
  line-height: 1.1;
  letter-spacing: -0.035em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-metric-grid strong small,
.error-summary-grid strong small {
  margin-left: 2px;
  color: #727c8e;
  font-size: 9px;
  font-weight: 650;
}

.detail-metric-grid article > p,
.error-summary-grid article > small {
  display: block;
  margin: 6px 0 0;
  overflow: hidden;
  color: #a0a7b4;
  font-size: 8px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-metric-grid article.is-danger > strong,
.error-summary-grid article.is-danger > strong {
  color: #c94d58;
}

.detail-metric-grid article.is-warning > strong {
  color: #ae7417;
}

.detail-metric-grid article.is-success > strong {
  color: #258368;
}

.student-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(260px, 0.55fr);
  gap: 12px;
  margin-top: 12px;
}

.student-history-panel,
.student-diagnosis-panel {
  min-width: 0;
  background: #fff;
  border: 1px solid #e8ebf1;
  border-radius: 11px;
}

.student-history-panel {
  padding: 16px 17px 8px;
}

.subsection-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 8px;
}

.subsection-heading h4 {
  margin: 0;
  color: #313c50;
  font-size: 12px;
}

.subsection-heading p {
  margin: 4px 0 0;
  color: #9aa2b0;
  font-size: 8px;
}

.student-history-list article {
  display: grid;
  grid-template-columns: 25px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  min-height: 49px;
  border-top: 1px solid #eff1f5;
}

.history-sequence {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 23px;
  height: 23px;
  color: #655bd7;
  font-size: 9px;
  font-weight: 720;
  background: #efedfd;
  border-radius: 7px;
}

.history-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.history-copy strong {
  overflow: hidden;
  color: #465165;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-copy small {
  color: #9ba3b1;
  font-size: 8px;
}

.history-score {
  display: flex;
  align-items: flex-end;
  min-width: 48px;
  flex-direction: column;
  gap: 2px;
}

.history-score > strong {
  font-size: 12px;
}

.history-score > span {
  color: #929aa8;
  font-size: 8px;
}

.student-diagnosis-panel {
  padding: 18px;
  background: linear-gradient(145deg, #f7f6ff, #fff);
  border-color: #dedbf7;
}

.student-diagnosis-panel.is-danger {
  background: linear-gradient(145deg, #fff5f5, #fff);
  border-color: #f0d3d6;
}

.student-diagnosis-panel.is-warning {
  background: linear-gradient(145deg, #fffbf1, #fff);
  border-color: #f0e2bd;
}

.student-diagnosis-panel.is-success {
  background: linear-gradient(145deg, #f2faf7, #fff);
  border-color: #d5eadf;
}

.diagnosis-label {
  color: #7a71ce;
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.student-diagnosis-panel h4 {
  margin: 7px 0 6px;
  color: #302a6d;
  font-size: 14px;
}

.student-diagnosis-panel.is-danger h4 {
  color: #a83d47;
}

.student-diagnosis-panel.is-warning h4 {
  color: #8f6116;
}

.student-diagnosis-panel.is-success h4 {
  color: #256f59;
}

.student-diagnosis-panel > p {
  margin: 0;
  color: #747e90;
  font-size: 9px;
  line-height: 1.65;
}

.diagnosis-divider {
  height: 1px;
  margin: 14px 0;
  background: rgba(109, 99, 221, 0.12);
}

.student-diagnosis-panel ul {
  display: flex;
  gap: 9px;
  margin: 10px 0 0;
  padding: 0;
  flex-direction: column;
  list-style: none;
}

.student-diagnosis-panel li {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: #5e687a;
  font-size: 9px;
  line-height: 1.5;
}

.student-diagnosis-panel li span {
  color: #6a60d7;
  font-weight: 800;
}

.diagnosis-basis {
  margin-top: 16px;
  padding: 9px;
  color: #969eab;
  font-size: 8px;
  line-height: 1.55;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 7px;
}

.student-detail-empty {
  min-height: 360px;
}

/* 新增知识点 */
.knowledge-create-body {
  padding: 20px 24px 24px;
  background: #fafbfc;
}

.classification-rule-card {
  display: grid;
  grid-template-columns: 1fr 22px 1fr 22px 1fr;
  align-items: center;
  gap: 6px;
  padding: 12px;
  background: #f3f2fd;
  border: 1px solid #e2dff8;
  border-radius: 10px;
}

.classification-rule-card > div {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  column-gap: 8px;
  align-items: center;
}

.classification-rule-card > div > span {
  display: inline-flex;
  grid-row: 1 / 3;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: #6157cb;
  font-size: 9px;
  font-weight: 800;
  background: #fff;
  border-radius: 7px;
}

.classification-rule-card strong {
  color: #4d476f;
  font-size: 9px;
}

.classification-rule-card small {
  margin-top: 2px;
  color: #8b86a8;
  font-size: 7px;
}

.classification-rule-card > i {
  color: #aaa5cc;
  font-size: 11px;
  font-style: normal;
  text-align: center;
}

.knowledge-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(250px, 0.75fr);
  gap: 14px;
  margin-top: 14px;
}

.knowledge-form-main,
.taxonomy-preview-panel {
  padding: 18px;
  background: #fff;
  border: 1px solid #e6e9ef;
  border-radius: 11px;
}

.form-label {
  display: block;
  margin-bottom: 7px;
  color: #3f4a5e;
  font-size: 10px;
  font-weight: 700;
}

.form-label:not(:first-child) {
  margin-top: 18px;
}

.knowledge-name-field {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 10px 0 12px;
  background: #fff;
  border: 1px solid #dce1e9;
  border-radius: 8px;
  transition: all 0.18s ease;
}

.knowledge-name-field:focus-within {
  border-color: #8b82e6;
  box-shadow: 0 0 0 3px rgba(101, 91, 215, 0.1);
}

.knowledge-name-field.has-error {
  border-color: #df7079;
  box-shadow: 0 0 0 3px rgba(223, 112, 121, 0.09);
}

.knowledge-name-field input {
  min-width: 0;
  flex: 1;
  color: #303b4e;
  font-family: inherit;
  font-size: 11px;
  background: transparent;
  border: 0;
  outline: 0;
}

.knowledge-name-field > span {
  color: #a0a7b4;
  font-size: 8px;
}

.form-help,
.form-error {
  margin: 7px 0 0;
  font-size: 8px;
  line-height: 1.55;
}

.form-help {
  color: #969eac;
}

.form-error {
  color: #bf4651;
}

.knowledge-color-options {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.knowledge-color-options > button,
.custom-color-picker {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 27px;
  height: 27px;
  background: var(--option-color);
  border: 2px solid #fff;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #dce1e9;
  cursor: pointer;
}

.knowledge-color-options > button.active::after {
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  content: '✓';
}

.knowledge-color-options > button.active {
  box-shadow: 0 0 0 2px #655bd7;
}

.custom-color-picker {
  color: #788294;
  background: #f3f5f7;
}

.custom-color-picker input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.knowledge-color-options code {
  color: #7d8696;
  font-family: Consolas, monospace;
  font-size: 9px;
}

.taxonomy-preview-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 11px;
}

.taxonomy-preview-heading strong {
  color: #3f4a5e;
  font-size: 10px;
}

.taxonomy-preview-heading span {
  padding: 3px 6px;
  color: #655bd7;
  font-size: 8px;
  font-weight: 700;
  background: #efedfd;
  border-radius: 5px;
}

.taxonomy-chip-list {
  display: flex;
  align-content: flex-start;
  min-height: 78px;
  max-height: 105px;
  overflow: auto;
  flex-wrap: wrap;
  gap: 6px;
}

.taxonomy-chip-list > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 23px;
  padding: 0 7px;
  color: #687286;
  font-size: 8px;
  background: #f5f6f8;
  border-radius: 6px;
}

.taxonomy-chip-list i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.taxonomy-chip-list em {
  color: #9ba3b0;
  font-size: 9px;
  font-style: normal;
}

.reclassify-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 13px;
  padding: 10px;
  background: #fffbf1;
  border: 1px solid #efe1ba;
  border-radius: 8px;
}

.reclassify-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  color: #9a6812;
  font-size: 13px;
  font-weight: 760;
  background: #faeabe;
  border-radius: 8px;
}

.reclassify-preview strong {
  display: block;
  color: #6f5526;
  font-size: 8px;
}

.reclassify-preview p {
  margin: 3px 0 0;
  color: #9a875f;
  font-size: 7px;
  line-height: 1.5;
}

/* 知识点错误诊断 */
.knowledge-error-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.knowledge-error-title-row > span {
  padding: 4px 7px;
  color: #655bd7;
  font-size: 8px;
  font-weight: 700;
  background: #efedfd;
  border-radius: 5px;
}

.knowledge-error-title-row > span.is-other {
  color: #946419;
  background: #fff2d4;
}

.knowledge-error-body {
  padding: 20px 24px 24px;
  background: #f7f8fb;
}

.error-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid #e7eaf0;
  border-radius: 9px;
}

.error-search {
  display: flex;
  align-items: center;
  gap: 7px;
  width: 210px;
  height: 30px;
  padding: 0 9px;
  background: #f8f9fb;
  border: 1px solid #e5e8ed;
  border-radius: 7px;
}

.error-search svg {
  width: 13px;
  height: 13px;
  color: #9aa2af;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-width: 1.8;
}

.error-search input {
  width: 100%;
  min-width: 0;
  color: #4a5568;
  font-family: inherit;
  font-size: 9px;
  background: transparent;
  border: 0;
  outline: 0;
}

.error-filter-tabs {
  display: flex;
  gap: 5px;
}

.error-filter-tabs button {
  min-height: 29px;
  padding: 0 8px;
  color: #747e90;
  font-family: inherit;
  font-size: 8px;
  background: #f5f6f8;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
}

.error-filter-tabs button span {
  margin-left: 3px;
  color: #a0a7b3;
}

.error-filter-tabs button.active {
  color: #5d53ca;
  font-weight: 700;
  background: #efedfd;
  border-color: #dcd8f7;
}

.diagnostic-error-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
  margin-top: 10px;
}

.diagnostic-error-list > article {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  min-height: 112px;
  padding: 13px;
  background: #fff;
  border: 1px solid #e7eaf0;
  border-radius: 10px;
}

.diagnostic-error-list > article.is-priority {
  border-color: #efd2d5;
  box-shadow: inset 3px 0 0 #d9616b;
}

.diagnostic-rank {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #736aaf;
  font-size: 9px;
  font-weight: 760;
  background: #f0eefc;
  border-radius: 8px;
}

.diagnostic-error-copy {
  min-width: 0;
}

.diagnostic-error-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.error-tag {
  padding: 3px 6px;
  font-size: 8px;
  font-weight: 700;
  border-radius: 5px;
}

.error-tag.high,
.error-tag.hard {
  color: #b4424c;
  background: #fff0f1;
}

.error-tag.medium {
  color: #916013;
  background: #fff3d8;
}

.error-tag.low,
.error-tag.easy {
  color: #28745e;
  background: #e8f7f0;
}

.error-occurrences {
  color: #8992a2;
  font-size: 8px;
  white-space: nowrap;
}

.diagnostic-error-copy > p {
  display: -webkit-box;
  margin: 9px 0 12px;
  overflow: hidden;
  color: #465165;
  font-size: 10px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.frequency-track {
  height: 4px;
  overflow: hidden;
  background: #eef0f4;
  border-radius: 999px;
}

.frequency-track span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #8b82e7, #655bd7);
  border-radius: inherit;
}

.is-priority .frequency-track span {
  background: linear-gradient(90deg, #e6858d, #cf515c);
}

.knowledge-error-empty {
  min-height: 340px;
}

.reclassification-progress {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 11px;
  margin: 0 22px 20px;
  padding: 12px 14px;
  background: #f5f4fe;
  border: 1px solid #dfdcf8;
  border-radius: 9px;
}

.reclassification-progress .loading-spinner {
  width: 24px;
  height: 24px;
  border-width: 2px;
}

.reclassification-progress strong {
  color: #4b456f;
  font-size: 10px;
}

.reclassification-progress p {
  margin: 3px 0 7px;
  color: #86809f;
  font-size: 8px;
}

.reclassification-track {
  height: 4px;
  overflow: hidden;
  background: #e4e1f7;
  border-radius: 999px;
}

.reclassification-track span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #8b82e7, #655bd7);
  border-radius: inherit;
  transition: width 0.25s ease;
}

.student-evidence-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.student-evidence-grid > section {
  min-width: 0;
  padding: 16px 17px;
  background: #fff;
  border: 1px solid #e8ebf1;
  border-radius: 11px;
}

.student-evidence-grid .subsection-heading > span {
  padding: 3px 6px;
  color: #655bd7;
  font-size: 8px;
  font-weight: 700;
  background: #efedfd;
  border-radius: 5px;
}

.student-weak-list,
.student-recent-errors {
  display: flex;
  flex-direction: column;
}

.student-weak-list article {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 42px;
  border-top: 1px solid #eff1f5;
}

.student-weak-list article > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.student-weak-list strong {
  overflow: hidden;
  color: #465165;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-weak-list small {
  color: #9aa2b0;
  font-size: 7px;
}

.student-weak-list article > span {
  color: #c34c57;
  font-size: 9px;
  font-weight: 700;
  white-space: nowrap;
}

.student-recent-errors article {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  min-height: 42px;
  border-top: 1px solid #eff1f5;
}

.student-recent-errors article > span {
  max-width: 78px;
  padding: 3px 6px;
  overflow: hidden;
  font-size: 7px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-radius: 5px;
}

.student-recent-errors article > span.is-high {
  color: #b4424c;
  background: #fff0f1;
}

.student-recent-errors article > span.is-medium {
  color: #916013;
  background: #fff3d8;
}

.student-recent-errors article > span.is-low {
  color: #28745e;
  background: #e8f7f0;
}

.student-recent-errors article > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.student-recent-errors strong {
  overflow: hidden;
  color: #465165;
  font-size: 8px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-recent-errors small {
  overflow: hidden;
  color: #9aa2b0;
  font-size: 7px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.evidence-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 90px;
  color: #9ca4b1;
  font-size: 9px;
  border-top: 1px solid #eff1f5;
}

.error-evidence-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: -4px 0 10px;
  overflow: hidden;
  color: #8d96a5;
  font-size: 7px;
  white-space: nowrap;
}

.error-evidence-row span,
.error-evidence-row time {
  padding-right: 6px;
  border-right: 1px solid #e5e8ed;
}

.error-evidence-row time {
  padding-right: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  border-right: 0;
}

@media (max-width: 820px) {
  .modal-overlay {
    padding: 14px;
  }

  .detail-metric-grid,
  .error-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .student-detail-grid,
  .knowledge-form-grid,
  .diagnostic-error-list,
  .student-evidence-grid {
    grid-template-columns: 1fr;
  }

  .classification-rule-card {
    grid-template-columns: 1fr;
  }

  .classification-rule-card > i {
    transform: rotate(90deg);
  }

  .error-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .error-search {
    width: 100%;
  }

  .error-filter-tabs {
    overflow-x: auto;
  }
}

@media (max-width: 560px) {
  .modal-overlay {
    align-items: flex-end;
    padding: 0;
  }

  .modal-content,
  .modal-content.modal-lg,
  .modal-content.modal-xl,
  .modal-content.wide {
    width: 100%;
    max-width: none;
    max-height: 92vh;
    border-radius: 16px 16px 0 0;
  }

  .modal-header-rich,
  .modal-body,
  .student-detail-body,
  .knowledge-create-body,
  .knowledge-error-body {
    padding-right: 18px;
    padding-left: 18px;
  }

  .modal-footer {
    align-items: stretch;
    padding: 13px 18px;
    flex-direction: column;
  }

  .modal-footer > div,
  .modal-footer .btn-primary,
  .modal-footer .btn-secondary {
    width: 100%;
  }

  .modal-footer .btn-primary,
  .modal-footer .btn-secondary {
    flex: 1;
  }

  .detail-metric-grid,
  .error-summary-grid {
    gap: 7px;
  }

  .detail-metric-grid > article,
  .error-summary-grid > article {
    padding: 12px;
  }

  .modal-person-title {
    align-items: flex-start;
  }
}
</style>
