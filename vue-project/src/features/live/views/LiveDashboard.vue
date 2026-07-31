<template>
  <div class="live-dashboard">
    <header class="live-header">
      <div class="header-left">
        <el-button text @click="goBack">← 返回</el-button>
        <h2>{{ store.sessionInfo?.title || '课堂实时互动' }}</h2>
        <el-tag :type="store.connectionStatus === 'connected' ? 'success' : 'danger'" size="small">
          {{ store.connectionStatus === 'connected' ? '已连接' : '未连接' }}
        </el-tag>
      </div>
      <div class="header-center">
        <div class="join-code-box">
          <span class="label">加入码</span>
          <span class="code">{{ store.sessionInfo?.sessionCode }}</span>
          <el-button size="small" @click="copyCode">复制</el-button>
        </div>
        <el-button size="small" type="primary" @click="showQR = true">📱 二维码</el-button>
      </div>
      <div class="header-right">
        <span class="student-badge">👥 {{ store.studentCount }} 人在线</span>
        <el-button size="small" @click="exportReport">📄 导出报告</el-button>
        <el-button type="danger" size="small" @click="handleEndSession">结束课堂</el-button>
      </div>
    </header>

    <div class="live-body">
      <aside class="live-sidebar">
        <div class="tool-panel">
          <h3>🧭 课堂辅助</h3>
          <el-button type="info" @click="pickRandom" :disabled="!store.studentList.length"
            >🎲 随机点名</el-button
          >
          <el-divider />
          <h3>👥 在线学生 ({{ store.studentCount }})</h3>
          <div class="student-list">
            <div
              v-for="s in store.studentList"
              :key="s.studentId"
              class="student-item clickable"
              @click="viewProfile(s.studentId, s.studentName)"
            >
              <span class="student-dot online"></span>{{ s.studentName }}({{ s.studentId }})
            </div>
            <div v-if="!store.studentList.length" class="no-students">暂无学生加入</div>
          </div>
          <!-- 举手队列 -->
          <div
            v-if="store.handQueue.waiting.length || store.handQueue.called.length"
            class="hand-queue-panel"
          >
            <el-divider />
            <h3>✋ 举手队列 ({{ store.handQueue.waiting.length }})</h3>
            <div class="hand-list">
              <div v-for="(h, i) in store.handQueue.waiting" :key="h.studentId" class="hand-item">
                <span class="hand-idx">{{ i + 1 }}.</span>
                <span class="hand-name">{{ h.studentName }}({{ h.studentId }})</span>
                <el-button size="small" type="primary" @click="store.callStudent(h.studentId)"
                  >点名</el-button
                >
                <el-button size="small" text type="danger" @click="store.dismissHand(h.studentId)"
                  >✕</el-button
                >
              </div>
            </div>
            <template v-if="store.handQueue.called.length">
              <div class="hand-called-title">已点名:</div>
              <div v-for="h in store.handQueue.called" :key="h.studentId" class="hand-item called">
                <span class="hand-name">{{ h.studentName }}</span>
                <el-button size="small" text type="danger" @click="store.dismissHand(h.studentId)"
                  >✕</el-button
                >
              </div>
            </template>
          </div>
          <template v-if="store.absentStudents.length">
            <el-divider />
            <h3>🚫 未加入 ({{ store.absentCount }})</h3>
            <div class="student-list">
              <div v-for="s in store.absentStudents" :key="s.studentId" class="student-item absent">
                <span class="student-dot absent"></span>{{ s.studentName }}({{ s.studentId }})
              </div>
            </div>
          </template>
        </div>
      </aside>

      <main class="live-main">
        <div class="board-toolbar">
          <div>
            <h3>📋 课堂题目</h3>
            <p>统一题库与本节课发送记录集中展示，题目可以重复使用</p>
          </div>
          <div class="board-toolbar-actions">
            <el-radio-group v-model="boardFilter" size="small">
              <el-radio-button value="ALL">全部 {{ store.questionBoard.length }}</el-radio-button>
              <el-radio-button value="UNSENT">未发送 {{ boardCount('UNSENT') }}</el-radio-button>
              <el-radio-button value="ACTIVE">作答中 {{ boardCount('ACTIVE') }}</el-radio-button>
              <el-radio-button value="CLOSED">已结束 {{ boardCount('CLOSED') }}</el-radio-button>
            </el-radio-group>
            <el-button size="small" :loading="boardLoading" @click="refreshBoard">刷新</el-button>
          </div>
        </div>

        <div v-if="filteredQuestionBoard.length" class="question-board">
          <article
            v-for="item in filteredQuestionBoard"
            :key="item.questionId"
            class="question-card"
            :class="`status-${item.status.toLowerCase()}`"
          >
            <div class="question-card-header">
              <div class="question-meta">
                <el-tag :type="typeTag(item.type)" size="small">{{ typeLabel(item.type) }}</el-tag>
                <el-tag v-if="item.knowledgePoint" size="small" effect="plain" type="info">
                  {{ item.knowledgePoint }}
                </el-tag>
                <el-tag v-if="item.status === 'UNSENT'" size="small" type="info">未发送</el-tag>
                <el-tag
                  v-else-if="item.status === 'ACTIVE'"
                  size="small"
                  type="danger"
                  effect="dark"
                >
                  作答中
                </el-tag>
                <el-tag v-else size="small" type="success">
                  已发送 · 已结束<span v-if="item.sendCount > 1"> · {{ item.sendCount }}次</span>
                </el-tag>
              </div>
              <div
                v-if="item.status === 'ACTIVE'"
                class="countdown"
                :class="{ urgent: remainingSeconds(item) <= 30 }"
              >
                剩余 {{ formatRemaining(item) }}
              </div>
              <div v-else-if="item.timeLimit" class="planned-time">
                计划 {{ formatDuration(item.timeLimit) }}
              </div>
            </div>

            <h4 class="question-title">{{ item.title }}</h4>
            <p v-if="item.description" class="question-description">{{ item.description }}</p>
            <div v-if="item.options?.length" class="question-options">
              <div
                v-for="option in item.options"
                :key="option.key"
                class="question-option"
                :class="{ correct: option.key === item.correctKey }"
              >
                <b>{{ option.key }}</b
                ><span>{{ option.text }}</span>
              </div>
            </div>
            <div v-if="item.correctKey && item.type !== 'CHOICE'" class="reference-answer">
              参考答案：{{ item.correctKey }}
            </div>

            <template v-if="item.status !== 'UNSENT'">
              <div class="response-summary">
                <div class="response-total">
                  <strong>{{ item.respondedCount }}</strong> / {{ item.totalStudents }} 人已作答
                </div>
                <el-progress
                  class="response-progress"
                  :percentage="responsePercent(item)"
                  :stroke-width="10"
                  :show-text="false"
                />
                <span v-if="item.correctRate !== null" class="correct-rate">
                  正确率 {{ item.correctRate }}%
                </span>
              </div>
              <div v-if="item.type === 'CHOICE' && item.distribution" class="answer-distribution">
                <div
                  v-for="(value, key) in item.distribution"
                  :key="key"
                  class="answer-distribution-item"
                >
                  <span class="distribution-key">{{ key }}</span>
                  <span class="distribution-track">
                    <span class="distribution-fill" :style="{ width: `${value.percent}%` }"></span>
                  </span>
                  <span class="distribution-value">{{ value.count }}人 · {{ value.percent }}%</span>
                </div>
              </div>
            </template>

            <div class="question-actions">
              <template v-if="item.status === 'UNSENT'">
                <span v-if="hasActiveQuestion" class="active-tip">当前题结束后可发送</span>
                <el-button
                  type="primary"
                  :disabled="hasActiveQuestion"
                  :loading="activatingId === item.questionId"
                  @click="sendQuestion(item.questionId)"
                >
                  发送到课堂
                </el-button>
              </template>
              <template v-else-if="item.status === 'ACTIVE'">
                <span class="extend-label">快捷延时</span>
                <el-button size="small" @click="extendQuestion(item.interactionId, 30)"
                  >+30秒</el-button
                >
                <el-button size="small" @click="extendQuestion(item.interactionId, 60)"
                  >+1分钟</el-button
                >
                <el-button size="small" @click="extendQuestion(item.interactionId, 300)"
                  >+5分钟</el-button
                >
                <el-button
                  size="small"
                  type="danger"
                  plain
                  @click="finishQuestion(item.interactionId)"
                >
                  提前结束
                </el-button>
                <el-button size="small" text @click="toggleDetail(item.interactionId)"
                  >作答明细</el-button
                >
              </template>
              <el-button v-else size="small" text @click="toggleDetail(item.interactionId)">
                {{ expandedId === item.interactionId ? '收起明细' : '查看作答明细' }}
              </el-button>
            </div>

            <div v-if="expandedId === item.interactionId" class="card-detail">
              <div v-if="detailLoading && detailId === item.interactionId" class="detail-loading">
                加载中...
              </div>
              <template v-else-if="detail">
                <div class="detail-responses">
                  <h4>作答明细 ({{ detail.responses.length }})</h4>
                  <div
                    v-for="response in detail.responses"
                    :key="response.studentId"
                    class="resp-row"
                  >
                    <span>{{ response.studentName }}({{ response.studentId }})</span>
                    <span
                      :class="
                        response.isCorrect === null
                          ? ''
                          : response.isCorrect
                            ? 'resp-correct'
                            : 'resp-wrong'
                      "
                    >
                      {{ response.answer || '-' }}
                    </span>
                  </div>
                  <div v-if="detail.unrespondedStudents.length" class="unresp">
                    <span class="unresp-label">未作答：</span
                    >{{ detail.unrespondedStudents.join(', ') }}
                  </div>
                </div>
              </template>
            </div>
          </article>
        </div>
        <el-empty v-else description="当前筛选下暂无题目" :image-size="60" />

        <div class="confusion-panel compact">
          <h3>
            🤔 学生标记“不懂” <span v-if="confusionTotal">({{ confusionTotal }}次)</span>
          </h3>
          <div v-if="confusionStats.length" class="confusion-bars">
            <div v-for="stat in confusionStats" :key="stat.name" class="confusion-bar-row">
              <span class="confusion-kp-name">{{ stat.name }}</span>
              <span class="confusion-bar-track">
                <span
                  class="confusion-bar-fill"
                  :style="{ width: Math.min(stat.count * 25, 100) + '%' }"
                ></span>
              </span>
              <span class="confusion-count">{{ stat.count }}人</span>
            </div>
          </div>
          <div v-else class="confusion-empty">暂无学生标记不懂</div>
        </div>
        <div v-if="store.reactions.length" class="reaction-bar">
          <span
            v-for="(r, i) in store.reactions"
            :key="i"
            class="reaction-bubble"
            :title="r.studentName"
          >
            {{ r.emoji }} {{ r.studentName }}
          </span>
          <el-button text size="small" @click="store.reactions = []">清除</el-button>
        </div>
      </main>

      <aside class="live-qa">
        <h3>🙋 匿名提问</h3>
        <div
          v-for="q in store.topQuestions"
          :key="q.id"
          class="qa-item"
          :class="{ answered: q.answered }"
        >
          <div class="qa-question">
            <span v-if="q.similarCount > 0" class="qa-count">×{{ q.similarCount + 1 }}</span>
            {{ q.question }}
          </div>
          <div v-if="q.answered" class="qa-answer">{{ q.answerText }}</div>
          <el-button v-else size="small" type="primary" @click="openAnswerDialog(q)"
            >回答</el-button
          >
        </div>
        <el-empty v-if="!store.topQuestions.length" description="暂无提问" :image-size="40" />
      </aside>
    </div>

    <el-dialog v-model="ans.visible" title="回答提问" width="400px">
      <p>{{ ans.q?.question }}</p>
      <el-input v-model="ans.text" type="textarea" :rows="3" />
      <template #footer
        ><el-button @click="ans.visible = false">取消</el-button
        ><el-button type="primary" @click="submitAns">回答</el-button></template
      >
    </el-dialog>

    <el-dialog v-model="showQR" title="扫码直接加入课堂" width="340px" align-center>
      <div class="qr-container">
        <canvas ref="qrCanvas"></canvas>
        <p>学生首次确认身份后，以后扫码即可自动进入</p>
        <div class="qr-code-fallback">无法扫码时输入：{{ store.sessionInfo?.sessionCode }}</div>
        <el-button text type="primary" @click="copyLink">复制课堂链接</el-button>
      </div>
    </el-dialog>

    <!-- 随机点名 -->
    <el-dialog v-model="picker.visible" title="🎲 随机点名" width="360px" align-center>
      <div class="picker-display">
        <div v-if="picker.picking" class="picker-rolling">{{ picker.current }}</div>
        <div v-else class="picker-result">{{ picker.result || '—' }}</div>
      </div>
      <template #footer
        ><el-button @click="picker.visible = false">关闭</el-button
        ><el-button type="primary" @click="doPick" :disabled="!store.studentList.length"
          >开始抽选</el-button
        ></template
      >
    </el-dialog>

    <!-- 学生画像 -->
    <el-dialog v-model="profile.visible" :title="'📊 ' + profile.name" width="420px">
      <div v-if="store.studentProfile" class="profile-body">
        <p>
          📚 参与课堂: <b>{{ store.studentProfile.totalSessions }}</b> 次
        </p>
        <p>
          📋 总互动: <b>{{ store.studentProfile.totalInteractions }}</b> 题
        </p>
        <p>
          ✍️ 已作答: <b>{{ store.studentProfile.totalAnswers }}</b> 题 ({{
            store.studentProfile.participationRate
          }}%)
        </p>
        <p>
          ✅ 正确率: <b>{{ store.studentProfile.correctRate }}%</b> ({{
            store.studentProfile.correctAnswers
          }}/{{ store.studentProfile.totalAnswers }})
        </p>
      </div>
      <div v-else class="detail-loading">加载中...</div>
    </el-dialog>

    <!-- 课程总结 -->
    <el-dialog
      v-model="showSummary"
      title="📊 课程总结"
      width="560px"
      :close-on-click-modal="false"
      @closed="goBack"
    >
      <div class="summary-body" v-if="summary">
        <div class="summary-header">
          <p><strong>课程:</strong> {{ summary.title }}</p>
          <p><strong>时长:</strong> {{ summary.duration }}</p>
          <p><strong>互动总数:</strong> {{ summary.totalInteractions }} 次</p>
        </div>
        <el-divider />
        <div v-if="summary.interactions.length" class="summary-interactions">
          <h4>📋 互动详情</h4>
          <div v-for="(h, i) in summary.interactions" :key="i" class="sum-item">
            <span class="sum-idx">#{{ i + 1 }}</span>
            <el-tag
              :type="h.type === 'CHOICE' ? 'primary' : h.type === 'OPEN' ? 'success' : 'warning'"
              size="small"
              >{{ typeLabel(h.type) }}</el-tag
            >
            <span class="sum-title">{{ h.title }}</span>
            <span class="sum-stat"
              >已答 {{ h.respondedCount }}/{{ h.totalStudents
              }}<span v-if="h.correctRate !== null"> · {{ h.correctRate }}% 正确</span></span
            >
          </div>
        </div>
        <el-divider />
        <div class="summary-attendance">
          <p>
            ✅ 已加入: <b>{{ summary.onlineCount }}</b> 人
          </p>
          <p v-if="summary.absentCount > 0">
            ❌ 未加入: <b>{{ summary.absentCount }}</b> 人
          </p>
          <p>
            🙋 提问: <b>{{ summary.qaCount }}</b> 条
          </p>
          <p v-if="summary.confusionTotal > 0">
            🤔 标记不懂: <b>{{ summary.confusionTotal }}</b> 次
          </p>
        </div>
        <div
          v-if="summary.confusionStats && summary.confusionStats.length"
          class="summary-confusions"
        >
          <h4>🤔 学生不懂的知识点</h4>
          <div v-for="s in summary.confusionStats" :key="s.name" class="sum-confusion-row">
            <span>{{ s.name }}</span
            ><span class="sum-confusion-count">{{ s.count }}人</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="closeSummary">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLiveSessionStore } from '@/features/live/store'
