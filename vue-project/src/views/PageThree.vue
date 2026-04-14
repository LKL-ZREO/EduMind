<template>
  <div class="document-page">
    <h1>📚 知识库管理</h1>
    <p class="subtitle">上传课本、知识点或参考资料，让AI助手基于这些内容回答你的问题</p>

    <!-- 统计信息 -->
    <div class="stats-bar">
      <div class="stat-item">
        <span class="stat-number">{{ documents.length }}</span>
        <span class="stat-label">总文档</span>
      </div>
      <div class="stat-item">
        <span class="stat-number">{{ processedCount }}</span>
        <span class="stat-label">已处理</span>
      </div>
      <div class="stat-item">
        <span class="stat-number">{{ processingCount }}</span>
        <span class="stat-label">处理中</span>
      </div>
    </div>

    <!-- 上传区域 -->
    <div
      class="upload-area"
      :class="{ dragging: isDragging }"
      @dragenter.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false"
      @dragover.prevent
      @drop.prevent="handleDrop"
      @click="triggerFileInput"
    >
      <input
        ref="fileInput"
        type="file"
        multiple
        accept=".txt,.md,.pdf,.doc,.docx"
        @change="handleFileSelect"
        style="display: none"
      />
      <div class="upload-content">
        <div class="upload-icon">📁</div>
        <p class="upload-text">点击或拖拽文件到此处上传</p>
        <p class="upload-hint">支持: TXT, Markdown, PDF, Word (最大 10MB)</p>
      </div>
    </div>

    <!-- 上传进度 -->
    <div v-if="uploadingFiles.length > 0" class="upload-progress">
      <h3>上传中...</h3>
      <div
        v-for="file in uploadingFiles"
        :key="file.id"
        class="progress-item"
      >
        <span class="file-name">{{ file.name }}</span>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: file.progress + '%' }"></div>
        </div>
        <span class="progress-text">{{ file.progress }}%</span>
      </div>
    </div>

    <!-- 文档列表 -->
    <div class="document-list">
      <h3>已上传文档</h3>
      <div v-if="documents.length === 0" class="empty-state">
        <p>暂无文档，请先上传</p>
      </div>
      <div
        v-for="doc in documents"
        :key="doc.docId"
        class="document-item"
      >
        <div class="doc-info">
          <div class="doc-icon">{{ getFileIcon(doc.contentType) }}</div>
          <div class="doc-details">
            <span class="doc-name">{{ doc.docName }}</span>
            <span class="doc-meta">
              {{ formatFileSize(doc.fileSize) }} · {{ formatDate(doc.createdAt) }}
            </span>
          </div>
        </div>
        <div class="doc-status">
          <span :class="['status-badge', getStatusClass(doc.status)]">
            {{ getStatusText(doc.status) }}
          </span>
          <span v-if="doc.chunkCount > 0" class="chunk-count">
            {{ doc.chunkCount }} 个片段
          </span>
        </div>
        <button class="delete-btn" @click="deleteDocument(doc.docId)" title="删除">
          🗑️
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'

const API_BASE_URL = 'http://localhost:8080/api'

// 状态
const fileInput = ref(null)
const isDragging = ref(false)
const documents = ref([])
const uploadingFiles = ref([])

// 计算属性
const processedCount = computed(() =>
  documents.value.filter(d => d.status === 1).length
)
const processingCount = computed(() =>
  documents.value.filter(d => d.status === 0).length
)

// 获取 token
const getToken = () => localStorage.getItem('token') || ''

// 触发文件选择
const triggerFileInput = () => {
  fileInput.value?.click()
}

// 处理文件选择
const handleFileSelect = (e) => {
  const files = Array.from(e.target.files)
  files.forEach(uploadFile)
  e.target.value = '' // 清空，允许重复选择
}

// 处理拖拽
const handleDrop = (e) => {
  isDragging.value = false
  const files = Array.from(e.dataTransfer.files)
  files.forEach(uploadFile)
}

