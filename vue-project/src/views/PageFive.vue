<template>
  <div class="tasks-page">
    <!-- 创建新作业 -->
    <section class="create-section">
      <div class="section-header" @click="showCreateForm = !showCreateForm">
        <h3>📝 创建新作业</h3>
        <span class="toggle-icon">{{ showCreateForm ? '▲' : '▼' }}</span>
      </div>
      <div v-if="showCreateForm" class="create-form">
        <div class="form-row">
          <div class="form-group">
            <label>作业名称</label>
            <input v-model="createForm.taskName" placeholder="例如：第三次作业：数组与链表" class="form-input" />
          </div>
          <div class="form-group">
            <label>班级</label>
            <select v-model="createForm.classId" class="form-input">
              <option value="">请选择班级</option>
              <option v-for="cls in classList" :key="cls.id" :value="cls.id">{{ cls.name }}</option>
            </select>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>截止时间</label>
            <input v-model="createForm.deadline" type="datetime-local" class="form-input" />
          </div>
          <div class="form-group checkbox-group">
            <label class="checkbox-label">
              <input type="checkbox" v-model="createForm.allowLate" />
              允许逾期提交
            </label>
          </div>
          <div class="form-group" v-if="createForm.allowLate">
            <label>逾期每天扣</label>
            <div class="input-suffix">
              <input v-model.number="createForm.latePenalty" type="number" min="0" max="100" class="form-input" />
              <span class="suffix">分</span>
            </div>
          </div>
        </div>
        <div class="form-group">
          <label>作业说明</label>
          <textarea v-model="createForm.description" placeholder="请输入作业要求、题目说明等..." rows="4" class="form-textarea"></textarea>
        </div>
        <div class="form-actions">
          <button class="btn-primary" @click="createTask" :disabled="submitting">
            {{ submitting ? '创建中...' : '创建作业' }}
          </button>
        </div>
      </div>
    </section>

    <!-- 作业列表 -->
    <section class="list-section">
      <div class="list-header">
        <h3>📋 作业列表</h3>
        <div class="list-actions">
          <input v-model="searchKeyword" placeholder="搜索作业名称..." class="search-input" />
          <select v-model="filterStatus" class="filter-select">
            <option value="">全部</option>
            <option value="active">进行中</option>
            <option value="closed">已结束</option>
          </select>
          <select v-model="filterClassId" class="filter-select">
            <option value="">全部班级</option>
            <option v-for="cls in classList" :key="cls.id" :value="cls.id">{{ cls.name }}</option>
          </select>
        </div>
      </div>

      <div v-if="filteredTasks.length === 0" class="empty-state">
        <p>暂无作业数据</p>
      </div>

      <div v-for="group in taskGroups" :key="group.label" class="task-group">
        <div class="group-label">{{ group.label }}（{{ group.tasks.length }}）</div>
        <div class="task-card" v-for="task in group.tasks" :key="task.id">
          <div class="task-main">
            <div class="task-info">
              <div class="task-name">
                <span class="status-dot" :class="task.expired ? 'expired' : 'active'"></span>
                {{ task.taskName }}
              </div>
              <div class="task-meta">
                <span class="meta-item">📚 {{ getClassName(task.classId) }}</span>
                <span class="meta-item" v-if="task.deadline">
                  📅 截止 {{ formatDate(task.deadline) }}
                </span>
                <span class="meta-item" v-else>📅 无截止时间</span>
                <span class="meta-item" v-if="task.allowLate">
                  ⏰ 逾期允许，每天扣 {{ task.latePenalty }} 分
                </span>
              </div>
              <div class="task-stats">
                <span class="stat-item">已交：<b>{{ task.submittedCount }}</b></span>
                <span class="stat-item">总评均分：<b>{{ task.avgScore }}</b></span>
              </div>
            </div>
            <div class="task-actions">
              <button class="btn-sm" @click="editTask(task)">✏️ 编辑</button>
              <button class="btn-sm" @click="viewStats(task)">📊 统计</button>
              <button class="btn-sm btn-warning" @click="testReminder(task)">🔔 测试提醒</button>
              <button class="btn-sm btn-danger" @click="deleteTask(task)">🗑️ 删除</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 编辑弹窗 -->
    <div v-if="showEditModal" class="modal-overlay" @click.self="closeEditModal">
      <div class="modal-content">
        <div class="modal-header">
          <h3>✏️ 编辑作业</h3>
          <button class="close-btn" @click="closeEditModal">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>作业名称</label>
            <input v-model="editForm.taskName" class="form-input" />
          </div>
          <div class="form-group">
            <label>截止时间</label>
            <input v-model="editForm.deadline" type="datetime-local" class="form-input" />
          </div>
          <div class="form-row">
            <div class="form-group checkbox-group">
              <label class="checkbox-label">
                <input type="checkbox" v-model="editForm.allowLate" />
                允许逾期提交
              </label>
            </div>
            <div class="form-group" v-if="editForm.allowLate">
              <label>逾期每天扣</label>
              <div class="input-suffix">
                <input v-model.number="editForm.latePenalty" type="number" min="0" max="100" class="form-input" style="width:80px" />
                <span class="suffix">分</span>
              </div>
            </div>
          </div>
          <div class="form-group">
            <label>作业说明</label>
            <textarea v-model="editForm.description" rows="4" class="form-textarea"></textarea>
          </div>
          <div class="form-actions">
            <button class="btn-cancel" @click="closeEditModal">取消</button>
            <button class="btn-primary" @click="saveEdit" :disabled="submitting">
              {{ submitting ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'

export default {
  name: 'PageFive',
  data() {
    return {
      apiBaseUrl: 'http://localhost:8080/api',
      classList: [],
      tasks: [],
      searchKeyword: '',
      filterStatus: '',
      filterClassId: '',
      showCreateForm: true,
      submitting: false,
      showEditModal: false,
      editingTaskId: null,
      createForm: {
        taskName: '',
        classId: '',
        deadline: '',
        allowLate: true,
        latePenalty: 5,
        description: ''
      },
      editForm: {
        taskName: '',
        deadline: '',
        allowLate: true,
        latePenalty: 5,
        description: ''
      }
    }
  },

  computed: {
    filteredTasks() {
      return this.tasks.filter(t => {
        if (this.searchKeyword && !t.taskName.includes(this.searchKeyword)) return false
        if (this.filterStatus && t.status !== this.filterStatus) return false
        if (this.filterClassId && t.classId !== this.filterClassId) return false
        return true
      })
    },
    taskGroups() {
      const active = this.filteredTasks.filter(t => t.status === 'active')
      const closed = this.filteredTasks.filter(t => t.status === 'closed')
      const groups = []
      if (active.length > 0) groups.push({ label: '⏳ 进行中', tasks: active })
      if (closed.length > 0) groups.push({ label: '✅ 已结束', tasks: closed })
      return groups
    }
  },

 async mounted() {
  await this.loadClassList()
  this.loadTasks()
  },

  methods: {
    getToken() {
      return localStorage.getItem('token') || ''
    },

    async loadClassList() {
      try {
        const response = await fetch(`${this.apiBaseUrl}/dashboard/classes`, {
          headers: { 'Authorization': `Bearer ${this.getToken()}` }
        })
        if (!response.ok) throw new Error('获取班级列表失败')
        const result = await response.json()
        if (result.code === 200) {
          this.classList = result.data
            if (this.classList.length > 0) {
              
      }
        }
      } catch (e) {
        console.error('加载班级列表失败', e)
      }
    },

    async loadTasks() {
      if (!this.classList.length) return
      try {
        const results = await Promise.allSettled(
          this.classList.map(cls =>
            fetch(`${this.apiBaseUrl}/tasks?classId=${cls.id}`, {
              headers: { 'Authorization': `Bearer ${this.getToken()}` }
            }).then(r => r.ok ? r.json() : Promise.reject(r.status))
          )
        )
        this.tasks = results
          .filter(r => r.status === 'fulfilled' && r.value.code === 200)
          .flatMap(r => r.value.data)
      } catch (e) {
        console.error('加载任务失败', e)
      }
    },

    getClassName(classId) {
      const cls = this.classList.find(c => c.id === classId)
      return cls ? cls.name : '未知班级'
    },

    formatDate(dateStr) {
      if (!dateStr) return ''
      const d = new Date(dateStr)
      const pad = n => String(n).padStart(2, '0')
      return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    },

    toDatetimeLocal(date) {
      if (!date) return ''
      const d = new Date(date)
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
    },

    async createTask() {
      if (!this.createForm.taskName || !this.createForm.classId) {
        alert('请填写作业名称并选择班级')
        return
      }
      this.submitting = true
      try {
        const body = JSON.stringify({
          classId: this.createForm.classId,
          taskName: this.createForm.taskName,
          description: this.createForm.description,
          deadline: this.createForm.deadline ? new Date(this.createForm.deadline).toISOString() : null,
          allowLate: this.createForm.allowLate,
          latePenalty: this.createForm.allowLate ? this.createForm.latePenalty : 0
        })
        const response = await fetch(`${this.apiBaseUrl}/tasks`, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${this.getToken()}`,
            'Content-Type': 'application/json'
          },
          body
        })
        const result = await response.json()
        if (result.code === 200) {
          alert('作业创建成功')
          this.createForm = { taskName: '', classId: '', deadline: '', allowLate: true, latePenalty: 5, description: '' }
          this.loadTasks()
        }
      } catch (e) {
        alert('创建失败：' + e.message)
      } finally {
        this.submitting = false
      }
    },

    editTask(task) {
      this.editingTaskId = task.id
      this.editForm = {
        taskName: task.taskName,
        deadline: this.toDatetimeLocal(task.deadline),
        allowLate: task.allowLate,
        latePenalty: task.latePenalty || 0,
        description: task.description || ''
      }
      this.showEditModal = true
    },

    closeEditModal() {
      this.showEditModal = false
      this.editingTaskId = null
    },

    async saveEdit() {
      if (!this.editForm.taskName) {
        alert('作业名称不能为空')
        return
      }
      this.submitting = true
      try {
        const body = JSON.stringify({
          taskName: this.editForm.taskName,
          deadline: this.editForm.deadline ? new Date(this.editForm.deadline).toISOString() : null,
          allowLate: this.editForm.allowLate,
          latePenalty: this.editForm.allowLate ? this.editForm.latePenalty : 0,
          description: this.editForm.description
        })
        const response = await fetch(`${this.apiBaseUrl}/tasks/${this.editingTaskId}`, {
          method: 'PUT',
          headers: {
            'Authorization': `Bearer ${this.getToken()}`,
            'Content-Type': 'application/json'
          },
          body
        })
        const result = await response.json()
        if (result.code === 200) {
          alert('保存成功')
          this.closeEditModal()
          this.loadTasks()
        }
      } catch (e) {
        alert('保存失败：' + e.message)
      } finally {
        this.submitting = false
      }
    },

    viewStats(task) {
      this.$router.push(`/teacher/tasks/${task.id}`)
    },

    async testReminder(task) {
      if (!confirm(`立即发送测试提醒给作业「${task.taskName}」？\n\n将模拟截止前1小时的提醒流程。`)) return
      try {
        const response = await fetch(`${this.apiBaseUrl}/tasks/${task.id}/test-reminder`, {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${this.getToken()}` }
        })
        const result = await response.json()
        if (result.code === 200) {
          alert('测试提醒已发送！\n\n' + (result.data?.message || ''))
        } else {
          alert('发送失败：' + (result.message || '未知错误'))
        }
      } catch (e) {
        alert('发送失败：' + e.message)
      }
    },

    async deleteTask(task) {
      if (!confirm(`确定删除作业「${task.taskName}」吗？`)) return
      try {
        const response = await fetch(`${this.apiBaseUrl}/tasks/${task.id}`, {
          method: 'DELETE',
          headers: { 'Authorization': `Bearer ${this.getToken()}` }
        })
        const result = await response.json()
        if (result.code === 200) {
          alert('删除成功')
          this.loadTasks()
        }
      } catch (e) {
        alert('删除失败')
      }
    }
  }
}
</script>

