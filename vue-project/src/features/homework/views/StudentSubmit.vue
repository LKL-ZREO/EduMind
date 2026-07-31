<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import { RouterLink } from 'vue-router'
import request from '@/shared/api/request'
import { getApiErrorData, getApiErrorMessage } from '@/shared/api/errors'
import { sanitizeHtml, sanitizeRenderedMathHtml } from '@/shared/utils/safeHtml'

interface ClassOption {
  id: number
  name: string
}
interface TaskOption {
  id: number
  taskName: string
  description: string
  deadline: string
  allowLate: boolean
  latePenalty: number
}
interface GradingResult {
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  totalScore: number
  finalScore?: number | null
  overallComment?: string
  strengths?: string[]
  weaknesses?: string[]
  suggestions?: string
  errorMessage?: string
}
interface MismatchWarning {
  fileNameValue: string
  selectedValue: string
}
interface SubmitResponseData {
  needBind?: boolean
  studentId?: string
  studentName?: string
  submissionId?: number
  submitCount?: number
  remainingAttempts?: number
  warnings?: {
    classMismatch?: MismatchWarning
    taskMismatch?: MismatchWarning
  }
}
interface SubmitResponse {
  code: number
  message?: string
  data?: SubmitResponseData
}

// ===== 班级 & 作业选择 =====
const classes = ref<ClassOption[]>([])
const tasks = ref<TaskOption[]>([])
const selectedClassId = ref<number | null>(null)
const selectedTaskId = ref<number | null>(null)

// 倒计时
const countdown = ref('')
let countdownTimer: ReturnType<typeof setInterval> | null = null

const selectedTask = computed(() => {
  return tasks.value.find((t) => t.id === selectedTaskId.value) || null
})

// 作业描述 HTML 渲染（含 KaTeX 公式）
const renderedDescription = computed(() => {
  const html = sanitizeHtml(selectedTask.value?.description)
  if (!html) return ''
  // 用临时 DOM 解析 HTML，将 math-inline 的 data-latex 渲染为 KaTeX
  const div = document.createElement('div')
  div.innerHTML = html
  div.querySelectorAll('.math-inline[data-latex]').forEach((el) => {
    const latex = el.getAttribute('data-latex')
    if (latex) {
      try {
        el.innerHTML = katex.renderToString(latex, { throwOnError: false, displayMode: false })
      } catch {
        el.textContent = latex
      }
    }
  })
  return sanitizeRenderedMathHtml(div.innerHTML)
})

// 切换作业时若描述已有 HTML，后续 watch 会处理
watch(renderedDescription, async () => {
  await nextTick()
  // 额外处理：如果 DOM 中已有 .math-inline[data-latex] 但未渲染，兜底渲染
  document.querySelectorAll('.task-desc-body .math-inline[data-latex]').forEach((el) => {
    const latex = el.getAttribute('data-latex')
    if (latex && !el.querySelector('.katex')) {
      try {
        katex.render(latex, el as HTMLElement, { throwOnError: false, displayMode: false })
      } catch {
        /* ignore */
      }
    }
  })
})

// 提交状态（提交后从后端返回获取）
const submitCount = ref(0)
const remainingAttempts = ref(3)
const submittedStudentId = ref('')

// QQ绑定弹窗
const showQqBindDialog = ref(false)
const bindStudentId = ref('')
const bindStudentName = ref('')
const qqNumberInput = ref('')
const bindLoading = ref(false)

// 文件名校验
const fileNameWarnings = ref<string[]>([])
const showConfirmSubmit = ref(false)
const pendingSubmit = ref(false) // confirm=true 重新提交标记

// 文件上传
const file = ref<File | null>(null)
const uploading = ref(false)
const submitting = ref(false) // 提交锁：防止双击重复提交
const uploaded = ref(false)
const error = ref('')
const progress = ref(0)

// 异步批改轮询
const submissionId = ref<number | null>(null)
const gradingStatus = ref('') // PENDING / PROCESSING / COMPLETED / FAILED
const gradingResult = ref<GradingResult | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

