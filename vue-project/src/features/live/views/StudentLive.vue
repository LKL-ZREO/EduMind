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
import { useLiveSessionStore } from '@/features/live/store'
import {
  previewSession,
  unbindStudentDevice,
  type InteractionHistoryItem,
  type LiveSessionInfo,
} from '@/features/live/api/live'
import request from '@/shared/api/request'
import { getApiErrorMessage } from '@/shared/api/errors'
import type { ApiResponse } from '@/shared/api/types'
import { renderTextWithBreaks } from '@/shared/utils/safeHtml'

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

<style scoped src="../styles/StudentLive.css"></style>
