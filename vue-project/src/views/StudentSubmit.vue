<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import katex from 'katex'
import 'katex/dist/katex.min.css'
import { RouterLink } from 'vue-router'
import request from '@/api/request'

// ===== 班级 & 作业选择 =====
const classes = ref<Array<{id: number, name: string}>>([])
const tasks = ref<Array<{id: number, taskName: string, description: string, deadline: string, allowLate: boolean, latePenalty: number}>>([])
const selectedClassId = ref<number | null>(null)
const selectedTaskId = ref<number | null>(null)

// 倒计时
const countdown = ref('')
let countdownTimer: ReturnType<typeof setInterval> | null = null

const selectedTask = computed(() => {
  return tasks.value.find(t => t.id === selectedTaskId.value) || null
})

// 作业描述 HTML 渲染（含 KaTeX 公式）
const renderedDescription = computed(() => {
  const html = selectedTask.value?.description || ''
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
  return div.innerHTML
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
      } catch { /* ignore */ }
    }
  })
})

// 提交状态（提交后从后端返回获取）
const submitCount = ref(0)
const remainingAttempts = ref(3)
const maxAttempts = 3
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
const pendingSubmit = ref(false)  // confirm=true 重新提交标记

// 文件上传
const file = ref<File | null>(null)
const uploading = ref(false)
const uploaded = ref(false)
const error = ref('')
const progress = ref(0)

// 异步批改轮询
const submissionId = ref<number | null>(null)
const gradingStatus = ref('')  // PENDING / PROCESSING / COMPLETED / FAILED
const gradingResult = ref<any>(null)
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
    countdown.value = '⏰ 已截止'
    return
  }

  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  const minutes = Math.floor((diff % 3600000) / 60000)
  const secs = Math.floor((diff % 60000) / 1000)

  if (days > 0) {
    countdown.value = `⏰ 距离截止还有 ${days} 天 ${hours} 小时 ${minutes} 分`
  } else if (hours > 0) {
    countdown.value = `⏰ 距离截止还有 ${hours} 小时 ${minutes} 分 ${secs} 秒`
  } else {
    countdown.value = `⏰ 距离截止还有 ${minutes} 分 ${secs} 秒`
  }
}

// 提交成功后更新状态（从后端返回获取）
function updateSubmitStatusFromResponse(data: { submitCount: number, remainingAttempts: number, studentId: string }) {
  submitCount.value = data.submitCount
  remainingAttempts.value = data.remainingAttempts
  submittedStudentId.value = data.studentId
}

// 文件名解析（新格式：学号_姓名_班级_作业名.扩展名）
function parseFileName(name: string): { studentId: string; studentName: string; className: string; assignmentName: string } | null {
  const match = name.match(/^(.+)_(.+)_(.+)_(.+)\.\w+$/)
  if (!match) return null
  return {
    studentId: match[1].trim(),
    studentName: match[2].trim(),
    className: match[3].trim(),
    assignmentName: match[4].trim()
  }
}