import type { InteractionDetail, QAQuestion, QuestionBoardItem } from '@/features/live/api/live'
import { getInteractionDetail, getReport } from '@/features/live/api/live'
import request from '@/shared/api/request'
import type { ApiResponse } from '@/shared/api/types'
import QRCode from 'qrcode'

const route = useRoute()
const router = useRouter()
const store = useLiveSessionStore()
const classId = computed(() => Number(route.params.classId))

const TYPE_LABELS: Record<string, string> = {
  CHOICE: '选择题',
  OPEN: '简答题',
  EXERCISE: '随堂练习',
}
function typeLabel(type: string) {
  return TYPE_LABELS[type] ?? type
}

type BoardFilter = 'ALL' | QuestionBoardItem['status']
const boardFilter = ref<BoardFilter>('ALL')
const boardLoading = ref(false)
const activatingId = ref<number | null>(null)
const nowEpoch = ref(Date.now())
let countdownTimer: ReturnType<typeof setInterval> | null = null

const hasActiveQuestion = computed(() =>
  store.questionBoard.some((item) => item.status === 'ACTIVE'),
)
const filteredQuestionBoard = computed(() => {
  return boardFilter.value === 'ALL'
    ? store.questionBoard
    : store.questionBoard.filter((item) => item.status === boardFilter.value)
})

