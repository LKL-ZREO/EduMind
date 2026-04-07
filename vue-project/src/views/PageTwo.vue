<template>
  <div class="chat-page">
    <!-- 头部 -->
    <header class="chat-header">
      <div class="header-content">
        <div class="avatar">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"/>
            <path d="M12 6v6l4 2"/>
          </svg>
        </div>
        <div class="header-info">
          <h2>AI 助手</h2>
          <span class="status" :class="{ online: isConnected }">
            {{ isConnected ? '在线' : '连接中...' }}
          </span>
        </div>
      </div>
      <button class="clear-btn" @click="clearChat" title="清空对话">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
        </svg>
      </button>
    </header>

    <!-- 消息区域 -->
    <main class="chat-messages" ref="messagesContainer">
      <!-- 欢迎消息 -->
      <div v-if="messages.length === 0" class="welcome-message">
        <div class="welcome-icon">👋</div>
        <h3>你好！我是 AI 助手</h3>
        <p>有什么我可以帮你的吗？</p>
        <div class="quick-actions">
          <button v-for="(action, index) in quickActions" :key="index"
                  @click="sendQuickMessage(action)" class="action-chip">
            {{ action }}
          </button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, index) in messages" :key="index"
           :class="['message', msg.role]">
        <div class="message-avatar">
          <div v-if="msg.role === 'user'" class="user-avatar">我</div>
          <div v-else class="ai-avatar">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"/>
              <path d="M12 6v6l4 2"/>
            </svg>
          </div>
        </div>
        <div class="message-content">
          <div class="message-bubble">
            <div class="message-text" v-html="formatMessage(msg.content)"></div>
            <!-- 代码块特殊处理 -->
            <div v-if="msg.codeBlocks && msg.codeBlocks.length" class="code-blocks">
              <div v-for="(code, cIndex) in msg.codeBlocks" :key="cIndex" class="code-block">
                <div class="code-header">
                  <span>{{ code.language || 'code' }}</span>
                  <button @click="copyCode(code.content)" class="copy-btn">复制</button>
                </div>
                <pre><code>{{ code.content }}</code></pre>
              </div>
            </div>
          </div>
          <div class="message-meta">
            <span class="time">{{ msg.time }}</span>
            <span v-if="msg.role === 'assistant' && msg.model" class="model-tag">{{ msg.model }}</span>
          </div>
        </div>
      </div>

      <!-- 正在输入指示器 -->
      <div v-if="isTyping" class="message assistant typing">
        <div class="message-avatar">
          <div class="ai-avatar">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z"/>
              <path d="M12 6v6l4 2"/>
            </svg>
          </div>
        </div>
        <div class="typing-indicator">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>
    </main>

    <!-- 输入区域 -->
    <footer class="chat-input-area">
      <!-- 文件选择按钮和已选文件显示 -->
      <div class="file-upload-bar">
        <input
          type="file"
          ref="fileInput"
          @change="handleFileSelect"
          style="display: none"
          accept=".txt,.pdf,.doc,.docx,.jpg,.jpeg,.png"
        />
        <button
          @click="$refs.fileInput.click()"
          class="attach-btn"
          title="选择文件"
          :disabled="isUploading || isLoading"
        >
          📎
        </button>
        <span v-if="selectedFile" class="file-tag">
          {{ selectedFile.name }}
          <button @click="clearFile" class="clear-file" title="移除文件">✕</button>
        </span>
        <span v-else-if="isUploading" class="upload-status">上传中...</span>
      </div>

      <div class="input-container">
        <textarea
          v-model="inputMessage"
          @keydown.enter.prevent="handleEnter"
          @input="autoResize"
          ref="textarea"
          :placeholder="selectedFile ? '输入批改要求...' : '输入消息...'"
          rows="1"
          :disabled="isLoading || isUploading"
        ></textarea>
        <button
          @click="sendMessage"
          :disabled="(!inputMessage.trim() && !selectedFile) || isLoading || isUploading"
          class="send-btn"
        >
          <svg v-if="!isLoading && !isUploading" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
          </svg>
          <span v-else class="loading-spinner"></span>
        </button>
      </div>
      <div class="input-hint">
        <span>{{ selectedFile ? '按 Enter 上传文件并批改' : '按 Enter 发送，Shift + Enter 换行' }}</span>
        <span class="powered-by">Powered by Spring AI + OpenClaw</span>
      </div>
    </footer>
  </div>
