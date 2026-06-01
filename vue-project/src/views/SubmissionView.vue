<template>
  <div class="view-page">
    <div v-if="loading" class="loading-state">加载中...</div>

    <template v-else-if="data">
      <header class="view-header">
        <button class="back-btn" @click="closeWindow">✕ 关闭</button>
        <div class="header-info">
          <h2>{{ data.fileName }}</h2>
          <div class="header-meta">
            <span>👤 {{ data.studentName }}</span>
          </div>
        </div>
      </header>

      <section class="content-section">
        <pre class="code-block"><code>{{ data.content }}</code></pre>
      </section>
    </template>

    <div v-else class="loading-state">
      <p>数据不存在</p>
      <button class="back-btn" @click="closeWindow">关闭</button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'SubmissionView',
  data() {
    return {
      apiBaseUrl: 'http://localhost:8080/api',
      submissionId: null,
      data: null,
      loading: true
    }
  },

  mounted() {
    this.submissionId = this.$route.params.id
    this.loadContent()
  },

  methods: {
    closeWindow() {
      window.close()
    },

    getToken() {
      return localStorage.getItem('token') || ''
    },

    async loadContent() {
      this.loading = true
      try {
        const response = await fetch(
          `${this.apiBaseUrl}/submissions/${this.submissionId}/content`,
          { headers: { 'Authorization': `Bearer ${this.getToken()}` } }
        )
        const result = await response.json()
        if (result.code === 200) {
          this.data = result.data
        } else {
          this.data = null
        }
      } catch (e) {
        console.error('加载提交内容失败', e)
        this.data = null
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.view-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px;
  color: #303133;
  font-size: 14px;
}

.loading-state {
  text-align: center;
  padding: 60px;
  color: #606266;
}

/* 头部 */
.view-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.back-btn {
  padding: 6px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: transparent;
  color: #303133;
  cursor: pointer;
  flex-shrink: 0;
  margin-top: 4px;
  font-size: 13px;
}

.back-btn:hover {
  background: #ebeef5;
}

.header-info h2 {
  margin: 0 0 4px 0;
  font-size: 18px;
  word-break: break-all;
}

.header-meta {
  color: #606266;
  font-size: 13px;
}

/* 代码块 */
.content-section {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}

.code-block {
  margin: 0;
  padding: 20px;
  overflow-x: auto;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-word;
  tab-size: 4;
}

.code-block code {
  font-family: inherit;
}
</style>