function boardCount(status: QuestionBoardItem['status']) {
  return store.questionBoard.filter((item) => item.status === status).length
}
function typeTag(type: string): 'primary' | 'success' | 'warning' {
  if (type === 'OPEN') return 'success'
  if (type === 'EXERCISE') return 'warning'
  return 'primary'
}
function responsePercent(item: QuestionBoardItem) {
  if (!item.totalStudents) return 0
  return Math.min(100, Math.round((item.respondedCount * 100) / item.totalStudents))
}
function remainingSeconds(item: QuestionBoardItem) {
  if (!item.deadlineEpochMs) return Number.MAX_SAFE_INTEGER
  return Math.max(0, Math.ceil((item.deadlineEpochMs - nowEpoch.value) / 1000))
}
function formatRemaining(item: QuestionBoardItem) {
  if (!item.deadlineEpochMs) return '不限时'
  return formatDuration(remainingSeconds(item))
}
function formatDuration(seconds: number) {
  if (seconds < 60) return `${seconds}秒`
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return rest ? `${minutes}分${rest}秒` : `${minutes}分钟`
}
async function refreshBoard() {
  boardLoading.value = true
  try {
    await store.refreshQuestionBoard()
  } catch {
    ElMessage.error('题目看板刷新失败')
  } finally {
    boardLoading.value = false
  }
}
async function sendQuestion(questionId: number) {
  activatingId.value = questionId
  try {
    await store.sendQuestion(questionId)
    ElMessage.success('题目已发送，学生端开始作答')
  } catch {
    ElMessage.error('发送失败，请确认当前没有其他题目正在作答')
    await refreshBoard()
  } finally {
    activatingId.value = null
  }
}
async function extendQuestion(interactionId: number | null, seconds: number) {
  if (!interactionId) return
  try {
    await store.extendInteraction(interactionId, seconds)
    ElMessage.success(`已延时${formatDuration(seconds)}`)
  } catch {
    ElMessage.error('延时失败，题目可能已经结束')
    await refreshBoard()
  }
}
function finishQuestion(interactionId: number | null) {
  if (!interactionId) return
  store.closeInteraction(interactionId)
  ElMessage.success('已结束作答')
}

