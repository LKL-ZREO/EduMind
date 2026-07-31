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
            <div class="queue-marker">{{ Number(index) + 1 }}</div>
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
              <span class="confusion-rank">{{ Number(index) + 1 }}</span>
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
                  v-for="item in knowledgeMasterySorted"
                  :key="item.id || item.name"
                  v-show="item.name !== '其他'"
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
                <span class="diagnostic-rank">{{
                  String(Number(index) + 1).padStart(2, '0')
                }}</span>
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

<script lang="ts">
import dashboardPage from '../model/dashboardPage'

export default dashboardPage
</script>

<style scoped src="../styles/Dashboard.css"></style>