function validateFileName(name: string): string[] {
  const warnings: string[] = []
  const parsed = parseFileName(name)
  if (!parsed) {
    warnings.push('文件名格式不正确，请使用「学号_姓名_班级_作业名.扩展名」格式，例如：202103001_张三_计科2101_第三次作业.pdf')
    return warnings
  }

  // 校验班级
  const selectedClass = classes.value.find(c => c.id === selectedClassId.value)
  if (selectedClass && parsed.className !== selectedClass.name) {
    warnings.push(`班级不匹配：文件名写的是「${parsed.className}」，你选的是「${selectedClass.name}」`)
  }

  // 校验作业名
  const task = selectedTask.value
  if (task) {
    const matches = parsed.assignmentName.includes(task.taskName) || task.taskName.includes(parsed.assignmentName)
    if (!matches) {
      warnings.push(`作业不匹配：文件名写的是「${parsed.assignmentName}」，你选的是「${task.taskName}」`)
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
}

async function submit() {
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

    clearInterval(progTimer)

    const result = res.data

    // 需要绑定QQ号
    if (result.code === 401 && result.data?.needBind) {
      bindStudentId.value = result.data?.studentId || ''
      bindStudentName.value = result.data?.studentName || ''
      showQqBindDialog.value = true
      uploading.value = false
      progress.value = 0
      return
    }

    if (result.code === 300) {
      // 警告，显示差异信息
      const warns = result.data?.warnings || {}
      fileNameWarnings.value = []
      if (warns.classMismatch) {
        fileNameWarnings.value.push(`班级不匹配：文件名写的是「${warns.classMismatch.fileNameValue}」，你选的是「${warns.classMismatch.selectedValue}」`)
      }
      if (warns.taskMismatch) {
        fileNameWarnings.value.push(`作业不匹配：文件名写的是「${warns.taskMismatch.fileNameValue}」，你选的是「${warns.taskMismatch.selectedValue}」`)
      }
      showConfirmSubmit.value = true
      pendingSubmit.value = true
      uploading.value = false
      progress.value = 0
      return
    }

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
    if (result.data) {
      updateSubmitStatusFromResponse(result.data)
    }

    file.value = null
    showConfirmSubmit.value = false
    fileNameWarnings.value = []
    pendingSubmit.value = false
  } catch (err) {
    error.value = err instanceof Error ? err.message : '上传失败，请检查网络后重试'
    progress.value = 0
  } finally {
    uploading.value = false
  }
}

function confirmSubmitAnyway() {
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
  } catch (e) {
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
      const s = data.data
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
  PENDING: '📋 排队中...',
  PROCESSING: '🤖 正在批改中...',
  COMPLETED: '✅ 批改完成',
  FAILED: '❌ 批改失败',
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
      <router-link to="/login" class="teacher-entry">👨‍🏫 教师登录</router-link>
    </div>

    <div class="hero">
      <div class="hero-icon">📚</div>
      <h1>作业提交系统</h1>
      <p class="subtitle">选择班级和作业后上传文件，无需注册登录</p>
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
        <div class="task-desc-header">📋 作业要求</div>
        <div class="task-desc-body" v-html="renderedDescription"></div>
      </div>

      <!-- 倒计时 -->
      <div v-if="countdown" class="countdown-bar" :class="{ expired: countdown.includes('已截止') }">
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
        <div class="upload-icon">📤</div>
        <p>将作业文件拖到此处</p>
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
          <div class="file-icon">{{ file.name.match(/\.(\w+)$/)?.[1]?.toUpperCase() || '📄' }}</div>
          <div class="file-info">
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size">{{ (file.size / 1024 / 1024).toFixed(2) }} MB</span>
          </div>
          <div class="file-actions">
            <button class="btn-remove" @click="removeFile">移除</button>
            <button class="btn-submit" @click="submit" :disabled="uploading || remainingAttempts <= 0">
              {{ remainingAttempts <= 0 ? '已达上限' : '提交作业' }}
            </button>
          </div>
        </div>

        <!-- 文件名警告 -->
        <div v-if="showConfirmSubmit && fileNameWarnings.length > 0" class="warning-box">
          <div class="warning-title">⚠️ 文件名与选择不匹配</div>
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
        <div v-if="gradingStatus !== 'COMPLETED' && gradingStatus !== 'FAILED'" class="grading-progress">
          <div class="grading-spinner"></div>
          <div class="grading-status">{{ gradingStatusText[gradingStatus] || '📋 排队中...' }}</div>
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
              <span class="detail-label">📝 综合评语</span>
              <span class="detail-value">{{ gradingResult.overallComment }}</span>
            </div>
            <div class="detail-row" v-if="gradingResult.strengths?.length">
              <span class="detail-label">👍 优点</span>
              <span class="detail-value">{{ joinArray(gradingResult.strengths) }}</span>
            </div>
            <div class="detail-row" v-if="gradingResult.weaknesses?.length">
              <span class="detail-label">👎 不足</span>
              <span class="detail-value">{{ joinArray(gradingResult.weaknesses) }}</span>
            </div>
            <div class="detail-row" v-if="gradingResult.suggestions">
              <span class="detail-label">💡 建议</span>
              <span class="detail-value">{{ gradingResult.suggestions }}</span>
            </div>
            <div class="detail-row" v-if="gradingResult.finalScore != null && gradingResult.finalScore !== gradingResult.totalScore">
              <span class="detail-label">⚠️ 最终分数（逾期扣分后）</span>
              <span class="detail-value final-score">{{ gradingResult.finalScore }} 分</span>
            </div>
          </div>
        </div>

        <!-- 批改失败 -->
        <div v-else-if="gradingStatus === 'FAILED'" class="grading-failed">
          <div class="failed-icon">❌</div>
          <div class="result-title">{{ gradingStatusText['FAILED'] }}</div>
          <div class="error-message" v-if="gradingResult?.errorMessage">{{ gradingResult.errorMessage }}</div>
        </div>
      </div>
    </div>

    <!-- 旧成功提示（保留兼容） -->
    <div v-if="uploaded" class="success-banner">
      <div class="success-title">✅ 作业提交成功！</div>
      <div class="success-detail">
        <div>学号：{{ submittedStudentId }}</div>
        <div>本次为第 <b>{{ submitCount }}</b> 次提交</div>
        <div>剩余提交次数：<b>{{ remainingAttempts }}</b> 次</div>
      </div>
    </div>

    <!-- 错误提示 -->
    <div v-if="error && !uploading" class="error-banner">
      ❌ {{ error }}
    </div>

    <!-- 文件名规范说明 -->
    <div class="usage">
      <h3>📋 文件名命名规范</h3>
      <p>请按以下格式命名文件，用于识别身份和统计提交次数：</p>
      <code>学号_姓名_班级_作业名称.pdf</code>
      <p class="example">例如：<strong>202103001_张三_计科2101_第三次作业.pdf</strong></p>
    </div>

    <!-- QQ绑定弹窗 -->
    <div v-if="showQqBindDialog" class="dialog-overlay" @click="cancelBind">
      <div class="dialog-content" @click.stop>
        <h3>🔔 首次提交，请绑定QQ号</h3>
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

<style scoped>
.home-submit {
  max-width: 600px;
  margin: 0 auto;
  padding: 2rem 1rem;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
  color: #1a202c;
}

.top-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 1rem;
}

.teacher-entry {
  padding: 0.4rem 1rem;
  border: 1px solid #667eea;
  border-radius: 8px;
  color: #667eea;
  font-size: 0.85rem;
  text-decoration: none;
  transition: all 0.2s;
}

.teacher-entry:hover {
  background: #667eea;
  color: white;
}

.hero {
  text-align: center;
  margin-bottom: 1.5rem;
}

.hero-icon {
  font-size: 3rem;
  margin-bottom: 0.5rem;
}

.hero h1 {
  margin: 0 0 0.3rem;
  font-size: 1.8rem;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  color: #718096;
  font-size: 0.95rem;
}

/* 选择区 */
.selector-section {
  background: white;
  border-radius: 12px;
  padding: 1.25rem;
  margin-bottom: 1rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

.selector-row {
  display: flex;
  gap: 12px;
}

.selector-group {
  flex: 1;
}

.selector-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 0.85rem;
  color: #718096;
  font-weight: 500;
}

.sel-input {
  width: 100%;
  padding: 0.55rem 0.75rem;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 0.9rem;
  color: #2d3748;
  background: #f7fafc;
  outline: none;
  transition: border-color 0.2s;
}

.sel-input:focus {
  border-color: #667eea;
}

.countdown-bar {
  margin-top: 10px;
  padding: 8px 12px;
  background: #f0f4ff;
  border: 1px solid #c3d9ff;
  border-radius: 8px;
  font-size: 0.9rem;
  color: #2b6cb0;
}

.countdown-bar.expired {
  background: #fff5f5;
  border-color: #feb2b2;
  color: #c53030;
}

/* 作业描述卡片 */
.task-desc-card {
  margin-top: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-left: 4px solid #667eea;
  border-radius: 8px;
  overflow: hidden;
}

.task-desc-header {
  padding: 8px 14px;
  background: #f7fafc;
  font-size: 0.85rem;
  font-weight: 600;
  color: #4a5568;
  border-bottom: 1px solid #e2e8f0;
}

.task-desc-body {
  padding: 12px 14px;
  font-size: 0.9rem;
  color: #2d3748;
  line-height: 1.7;
  line-height: 1.8;
}

/* KaTeX 公式在描述中的样式 */
.task-desc-body .math-inline {
  display: inline;
}

.task-desc-body .katex {
  font-size: 1.05em;
}

.attempts-bar {
  margin-top: 8px;
  font-size: 0.85rem;
  color: #4a5568;
}

.attempts-bar b {
  color: #667eea;
}

.attempts-exhausted {
  color: #e53e3e;
}

.attempts-remaining {
  color: #52c41a;
}

/* 上传区域 */
.upload-zone {
  background: white;
  border: 2px dashed #cbd5e0;
  border-radius: 16px;
  padding: 2rem;
  text-align: center;
  transition: all 0.2s;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-zone:hover {
  border-color: #667eea;
  background: #f7fafc;
}

.upload-zone.has-file {
  border-style: solid;
  border-color: #667eea;
  background: #f0f4ff;
}

.upload-zone.error {
  border-color: #fc8181;
  background: #fff5f5;
}

.upload-zone.uploading {
  border-color: #68d391;
  background: #f0fff4;
}

.drop-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.upload-icon {
  font-size: 3rem;
}

.drop-hint p {
  margin: 0;
  color: #4a5568;
  font-size: 1rem;
}

.drop-hint .or {
  color: #a0aec0;
  font-size: 0.85rem;
}

.file-btn {
  display: inline-block;
  padding: 0.6rem 1.5rem;
  background: #667eea;
  color: white;
  border-radius: 8px;
  font-size: 0.95rem;
  cursor: pointer;
  transition: background 0.2s;
}

.file-btn:hover {
  background: #5a6fd6;
}

.tip {
  font-size: 0.8rem !important;
  color: #a0aec0 !important;
}

/* 文件预览 + 警告 */
.file-preview-area {
  width: 100%;
}

.file-preview {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 1rem;
}

.file-icon {
  width: 48px;
  height: 48px;
  background: #667eea;
  color: white;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.75rem;
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  text-align: left;
  min-width: 0;
}

.file-name {
  font-weight: 600;
  font-size: 0.95rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  font-size: 0.8rem;
  color: #718096;
}

.file-actions {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

.btn-remove {
  padding: 0.4rem 0.8rem;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  color: #4a5568;
  font-size: 0.85rem;
  transition: all 0.2s;
}

.btn-remove:hover {
  background: #fff5f5;
  border-color: #fc8181;
  color: #e53e3e;
}

.btn-submit {
  padding: 0.4rem 1rem;
  background: #667eea;
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-submit:hover {
  background: #5a6fd6;
}

.btn-submit:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.btn-warning {
  background: #faad14;
}

.btn-warning:hover {
  background: #d48806;
}

/* 警告框 */
.warning-box {
  margin-top: 12px;
  padding: 12px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 8px;
  text-align: left;
}

.warning-title {
  font-weight: 600;
  color: #d46b08;
  margin-bottom: 6px;
  font-size: 0.9rem;
}

.warning-item {
  color: #ad6800;
  font-size: 0.85rem;
  margin-bottom: 4px;
  padding-left: 12px;
  position: relative;
}

.warning-item::before {
  content: '•';
  position: absolute;
  left: 0;
}

.warning-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  justify-content: flex-end;
}

/* 上传中 */
.uploading-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.8rem;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #e2e8f0;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.uploading-status p {
  margin: 0;
  color: #4a5568;
}

.progress-bar {
  width: 100%;
  max-width: 300px;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea, #764ba2);
  border-radius: 4px;
  transition: width 0.3s;
}

.progress-text {
  font-size: 0.85rem;
  color: #718096;
}

/* 成功/错误 */
.success-banner {
  margin-top: 1rem;
  padding: 1rem;
  background: #f0fff4;
  border: 1px solid #9ae6b4;
  border-radius: 8px;
  color: #276749;
}

.success-title {
  font-size: 1rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.success-detail {
  font-size: 0.9rem;
  line-height: 1.6;
}

.success-detail b {
  color: #276749;
}

.error-banner {
  margin-top: 1rem;
  padding: 0.75rem 1rem;
  background: #fff5f5;
  border: 1px solid #feb2b2;
  border-radius: 8px;
  color: #c53030;
  font-size: 0.95rem;
}

/* 说明 */
.usage {
  margin-top: 2rem;
  padding: 1.25rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
}

.usage h3 {
  margin: 0 0 0.5rem;
  font-size: 1rem;
}

.usage p {
  margin: 0 0 0.5rem;
  color: #4a5568;
  font-size: 0.9rem;
}

.usage code {
  display: block;
  padding: 0.5rem 0.75rem;
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-family: 'Consolas', monospace;
  font-size: 0.9rem;
  color: #2d3748;
}

.usage .example {
  margin: 0.5rem 0 0;
  font-size: 0.85rem;
  color: #718096;
}

/* QQ绑定弹窗 */
.dialog-overlay {
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

.dialog-content {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  width: 90%;
  max-width: 400px;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.2);
}

.dialog-content h3 {
  margin: 0 0 0.5rem;
  font-size: 1.1rem;
}

.dialog-desc {
  color: #718096;
  font-size: 0.85rem;
  margin-bottom: 1rem;
}

.dialog-content .form-group {
  margin-bottom: 1rem;
}

.dialog-content label {
  display: block;
  margin-bottom: 0.4rem;
  font-size: 0.85rem;
  color: #4a5568;
  font-weight: 500;
}

.dialog-content input {
  width: 100%;
  padding: 0.6rem 0.8rem;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 0.95rem;
  box-sizing: border-box;
}

.dialog-content input:focus {
  outline: none;
  border-color: #667eea;
}

.input-disabled {
  background: #f7fafc;
  color: #a0aec0;
}

.dialog-actions {
  display: flex;
  gap: 0.75rem;
  margin-top: 1.25rem;
}

.btn-cancel {
  flex: 1;
  padding: 0.6rem;
  background: transparent;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  color: #4a5568;
  font-size: 0.9rem;
  transition: all 0.2s;
}

.btn-cancel:hover {
  background: #f7fafc;
}

.btn-confirm {
  flex: 1;
  padding: 0.6rem;
  background: #667eea;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  color: white;
  font-size: 0.9rem;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-confirm:hover {
  background: #5a6fd6;
}

.btn-confirm:disabled {
  background: #ccc;
  cursor: not-allowed;
}

/* ===== 批改进度 & 结果 ===== */
.grading-section {
  margin-top: 1rem;
}

.grading-card {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  text-align: center;
}

.grading-progress {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.grading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #e2e8f0;
  border-top-color: #667eea;
  border-radius: 50%;
  animation: grading-spin 0.8s linear infinite;
}

@keyframes grading-spin {
  to { transform: rotate(360deg); }
}

.grading-status {
  font-size: 1.1rem;
  font-weight: 600;
  color: #2d3748;
}

.grading-sub {
  font-size: 0.8rem;
  color: #a0aec0;
}

/* 批改完成 */
.grading-done {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}

.score-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 700;
}

.score-circle.score-high {
  background: linear-gradient(135deg, #48bb78, #38a169);
}

.score-circle.score-mid {
  background: linear-gradient(135deg, #ed8936, #dd6b20);
}

.score-circle.score-low {
  background: linear-gradient(135deg, #fc8181, #e53e3e);
}

.score-num {
  font-size: 1.8rem;
  line-height: 1;
}

.score-label {
  font-size: 0.75rem;
}

.result-title {
  font-size: 1rem;
  font-weight: 600;
  color: #2d3748;
}

.result-details {
  width: 100%;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-top: 0.25rem;
}

.detail-row {
  padding: 0.5rem 0.75rem;
  background: #f7fafc;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.detail-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: #4a5568;
}

.detail-value {
  font-size: 0.85rem;
  color: #718096;
  line-height: 1.5;
}

.final-score {
  color: #e53e3e;
  font-weight: 600;
}

/* 批改失败 */
.grading-failed {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.failed-icon {
  font-size: 2.5rem;
}

.error-message {
  font-size: 0.85rem;
  color: #e53e3e;
  padding: 0.5rem 0.75rem;
  background: #fff5f5;
  border-radius: 8px;
  border: 1px solid #feb2b2;
  max-width: 100%;
  word-break: break-word;
}
</style>