// === 不懂标记统计 ===
const confusionStats = ref<{ name: string; count: number }[]>([])
const confusionTotal = ref(0)
let confusionTimer: ReturnType<typeof setInterval> | null = null
async function loadConfusionStats() {
  if (!store.sessionId) return
  try {
    const res = await request.get<
      ApiResponse<{ stats: { name: string; count: number }[]; total: number }>
    >(`/live/session/${store.sessionId}/confusion-stats`)
    if (res.data?.code === 200) {
      confusionStats.value = res.data.data.stats || []
      confusionTotal.value = res.data.data.total || 0
    }
  } catch {}
}

const expandedId = ref<number | null>(null)
const detail = ref<InteractionDetail | null>(null)
const detailLoading = ref(false)
const detailId = ref<number | null>(null)

async function toggleDetail(interactionId: number | null) {
  if (!interactionId) return
  if (expandedId.value === interactionId) {
    expandedId.value = null
    detail.value = null
    return
  }
  expandedId.value = interactionId
  detailId.value = interactionId
  detailLoading.value = true
  try {
    const r = await getInteractionDetail(store.sessionId!, interactionId)
    detail.value = r.data.data
  } catch {
    detail.value = null
    expandedId.value = null
  } finally {
    detailLoading.value = false
  }
}

