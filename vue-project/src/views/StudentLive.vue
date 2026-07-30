<template>
  <div class="student-live">
    <div v-if="!joined" class="join-page">
      <div class="join-orb orb-one"></div>
      <div class="join-orb orb-two"></div>
      <header class="join-brand">
        <span class="brand-mark">E</span>
        <div>
          <strong>EduMind</strong>
          <span>学生课堂</span>
        </div>
      </header>

      <main class="join-shell">
        <section class="join-intro">
          <span class="intro-kicker">LIVE CLASSROOM</span>
          <h1>专注课堂，<br />每次互动都有回应。</h1>
          <p>接收老师的随堂题、快速举手或匿名提问，课后还能回看自己的作答记录。</p>
          <div class="intro-features">
            <div><span>01</span><strong>实时接题</strong><small>课堂节奏一目了然</small></div>
            <div><span>02</span><strong>即时反馈</strong><small>提交与连接状态明确</small></div>
            <div><span>03</span><strong>针对答疑</strong><small>不懂的题可获取 AI 解析</small></div>
          </div>
        </section>

        <section class="join-card">
          <div v-if="identifying" class="identity-loading">
            <span class="loading-rings"><i></i></span>
            <strong>正在确认课堂与设备身份</strong>
            <p>已绑定过的设备会自动进入</p>
          </div>

          <div v-else-if="previewFailed" class="join-error-state">
            <span class="state-icon">!</span>
            <h2>暂时无法进入课堂</h2>
            <p>课堂码可能无效、课堂已经结束，或当前网络不可用。</p>
            <el-button type="primary" size="large" @click="loadPreview">重新检查</el-button>
            <router-link to="/live/join">返回输入课堂码</router-link>
          </div>

          <template v-else>
            <div class="join-card-head">
              <span class="live-pill"><i></i> 课堂进行中</span>
              <h2>确认学生身份</h2>
              <p>只用于本次课堂互动和个人作答记录</p>
            </div>

            <div v-if="preview" class="class-preview">
              <span class="class-preview-icon">课</span>
              <div>
                <strong>{{ preview.title }}</strong>
                <p>
                  {{ preview.className || '当前班级' }} · {{ preview.teacherName || '任课教师' }}
                </p>
              </div>
              <span class="class-code">{{ code }}</span>
            </div>

            <el-form label-position="top" class="identity-form" @submit.prevent="handleJoin">
              <el-form-item label="学号">
                <el-input
                  v-model="form.studentId"
                  size="large"
                  autocomplete="username"
                  placeholder="请输入你的学号"
                  clearable
                  @keyup.enter="handleJoin"
                />
              </el-form-item>
              <el-form-item v-if="preview?.requiresStudentName" label="姓名">
                <el-input
                  v-model="form.studentName"
                  size="large"
                  autocomplete="name"
                  placeholder="班级暂无花名册，请输入姓名"
                  clearable
                  @keyup.enter="handleJoin"
                />
              </el-form-item>
            </el-form>

            <p class="bind-tip">
              <span>✓</span>
              {{
                preview?.requiresStudentName
                  ? '首次确认后，这台设备下次扫码可自动进入'
                  : '姓名会根据班级花名册自动匹配，无需手动输入'
              }}
            </p>
            <el-button
              type="primary"
              size="large"
              class="join-button"
              :loading="joining"
              @click="handleJoin"
            >
              进入实时课堂 <span aria-hidden="true">→</span>
            </el-button>
          </template>
        </section>
      </main>
    </div>

    <div v-else class="classroom-page">
      <header class="student-header">
        <div class="header-brand">
          <span class="brand-mark small">E</span>
          <div class="header-title">
            <strong>{{ store.sessionInfo?.title || '实时课堂' }}</strong>
            <span>{{ store.sessionInfo?.className || '课堂互动' }}</span>
          </div>
        </div>

        <div class="header-actions">
          <span class="connection-chip" :class="connectionTone">
            <i></i>{{ connectionLabel }}
          </span>
          <div class="student-identity">
            <span class="student-avatar">{{ studentInitial }}</span>
            <div>
              <strong>{{ store.sessionInfo?.studentName }}</strong>
              <button type="button" @click="handleSwitchIdentity">切换身份</button>
            </div>
          </div>
        </div>
      </header>

      <main class="student-main">
        <div v-if="store.sessionEnded" class="system-banner ended">
          <span>✓</span>
          <div><strong>本次课堂已经结束</strong><small>你的互动与作答记录已保存</small></div>
        </div>
        <div v-else-if="store.connectionStatus !== 'connected'" class="system-banner reconnecting">
          <span class="mini-spinner"></span>
          <div><strong>正在恢复课堂连接</strong><small>连接恢复前请暂缓提交答案</small></div>
        </div>
        <div v-else-if="!store.teacherOnline" class="system-banner waiting">
          <span>···</span>
          <div><strong>老师暂时离开课堂</strong><small>页面会保持连接，请耐心等待</small></div>
        </div>

        <div class="classroom-grid">
          <section class="stage-column">
            <article
              v-if="store.currentInteraction?.status === 'ACTIVE'"
              class="question-card active-question"
            >
              <div class="question-head">
                <div>
                  <span class="question-kicker"> <i></i> 正在作答 · {{ typeLabel }} </span>
                  <span v-if="done" class="submitted-chip">✓ 已提交，可修改</span>
                </div>
                <div
                  v-if="store.currentInteraction.deadlineEpochMs"
                  class="timer-ring"
                  :class="{ urgent: remaining <= 10 }"
                  :style="countdownStyle"
                >
                  <span
                    ><strong>{{ remaining }}</strong
                    ><small>秒</small></span
                  >
                </div>
              </div>

              <div class="question-content">
                <h1>{{ store.currentInteraction.title }}</h1>
                <div
                  v-if="store.currentInteraction.description"
                  class="question-description"
                  v-html="renderTextWithBreaks(store.currentInteraction.description)"
                ></div>
              </div>

              <div v-if="store.currentInteraction.type === 'CHOICE'" class="choice-options">
                <button
                  v-for="option in store.currentInteraction.options || []"
                  :key="option.key"
                  type="button"
                  class="choice-option"
                  :class="{ selected: sel === option.key }"
                  @click="sel = option.key"
                >
                  <span class="opt-key">{{ option.key }}</span>
                  <span class="opt-text">{{ option.text }}</span>
                  <span class="option-check">✓</span>
                </button>
              </div>
              <div v-else class="open-answer">
                <label for="live-answer">你的答案</label>
                <el-input
                  id="live-answer"
                  v-model="textAns"
                  type="textarea"
                  :rows="6"
                  maxlength="800"
                  show-word-limit
                  resize="none"
                  placeholder="写下你的思路或答案，清晰表达比字数更重要……"
                />
              </div>

              <div class="submit-area">
                <div class="submit-hint">
                  <strong>{{ answerHintTitle }}</strong>
                  <span>{{ answerHintText }}</span>
                </div>
                <el-button
                  type="primary"
                  size="large"
                  class="submit-button"
                  :disabled="!canSubmit"
                  :loading="submitting"
                  @click="handleSubmit"
                >
                  {{ submitButtonText }}
                </el-button>
              </div>
              <el-progress
                v-if="store.currentInteraction.deadlineEpochMs"
                class="time-progress"
                :percentage="pct"
                :show-text="false"
                :status="remaining <= 10 ? 'exception' : undefined"
                :stroke-width="5"
              />
            </article>

            <article
              v-else-if="store.currentInteraction?.status === 'CLOSED'"
              class="question-card result-card"
            >
              <div class="result-summary" :class="resultTone">
                <span class="result-icon">{{ resultIcon }}</span>
                <div>
                  <span class="question-kicker">本题已截止 · {{ typeLabel }}</span>
                  <h2>{{ resultTitle }}</h2>
                  <p>{{ resultSubtitle }}</p>
                </div>
              </div>

              <div class="question-content result-question">
                <h1>{{ store.currentInteraction.title }}</h1>
                <div
                  v-if="store.currentInteraction.description"
                  class="question-description"
                  v-html="renderTextWithBreaks(store.currentInteraction.description)"
                ></div>
              </div>

              <div v-if="store.currentInteraction.type === 'CHOICE'" class="choice-options result">
                <div
                  v-for="option in store.currentInteraction.options || []"
                  :key="option.key"
                  class="choice-option"
                  :class="{
                    correct: option.key === store.currentInteraction.correctKey,
                    wrong:
                      currentAnswer === option.key &&
                      option.key !== store.currentInteraction.correctKey,
                  }"
                >
                  <span class="opt-key">{{ option.key }}</span>
                  <span class="opt-text">{{ option.text }}</span>
                  <span v-if="option.key === store.currentInteraction.correctKey" class="answer-tag"
                    >正确答案</span
                  >
                  <span v-else-if="currentAnswer === option.key" class="answer-tag mine"
                    >我的选择</span
                  >
                </div>
              </div>
              <div v-else class="open-result">
                <div>
                  <span>我的答案</span>
                  <p>{{ currentAnswer || '本题未作答' }}</p>
                </div>
                <div v-if="store.currentInteraction.correctKey">
                  <span>参考答案</span>
                  <p>{{ store.currentInteraction.correctKey }}</p>
                </div>
              </div>

              <div class="result-actions">
                <div>
                  <strong>这道题还有疑问？</strong>
                  <span>标记后可查看针对本题的知识点解析</span>
                </div>
                <el-button
                  type="warning"
                  plain
                  size="large"
                  :disabled="confusionState(store.currentInteraction.interactionId)?.confused"
                  :loading="confusionState(store.currentInteraction.interactionId)?.confusing"
                  @click="handleConfused(store.currentInteraction.interactionId)"
                >
                  {{
                    confusionState(store.currentInteraction.interactionId)?.confused
                      ? '已生成解析'
                      : '我没弄懂'
                  }}
                </el-button>
              </div>

              <div
                v-if="confusionState(store.currentInteraction.interactionId)?.confused"
                class="ai-explanation"
              >
                <div class="explanation-header">
                  <div><span>AI</span><strong>知识点解析</strong></div>
                  <el-tag type="warning" effect="light" round>
                    {{ confusionState(store.currentInteraction.interactionId)?.kp || '本题知识点' }}
                  </el-tag>
                </div>
                <div
                  class="explanation-body"
                  v-html="
                    renderTextWithBreaks(
                      confusionState(store.currentInteraction.interactionId)?.explanation,
                    )
                  "
                ></div>
              </div>
            </article>

            <article v-else class="question-card waiting-card">
              <div class="waiting-visual"><span></span><i></i><b>课</b></div>
              <span class="question-kicker">课堂进行中</span>
              <h2>{{ waitingTitle }}</h2>
              <p>{{ waitingDescription }}</p>
              <div class="waiting-tips">
                <span><i>✓</i> 保持页面打开</span>
                <span><i>✓</i> 新题会自动出现</span>
                <span><i>✓</i> 有疑问可匿名提问</span>
              </div>
            </article>

            <section v-if="closedHistory.length" class="history-section">
              <div class="section-heading">
                <div>
                  <span>MY ACTIVITY</span>
                  <h2>我的课堂记录</h2>
                </div>
                <div class="history-filters">
                  <button
                    v-for="filter in historyFilters"
                    :key="filter.value"
                    type="button"
                    :class="{ active: historyFilter === filter.value }"
                    @click="historyFilter = filter.value"
                  >
                    {{ filter.label }}
                  </button>
                </div>
              </div>

              <div v-if="displayHistory.length" class="history-list">
                <article
                  v-for="(item, index) in displayHistory"
                  :key="item.interactionId"
                  class="history-card"
                >
                  <div class="history-index">{{ String(index + 1).padStart(2, '0') }}</div>
                  <div class="history-body">
                    <div class="history-title-row">
                      <div>
                        <span class="type-tag">{{ typeLabelMap[item.type] || item.type }}</span>
                        <strong>{{ item.title }}</strong>
                      </div>
                      <span class="history-result" :class="historyResultClass(item)">
                        {{ historyResultLabel(item) }}
                      </span>
                    </div>
                    <div class="history-answers">
                      <span
                        >我的答案：<b>{{ item.myAnswer || '未作答' }}</b></span
                      >
                      <span v-if="item.correctKey"
                        >正确答案：<b>{{ item.correctKey }}</b></span
                      >
                    </div>
                    <button
                      type="button"
                      class="confusion-link"
                      :disabled="confusionState(item.interactionId)?.confusing"
                      @click="handleConfused(item.interactionId)"
                    >
                      {{
                        confusionState(item.interactionId)?.confused
                          ? '重新查看 AI 解析'
                          : '没弄懂？查看 AI 解析 →'
                      }}
                    </button>
                    <div
                      v-if="confusionState(item.interactionId)?.confused"
                      class="ai-explanation compact"
                    >
                      <div class="explanation-header">
                        <div><span>AI</span><strong>知识点解析</strong></div>
                        <el-tag type="warning" effect="light" round>
                          {{ confusionState(item.interactionId)?.kp || '本题知识点' }}
                        </el-tag>
                      </div>
                      <div
                        class="explanation-body"
                        v-html="
                          renderTextWithBreaks(confusionState(item.interactionId)?.explanation)
                        "
                      ></div>
                    </div>
                  </div>
                </article>
              </div>
              <div v-else class="history-empty">这个筛选条件下暂时没有记录</div>
              <button
                v-if="filteredHistory.length > 3"
                type="button"
                class="history-toggle"
                @click="historyExpanded = !historyExpanded"
              >
                {{ historyExpanded ? '收起记录' : `查看全部 ${filteredHistory.length} 条记录` }}
              </button>
            </section>
          </section>

          <aside class="classroom-sidebar">
            <section class="side-card session-card">
              <div class="side-card-title"><span>课堂状态</span><i class="live-dot"></i></div>
              <div class="session-name">{{ store.sessionInfo?.title }}</div>
              <dl>
                <div>
                  <dt>班级</dt>
                  <dd>{{ store.sessionInfo?.className || '—' }}</dd>
                </div>
                <div>
                  <dt>教师</dt>
                  <dd>{{ store.sessionInfo?.teacherName || '—' }}</dd>
                </div>
                <div>
                  <dt>加入码</dt>
                  <dd class="mono">{{ store.sessionInfo?.sessionCode }}</dd>
                </div>
                <div>
                  <dt>开始时间</dt>
                  <dd>{{ sessionStartedLabel }}</dd>
                </div>
              </dl>
            </section>

            <section class="side-card participation-card">
              <div class="side-card-title"><span>我的参与</span><small>本节课</small></div>
              <div class="participation-stats">
                <div>
                  <strong>{{ completedCount }}</strong
                  ><span>已结束互动</span>
                </div>
                <div>
                  <strong>{{ answeredCount }}</strong
                  ><span>我的作答</span>
                </div>
                <div>
                  <strong>{{ correctCount }}</strong
                  ><span>答对题目</span>
                </div>
              </div>
              <div class="participation-progress">
                <div>
                  <span>参与进度</span><b>{{ participationRate }}%</b>
                </div>
                <el-progress :percentage="participationRate" :show-text="false" :stroke-width="7" />
              </div>
            </section>

            <section class="side-card feedback-card">
              <div class="side-card-title"><span>课堂反馈</span><small>轻触发送</small></div>
              <div class="reaction-grid">
                <button
                  v-for="reaction in reactions"
                  :key="reaction.emoji"
                  type="button"
                  :disabled="!canCommunicate"
                  @click="sendEmoji(reaction.emoji)"
                >
                  <span>{{ reaction.emoji }}</span
                  ><small>{{ reaction.label }}</small>
                </button>
              </div>
              <button
                type="button"
                class="hand-action"
                :class="{ raised: store.handRaised }"
                :disabled="!canCommunicate"
                @click="toggleHand"
              >
                <span>✋</span>
                <div>
                  <strong>{{
                    store.handRaised ? '已经举手，等待老师回应' : '有问题？向老师举手'
                  }}</strong>
                  <small>{{
                    store.handRaised ? '再次点击可取消举手' : '老师会在课堂端看到你'
                  }}</small>
                </div>
              </button>
            </section>
          </aside>
        </div>
      </main>

      <footer class="question-dock">
        <div class="dock-inner">
          <div class="dock-label">
            <span>?</span>
            <div><strong>匿名提问</strong><small>问题内容不会展示你的姓名</small></div>
          </div>
          <el-input
            v-model="qaText"
            size="large"
            maxlength="200"
            :disabled="!canCommunicate"
            placeholder="输入你没听懂的地方，老师会在课堂端看到……"
            @keyup.enter="handleQA"
          />
          <el-button
            type="primary"
            size="large"
            :disabled="!qaText.trim() || !canCommunicate"
            @click="handleQA"
          >
            发送问题
          </el-button>
        </div>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLiveSessionStore } from '../stores/live-session'