// 上传文件
const uploadFile = async (file) => {
  // 文件类型检查
  const allowedTypes = [
    'text/plain',
    'text/markdown',
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  ]
  const allowedExts = ['.txt', '.md', '.pdf', '.doc', '.docx']
  const ext = '.' + file.name.split('.').pop().toLowerCase()

  if (!allowedExts.includes(ext)) {
    alert(`不支持的文件类型: ${ext}`)
    return
  }

  // 文件大小检查 (10MB)
  if (file.size > 10 * 1024 * 1024) {
    alert('文件大小不能超过 10MB')
    return
  }

  const uploadId = Date.now() + Math.random().toString(36).substr(2, 9)
  const uploadItem = {
    id: uploadId,
    name: file.name,
    progress: 0
  }
  uploadingFiles.value.push(uploadItem)

  try {
    const formData = new FormData()
    formData.append('file', file)

    // 模拟进度
    const progressInterval = setInterval(() => {
      if (uploadItem.progress < 90) {
        uploadItem.progress += 10
      }
    }, 200)

    const response = await fetch(`${API_BASE_URL}/documents/upload`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${getToken()}`
      },
      body: formData
    })

    clearInterval(progressInterval)

    if (!response.ok) {
      throw new Error('上传失败')
    }

    uploadItem.progress = 100

    // 延迟移除进度条并刷新列表
    setTimeout(() => {
      uploadingFiles.value = uploadingFiles.value.filter(f => f.id !== uploadId)
      fetchDocuments()
    }, 500)

  } catch (error) {
    alert(`上传失败: ${error.message}`)
    uploadingFiles.value = uploadingFiles.value.filter(f => f.id !== uploadId)
  }
}

// 获取文档列表
const fetchDocuments = async () => {
  try {
    const response = await fetch(`${API_BASE_URL}/documents/list`, {
      headers: {
        'Authorization': `Bearer ${getToken()}`
      }
    })

    if (!response.ok) {
      throw new Error('获取列表失败')
    }

    documents.value = await response.json()
  } catch (error) {
    console.error('获取文档列表失败:', error)
  }
}

// 删除文档
const deleteDocument = async (docId) => {
  if (!confirm('确定要删除这个文档吗？')) {
    return
  }

  try {
    const response = await fetch(`${API_BASE_URL}/documents/${docId}`, {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${getToken()}`
      }
    })

    if (!response.ok) {
      throw new Error('删除失败')
    }

    fetchDocuments()
  } catch (error) {
    alert(`删除失败: ${error.message}`)
  }
}

// 工具函数
const getFileIcon = (contentType) => {
  if (contentType?.includes('pdf')) return '📕'
  if (contentType?.includes('word')) return '📘'
  if (contentType?.includes('markdown')) return '📝'
  return '📄'
}

const getStatusClass = (status) => {
  const map = { 0: 'processing', 1: 'success', 2: 'error' }
  return map[status] || 'unknown'
}

const getStatusText = (status) => {
  const map = { 0: '处理中', 1: '已完成', 2: '失败' }
  return map[status] || '未知'
}

const formatFileSize = (bytes) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 初始化
onMounted(() => {
  fetchDocuments()
  // 每 10 秒刷新一次，查看处理状态
  setInterval(fetchDocuments, 10000)
})
</script>

<style scoped>
.document-page {
  padding: 1.5rem;
  max-width: 900px;
  margin: 0 auto;
}

h1 {
  margin-bottom: 0.5rem;
  color: #333;
}

.subtitle {
  color: #666;
  margin-bottom: 1.5rem;
  font-size: 0.9rem;
}

/* 统计栏 */
.stats-bar {
  display: flex;
  gap: 2rem;
  margin-bottom: 1.5rem;
  padding: 1rem;
  background: #f8f9fa;
  border-radius: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-number {
  font-size: 1.5rem;
  font-weight: bold;
  color: #4a90d9;
}

.stat-label {
  font-size: 0.8rem;
  color: #666;
}

/* 上传区域 */
.upload-area {
  border: 2px dashed #ccc;
  border-radius: 12px;
  padding: 2rem;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 1.5rem;
}

.upload-area:hover,
.upload-area.dragging {
  border-color: #4a90d9;
  background: #f0f7ff;
}

.upload-icon {
  font-size: 3rem;
  margin-bottom: 0.5rem;
}

.upload-text {
  font-size: 1.1rem;
  color: #333;
  margin-bottom: 0.3rem;
}

.upload-hint {
  font-size: 0.8rem;
  color: #999;
}

/* 上传进度 */
.upload-progress {
  margin-bottom: 1.5rem;
  padding: 1rem;
  background: #f8f9fa;
  border-radius: 8px;
}

.upload-progress h3 {
  margin-bottom: 0.8rem;
  font-size: 1rem;
}

.progress-item {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  margin-bottom: 0.5rem;
}

.file-name {
  flex: 0 0 150px;
  font-size: 0.85rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #4a90d9;
  transition: width 0.3s;
}

.progress-text {
  flex: 0 0 40px;
  font-size: 0.8rem;
  color: #666;
  text-align: right;
}

/* 文档列表 */
.document-list h3 {
  margin-bottom: 1rem;
  font-size: 1.1rem;
}

.empty-state {
  text-align: center;
  padding: 3rem;
  color: #999;
  background: #f8f9fa;
  border-radius: 8px;
}

.document-item {
  display: flex;
  align-items: center;
  padding: 1rem;
  background: #fff;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  margin-bottom: 0.8rem;
  transition: box-shadow 0.2s;
}

.document-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.doc-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.8rem;
}

.doc-icon {
  font-size: 1.5rem;
}

.doc-details {
  display: flex;
  flex-direction: column;
}

.doc-name {
  font-weight: 500;
  color: #333;
}

.doc-meta {
  font-size: 0.8rem;
  color: #999;
}

.doc-status {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  margin-right: 1rem;
}

.status-badge {
  padding: 0.3rem 0.6rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
}

.status-badge.processing {
  background: #fff3cd;
  color: #856404;
}

.status-badge.success {
  background: #d4edda;
  color: #155724;
}

.status-badge.error {
  background: #f8d7da;
  color: #721c24;
}

.chunk-count {
  font-size: 0.75rem;
  color: #666;
}

.delete-btn {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1.2rem;
  padding: 0.3rem;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.delete-btn:hover {
  opacity: 1;
}
</style>