// 实时刷新已展开的详情
watch(
  () => store.lastStats,
  async (s) => {
    if (!s || expandedId.value !== s.interactionId) return
    try {
      const r = await getInteractionDetail(store.sessionId!, s.interactionId)
      if (r.data.data) detail.value = r.data.data
    } catch {}
  },
)

const ans = reactive({ visible: false, q: null as QAQuestion | null, text: '' })
function openAnswerDialog(q: QAQuestion) {
  ans.q = q
  ans.text = ''
  ans.visible = true
}
function submitAns() {
  if (ans.q) {
    store.answerQuestion(ans.q.id, ans.text)
    ans.visible = false
  }
}

const showQR = ref(false)
const qrCanvas = ref<HTMLCanvasElement | null>(null)
const liveUrl = computed(() => `${window.location.origin}/live/${store.sessionInfo?.sessionCode}`)
watch(showQR, async (v) => {
  if (!v) return
  await nextTick()
  if (!qrCanvas.value) return
  await QRCode.toCanvas(qrCanvas.value, liveUrl.value, { width: 220 })
})

function copyCode() {
  navigator.clipboard
    .writeText(store.sessionInfo?.sessionCode || '')
    .then(() => ElMessage.success('已复制'))
}
function copyLink() {
  navigator.clipboard.writeText(liveUrl.value).then(() => ElMessage.success('课堂链接已复制'))
}
function goBack() {
  router.push({ name: 'classManage', params: { id: classId.value } })
}

