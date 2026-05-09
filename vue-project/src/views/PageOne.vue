<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'

// ===== 班级 & 作业选择 =====
const classes = ref<Array<{id: number, name: string}>>([])
const tasks = ref<Array<{id: number, taskName: string, deadline: string, allowLate: boolean, latePenalty: number}>>([])
const selectedClassId = ref<number | null>(null)
const selectedTaskId = ref<number | null>(null)

// 倒计时
const countdown = ref('')
let countdownTimer: ReturnType<typeof setInterval> | null = null

const selectedTask = computed(() => {
  return tasks.value.find(t => t.id === selectedTaskId.value) || null
})

// 提交状态
const submitCount = ref(0)
const remainingAttempts = ref(3)
const maxAttempts = 3

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

const MAX_SIZE_MB = 20
const acceptTypes = '.pdf,.doc,.docx,.txt,.jpg,.jpeg,.png,.zip,.rar'

const apiBaseUrl = import.meta.env.VITE_API_URL || ''

// ===== 生命周期 =====
onMounted(async () => {
  await loadClasses()
  startCountdown()
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

// ===== 方法 =====
async function loadClasses() {
  try {
    const res = await fetch(`${apiBaseUrl}/api/homework/classes`)
    const data = await res.json()
    if (data.code === 200) {
      classes.value = data.data
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
    const res = await fetch(`${apiBaseUrl}/api/homework/tasks?classId=${selectedClassId.value}`)
    const data = await res.json()
    if (data.code === 200) {
      tasks.value = data.data
    }
  } catch (e) {
    console.error('加载作业列表失败', e)
  }
}

async function onTaskChange() {
  submitCount.value = 0
  remainingAttempts.value = 3
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

async function checkSubmitStatus() {
  if (!selectedTaskId.value) return
  // 从文件名获取学生姓名
  if (!file.value) return
  const studentName = parseFileName(file.value.name)?.studentName
  if (!studentName) return

  try {
    const res = await fetch(`${apiBaseUrl}/api/homework/submit-status?studentName=${encodeURIComponent(studentName)}&taskId=${selectedTaskId.value}`)
    const data = await res.json()
    if (data.code === 200) {
      submitCount.value = data.data.submitCount
      remainingAttempts.value = data.data.remainingAttempts
    }
  } catch (e) {
    console.error('检查提交状态失败', e)
  }
}

// 文件名解析
function parseFileName(name: string): { studentName: string; className: string; assignmentName: string } | null {
  const match = name.match(/^(.+)_(.+)_(.+)\.\w+$/)
  if (!match) return null
  return {
    studentName: match[1].trim(),
    className: match[2].trim(),
    assignmentName: match[3].trim()
  }
}

function validateFileName(name: string): string[] {
  const warnings: string[] = []
  const parsed = parseFileName(name)
  if (!parsed) {
    warnings.push('文件名格式不正确，请使用「姓名_班级_作业名.扩展名」格式')
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

  checkSubmitStatus()
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

  checkSubmitStatus()
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

  if (remainingAttempts.value <= 0) {
    error.value = `提交次数已达上限（${maxAttempts}次），无法继续提交`
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
    const res = await fetch(`${apiBaseUrl}/api/homework/submit`, {
      method: 'POST',
      body: formData,
    })

    clearInterval(progTimer)

    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || data.error || '上传失败，请稍后重试')
    }

    const result = await res.json()

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
    file.value = null
    showConfirmSubmit.value = false
    fileNameWarnings.value = []
    pendingSubmit.value = false
    submitCount.value++
    remainingAttempts.value = Math.max(0, remainingAttempts.value - 1)
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

      <!-- 倒计时 -->
      <div v-if="countdown" class="countdown-bar" :class="{ expired: countdown.includes('已截止') }">
        {{ countdown }}
      </div>

      <!-- 提交次数 -->
      <div v-if="selectedTaskId" class="attempts-bar">
        提交次数：<b>{{ submitCount }}</b> / {{ maxAttempts }}
        <span v-if="remainingAttempts <= 0" class="attempts-exhausted">（已达上限）</span>
        <span v-else class="attempts-remaining">（剩余 {{ remainingAttempts }} 次）</span>
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

    <!-- 成功提示 -->
    <div v-if="uploaded" class="success-banner">
      ✅ 作业提交成功！
    </div>

    <!-- 错误提示 -->
    <div v-if="error && !uploading" class="error-banner">
      ❌ {{ error }}
    </div>

    <!-- 文件名规范说明 -->
    <div class="usage">
      <h3>📋 文件名命名规范</h3>
      <p>建议按以下格式命名文件，方便老师批改时识别：</p>
      <code>姓名_班级_作业名称.pdf</code>
      <p class="example">例如：<strong>张三_计科2101_第三次作业.pdf</strong></p>
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
  padding: 0.75rem 1rem;
  background: #f0fff4;
  border: 1px solid #9ae6b4;
  border-radius: 8px;
  color: #276749;
  font-size: 0.95rem;
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
</style>