<style scoped>
.tasks-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
  color: #e0e0e0;
  font-size: 14px;
}

/* 创建区 */
.create-section {
  background: #2a2a2a;
  border-radius: 10px;
  margin-bottom: 20px;
  overflow: hidden;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 20px;
  cursor: pointer;
  background: #333;
  user-select: none;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
}

.toggle-icon {
  color: #999;
  font-size: 12px;
}

.create-form {
  padding: 20px;
}

.form-row {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.form-row .form-group {
  flex: 1;
}

.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  color: #aaa;
  font-size: 13px;
}

.form-input,
.form-textarea,
.search-input,
.filter-select {
  width: 100%;
  padding: 9px 12px;
  border: 1px solid #444;
  border-radius: 6px;
  background: #1a1a1a;
  color: #e0e0e0;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.form-input:focus,
.form-textarea:focus {
  border-color: #ff7d00;
}

.form-textarea {
  resize: vertical;
  font-family: inherit;
}

.checkbox-group {
  display: flex;
  align-items: flex-end;
  padding-bottom: 12px;
}

.checkbox-label {
  display: flex !important;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #e0e0e0 !important;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  accent-color: #ff7d00;
}

.input-suffix {
  display: flex;
  align-items: center;
  gap: 8px;
}

.input-suffix .form-input {
  width: 100%;
}

.suffix {
  color: #999;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.btn-primary {
  padding: 10px 24px;
  background: #ff7d00;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}

.btn-primary:hover {
  background: #e66d00;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-cancel {
  padding: 10px 24px;
  background: #444;
  color: #ccc;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.btn-cancel:hover {
  background: #555;
}

/* 列表区 */
.list-section {
  background: #2a2a2a;
  border-radius: 10px;
  padding: 20px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.list-header h3 {
  margin: 0;
  font-size: 16px;
}

.list-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.search-input {
  width: 180px;
  padding: 7px 10px;
  font-size: 13px;
}

.filter-select {
  width: 120px;
  padding: 7px 10px;
  font-size: 13px;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #666;
}

/* 分组 */
.task-group {
  margin-bottom: 16px;
}

.group-label {
  font-size: 13px;
  color: #999;
  margin-bottom: 8px;
  padding: 0 4px;
}

/* 卡片 */
.task-card {
  background: #333;
  border-radius: 8px;
  margin-bottom: 8px;
  transition: background 0.2s;
}

.task-card:hover {
  background: #3a3a3a;
}

.task-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  gap: 12px;
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.active {
  background: #52c41a;
}

.status-dot.expired {
  background: #faad14;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: #999;
  font-size: 13px;
  margin-bottom: 6px;
}

.task-stats {
  display: flex;
  gap: 16px;
  color: #ccc;
  font-size: 13px;
}

.task-stats b {
  color: #ff7d00;
}

.task-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.btn-sm {
  padding: 6px 12px;
  border: 1px solid #555;
  border-radius: 5px;
  background: transparent;
  color: #ccc;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-sm:hover {
  background: #444;
  border-color: #ff7d00;
  color: #fff;
}

.btn-danger:hover {
  border-color: #f5222d;
  color: #f5222d;
  background: rgba(245, 34, 45, 0.1);
}

.btn-warning:hover {
  border-color: #faad14;
  color: #faad14;
  background: rgba(250, 173, 20, 0.1);
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
}

.modal-content {
  background: #2a2a2a;
  border-radius: 12px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #444;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
}

.close-btn {
  background: none;
  border: none;
  color: #999;
  font-size: 24px;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.close-btn:hover {
  color: #fff;
}

.modal-body {
  padding: 20px;
}
</style>