import {
  previewSession,
  unbindStudentDevice,
  type InteractionHistoryItem,
  type LiveSessionInfo,
} from '../api/live'
import request from '../api/request'
import { getApiErrorMessage } from '../api/errors'
import type { ApiResponse } from '../api/types'
import { renderTextWithBreaks } from '@/utils/safeHtml'

const route = useRoute()
const store = useLiveSessionStore()
const code = computed(() => String(route.params.sessionCode || '').toUpperCase())

const joined = ref(false)
const joining = ref(false)
const identifying = ref(true)
const previewFailed = ref(false)
const preview = ref<LiveSessionInfo | null>(null)
const form = ref({
  studentId: localStorage.getItem('live_student_id') || '',
  studentName: localStorage.getItem('live_student_name') || '',
})

async function handleJoin() {
  if (!form.value.studentId.trim()) {
    ElMessage.warning('请输入学号')
    return
  }
  if (preview.value?.requiresStudentName && !form.value.studentName.trim()) {
    ElMessage.warning('班级尚无花名册，请输入姓名')
    return
  }
  joining.value = true
  try {
    const data = await store.joinSession(
      code.value,
      form.value.studentId.trim(),
      form.value.studentName.trim() || undefined,
    )
    finishJoin(data)
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '身份确认失败，请检查学号'))
  } finally {
    joining.value = false
  }
}

