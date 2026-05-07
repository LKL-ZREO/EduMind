<script setup lang="ts">
import { ref, computed } from 'vue'
import { RouterLink } from 'vue-router'

// 文件上传
const file = ref<File | null>(null)
const uploading = ref(false)
const uploaded = ref(false)
const error = ref('')
const progress = ref(0)

// 提示信息
const hint = ref('请上传你的作业文件')

// 限制
const MAX_SIZE_MB = 20
const acceptTypes = '.pdf,.doc,.docx,.txt,.jpg,.jpeg,.png,.zip,.rar'

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

  if (f.size > MAX_SIZE_MB * 1024 * 1024) {
    error.value = `文件超过 ${MAX_SIZE_MB}MB 限制，请压缩后重试`
    file.value = null
    return
  }

  file.value = f
  hint.value = `已选择: ${f.name} (${(f.size / 1024 / 1024).toFixed(1)}MB)`
}

function dropHandler(e: DragEvent) {
  e.preventDefault()
  e.stopPropagation()

  const dt = e.dataTransfer
  if (!dt?.files.length) return

  const f = dt.files[0]
  error.value = ''
  uploaded.value = false

  if (f.size > MAX_SIZE_MB * 1024 * 1024) {
    error.value = `文件超过 ${MAX_SIZE_MB}MB 限制`
    return
  }

  file.value = f
  hint.value = `已选择: ${f.name} (${(f.size / 1024 / 1024).toFixed(1)}MB)`
}

function dragOverHandler(e: DragEvent) {
  e.preventDefault()
  e.stopPropagation()
}

function removeFile() {
  file.value = null
  hint.value = '请上传你的作业文件'
}

async function submit() {
  if (!file.value) {
    error.value = '请先选择文件'
    return
  }

  if (!fileSizeOk.value) {
    error.value = `文件超过 ${MAX_SIZE_MB}MB 限制`
    return
  }

  uploading.value = true
  error.value = ''
  progress.value = 0

  const formData = new FormData()
  formData.append('file', file.value)

  // 模拟进度（XHR 才有真实进度，fetch 没有）
  const progTimer = setInterval(() => {
    progress.value = Math.min(progress.value + Math.random() * 20, 90)
  }, 300)

  try {
    const res = await fetch('/api/homework/submit', {
      method: 'POST',
      body: formData,
    })

    clearInterval(progTimer)

    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || data.error || '上传失败，请稍后重试')
    }

    progress.value = 100
    uploaded.value = true
    file.value = null
    hint.value = '提交成功！可以继续上传下一份作业'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '上传失败，请检查网络后重试'
    progress.value = 0
  } finally {
    uploading.value = false
  }
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
      <p class="subtitle">选择文件后直接上传，无需注册登录</p>
    </div>

    <!-- 上传区域 -->
    <div
      class="upload-zone"
      :class="{ 'has-file': !!file, uploading, error: !!error }"
      @drop="dropHandler"
      @dragover="dragOverHandler"
    >
      <!-- 未选文件：拖拽提示 -->
      <div v-if="!file && !uploading" class="drop-hint">
        <div class="upload-icon">📤</div>
        <p>将作业文件拖到此处</p>
        <p class="or">或</p>
        <label class="file-btn">
          选择文件
          <input
            type="file"
            :accept="acceptTypes"
            hidden
            @change="onFileSelected"
          />
        </label>
        <p class="tip">支持 PDF、Word、TXT、图片、压缩包，最大 {{ MAX_SIZE_MB }}MB</p>
      </div>

      <!-- 已选文件 -->
      <div v-else-if="file && !uploading" class="file-preview">
        <div class="file-icon">{{ file.name.match(/\.(\w+)$/)?.[1]?.toUpperCase() || '📄' }}</div>
        <div class="file-info">
          <span class="file-name">{{ file.name }}</span>
          <span class="file-size">{{ (file.size / 1024 / 1024).toFixed(2) }} MB</span>
        </div>
        <div class="file-actions">
          <button class="btn-remove" @click="removeFile">移除</button>
          <button class="btn-submit" @click="submit" :disabled="uploading">提交作业</button>
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
      <code>姓名_课程_作业名称.pdf</code>
      <p class="example">例如：<strong>张三_数据结构_第三次作业.pdf</strong></p>
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
  margin-bottom: 2rem;
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

/* 拖拽提示 */
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

/* 文件预览 */
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

/* 成功/错误提示 */
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

/* 使用说明 */
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