</template>

<script>
export default {
  name: 'AgentChat',
  data() {
    return {
      messages: [],
      inputMessage: '',
      isLoading: false,
      isTyping: false,
      isConnected: false,
      quickActions: [
        '帮我写一段Java代码',
        '解释一下Spring Boot',
        '如何学习Vue3？',
        '讲个笑话'
      ],
      apiBaseUrl: 'http://localhost:8080/api',
      // 文件上传相关
      selectedFile: null,
      uploadedFilePath: null,
      isUploading: false,
      // 会话上下文
      sessionId: null
    }
  },
  mounted() {
    this.checkConnection()
    this.loadSession()
    this.$nextTick(() => {
      this.autoResize()
    })
  },
  beforeDestroy() {
    // 清理工作（如有需要）
  },
  methods: {
    // 生成唯一会话ID
    generateSessionId() {
      return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
    },

    // 保存会话到localStorage
    saveSession() {
      const sessionData = {
        sessionId: this.sessionId,
        messages: this.messages,
        timestamp: Date.now()
      }
      localStorage.setItem('chat_session', JSON.stringify(sessionData))
    },

    // 从localStorage加载会话
    loadSession() {
      try {
        const saved = localStorage.getItem('chat_session')
        if (saved) {
          const sessionData = JSON.parse(saved)
          // 检查是否过期（7天）
          const sevenDays = 7 * 24 * 60 * 60 * 1000
          if (Date.now() - sessionData.timestamp < sevenDays) {
            this.sessionId = sessionData.sessionId
            this.messages = sessionData.messages || []
          } else {
            // 过期，创建新会话
            this.sessionId = this.generateSessionId()
          }
        } else {
          // 没有保存的会话，创建新的
          this.sessionId = this.generateSessionId()
        }
      } catch (e) {
        console.error('加载会话失败:', e)
        this.sessionId = this.generateSessionId()
      }
    },

    // 清空对话
    clearChat() {
      this.messages = []
      this.sessionId = this.generateSessionId()
      this.saveSession()
    },

    // 检查连接状态
    async checkConnection() {
      try {
        const response = await fetch(`${this.apiBaseUrl}/chat/health`, {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json'
          }
        })
        this.isConnected = response.ok
      } catch (error) {
        this.isConnected = false
        console.log('后端连接检查失败:', error)
      }
    },

    // 发送快捷消息
    sendQuickMessage(text) {
      this.inputMessage = text
      this.sendMessage()
    },

    // 处理回车键
    handleEnter(e) {
      if (e.shiftKey) {
        // Shift+Enter 换行
        return
      }
      this.sendMessage()
    },

    // 自动调整文本框高度
    autoResize() {
      const textarea = this.$refs.textarea
      textarea.style.height = 'auto'
      textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px'
    },

    // 选择文件
    handleFileSelect(event) {
      const file = event.target.files[0]
      if (file) {
        this.selectedFile = file
        // 自动填充默认提示
        if (!this.inputMessage.trim()) {
          this.inputMessage = '请批改这份作业'
        }
        this.$nextTick(() => {
          this.autoResize()
        })
      }
    },

    // 清除文件
    clearFile() {
      this.selectedFile = null
      this.uploadedFilePath = null
      if (this.$refs.fileInput) {
        this.$refs.fileInput.value = ''
      }
    },

    // 发送消息（支持文件上传和批改）
    async sendMessage() {
      const message = this.inputMessage.trim()
      console.log('sendMessage 被调用，message:', message, 'selectedFile:', this.selectedFile)

      // 有文件时：上传 + 批改
      if (this.selectedFile) {
        await this.uploadAndGrade(message)
      } else if (message) {
        // 普通聊天
        await this.sendChatMessage(message)
      }
    },

    // 普通聊天消息
    async sendChatMessage(message) {
      // 添加用户消息
      const userMsg = {
        role: 'user',
        content: message,
        time: this.getCurrentTime()
      }
      this.messages.push(userMsg)
      this.inputMessage = ''
      this.$refs.textarea.style.height = 'auto'
      this.scrollToBottom()

      this.isLoading = true
      this.isTyping = true

      try {
        await this.streamMessage(message)
      } catch (error) {
        this.addAssistantMessage('抱歉，连接出现问题，请稍后重试。', 'error')
        console.error('发送消息失败:', error)
      } finally {
        this.isLoading = false
        this.isTyping = false
        this.saveSession()
      }
    },

    // 上传文件并批改
    async uploadAndGrade(requirement) {
      this.isUploading = true

      try {
        const token = localStorage.getItem('token')

        // 步骤1：上传文件
        const formData = new FormData()
        formData.append('file', this.selectedFile)

        const uploadRes = await fetch(`${this.apiBaseUrl}/upload`, {
          method: 'POST',
          headers: {
            'Authorization': token ? `Bearer ${token}` : ''
          },
          body: formData
        })

        if (!uploadRes.ok) {
          if (uploadRes.status === 401) {
            throw new Error('登录已过期，请重新登录')
          }
          throw new Error('文件上传失败')
        }

        const uploadData = await uploadRes.json()
        this.uploadedFilePath = uploadData.filePath

        // 显示用户消息
        const userMsg = {
          role: 'user',
          content: `[文件] ${this.selectedFile.name}\n${requirement || '请批改这份作业'}`,
          time: this.getCurrentTime()
        }
        this.messages.push(userMsg)
        this.inputMessage = ''
        this.$refs.textarea.style.height = 'auto'
        this.scrollToBottom()

        this.isLoading = true
        this.isTyping = true

        // 步骤2：调用批改
        const gradeRes = await fetch(`${this.apiBaseUrl}/chat/grade`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': token ? `Bearer ${token}` : ''
          },
          body: JSON.stringify({
            filePath: this.uploadedFilePath,
            requirement: requirement || '请批改这份作业'
          })
        })

        if (!gradeRes.ok) {
          if (gradeRes.status === 401) {
            throw new Error('登录已过期，请重新登录')
          }
          throw new Error('批改请求失败')
        }

        const gradeData = await gradeRes.json()

        // 显示批改结果
        this.messages.push({
          role: 'assistant',
          content: gradeData.content,
          time: this.getCurrentTime(),
          model: 'OpenClaw'
        })

        this.clearFile()
        this.scrollToBottom()

      } catch (error) {
        console.error('上传/批改失败:', error)
        this.addAssistantMessage('文件上传或批改失败：' + error.message, 'error')
      } finally {
        this.isUploading = false
        this.isLoading = false
        this.isTyping = false
        this.saveSession()
      }
    },

    // 流式接收消息（使用 fetch + ReadableStream）
    async streamMessage(userMessage) {
      const assistantMsg = {
        role: 'assistant',
        content: '',
        time: this.getCurrentTime(),
        model: 'OpenClaw'
      }
      this.messages.push(assistantMsg)
      const msgIndex = this.messages.length - 1

      const encodedMessage = encodeURIComponent(userMessage)
      const encodedSessionId = this.sessionId ? encodeURIComponent(this.sessionId) : ''
      const token = localStorage.getItem('token')
      
      const url = `${this.apiBaseUrl}/chat/stream?message=${encodedMessage}&sessionId=${encodedSessionId}`

      try {
        const response = await fetch(url, {
          method: 'GET',
          headers: {
            'Accept': 'text/event-stream',
            'Authorization': token ? `Bearer ${token}` : ''
          }
        })

        if (!response.ok) {
          if (response.status === 401) {
            throw new Error('登录已过期，请重新登录')
          }
          throw new Error(`HTTP ${response.status}`)
        }

        this.isConnected = true

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          
          if (done) {
            break
          }

          buffer += decoder.decode(value, { stream: true })
          
          // 按行分割处理 SSE 数据
          const lines = buffer.split('\n')
          buffer = lines.pop() || '' // 保留未完整的一行

          for (const line of lines) {
            const trimmed = line.trim()
            if (trimmed.startsWith('data: ')) {
              const data = trimmed.slice(6)
              
              if (data === '[DONE]') {
                this.isTyping = false
                this.saveSession()
                return
              }

              try {
                const parsed = JSON.parse(data)
                if (parsed.content) {
                  this.messages[msgIndex].content += parsed.content
                  this.scrollToBottom()
                }
              } catch {
                // 非 JSON 格式，直接追加
                this.messages[msgIndex].content += data
                this.scrollToBottom()
              }
            }
          }
        }

        // 处理剩余缓冲区
        if (buffer.trim()) {
          const trimmed = buffer.trim()
          if (trimmed.startsWith('data: ')) {
            const data = trimmed.slice(6)
            if (data !== '[DONE]') {
              try {
                const parsed = JSON.parse(data)
                if (parsed.content) {
                  this.messages[msgIndex].content += parsed.content
                }
              } catch {
                this.messages[msgIndex].content += data
              }
            }
          }
        }

        this.isTyping = false
        this.saveSession()

      } catch (error) {
        console.error('流式请求失败:', error)
        if (this.messages[msgIndex].content === '') {
          this.messages[msgIndex].content = error.message || '获取响应失败，请检查后端服务。'
        }
        this.saveSession()
        throw error
      }
    },

    // 添加助手消息（非流式）
    addAssistantMessage(content, type = 'normal') {
      this.messages.push({
        role: 'assistant',
        content: content,
        time: this.getCurrentTime(),
        model: 'OpenClaw',
        type: type
      })
      this.scrollToBottom()
    },

    // 格式化消息内容（简单处理换行和代码块）
    formatMessage(content) {
      if (!content) return ''

      // 转义 HTML
      let formatted = content
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')

      // 处理代码块（简单实现，实际可用 marked 等库）
      formatted = formatted.replace(/```(\w+)?\n([\s\S]*?)```/g, (match, lang, code) => {
        return `<div class="code-block-preview"><pre><code>${code}</code></pre></div>`
      })

      // 处理行内代码
      formatted = formatted.replace(/`([^`]+)`/g, '<code>$1</code>')

      // 处理换行
      formatted = formatted.replace(/\n/g, '<br>')

      return formatted
    },

    // 复制代码
    copyCode(code) {
      navigator.clipboard.writeText(code).then(() => {
        // 可以添加 toast 提示
        alert('代码已复制到剪贴板')
      })
    },

    // 清空对话
    clearChat() {
      if (confirm('确定要清空所有对话吗？')) {
        this.messages = []
      }
    },

    // 获取当前时间
    getCurrentTime() {
      const now = new Date()
      return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
    },

    // 滚动到底部
    scrollToBottom() {
      this.$nextTick(() => {
        const container = this.$refs.messagesContainer
        if (container) {
          container.scrollTop = container.scrollHeight
        }
      })
    }
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 900px;
  margin: 0 auto;
  background: #f5f7fa;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 头部样式 */
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  background: white;
  border-bottom: 1px solid #e8ecf1;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}

.header-content {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.avatar {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.avatar svg {
  width: 24px;
  height: 24px;
}

.header-info h2 {
  margin: 0;
  font-size: 1.1rem;
  color: #1a202c;
  font-weight: 600;
}

.status {
  font-size: 0.75rem;
  color: #a0aec0;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.status.online {
  color: #48bb78;
}

.status.online::before {
  content: '';
  width: 6px;
  height: 6px;
  background: #48bb78;
  border-radius: 50%;
  display: inline-block;
}

.clear-btn {
  background: none;
  border: none;
  padding: 0.5rem;
  cursor: pointer;
  color: #a0aec0;
  border-radius: 8px;
  transition: all 0.2s;
}

.clear-btn:hover {
  background: #fed7d7;
  color: #e53e3e;
}

.clear-btn svg {
  width: 20px;
  height: 20px;
}

/* 消息区域 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* 欢迎消息 */
.welcome-message {
  text-align: center;
  padding: 3rem 1rem;
  color: #4a5568;
}

.welcome-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.welcome-message h3 {
  margin: 0 0 0.5rem 0;
  color: #2d3748;
  font-size: 1.25rem;
}

.welcome-message p {
  margin: 0 0 1.5rem 0;
  color: #718096;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: center;
}

.action-chip {
  padding: 0.5rem 1rem;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  font-size: 0.875rem;
  color: #4a5568;
  cursor: pointer;
  transition: all 0.2s;
}

.action-chip:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
  transform: translateY(-1px);
  box-shadow: 0 4px 6px rgba(102, 126, 234, 0.2);
}

/* 消息气泡 */
.message {
  display: flex;
  gap: 0.75rem;
  max-width: 85%;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.user-avatar {
  width: 36px;
  height: 36px;
  background: #667eea;
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.875rem;
  font-weight: 600;
}

.ai-avatar {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.ai-avatar svg {
  width: 20px;
  height: 20px;
}

.message-content {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.message-bubble {
  padding: 0.875rem 1.125rem;
  border-radius: 1rem;
  line-height: 1.6;
  font-size: 0.9375rem;
  word-wrap: break-word;
}

.message.user .message-bubble {
  background: #667eea;
  color: white;
  border-bottom-right-radius: 0.25rem;
}

.message.assistant .message-bubble {
  background: white;
  color: #2d3748;
  border-bottom-left-radius: 0.25rem;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

.message-text :deep(code) {
  background: rgba(0,0,0,0.05);
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 0.875em;
}

.message.user .message-text :deep(code) {
  background: rgba(255,255,255,0.2);
}

.message-meta {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  font-size: 0.75rem;
  color: #a0aec0;
  padding: 0 0.25rem;
}

.model-tag {
  background: #edf2f7;
  padding: 0.125rem 0.375rem;
  border-radius: 4px;
  font-size: 0.7rem;
}

/* 代码块样式 */
.code-block-preview {
  margin-top: 0.5rem;
  background: #1a202c;
  border-radius: 8px;
  overflow: hidden;
}

.code-block-preview pre {
  margin: 0;
  padding: 0.75rem;
  overflow-x: auto;
  font-size: 0.875rem;
  line-height: 1.5;
}

.code-block-preview code {
  color: #e2e8f0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

/* 输入指示器 */
.typing-indicator {
  display: flex;
  gap: 0.25rem;
  padding: 1rem;
  background: white;
  border-radius: 1rem;
  border-bottom-left-radius: 0.25rem;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #cbd5e0;
  border-radius: 50%;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes typing {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* 输入区域 */
.chat-input-area {
  padding: 1rem 1.5rem 1.5rem;
  background: white;
  border-top: 1px solid #e8ecf1;
}

.input-container {
  display: flex;
  gap: 0.75rem;
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 1rem;
  padding: 0.75rem;
  transition: all 0.2s;
}

.input-container:focus-within {
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

textarea {
  flex: 1;
  border: none;
  background: transparent;
  resize: none;
  outline: none;
  font-size: 0.9375rem;
  line-height: 1.5;
  max-height: 120px;
  font-family: inherit;
}

textarea::placeholder {
  color: #a0aec0;
}

.send-btn {
  width: 40px;
  height: 40px;
  background: #667eea;
  border: none;
  border-radius: 50%;
  color: white;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: #5568d3;
  transform: scale(1.05);
}

.send-btn:disabled {
  background: #cbd5e0;
  cursor: not-allowed;
}

.send-btn svg {
  width: 20px;
  height: 20px;
  margin-left: 2px;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255,255,255,0.3);
  border-top-color: white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.input-hint {
  display: flex;
  justify-content: space-between;
  margin-top: 0.5rem;
  font-size: 0.75rem;
  color: #a0aec0;
  padding: 0 0.5rem;
}

/* 文件上传栏 */
.file-upload-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  padding: 0 0.25rem;
}

.attach-btn {
  padding: 0.5rem;
  background: #f7fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  font-size: 1.25rem;
  transition: all 0.2s;
}

.attach-btn:hover:not(:disabled) {
  background: #e2e8f0;
  border-color: #cbd5e0;
}

.attach-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.file-tag {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.75rem;
  background: #e3f2fd;
  border-radius: 6px;
  font-size: 0.875rem;
  color: #1976d2;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.clear-file {
  background: none;
  border: none;
  color: #666;
  cursor: pointer;
  padding: 0 2px;
  font-size: 0.75rem;
  line-height: 1;
}

.clear-file:hover {
  color: #f44336;
}

.upload-status {
  font-size: 0.875rem;
  color: #667eea;
  font-style: italic;
}

.powered-by {
  font-weight: 500;
}

/* 响应式 */
@media (max-width: 640px) {
  .chat-page {
    max-width: 100%;
  }

  .message {
    max-width: 90%;
  }

  .quick-actions {
    display: none;
  }
}

/* 滚动条美化 */
.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: transparent;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #cbd5e0;
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: #a0aec0;
}
</style>