function finishJoin(data: LiveSessionInfo) {
  localStorage.removeItem('live_student_id')
  localStorage.removeItem('live_student_name')
  form.value.studentId = data.studentId
  form.value.studentName = data.studentName
  joined.value = true
}

async function tryAutomaticJoin() {
  try {
    const data = await store.quickJoinSession(code.value)
    finishJoin(data)
    return
  } catch {
    // 没有设备身份时继续显示身份确认表单。
  }

  const rememberedId = form.value.studentId.trim()
  if (!rememberedId) return
  try {
    const data = await store.joinSession(
      code.value,
      rememberedId,
      form.value.studentName.trim() || undefined,
    )
    finishJoin(data)
  } catch {
    // 旧版暂存的身份无法匹配时保留输入内容，供学生自行修正。
  }
}

async function loadPreview() {
  identifying.value = true
  previewFailed.value = false
  try {
    const response = await previewSession(code.value)
    preview.value = response.data.data
    if (!preview.value) throw new Error('课堂不存在')
    await tryAutomaticJoin()
  } catch {
    preview.value = null
    previewFailed.value = true
  } finally {
    identifying.value = false
  }
}

async function handleSwitchIdentity() {
  try {
    await ElMessageBox.confirm('切换后需要重新输入学号，确定继续吗？', '切换学生身份', {
      confirmButtonText: '确认切换',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await unbindStudentDevice()
    store.disconnect()
    store.reset()
    localStorage.removeItem('live_student_id')
    localStorage.removeItem('live_student_name')
    form.value = { studentId: '', studentName: '' }
    joined.value = false
    previewFailed.value = false
    ElMessage.success('设备身份已清除，请重新确认')
  } catch {
    // 用户取消切换时无需提示。
  }
}

const sel = ref('')
const textAns = ref('')
const done = ref(false)
const submitting = ref(false)
const submittedAnswers = reactive<Record<string, string>>({})

const canCommunicate = computed(() => store.connectionStatus === 'connected' && !store.sessionEnded)
const canSubmit = computed(() => {
  if (!canCommunicate.value) return false
  if (!store.currentInteraction || store.currentInteraction.status !== 'ACTIVE') return false
  return store.currentInteraction.type === 'CHOICE' ? !!sel.value : textAns.value.trim().length > 0
})

async function handleSubmit() {
  if (!canSubmit.value || !store.currentInteraction) return
  submitting.value = true
  const answer = store.currentInteraction.type === 'CHOICE' ? sel.value : textAns.value.trim()
  const sent = store.submitResponse(answer)
  if (!sent) {
    ElMessage.error('课堂连接尚未恢复，答案未提交，请稍后重试')
    submitting.value = false
    return
  }
  submittedAnswers[String(store.currentInteraction.interactionId)] = answer
  done.value = true
  submitting.value = false
  ElMessage.success(done.value ? '答案已提交，截止前仍可修改' : '答案已提交')
}

const remaining = ref(0)
const pct = ref(100)
let timer: ReturnType<typeof setInterval> | null = null

function stopTimer() {
  if (timer) clearInterval(timer)
  timer = null
}

function updateCountdown(deadline: number, total: number) {
  remaining.value = Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
  pct.value = Math.max(0, Math.min(100, Math.round((remaining.value / total) * 10000) / 100))
  if (remaining.value <= 0) stopTimer()
}

watch(
  () =>
    [
      store.currentInteraction?.interactionId,
      store.currentInteraction?.status,
      store.currentInteraction?.deadlineEpochMs,
    ] as const,
  ([interactionId, status, deadline], previousValue) => {
    const previousId = previousValue?.[0]
    stopTimer()
    const interaction = store.currentInteraction
    if (!interactionId || !interaction) {
      done.value = false
      sel.value = ''
      textAns.value = ''
      return
    }

    const historyAnswer = store.interactionHistory.find(
      (item) => item.interactionId === interactionId,
    )?.myAnswer
    const savedAnswer = submittedAnswers[String(interactionId)] || historyAnswer || ''
    if (interactionId !== previousId) {
      sel.value = interaction.type === 'CHOICE' ? savedAnswer : ''
      textAns.value = interaction.type === 'CHOICE' ? '' : savedAnswer
      done.value = !!savedAnswer
    }

    if (deadline && status === 'ACTIVE') {
      const initialRemaining = Math.max(1, Math.ceil((deadline - Date.now()) / 1000))
      const total = Math.max(interaction.timeLimit ?? 0, initialRemaining)
      updateCountdown(deadline, total)
      timer = setInterval(() => updateCountdown(deadline, total), 250)
    }
  },
  { immediate: true },
)

const qaText = ref('')
function handleQA() {
  const question = qaText.value.trim()
  if (!question) return
  if (!store.askQuestion(question)) {
    ElMessage.error('课堂连接尚未恢复，问题未发送')
    return
  }
  qaText.value = ''
  ElMessage.success('问题已匿名发送给老师')
}

const reactions = [
  { emoji: '👍', label: '听懂了' },
  { emoji: '👏', label: '很清楚' },
  { emoji: '🤔', label: '再讲讲' },
  { emoji: '⚡', label: '有启发' },
]

function sendEmoji(emoji: string) {
  if (!store.sendReaction(emoji)) {
    ElMessage.error('连接尚未恢复，反馈未发送')
    return
  }
  ElMessage({ message: `${emoji} 反馈已发送`, duration: 800 })
}

function toggleHand() {
  const sent = store.handRaised ? store.lowerHand() : store.raiseHand()
  if (!sent) ElMessage.error('连接尚未恢复，请稍后再试')
}

const typeLabelMap: Record<string, string> = {
  CHOICE: '选择题',
  OPEN: '简答题',
  EXERCISE: '随堂练习',
}
const typeLabel = computed(() => typeLabelMap[store.currentInteraction?.type || ''] || '互动题')

const currentHistory = computed(() =>
  store.interactionHistory.find(
    (item) => item.interactionId === store.currentInteraction?.interactionId,
  ),
)
const currentAnswer = computed(() => {
  const id = store.currentInteraction?.interactionId
  if (!id) return ''
  return (
    submittedAnswers[String(id)] || currentHistory.value?.myAnswer || sel.value || textAns.value
  )
})
const currentCorrect = computed<boolean | null>(() => {
  if (typeof currentHistory.value?.myCorrect === 'boolean') return currentHistory.value.myCorrect
  const interaction = store.currentInteraction
  if (interaction?.type === 'CHOICE' && interaction.correctKey && currentAnswer.value) {
    return interaction.correctKey === currentAnswer.value
  }
  return null
})

const resultTone = computed(() => {
  if (!currentAnswer.value) return 'unanswered'
  if (currentCorrect.value === true) return 'correct'
  if (currentCorrect.value === false) return 'wrong'
  return 'submitted'
})
const resultIcon = computed(() => {
  if (!currentAnswer.value) return '—'
  if (currentCorrect.value === true) return '✓'
  if (currentCorrect.value === false) return '×'
  return '✓'
})
const resultTitle = computed(() => {
  if (!currentAnswer.value) return '这道题没有作答'
  if (currentCorrect.value === true) return '回答正确，做得不错'
  if (currentCorrect.value === false) return '这道题需要再理解一下'
  return '你的答案已经记录'
})
const resultSubtitle = computed(() => {
  if (!currentAnswer.value) return '可以查看正确答案，并标记不懂获取解析。'
  if (currentCorrect.value === true) return '继续保持，看看知识点是否真正掌握。'
  if (currentCorrect.value === false) return '对照正确答案，找到思路中的关键差异。'
  return '简答题由老师结合课堂情况进行讲解。'
})

const answerHintTitle = computed(() => {
  if (!canCommunicate.value) return '等待课堂连接恢复'
  if (done.value) return '当前答案已记录'
  if (store.currentInteraction?.type === 'CHOICE' && !sel.value) return '请选择一个答案'
  if (store.currentInteraction?.type !== 'CHOICE' && !textAns.value.trim()) return '请写下你的答案'
  return '答案已准备好'
})
const answerHintText = computed(() =>
  done.value ? '截止前修改后可再次提交' : '提交成功后会显示明确提示',
)
const submitButtonText = computed(() => {
  if (!canCommunicate.value) return '正在重新连接'
  return done.value ? '更新答案' : '确认提交'
})
const countdownStyle = computed(() => ({ '--timer-progress': `${pct.value * 3.6}deg` }))

const connectionLabel = computed(() => {
  if (store.sessionEnded) return '课堂已结束'
  if (store.connectionStatus === 'connected') return '实时连接'
  if (store.connectionStatus === 'connecting') return '正在连接'
  return '连接中断'
})
const connectionTone = computed(() => {
  if (store.sessionEnded) return 'ended'
  return store.connectionStatus
})
const studentInitial = computed(() => store.sessionInfo?.studentName?.trim().slice(0, 1) || '同')
const sessionStartedLabel = computed(() => {
  const value = store.sessionInfo?.startedAt
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
})

const waitingTitle = computed(() => {
  if (store.sessionEnded) return '本次课堂已结束'
  if (store.connectionStatus !== 'connected') return '正在重新连接课堂'
  if (!store.teacherOnline) return '老师暂时离开，请稍等'
  return '等待老师发起下一项互动'
})
const waitingDescription = computed(() => {
  if (store.sessionEnded) return '你可以在下方回顾本节课的作答记录。'
  if (store.connectionStatus !== 'connected') return '恢复后题目会自动同步，不需要刷新页面。'
  return '题目发布后会自动显示在这里，不需要手动刷新。'
})

const completedHistory = computed(() =>
  store.interactionHistory.filter((item) => item.status !== 'ACTIVE'),
)
const completedCount = computed(() => completedHistory.value.length)
const answeredCount = computed(
  () => completedHistory.value.filter((item) => !!item.myAnswer).length,
)
const correctCount = computed(
  () => completedHistory.value.filter((item) => item.myCorrect === true).length,
)
const participationRate = computed(() => {
  if (!completedCount.value) return 0
  return Math.round((answeredCount.value / completedCount.value) * 100)
})

type HistoryFilter = 'ALL' | 'CORRECT' | 'REVIEW'
const historyFilter = ref<HistoryFilter>('ALL')
const historyExpanded = ref(false)
const historyFilters: Array<{ label: string; value: HistoryFilter }> = [
  { label: '全部', value: 'ALL' },
  { label: '答对', value: 'CORRECT' },
  { label: '待复习', value: 'REVIEW' },
]
const closedHistory = computed(() =>
  completedHistory.value.filter(
    (item) => item.interactionId !== store.currentInteraction?.interactionId,
  ),
)
const filteredHistory = computed(() => {
  if (historyFilter.value === 'CORRECT') return closedHistory.value.filter((item) => item.myCorrect)
  if (historyFilter.value === 'REVIEW') {
    return closedHistory.value.filter((item) => item.myCorrect !== true)
  }
  return closedHistory.value
})
const displayHistory = computed(() =>
  historyExpanded.value ? filteredHistory.value : filteredHistory.value.slice(0, 3),
)

function historyResultLabel(item: InteractionHistoryItem) {
  if (!item.myAnswer) return '未作答'
  if (item.myCorrect === true) return '回答正确'
  if (item.myCorrect === false) return '需要复习'
  return '已提交'
}
function historyResultClass(item: InteractionHistoryItem) {
  if (!item.myAnswer) return 'unanswered'
  if (item.myCorrect === true) return 'correct'
  if (item.myCorrect === false) return 'wrong'
  return 'submitted'
}

interface ConfusionState {
  confused: boolean
  confusing: boolean
  explanation: string
  kp: string
}
const confusionMap = reactive<Record<string, ConfusionState>>({})

function getOrCreateState(id: number): ConfusionState {
  const key = String(id)
  const existing = confusionMap[key]
  if (existing) return existing
  const created = { confused: false, confusing: false, explanation: '', kp: '' }
  confusionMap[key] = created
  return created
}
function confusionState(id: number): ConfusionState | undefined {
  return confusionMap[String(id)]
}

async function handleConfused(interactionId: number) {
  if (!store.sessionInfo) return
  const state = getOrCreateState(interactionId)
  if (state.confused) return
  state.confusing = true
  try {
    const res = await request.post<ApiResponse<{ knowledgePoint: string; explanation: string }>>(
      '/live/confusion/mark',
      { sessionId: store.sessionInfo.sessionId, interactionId },
      {
        timeout: 30000,
        headers: { Authorization: `Bearer ${store.sessionInfo.token}` },
      },
    )
    if (res.data?.code === 200) {
      state.kp = res.data.data.knowledgePoint
      state.explanation = res.data.data.explanation
      state.confused = true
    } else {
      ElMessage.error(res.data?.message || '解析生成失败')
    }
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '网络错误，请稍后重试'))
  } finally {
    state.confusing = false
  }
}