const MAX_SIZE_MB = 20
const acceptTypes = '.pdf,.doc,.docx,.txt,.jpg,.jpeg,.png,.zip,.rar'

// ===== 生命周期 =====
onMounted(async () => {
  await loadClasses()
  startCountdown()
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  stopPolling()
})

// ===== 方法 =====
async function loadClasses() {
  try {
    const res = await request.get('/homework/classes')
    if (res.data.code === 200) {
      classes.value = res.data.data
    }
  } catch (e) {
    console.error('加载班级列表失败', e)
  }
}

async function onClassChange() {
  selectedTaskId.value = null
  tasks.value = []
  submitCount.value = 0
  remainingAttempts.value = 3
  if (!selectedClassId.value) return

  try {
    const res = await request.get('/homework/tasks', { params: { classId: selectedClassId.value } })
    if (res.data.code === 200) {
      tasks.value = res.data.data
    }
  } catch (e) {
    console.error('加载作业列表失败', e)
  }
}

async function onTaskChange() {
  fileNameWarnings.value = []
  showConfirmSubmit.value = false
  // 重新倒计时
  startCountdown()
}

function startCountdown() {
  if (countdownTimer) clearInterval(countdownTimer)
  updateCountdown()
  countdownTimer = setInterval(updateCountdown, 1000)
}

function updateCountdown() {
  const task = selectedTask.value
  if (!task?.deadline) {
    countdown.value = ''
    return
  }

  const now = Date.now()
  const deadline = new Date(task.deadline).getTime()
  const diff = deadline - now

  if (diff <= 0) {
    countdown.value = '已截止'
    return
  }

  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  const minutes = Math.floor((diff % 3600000) / 60000)
  const secs = Math.floor((diff % 60000) / 1000)

  if (days > 0) {
    countdown.value = `距离截止还有 ${days} 天 ${hours} 小时 ${minutes} 分`
  } else if (hours > 0) {
    countdown.value = `距离截止还有 ${hours} 小时 ${minutes} 分 ${secs} 秒`
  } else {
    countdown.value = `距离截止还有 ${minutes} 分 ${secs} 秒`
  }
}

// 提交成功后更新状态（从后端返回获取）
function updateSubmitStatusFromResponse(data: {
  submitCount: number
  remainingAttempts: number
  studentId: string
}) {
  submitCount.value = data.submitCount
  remainingAttempts.value = data.remainingAttempts
  submittedStudentId.value = data.studentId
}

// 文件名解析（新格式：学号_姓名_班级_作业名.扩展名）
function parseFileName(
  name: string,
): { studentId: string; studentName: string; className: string; assignmentName: string } | null {
  const match = name.match(/^(.+)_(.+)_(.+)_(.+)\.\w+$/)
  if (!match) return null
  const [, studentId, studentName, className, assignmentName] = match
  if (!studentId || !studentName || !className || !assignmentName) return null
  return {
    studentId: studentId.trim(),
    studentName: studentName.trim(),
    className: className.trim(),
    assignmentName: assignmentName.trim(),
  }
}

function validateFileName(name: string): string[] {
  const warnings: string[] = []
  const parsed = parseFileName(name)
  if (!parsed) {
    warnings.push(
      '文件名格式不正确，请使用「学号_姓名_班级_作业名.扩展名」格式，例如：202103001_张三_计科2101_第三次作业.pdf',
    )
    return warnings
  }

  // 校验班级
  const selectedClass = classes.value.find((c) => c.id === selectedClassId.value)
  if (selectedClass && parsed.className !== selectedClass.name) {
    warnings.push(
      `班级不匹配：文件名写的是「${parsed.className}」，你选的是「${selectedClass.name}」`,
    )
  }

  // 校验作业名
  const task = selectedTask.value
  if (task) {
    const matches =
      parsed.assignmentName.includes(task.taskName) || task.taskName.includes(parsed.assignmentName)
    if (!matches) {
      warnings.push(
        `作业不匹配：文件名写的是「${parsed.assignmentName}」，你选的是「${task.taskName}」`,
      )
    }
  }

  return warnings
}