const showSummary = ref(false)
function closeSummary() {
  showSummary.value = false
  goBack()
}
interface LessonSummary {
  title: string
  duration: string
  totalInteractions: number
  interactions: Array<{
    type: string
    title: string
    respondedCount: number
    totalStudents: number
    correctRate: number | null
  }>
  onlineCount: number
  absentCount: number
  qaCount: number
  confusionTotal: number
  confusionStats: { name: string; count: number }[]
}
const summary = ref<LessonSummary | null>(null)
function buildSummary(): LessonSummary {
  const start = store.sessionInfo?.startedAt ? new Date(store.sessionInfo.startedAt) : null
  const now = new Date()
  const diffMin = start ? Math.round((now.getTime() - start.getTime()) / 60000) : 0
  return {
    title: store.sessionInfo?.title || '',
    duration: diffMin >= 60 ? `${Math.floor(diffMin / 60)}h${diffMin % 60}m` : `${diffMin} 分钟`,
    totalInteractions: store.interactionHistory.length,
    interactions: store.interactionHistory.map((h) => ({
      type: h.type,
      title: h.title,
      respondedCount: h.respondedCount,
      totalStudents: h.totalStudents,
      correctRate: h.correctRate,
    })),
    onlineCount: store.studentCount,
    absentCount: store.absentCount,
    qaCount: store.topQuestions.length,
    confusionTotal: confusionTotal.value,
    confusionStats: confusionStats.value,
  }
}
async function handleEndSession() {
  await ElMessageBox.confirm('确定结束？', '确认', { confirmButtonText: '结束', type: 'warning' })
  await loadConfusionStats() // 结课前拉一次最新数据
  summary.value = buildSummary()
  await store.endLiveSession()
  showSummary.value = true
}

// === 随机点名 ===
const picker = reactive({ visible: false, picking: false, current: '', result: '' })
let pickerTimer: ReturnType<typeof setInterval> | null = null
function pickRandom() {
  picker.visible = true
  picker.result = ''
}
function doPick() {
  picker.picking = true
  picker.result = ''
  let i = 0
  pickerTimer = setInterval(() => {
    picker.current = store.studentList[i % store.studentList.length]?.studentName || ''
    i++
  }, 80)
  setTimeout(() => {
    if (pickerTimer) clearInterval(pickerTimer)
    pickerTimer = null
    picker.picking = false
    picker.result =
      store.studentList[Math.floor(Math.random() * store.studentList.length)]?.studentName || ''
  }, 1500)
}

// === 导出报告 ===
async function exportReport() {
  const sid = store.sessionId
  if (!sid) return
  const dur = summary.value?.duration || ''
  const html = await getReport(
    sid,
    store.sessionInfo?.title || '',
    dur,
    store.studentCount,
    store.absentCount,
    store.topQuestions.length,
  )
    .then((r) => r.data.data.html)
    .catch(() => '')
  if (html) {
    const reportWindow = window.open('', '_blank')
    if (!reportWindow) return
    reportWindow.document.write(html)
    reportWindow.document.close()
  }
}

// === 学生画像 ===
const profile = reactive({ visible: false, name: '' })
function viewProfile(studentId: string, studentName: string) {
  profile.visible = true
  profile.name = studentName
  store.fetchStudentProfile(studentId, classId.value)
}

onMounted(async () => {
  countdownTimer = setInterval(() => {
    nowEpoch.value = Date.now()
  }, 1000)
  try {
    const r = await store.checkActiveSession(classId.value)
    if (r.hasActive && r.sessionId && r.sessionCode && r.title) {
      store.sessionInfo = {
        sessionId: r.sessionId,
        sessionCode: r.sessionCode,
        title: r.title,
        className: '',
        teacherName: '',
        token: '',
        studentId: '',
        studentName: '',
        hasActive: true,
        currentInteraction: null,
        startedAt: r.startedAt,
      }
      store.role = 'teacher'
      store.connect()
    } else await store.createSession(classId.value)
  } catch {
    await store.createSession(classId.value)
  }
  // 30秒轮询不懂统计
  confusionTimer = setInterval(loadConfusionStats, 30000)
  loadConfusionStats()
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (confusionTimer) clearInterval(confusionTimer)
  if (pickerTimer) clearInterval(pickerTimer)
  store.disconnect()
})
</script>