onMounted(loadPreview)
onUnmounted(() => {
  stopTimer()
  store.disconnect()
})
</script>

<style scoped>
:global(body) {
  margin: 0;
  background: #f4f6fb;
}

.student-live {
  min-height: 100vh;
  color: #172039;
  background: #f4f6fb;
  font-family:
    Inter,
    'PingFang SC',
    'Microsoft YaHei',
    -apple-system,
    BlinkMacSystemFont,
    sans-serif;
}

button,
input,
textarea {
  font: inherit;
}

.join-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 12% 16%, rgba(95, 86, 226, 0.16), transparent 30%),
    radial-gradient(circle at 88% 82%, rgba(79, 125, 243, 0.13), transparent 30%),
    linear-gradient(135deg, #f8f9ff 0%, #f3f5ff 52%, #fafbff 100%);
}

.join-orb {
  position: absolute;
  border-radius: 999px;
  border: 1px solid rgba(95, 86, 226, 0.1);
  pointer-events: none;
}

.orb-one {
  width: 420px;
  height: 420px;
  left: -210px;
  bottom: -190px;
  box-shadow: 0 0 0 70px rgba(95, 86, 226, 0.025);
}

.orb-two {
  width: 260px;
  height: 260px;
  right: -110px;
  top: -100px;
  box-shadow: 0 0 0 54px rgba(79, 125, 243, 0.025);
}