// ===== 文件上传 =====
const fileSizeOk = computed(() => {
  if (!file.value) return true
  return file.value.size <= MAX_SIZE_MB * 1024 * 1024
})

function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return

  const f = input.files[0]
  if (!f) return
  error.value = ''
  uploaded.value = false
  showConfirmSubmit.value = false
  fileNameWarnings.value = []

  if (f.size > MAX_SIZE_MB * 1024 * 1024) {
    error.value = `文件超过 ${MAX_SIZE_MB}MB 限制，请压缩后重试`
    file.value = null
    return
  }

  file.value = f

  // 文件名校验
  if (selectedTaskId.value) {
    fileNameWarnings.value = validateFileName(f.name)
    if (fileNameWarnings.value.length > 0) {
      showConfirmSubmit.value = true
    }
  }
}

function dropHandler(e: DragEvent) {
  e.preventDefault()
  e.stopPropagation()

  const dt = e.dataTransfer
  if (!dt?.files.length) return

  const f = dt.files[0]
  if (!f) return
  error.value = ''
  uploaded.value = false
  showConfirmSubmit.value = false
  fileNameWarnings.value = []

  if (f.size > MAX_SIZE_MB * 1024 * 1024) {
    error.value = `文件超过 ${MAX_SIZE_MB}MB 限制`
    return
  }

  file.value = f
  if (selectedTaskId.value) {
    fileNameWarnings.value = validateFileName(f.name)
    if (fileNameWarnings.value.length > 0) {
      showConfirmSubmit.value = true
    }
  }
}

function dragOverHandler(e: DragEvent) {
  e.preventDefault()
  e.stopPropagation()
}

function removeFile() {
  file.value = null
  showConfirmSubmit.value = false
  fileNameWarnings.value = []
  pendingSubmit.value = false
  submitting.value = false
}

function handleRecoverableSubmitResult(result?: SubmitResponse): boolean {
  if (result?.code === 428 && result.data?.needBind) {
    bindStudentId.value = result.data?.studentId || ''
    bindStudentName.value = result.data?.studentName || ''
    showQqBindDialog.value = true
    progress.value = 0
    return true
  }

  if (result?.code === 429) {
    error.value = result.message || '请勿重复提交，请稍后再试'
    progress.value = 0
    return true
  }

  if (result?.code === 300) {
    const warns = result.data?.warnings || {}
    fileNameWarnings.value = []
    if (warns.classMismatch) {
      fileNameWarnings.value.push(
        `班级不匹配：文件名写的是「${warns.classMismatch.fileNameValue}」，你选的是「${warns.classMismatch.selectedValue}」`,
      )
    }
    if (warns.taskMismatch) {
      fileNameWarnings.value.push(
        `作业不匹配：文件名写的是「${warns.taskMismatch.fileNameValue}」，你选的是「${warns.taskMismatch.selectedValue}」`,
      )
    }
    showConfirmSubmit.value = true
    pendingSubmit.value = true
    progress.value = 0
    return true
  }

  return false
}