<style scoped>
.live-dashboard {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
}
.live-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 10;
}
.header-left,
.header-center,
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.join-code-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  background: #ecf5ff;
  border-radius: 6px;
}
.join-code-box .code {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #409eff;
  font-family: monospace;
}
.live-body {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 12px;
  overflow: hidden;
}
.live-sidebar {
  width: 220px;
  flex-shrink: 0;
}
.tool-panel {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.live-main {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  overflow-y: auto;
}
.board-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.board-toolbar h3 {
  margin: 0 0 4px;
  font-size: 18px;
}
.board-toolbar p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.board-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.question-board {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.question-card {
  padding: 16px 18px;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  background: #fff;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    background 0.2s;
}
.question-card.status-active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 3px 12px rgba(64, 158, 255, 0.14);
}
.question-card.status-closed {
  border-color: #b3e19d;
  background: #f0f9eb;
}
.question-card-header,
.question-meta,
.question-actions,
.response-summary {
  display: flex;
  align-items: center;
}
.question-card-header {
  justify-content: space-between;
  gap: 12px;
}
.question-meta {
  flex-wrap: wrap;
  gap: 6px;
}
.countdown {
  color: #409eff;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.countdown.urgent {
  color: #f56c6c;
}
.planned-time {
  color: #909399;
  font-size: 13px;
  white-space: nowrap;
}
.question-title {
  margin: 12px 0 8px;
  color: #303133;
  font-size: 16px;
  line-height: 1.55;
}
.question-description,
.reference-answer {
  margin: 8px 0;
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
.reference-answer {
  padding: 8px 10px;
  color: #529b2e;
  background: rgba(103, 194, 58, 0.1);
  border-radius: 6px;
}
.question-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 10px 0;
}
.question-option {
  display: flex;
  gap: 8px;
  padding: 8px 10px;
  color: #606266;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  font-size: 13px;
}
.question-option.correct {
  color: #529b2e;
  border-color: #95d475;
}
.response-summary {
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed rgba(144, 147, 153, 0.35);
  font-size: 13px;
}
.response-total {
  min-width: 128px;
}
.response-total strong {
  color: #409eff;
  font-size: 19px;
}
.response-progress {
  flex: 1;
}
.correct-rate {
  min-width: 88px;
  color: #529b2e;
  font-weight: 600;
  text-align: right;
}
.answer-distribution {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px 16px;
  margin-top: 10px;
}
.answer-distribution-item {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
}
.distribution-key {
  width: 20px;
  font-weight: 700;
}
.distribution-track {
  flex: 1;
  height: 8px;
  overflow: hidden;
  background: rgba(144, 147, 153, 0.18);
  border-radius: 4px;
}
.distribution-fill {
  display: block;
  height: 100%;
  min-width: 1px;
  background: #409eff;
  border-radius: 4px;
  transition: width 0.25s;
}
.distribution-value {
  min-width: 86px;
  color: #909399;
  text-align: right;
}
.question-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
}
.question-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.active-tip,
.extend-label {
  margin-right: auto;
  color: #909399;
  font-size: 12px;
}
.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.history-card {
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
  transition: all 0.2s;
}
.history-card.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 1px 6px rgba(64, 158, 255, 0.12);
}
.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  cursor: pointer;
  user-select: none;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  flex: 1;
}
.card-stats {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #909399;
}
.expand-hint {
  font-size: 11px;
  color: #a0c5e8;
  white-space: nowrap;
}
.expand-hint:hover {
  color: #409eff;
}
.card-detail {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e4e7ed;
}
.detail-loading {
  text-align: center;
  padding: 20px;
  color: #909399;
}
.detail-question {
  margin-bottom: 12px;
  font-size: 14px;
}
.detail-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 8px 0;
}
.detail-opt {
  padding: 4px 12px;
  background: #f0f2f5;
  border-radius: 4px;
  font-size: 13px;
}
.detail-opt.correct {
  background: #f0f9eb;
  color: #67c23a;
  font-weight: 600;
}
.detail-key {
  font-size: 13px;
  color: #67c23a;
  font-weight: 600;
}
.dist-bars {
  margin: 10px 0;
}
.dist-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0;
  font-size: 13px;
}
.dist-key {
  min-width: 32px;
  font-weight: 600;
}
.dist-bar-wrap {
  flex: 1;
  height: 16px;
  background: #f0f2f5;
  border-radius: 3px;
  overflow: hidden;
}
.dist-bar {
  height: 100%;
  background: linear-gradient(90deg, #409eff, #79bbff);
  border-radius: 3px;
  min-width: 2px;
  transition: width 0.3s;
}
.dist-num {
  min-width: 80px;
  text-align: right;
  color: #909399;
  font-size: 12px;
}
.detail-responses {
  font-size: 13px;
}
.detail-responses h4 {
  margin: 10px 0 6px;
}
.resp-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  border-bottom: 1px solid #f5f5f5;
}
.resp-correct {
  color: #67c23a;
}
.resp-wrong {
  color: #f56c6c;
}
.unresp {
  margin-top: 8px;
  color: #e6a23c;
  font-size: 12px;
}
.unresp-label {
  font-weight: 600;
}
.live-qa {
  width: 270px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  overflow-y: auto;
}
.qa-item {
  padding: 10px;
  margin-bottom: 8px;
  border-radius: 6px;
  background: #f9f9f9;
  border-left: 3px solid #e6a23c;
}
.qa-item.answered {
  border-left-color: #67c23a;
}
.qa-count {
  display: inline-block;
  background: #e6a23c;
  color: #fff;
  border-radius: 10px;
  padding: 0 6px;
  font-size: 11px;
  margin-right: 4px;
}
.qa-answer {
  margin-top: 6px;
  padding: 6px 8px;
  background: #f0f9eb;
  border-radius: 4px;
  font-size: 12px;
  color: #67c23a;
}
.option-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.option-key {
  font-weight: 700;
  min-width: 20px;
}
.qr-container {
  text-align: center;
}
.qr-container p {
  margin: 10px 0 8px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
}
.qr-code-fallback {
  margin-bottom: 4px;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
}
.student-badge {
  font-size: 14px;
  color: #666;
}
.student-list {
  max-height: 200px;
  overflow-y: auto;
  font-size: 13px;
}
.student-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  color: #333;
}
.student-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #67c23a;
  flex-shrink: 0;
}
.student-dot.absent {
  background: #f56c6c;
}
.student-item.absent {
  color: #909399;
}
.no-students {
  color: #ccc;
  font-size: 12px;
  text-align: center;
  padding: 8px;
}
.student-item.clickable {
  cursor: pointer;
}
.student-item.clickable:hover {
  background: #f0f2f5;
  border-radius: 4px;
}
.hand-queue-panel {
  margin-top: 4px;
}
.hand-list {
  max-height: 180px;
  overflow-y: auto;
  font-size: 13px;
}
.hand-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
  border-bottom: 1px solid #f5f5f5;
}
.hand-idx {
  min-width: 20px;
  color: #909399;
  font-size: 12px;
}
.hand-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hand-item.called .hand-name {
  color: #909399;
  text-decoration: line-through;
}
.hand-called-title {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  padding: 4px 0;
}
.reaction-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 16px;
  padding: 10px;
  background: #fafafa;
  border-radius: 6px;
  align-items: center;
}
.reaction-bubble {
  padding: 2px 8px;
  background: #fff;
  border-radius: 12px;
  font-size: 13px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.picker-display {
  text-align: center;
  padding: 30px 0;
  font-size: 36px;
  font-weight: 700;
}
.picker-rolling {
  color: #909399;
}
.picker-result {
  color: #409eff;
  animation: pop 0.3s;
}
@keyframes pop {
  0% {
    transform: scale(0.5);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
.profile-body p {
  margin: 8px 0;
  font-size: 14px;
}
.summary-body {
  font-size: 14px;
}
.summary-header p {
  margin: 4px 0;
}
.summary-interactions {
  margin: 10px 0;
}
.sum-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid #f5f5f5;
  font-size: 13px;
}
.sum-idx {
  color: #c0c4cc;
  min-width: 24px;
}
.sum-title {
  flex: 1;
}
.sum-stat {
  color: #909399;
  font-size: 12px;
}
.summary-attendance p {
  margin: 4px 0;
}
.confusion-panel {
  margin-bottom: 16px;
  padding: 14px 16px;
  background: #fff7f0;
  border: 1px solid #f5dab1;
  border-radius: 8px;
}
.confusion-panel.compact {
  margin-top: 20px;
  margin-bottom: 0;
}
.confusion-panel h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: #b88230;
}
.confusion-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.confusion-bar-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.confusion-kp-name {
  width: 80px;
  text-align: right;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.confusion-bar-track {
  flex: 1;
  height: 14px;
  background: #f5ead6;
  border-radius: 4px;
  overflow: hidden;
}
.confusion-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #e6a23c, #f56c6c);
  border-radius: 4px;
  min-width: 2px;
  transition: width 0.3s;
}
.confusion-count {
  min-width: 36px;
  font-weight: 600;
  color: #e6a23c;
  font-size: 12px;
}
.confusion-empty {
  text-align: center;
  color: #c0a87c;
  font-size: 13px;
  padding: 4px 0;
}
.sum-confusion-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
}
.sum-confusion-count {
  color: #e6a23c;
  font-weight: 600;
}
</style>