.join-brand {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 11px;
  width: min(1160px, calc(100% - 48px));
  margin: 0 auto;
  padding-top: 30px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  background: linear-gradient(145deg, #675ce7, #4b63d8);
  box-shadow: 0 8px 22px rgba(85, 81, 210, 0.24);
}

.join-brand > div,
.header-title {
  display: flex;
  flex-direction: column;
}

.join-brand strong {
  font-size: 16px;
  letter-spacing: 0.02em;
}

.join-brand span:last-child {
  color: #858da3;
  font-size: 11px;
  letter-spacing: 0.12em;
}

.join-shell {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(380px, 0.72fr);
  align-items: center;
  gap: 92px;
  width: min(1100px, calc(100% - 48px));
  min-height: calc(100vh - 92px);
  margin: 0 auto;
  padding: 48px 0 82px;
}

.intro-kicker {
  display: inline-flex;
  padding: 7px 11px;
  border: 1px solid rgba(95, 86, 226, 0.18);
  border-radius: 7px;
  color: #5f56df;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
  background: rgba(255, 255, 255, 0.68);
}

.join-intro h1 {
  margin: 24px 0 18px;
  font-size: clamp(38px, 4.2vw, 58px);
  line-height: 1.16;
  letter-spacing: -0.045em;
}

.join-intro > p {
  max-width: 540px;
  margin: 0;
  color: #68718a;
  font-size: 16px;
  line-height: 1.85;
}

.intro-features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
  margin-top: 52px;
}

.intro-features div {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding-top: 16px;
  border-top: 1px solid #dfe3f1;
}

.intro-features span {
  color: #7269e8;
  font:
    700 11px/1 ui-monospace,
    SFMono-Regular,
    Menlo,
    monospace;
}

.intro-features strong {
  font-size: 14px;
}

.intro-features small {
  color: #939aae;
  font-size: 12px;
}

.join-card {
  padding: 32px;
  border: 1px solid rgba(213, 218, 237, 0.8);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 28px 70px rgba(43, 52, 99, 0.13);
  backdrop-filter: blur(18px);
}

.identity-loading,
.join-error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 370px;
  text-align: center;
}

.identity-loading strong,
.join-error-state h2 {
  margin: 22px 0 7px;
  font-size: 18px;
}

.identity-loading p,
.join-error-state p {
  margin: 0;
  color: #8a92a8;
  font-size: 13px;
}

.loading-rings {
  position: relative;
  width: 62px;
  height: 62px;
  border: 2px solid #e7e9f8;
  border-radius: 50%;
}

.loading-rings::before,
.loading-rings i {
  position: absolute;
  inset: 8px;
  content: '';
  border: 2px solid transparent;
  border-top-color: #675ce7;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loading-rings i {
  inset: 19px;
  border-top-color: #5c8af0;
  animation-direction: reverse;
  animation-duration: 0.7s;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.join-error-state .state-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  color: #d36a62;
  font-size: 25px;
  font-weight: 700;
  background: #fff0ee;
}

.join-error-state .el-button {
  margin-top: 24px;
}
.join-error-state a {
  margin-top: 15px;
  color: #6a63d9;
  font-size: 13px;
  text-decoration: none;
}

.join-card-head {
  margin-bottom: 22px;
}
.join-card-head h2 {
  margin: 17px 0 7px;
  font-size: 24px;
  letter-spacing: -0.02em;
}
.join-card-head p {
  margin: 0;
  color: #8a92a8;
  font-size: 13px;
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 9px;
  border-radius: 7px;
  color: #277961;
  font-size: 11px;
  font-weight: 700;
  background: #eaf8f3;
}

.live-pill i,
.live-dot,
.connection-chip i,
.question-kicker i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #36b58d;
  box-shadow: 0 0 0 4px rgba(54, 181, 141, 0.12);
}

.class-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  padding: 14px;
  border: 1px solid #e6e8f4;
  border-radius: 13px;
  background: #f8f9fd;
}

.class-preview-icon {
  display: grid;
  flex: 0 0 40px;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  color: #5f56df;
  font-weight: 700;
  background: #ebeafe;
}

.class-preview > div {
  min-width: 0;
  flex: 1;
}
.class-preview strong {
  display: block;
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.class-preview p {
  margin: 4px 0 0;
  color: #8a92a8;
  font-size: 12px;
}
.class-code {
  color: #7d8498;
  font:
    700 11px/1 ui-monospace,
    monospace;
  letter-spacing: 0.08em;
}

.identity-form :deep(.el-form-item__label) {
  color: #414961;
  font-size: 13px;
  font-weight: 650;
}
.identity-form :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 10px;
  box-shadow: 0 0 0 1px #dfe2ed inset;
}
.identity-form :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px #675ce7 inset,
    0 0 0 4px rgba(103, 92, 231, 0.08);
}

.bind-tip {
  display: flex;
  gap: 7px;
  margin: 2px 0 20px;
  color: #747d94;
  font-size: 12px;
  line-height: 1.6;
}