async function submit() {
  if (submitting.value) return // 提交锁：防双击

  if (!file.value) {
    error.value = '请先选择文件'
    return
  }

  if (!selectedTaskId.value) {
    error.value = '请选择作业'
    return
  }

  if (!fileSizeOk.value) {
    error.value = `文件超过 ${MAX_SIZE_MB}MB 限制`
    return
  }

  if (showConfirmSubmit.value && !pendingSubmit.value) {
    // 先显示警告，让用户点击"确认提交"按钮
    return
  }

  submitting.value = true
  uploading.value = true
  error.value = ''
  progress.value = 0

  const formData = new FormData()
  formData.append('file', file.value)
  formData.append('expectedClassId', String(selectedClassId.value))
  formData.append('expectedTaskId', String(selectedTaskId.value))
  if (pendingSubmit.value || fileNameWarnings.value.length === 0) {
    formData.append('confirm', 'true')
  }

  const progTimer = setInterval(() => {
    progress.value = Math.min(progress.value + Math.random() * 20, 90)
  }, 300)

  try {
    const res = await request.post('/homework/submit', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })

    const result = res.data
    if (handleRecoverableSubmitResult(result)) return

    progress.value = 100
    uploaded.value = true

    // 保存 submissionId，启动轮询
    if (result.data?.submissionId) {
      submissionId.value = result.data.submissionId
      gradingStatus.value = 'PENDING'
      gradingResult.value = null
      startPolling()
    }

    // 从后端返回获取提交次数和剩余次数
    if (
      result.data?.submitCount !== undefined &&
      result.data.remainingAttempts !== undefined &&
      result.data.studentId
    ) {
      updateSubmitStatusFromResponse(result.data)
    }

    file.value = null
    showConfirmSubmit.value = false
    fileNameWarnings.value = []
    pendingSubmit.value = false
  } catch (err: unknown) {
    if (handleRecoverableSubmitResult(getApiErrorData<SubmitResponse>(err))) return
    error.value = getApiErrorMessage(err, '上传失败，请检查网络后重试')
    progress.value = 0
  } finally {
    clearInterval(progTimer)
    uploading.value = false
    submitting.value = false
  }
}

function confirmSubmitAnyway() {
  if (submitting.value) return
  pendingSubmit.value = true
  submit()
}

// ===== QQ绑定 =====
async function bindQq() {
  if (!qqNumberInput.value || !qqNumberInput.value.match(/^\d{5,11}$/)) {
    alert('请输入正确的QQ号（5-11位数字）')
    return
  }

  bindLoading.value = true
  try {
    const res = await request.post('/homework/bind-qq', {
      studentId: bindStudentId.value,
      studentName: bindStudentName.value,
      qqNumber: qqNumberInput.value,
    })
    if (res.data.code === 200) {
      showQqBindDialog.value = false
      qqNumberInput.value = ''
      // 自动重新提交
      submit()
    } else {
      alert(res.data.message || '绑定失败')
    }
  } catch {
    alert('绑定失败，请检查网络')
  } finally {
    bindLoading.value = false
  }
}

function cancelBind() {
  showQqBindDialog.value = false
  qqNumberInput.value = ''
}