.bind-tip span {
  color: #37a17f;
  font-weight: 700;
}
.join-button {
  width: 100%;
  min-height: 47px;
  border: 0;
  border-radius: 10px;
  background: linear-gradient(135deg, #675ce7, #516fe2);
}
.join-button span {
  margin-left: 6px;
}

.classroom-page {
  min-height: 100vh;
  padding-bottom: 94px;
  background: #f4f6fb;
}

.student-header {
  position: sticky;
  z-index: 20;
  top: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 66px;
  padding: 0 max(24px, calc((100vw - 1180px) / 2));
  border-bottom: 1px solid #e4e7f0;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(15px);
}

.header-brand,
.header-actions,
.student-identity,
.question-head,
.question-head > div,
.side-card-title,
.submit-area,
.result-actions,
.history-title-row,
.dock-inner,
.dock-label {
  display: flex;
  align-items: center;
}

.header-brand {
  gap: 11px;
  min-width: 0;
}
.brand-mark.small {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  font-size: 17px;
}
.header-title {
  min-width: 0;
}
.header-title strong {
  max-width: 440px;
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.header-title span {
  margin-top: 2px;
  color: #969caf;
  font-size: 11px;
}
.header-actions {
  gap: 19px;
}

.connection-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 999px;
  color: #327b65;
  font-size: 11px;
  font-weight: 650;
  background: #edf8f4;
}

.connection-chip.connecting,
.connection-chip.disconnected {
  color: #a96e24;
  background: #fff5e8;
}
.connection-chip.connecting i,
.connection-chip.disconnected i {
  background: #e6a23c;
  box-shadow: 0 0 0 4px rgba(230, 162, 60, 0.12);
  animation: pulse 1.2s infinite;
}
.connection-chip.ended {
  color: #7f879a;
  background: #f0f1f5;
}
.connection-chip.ended i {
  background: #9ca2b2;
  box-shadow: none;
}

@keyframes pulse {
  50% {
    opacity: 0.35;
  }
}

.student-identity {
  gap: 9px;
}
.student-avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: #5b55c9;
  font-size: 13px;
  font-weight: 700;
  background: #ecebfb;
}
.student-identity > div {
  display: flex;
  flex-direction: column;
}
.student-identity strong {
  font-size: 12px;
}
.student-identity button {
  padding: 0;
  border: 0;
  color: #9399aa;
  font-size: 10px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.student-main {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
  padding: 28px 0 44px;
}

.system-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  padding: 12px 16px;
  border: 1px solid;
  border-radius: 12px;
}
.system-banner > span {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  border-radius: 50%;
  font-weight: 700;
}
.system-banner > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.system-banner strong {
  font-size: 13px;
}
.system-banner small {
  font-size: 11px;
  opacity: 0.76;
}
.system-banner.ended {
  color: #327b65;
  border-color: #cce9de;
  background: #eff9f5;
}
.system-banner.ended > span {
  background: #d6f0e7;
}
.system-banner.reconnecting,
.system-banner.waiting {
  color: #98661f;
  border-color: #f1d8ae;
  background: #fff8ed;
}
.system-banner.waiting > span {
  background: #ffebca;
}

.mini-spinner {
  border: 2px solid #efd7b0;
  border-top-color: #d98a25;
  animation: spin 0.8s linear infinite;
}

.classroom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: start;
  gap: 24px;
}

.stage-column {
  min-width: 0;
}
.question-card,
.side-card,
.history-section {
  border: 1px solid #e3e6ef;
  background: #fff;
  box-shadow: 0 8px 28px rgba(37, 46, 86, 0.045);
}
.question-card {
  overflow: hidden;
  border-radius: 18px;
}
.active-question {
  padding: 30px 34px 0;
}
.question-head {
  justify-content: space-between;
}
.question-head > div {
  gap: 10px;
}
.question-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #676f84;
  font-size: 11px;
  font-weight: 750;
  letter-spacing: 0.04em;
}
.question-kicker i {
  background: #665ce1;
  box-shadow: 0 0 0 4px rgba(102, 92, 225, 0.1);
}
.submitted-chip {
  padding: 5px 8px;
  border-radius: 7px;
  color: #2b8066;
  font-size: 10px;
  font-weight: 700;
  background: #eaf8f3;
}

.timer-ring {
  --timer-progress: 360deg;
  display: grid;
  flex: 0 0 58px;
  place-items: center;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: conic-gradient(#665ce1 var(--timer-progress), #eceefa 0);
}
.timer-ring::before {
  grid-area: 1 / 1;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  content: '';
  background: #fff;
}
.timer-ring > span {
  z-index: 1;
  display: flex;
  align-items: baseline;
  gap: 2px;
}
.timer-ring strong {
  font-size: 18px;
  line-height: 1;
}
.timer-ring small {
  color: #8c93a6;
  font-size: 9px;
}
.timer-ring.urgent {
  color: #d95757;
  background: conic-gradient(#e55d5d var(--timer-progress), #f8e7e7 0);
}

.question-content {
  margin: 18px 0 22px;
}
.question-content h1 {
  margin: 0;
  color: #182039;
  font-size: clamp(21px, 2.2vw, 28px);
  line-height: 1.45;
  letter-spacing: -0.025em;
}
.question-description {
  margin-top: 12px;
  color: #687087;
  font-size: 14px;
  line-height: 1.75;
}

.choice-options {
  display: flex;
  flex-direction: column;
  gap: 11px;
  margin: 20px 0 26px;
}
.choice-option {
  display: flex;
  align-items: center;
  gap: 13px;
  width: 100%;
  min-height: 58px;
  padding: 11px 14px;
  border: 1px solid #e0e3ed;
  border-radius: 12px;
  color: #30384d;
  text-align: left;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.18s,
    background 0.18s,
    transform 0.18s;
}
.choice-option:hover {
  border-color: #ada8ee;
  background: #fafaff;
}
.choice-option:active {
  transform: scale(0.995);
}
.opt-key {
  display: grid;
  flex: 0 0 34px;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  color: #677087;
  font-size: 13px;
  font-weight: 750;
  background: #f0f2f7;
}
.opt-text {
  flex: 1;
  font-size: 14px;
  line-height: 1.55;
}
.option-check {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: transparent;
  font-size: 11px;
}
.choice-option.selected {
  border-color: #665ce1;
  background: #f7f6ff;
  box-shadow: 0 0 0 3px rgba(102, 92, 225, 0.08);
}
.choice-option.selected .opt-key {
  color: #fff;
  background: #665ce1;
}
.choice-option.selected .option-check {
  color: #fff;
  background: #665ce1;
}

.open-answer label {
  display: block;
  margin-bottom: 9px;
  color: #4a5267;
  font-size: 12px;
  font-weight: 700;
}
.open-answer :deep(.el-textarea__inner) {
  padding: 15px;
  border-radius: 12px;
  line-height: 1.7;
  box-shadow: 0 0 0 1px #dfe2ec inset;
}
.open-answer :deep(.el-textarea__inner:focus) {
  box-shadow:
    0 0 0 1px #665ce1 inset,
    0 0 0 4px rgba(102, 92, 225, 0.07);
}

.submit-area {
  justify-content: space-between;
  gap: 18px;
  margin-top: 24px;
  padding: 19px 0 24px;
  border-top: 1px solid #eceef4;
}
.submit-hint {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.submit-hint strong {
  font-size: 12px;
}
.submit-hint span {
  color: #969caf;
  font-size: 11px;
}
.submit-button {
  min-width: 138px;
  min-height: 42px;
  border: 0;
  border-radius: 9px;
  background: linear-gradient(135deg, #675ce7, #526ee0);
}
.time-progress {
  margin: 0 -34px;
}

.result-card {
  padding: 0 34px 30px;
}
.result-summary {
  display: flex;
  align-items: center;
  gap: 15px;
  margin: 0 -34px 25px;
  padding: 22px 34px;
  border-bottom: 1px solid #e4f1eb;
  background: #f3faf7;
}
.result-icon {
  display: grid;
  flex: 0 0 43px;
  place-items: center;
  width: 43px;
  height: 43px;
  border-radius: 50%;
  color: #fff;
  font-size: 22px;
  font-weight: 700;
  background: #3da47f;
}
.result-summary h2 {
  margin: 6px 0 2px;
  font-size: 18px;
}
.result-summary p {
  margin: 0;
  color: #6f7d78;
  font-size: 12px;
}
.result-summary.wrong {
  border-color: #f5dcdc;
  background: #fff6f5;
}
.result-summary.wrong .result-icon {
  background: #d96767;
}
.result-summary.unanswered {
  border-color: #e5e7ed;
  background: #f7f8fa;
}
.result-summary.unanswered .result-icon {
  background: #939bac;
}
.result-summary.submitted {
  border-color: #dfe6f8;
  background: #f5f7fd;
}
.result-summary.submitted .result-icon {
  background: #697bd4;
}
.result-question {
  margin-bottom: 18px;
}

.choice-options.result .choice-option {
  cursor: default;
}
.choice-options.result .choice-option:hover {
  border-color: #e0e3ed;
  background: #fff;
}
.choice-options.result .choice-option.correct {
  border-color: #54ad8d;
  background: #f0faf6;
}
.choice-options.result .choice-option.correct .opt-key {
  color: #fff;
  background: #48a482;
}
.choice-options.result .choice-option.wrong {
  border-color: #e89b9b;
  background: #fff5f4;
}
.choice-options.result .choice-option.wrong .opt-key {
  color: #fff;
  background: #d96b6b;
}
.answer-tag {
  padding: 5px 7px;
  border-radius: 6px;
  color: #2d8064;
  font-size: 10px;
  font-weight: 700;
  background: #dff3eb;
}
.answer-tag.mine {
  color: #b55e5e;
  background: #fde4e2;
}

.open-result {
  display: grid;
  gap: 10px;
  margin: 20px 0;
}
.open-result > div {
  padding: 14px 16px;
  border-radius: 11px;
  background: #f7f8fb;
}
.open-result span {
  color: #9097aa;
  font-size: 10px;
  font-weight: 700;
}
.open-result p {
  margin: 7px 0 0;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.result-actions {
  justify-content: space-between;
  gap: 18px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #eceef4;
}
.result-actions > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.result-actions strong {
  font-size: 13px;
}
.result-actions span {
  color: #9299ab;
  font-size: 11px;
}
.result-actions :deep(.el-button) {
  border-radius: 9px;
}

.ai-explanation {
  margin-top: 18px;
  overflow: hidden;
  border: 1px solid #e7ddc6;
  border-radius: 12px;
  background: #fffdf8;
}
.explanation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 14px;
  border-bottom: 1px solid #eee6d4;
  background: #fffaf0;
}
.explanation-header > div {
  display: flex;
  align-items: center;
  gap: 8px;
}
.explanation-header > div > span {
  display: grid;
  place-items: center;
  width: 25px;
  height: 25px;
  border-radius: 7px;
  color: #fff;
  font-size: 9px;
  font-weight: 800;
  background: #b98836;
}
.explanation-header strong {
  font-size: 12px;
}
.explanation-body {
  padding: 15px;
  color: #4c4b47;
  font-size: 13px;
  line-height: 1.8;
}

.waiting-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 410px;
  padding: 48px 28px;
  text-align: center;
}
.waiting-visual {
  position: relative;
  display: grid;
  place-items: center;
  width: 118px;
  height: 118px;
  margin: 10px 0 28px;
}
.waiting-visual span,
.waiting-visual i {
  position: absolute;
  inset: 0;
  border: 1px solid #dcdcf7;
  border-radius: 50%;
  animation: waiting-pulse 2.4s ease-out infinite;
}
.waiting-visual i {
  inset: 18px;
  animation-delay: 0.5s;
}
.waiting-visual b {
  display: grid;
  z-index: 1;
  place-items: center;
  width: 57px;
  height: 57px;
  border-radius: 17px;
  color: #fff;
  font-size: 17px;
  background: linear-gradient(145deg, #7167e8, #5871df);
  box-shadow: 0 12px 28px rgba(93, 91, 218, 0.25);
}
@keyframes waiting-pulse {
  70%,
  100% {
    opacity: 0;
    transform: scale(1.12);
  }
}
.waiting-card h2 {
  margin: 13px 0 9px;
  font-size: 21px;
}
.waiting-card > p {
  margin: 0;
  color: #8a91a5;
  font-size: 13px;
}
.waiting-tips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px 18px;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #eceef4;
  color: #7e879b;
  font-size: 11px;
}
.waiting-tips i {
  color: #42a482;
  font-style: normal;
}

.classroom-sidebar {
  position: sticky;
  top: 90px;
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.side-card {
  padding: 19px;
  border-radius: 15px;
}
.side-card-title {
  justify-content: space-between;
  margin-bottom: 16px;
}
.side-card-title > span {
  font-size: 12px;
  font-weight: 750;
}
.side-card-title small {
  color: #a0a6b5;
  font-size: 10px;
}
.session-name {
  margin-bottom: 14px;
  padding-bottom: 13px;
  border-bottom: 1px solid #eceef3;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.5;
}
.session-card dl {
  display: flex;
  flex-direction: column;
  gap: 11px;
  margin: 0;
}
.session-card dl div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.session-card dt {
  color: #9aa0b1;
  font-size: 11px;
}
.session-card dd {
  margin: 0;
  max-width: 175px;
  overflow: hidden;
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-card .mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  letter-spacing: 0.08em;
}

.participation-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.participation-stats div {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 11px 4px;
  border-radius: 10px;
  background: #f7f8fb;
}
.participation-stats strong {
  font-size: 19px;
}
.participation-stats span {
  margin-top: 4px;
  color: #969dae;
  font-size: 9px;
}
.participation-progress {
  margin-top: 16px;
}
.participation-progress > div {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 10px;
}
.participation-progress span {
  color: #8d94a7;
}
.participation-progress b {
  color: #625bd2;
}

.reaction-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 7px;
}
.reaction-grid button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 9px 2px;
  border: 1px solid #e5e7ef;
  border-radius: 9px;
  background: #fafbfc;
  cursor: pointer;
  transition:
    transform 0.15s,
    border-color 0.15s;
}
.reaction-grid button:hover:not(:disabled) {
  border-color: #b7b3e9;
  transform: translateY(-2px);
}
.reaction-grid button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.reaction-grid span {
  font-size: 20px;
}
.reaction-grid small {
  color: #838a9e;
  font-size: 8px;
}

.hand-action {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  margin-top: 12px;
  padding: 11px;
  border: 1px solid #e2e4ed;
  border-radius: 11px;
  text-align: left;
  background: #fff;
  cursor: pointer;
}
.hand-action > span {
  font-size: 24px;
}
.hand-action > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.hand-action strong {
  font-size: 10px;
}
.hand-action small {
  color: #999faf;
  font-size: 9px;
}
.hand-action.raised {
  border-color: #d9c179;
  background: #fff9e9;
}
.hand-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.history-section {
  margin-top: 22px;
  padding: 25px;
  border-radius: 17px;
}
.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 15px;
  margin-bottom: 19px;
}
.section-heading > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.section-heading span {
  color: #7770dd;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.15em;
}
.section-heading h2 {
  margin: 0;
  font-size: 17px;
}
.history-filters {
  display: flex;
  gap: 4px;
  padding: 3px;
  border-radius: 8px;
  background: #f1f2f6;
}
.history-filters button {
  padding: 6px 9px;
  border: 0;
  border-radius: 6px;
  color: #82899b;
  font-size: 10px;
  background: transparent;
  cursor: pointer;
}
.history-filters button.active {
  color: #4e4b96;
  font-weight: 700;
  background: #fff;
  box-shadow: 0 2px 6px rgba(54, 60, 92, 0.08);
}

.history-list {
  display: flex;
  flex-direction: column;
}
.history-card {
  display: grid;
  grid-template-columns: 39px minmax(0, 1fr);
  gap: 12px;
  padding: 16px 0;
  border-top: 1px solid #eceef3;
}
.history-card:first-child {
  border-top: 0;
}
.history-index {
  color: #b0b5c3;
  font:
    650 10px/1.5 ui-monospace,
    monospace;
}
.history-body {
  min-width: 0;
}
.history-title-row {
  justify-content: space-between;
  align-items: flex-start;
  gap: 15px;
}
.history-title-row > div {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.history-title-row strong {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.type-tag {
  flex: 0 0 auto;
  padding: 4px 6px;
  border-radius: 5px;
  color: #645dd2 !important;
  font-size: 8px !important;
  letter-spacing: 0 !important;
  background: #eeedfc;
}
.history-result {
  flex: 0 0 auto;
  padding: 4px 7px;
  border-radius: 999px;
  color: #7d8496 !important;
  font-size: 9px !important;
  letter-spacing: 0 !important;
  background: #f0f1f5;
}
.history-result.correct {
  color: #2d8064 !important;
  background: #e6f6f0;
}
.history-result.wrong {
  color: #b45d5d !important;
  background: #fbe9e8;
}
.history-result.submitted {
  color: #536bb2 !important;
  background: #e9eefb;
}
.history-answers {
  display: flex;
  flex-wrap: wrap;
  gap: 9px 22px;
  margin-top: 10px;
  color: #8c93a5;
  font-size: 10px;
}
.history-answers b {
  color: #4d5569;
}
.confusion-link {
  margin-top: 9px;
  padding: 0;
  border: 0;
  color: #a4762e;
  font-size: 10px;
  background: transparent;
  cursor: pointer;
}
.confusion-link:disabled {
  opacity: 0.5;
}
.ai-explanation.compact {
  margin-top: 12px;
}
.history-empty {
  padding: 35px 0;
  color: #9ca2b2;
  font-size: 12px;
  text-align: center;
}
.history-toggle {
  display: block;
  margin: 12px auto 0;
  padding: 7px 12px;
  border: 0;
  color: #6762c7;
  font-size: 10px;
  background: transparent;
  cursor: pointer;
}

.question-dock {
  position: fixed;
  z-index: 30;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 13px 20px;
  border-top: 1px solid #e0e3ec;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 -10px 30px rgba(33, 40, 74, 0.055);
  backdrop-filter: blur(16px);
}
.dock-inner {
  width: min(950px, 100%);
  margin: 0 auto;
  gap: 13px;
}
.dock-label {
  flex: 0 0 170px;
  gap: 9px;
}
.dock-label > span {
  display: grid;
  place-items: center;
  width: 31px;
  height: 31px;
  border-radius: 9px;
  color: #fff;
  font-weight: 750;
  background: #665ce1;
}
.dock-label > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.dock-label strong {
  font-size: 11px;
}
.dock-label small {
  color: #969dae;
  font-size: 9px;
}
.dock-inner :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 9px;
  box-shadow: 0 0 0 1px #dfe2eb inset;
}
.dock-inner :deep(.el-button) {
  min-width: 103px;
  min-height: 42px;
  border: 0;
  border-radius: 9px;
  background: #665ce1;
}

@media (max-width: 900px) {
  .join-shell {
    grid-template-columns: 1fr;
    gap: 42px;
    width: min(560px, calc(100% - 40px));
  }
  .join-intro {
    padding-top: 20px;
    text-align: center;
  }
  .join-intro > p {
    margin: 0 auto;
  }
  .join-intro h1 br {
    display: none;
  }
  .classroom-grid {
    grid-template-columns: 1fr;
  }
  .classroom-sidebar {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
  }
  .feedback-card {
    grid-column: 1 / -1;
  }
  .reaction-grid {
    grid-template-columns: repeat(4, minmax(55px, 1fr));
  }
}

@media (max-width: 640px) {
  .join-brand {
    width: calc(100% - 32px);
    padding-top: 20px;
  }
  .join-shell {
    width: calc(100% - 28px);
    min-height: auto;
    padding: 46px 0;
  }
  .join-intro {
    display: none;
  }
  .join-card {
    padding: 25px 20px;
    border-radius: 18px;
  }
  .join-card-head h2 {
    font-size: 21px;
  }

  .classroom-page {
    padding-bottom: 82px;
  }
  .student-header {
    min-height: 60px;
    padding: 0 14px;
  }
  .brand-mark.small {
    width: 31px;
    height: 31px;
    font-size: 15px;
  }
  .header-title strong {
    max-width: 160px;
  }
  .header-title span,
  .student-identity > div,
  .connection-chip {
    display: none;
  }
  .student-avatar {
    width: 32px;
    height: 32px;
  }
  .student-main {
    width: calc(100% - 24px);
    padding: 14px 0 30px;
  }
  .classroom-grid {
    gap: 14px;
  }
  .active-question {
    padding: 21px 17px 0;
  }
  .question-head {
    align-items: flex-start;
  }
  .question-head > div {
    flex-direction: column;
    align-items: flex-start;
    gap: 7px;
  }
  .timer-ring {
    flex-basis: 52px;
    width: 52px;
    height: 52px;
  }
  .timer-ring::before {
    width: 43px;
    height: 43px;
  }
  .timer-ring strong {
    font-size: 16px;
  }
  .question-content {
    margin-top: 16px;
  }
  .question-content h1 {
    font-size: 20px;
  }
  .choice-option {
    min-height: 55px;
    padding: 10px 11px;
  }
  .submit-area {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }
  .submit-button {
    width: 100%;
  }
  .time-progress {
    margin: 0 -17px;
  }

  .result-card {
    padding: 0 17px 22px;
  }
  .result-summary {
    margin: 0 -17px 21px;
    padding: 18px 17px;
  }
  .result-summary h2 {
    font-size: 16px;
  }
  .result-actions {
    align-items: stretch;
    flex-direction: column;
  }
  .result-actions :deep(.el-button) {
    width: 100%;
    margin: 0;
  }
  .answer-tag {
    display: none;
  }

  .waiting-card {
    min-height: 350px;
    padding: 38px 18px;
  }
  .waiting-tips {
    gap: 8px 13px;
  }
  .classroom-sidebar {
    grid-template-columns: 1fr;
  }
  .feedback-card {
    grid-column: auto;
  }
  .history-section {
    margin-top: 14px;
    padding: 19px 15px;
  }
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .history-filters {
    width: 100%;
  }
  .history-filters button {
    flex: 1;
  }
  .history-card {
    grid-template-columns: 27px minmax(0, 1fr);
    gap: 5px;
  }
  .history-title-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  .history-title-row > div {
    width: 100%;
  }
  .history-title-row strong {
    white-space: normal;
  }

  .question-dock {
    padding: 10px 12px;
  }
  .dock-inner {
    gap: 8px;
  }
  .dock-label {
    display: none;
  }
  .dock-inner :deep(.el-button) {
    min-width: 75px;
    padding: 8px 10px;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    scroll-behavior: auto !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