// ===== 异步批改轮询 =====
function startPolling() {
  stopPolling()
  pollResult()
  pollTimer = setInterval(pollResult, 2000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function pollResult() {
  if (!submissionId.value) return

  try {
    const res = await request.get(`/homework/result/${submissionId.value}`)
    const data = res.data

    if (data.code === 200 && data.data) {
      const s = data.data as GradingResult
      gradingStatus.value = s.status

      if (s.status === 'COMPLETED') {
        stopPolling()
        gradingResult.value = s
      } else if (s.status === 'FAILED') {
        stopPolling()
        gradingResult.value = s
      }
    }
  } catch (e) {
    console.error('轮询批改结果失败', e)
  }
}

const gradingStatusText: Record<string, string> = {
  PENDING: '排队中...',
  PROCESSING: '正在批改中...',
  COMPLETED: '批改完成',
  FAILED: '批改失败',
}

function scoreColor(score: number): string {
  if (score >= 80) return 'score-high'
  if (score >= 60) return 'score-mid'
  return 'score-low'
}

function joinArray(arr: string[]): string {
  return arr?.join('、') || ''
}
</script>

<template>
  <div class="home-submit">
    <div class="top-bar">
      <router-link to="/live/join" class="join-entry">加入课堂</router-link>
      <router-link to="/login" class="teacher-entry">教师入口</router-link>
    </div>

    <div class="hero">
      <div class="hero-mark">Submit Desk</div>
      <h1>学生提交台</h1>
      <p class="subtitle">选择班级和作业，上传文件即可完成提交。无需注册，不打扰流程。</p>
    </div>

    <!-- 选择区域 -->
    <div class="selector-section">
      <div class="selector-row">
        <div class="selector-group">
          <label>班级</label>
          <select v-model="selectedClassId" @change="onClassChange" class="sel-input">
            <option :value="null">请选择班级</option>
            <option v-for="cls in classes" :key="cls.id" :value="cls.id">{{ cls.name }}</option>
          </select>
        </div>
        <div class="selector-group">
          <label>作业</label>
          <select v-model="selectedTaskId" @change="onTaskChange" class="sel-input">
            <option :value="null">请选择作业</option>
            <option v-for="t in tasks" :key="t.id" :value="t.id">{{ t.taskName }}</option>
          </select>
        </div>
      </div>

      <!-- 作业描述 -->
      <div v-if="selectedTask?.description" class="task-desc-card">
        <div class="task-desc-header">作业要求</div>
        <div class="task-desc-body" v-html="renderedDescription"></div>
      </div>

      <!-- 倒计时 -->
      <div
        v-if="countdown"
        class="countdown-bar"
        :class="{ expired: countdown.includes('已截止') }"
      >
        {{ countdown }}
      </div>
    </div>

    <!-- 上传区域 -->
    <div
      class="upload-zone"
      :class="{ 'has-file': !!file, uploading, error: !!error }"
      @drop="dropHandler"
      @dragover="dragOverHandler"
    >
      <!-- 未选文件 -->
      <div v-if="!file && !uploading" class="drop-hint">
        <div class="upload-icon">Upload</div>
        <p>将作业文件拖到这里</p>
        <p class="or">或</p>
        <label class="file-btn">
          选择文件
          <input type="file" :accept="acceptTypes" hidden @change="onFileSelected" />
        </label>
        <p class="tip">支持 PDF、Word、TXT、图片、压缩包，最大 {{ MAX_SIZE_MB }}MB</p>
      </div>

      <!-- 已选文件 + 警告 -->
      <div v-else-if="file && !uploading" class="file-preview-area">
        <div class="file-preview">
          <div class="file-icon">
            {{ file.name.match(/\.(\w+)$/)?.[1]?.toUpperCase() || 'FILE' }}
          </div>
          <div class="file-info">
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size">{{ (file.size / 1024 / 1024).toFixed(2) }} MB</span>
          </div>
          <div class="file-actions">
            <button class="btn-remove" @click="removeFile">移除</button>
            <button
              class="btn-submit"
              @click="submit"
              :disabled="submitting || uploading || remainingAttempts <= 0"
            >
              {{ submitting ? '提交中...' : remainingAttempts <= 0 ? '已达上限' : '提交作业' }}
            </button>
          </div>
        </div>

        <!-- 文件名警告 -->
        <div v-if="showConfirmSubmit && fileNameWarnings.length > 0" class="warning-box">
          <div class="warning-title">文件名与选择不匹配</div>
          <div v-for="(w, i) in fileNameWarnings" :key="i" class="warning-item">{{ w }}</div>
          <div class="warning-actions">
            <button class="btn-remove" @click="removeFile">重新选择文件</button>
            <button class="btn-submit btn-warning" @click="confirmSubmitAnyway">确认提交</button>
          </div>
        </div>
      </div>

      <!-- 上传中 -->
      <div v-if="uploading" class="uploading-status">
        <div class="spinner"></div>
        <p>正在上传...</p>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progress + '%' }"></div>
        </div>
        <span class="progress-text">{{ Math.round(progress) }}%</span>
      </div>
    </div>

    <!-- 批改进度 & 结果 -->
    <div v-if="uploaded && submissionId" class="grading-section">
      <div class="grading-card">
        <!-- 进度状态 -->
        <div
          v-if="gradingStatus !== 'COMPLETED' && gradingStatus !== 'FAILED'"
          class="grading-progress"
        >
          <div class="grading-spinner"></div>
          <div class="grading-status">{{ gradingStatusText[gradingStatus] || '排队中...' }}</div>
          <div class="grading-sub">submissionId: {{ submissionId }} | 每 2 秒自动刷新</div>
        </div>

        <!-- 批改完成 -->
        <div v-else-if="gradingStatus === 'COMPLETED' && gradingResult" class="grading-done">
          <div class="score-circle" :class="scoreColor(gradingResult.totalScore)">
            <span class="score-num">{{ gradingResult.totalScore }}</span>
            <span class="score-label">分</span>
          </div>
          <div class="result-title">{{ gradingStatusText['COMPLETED'] }}</div>

          <div class="result-details" v-if="gradingResult">
            <div class="detail-row" v-if="gradingResult.overallComment">
              <span class="detail-label">综合评语</span>
              <span class="detail-value">{{ gradingResult.overallComment }}</span>
            </div>
            <div class="detail-row" v-if="gradingResult.strengths?.length">
              <span class="detail-label">优点</span>
              <span class="detail-value">{{ joinArray(gradingResult.strengths) }}</span>
            </div>
            <div class="detail-row" v-if="gradingResult.weaknesses?.length">
              <span class="detail-label">不足</span>
              <span class="detail-value">{{ joinArray(gradingResult.weaknesses) }}</span>
            </div>
            <div class="detail-row" v-if="gradingResult.suggestions">
              <span class="detail-label">建议</span>
              <span class="detail-value">{{ gradingResult.suggestions }}</span>
            </div>
            <div
              class="detail-row"
              v-if="
                gradingResult.finalScore != null &&
                gradingResult.finalScore !== gradingResult.totalScore
              "
            >
              <span class="detail-label">最终分数（逾期扣分后）</span>
              <span class="detail-value final-score">{{ gradingResult.finalScore }} 分</span>
            </div>
          </div>
        </div>

        <!-- 批改失败 -->
        <div v-else-if="gradingStatus === 'FAILED'" class="grading-failed">
          <div class="failed-icon">Failed</div>
          <div class="result-title">{{ gradingStatusText['FAILED'] }}</div>
          <div class="error-message" v-if="gradingResult?.errorMessage">
            {{ gradingResult.errorMessage }}
          </div>
        </div>
      </div>
    </div>

    <!-- 旧成功提示（保留兼容） -->
    <div v-if="uploaded" class="success-banner">
      <div class="success-title">作业提交成功</div>
      <div class="success-detail">
        <div>学号：{{ submittedStudentId }}</div>
        <div>
          本次为第 <b>{{ submitCount }}</b> 次提交
        </div>
        <div>
          剩余提交次数：<b>{{ remainingAttempts }}</b> 次
        </div>
      </div>
    </div>

    <!-- 错误提示 -->
    <div v-if="error && !uploading" class="error-banner">
      {{ error }}
    </div>

    <!-- 文件名规范说明 -->
    <div class="usage">
      <h3>文件名命名规范</h3>
      <p>请按以下格式命名文件，用于识别身份和统计提交次数：</p>
      <code>学号_姓名_班级_作业名称.pdf</code>
      <p class="example">例如：<strong>202103001_张三_计科2101_第三次作业.pdf</strong></p>
    </div>

    <!-- QQ绑定弹窗 -->
    <div v-if="showQqBindDialog" class="dialog-overlay" @click="cancelBind">
      <div class="dialog-content" @click.stop>
        <h3>首次提交，请绑定 QQ 号</h3>
        <p class="dialog-desc">绑定后，当作业成绩不理想时，我们会通过QQ私聊提醒你。</p>
        <div class="form-group">
          <label>学号</label>
          <input type="text" :value="bindStudentId" disabled class="input-disabled" />
        </div>
        <div class="form-group">
          <label>姓名</label>
          <input type="text" :value="bindStudentName" disabled class="input-disabled" />
        </div>
        <div class="form-group">
          <label>QQ号</label>
          <input
            type="text"
            v-model="qqNumberInput"
            placeholder="请输入你的QQ号"
            maxlength="11"
            @keyup.enter="bindQq"
          />
        </div>
        <div class="dialog-actions">
          <button class="btn-cancel" @click="cancelBind">取消</button>
          <button class="btn-confirm" @click="bindQq" :disabled="bindLoading">
            {{ bindLoading ? '绑定中...' : '绑定并提交' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped src="../styles/StudentSubmit.css"></style>
