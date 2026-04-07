<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'

interface FormData {
  username: string
  email: string
  password: string
  confirmPassword: string
}

const form = reactive<FormData>({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const error = ref<string | null>(null)
const success = ref<string | null>(null)
const loading = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)

const router = useRouter()

// 验证函数
const validateForm = (): string | null => {
  if (!form.username.trim()) {
    return '请输入用户名'
  }
  if (form.username.length < 3) {
    return '用户名至少需要3个字符'
  }
  if (!form.email.trim()) {
    return '请输入邮箱地址'
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(form.email)) {
    return '请输入有效的邮箱地址'
  }
  if (!form.password) {
    return '请输入密码'
  }
  if (form.password.length < 6) {
    return '密码至少需要6个字符'
  }
  if (form.password !== form.confirmPassword) {
    return '两次输入的密码不一致'
  }
  return null
}

async function submit(e: Event) {
  e.preventDefault() // 👈 新增

  const validationError = validateForm()
  if (validationError) {
    error.value = validationError
    success.value = null
    return
  }

  error.value = null
  success.value = null
  loading.value = true

  try {
    const response = await fetch('/api/auth/register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username: form.username,
        email: form.email,
        password: form.password
      })
    })

    if (!response.ok) {
      let errorMessage = '注册失败，请稍后重试'
      try {
        const errorData = await response.json()
        errorMessage = errorData.message || errorData.error || errorMessage
      } catch {
        errorMessage = response.statusText || errorMessage
      }
      throw new Error(errorMessage)
    }

    success.value = '注册成功！正在跳转...'
    setTimeout(() => {
      router.push('/login')
    }, 1500)
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err)
    error.value = msg || '注册失败，请稍后重试'
    success.value = null
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="card">
      <h1>创建账户</h1>
      <p class="subtitle">填写信息开始使用我们的服务</p>

      <form @submit.prevent="submit" class="form">
        <div class="form-group">
          <label for="username">用户名</label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            placeholder="请输入用户名"
            :disabled="loading"
          />
        </div>

        <div class="form-group">
          <label for="email">邮箱</label>
          <input
            id="email"
            v-model="form.email"
            type="email"
            placeholder="your@email.com"
            :disabled="loading"
          />
        </div>

        <div class="form-group">
          <label for="password">密码</label>
          <div class="password-input-wrapper">
            <input
              id="password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="至少6位密码"
              :disabled="loading"
            />
            <button
              type="button"
              class="toggle-password"
              @click="showPassword = !showPassword"
              :aria-label="showPassword ? '隐藏密码' : '显示密码'"
            >
              {{ showPassword ? '🙈' : '👁️' }}
            </button>
          </div>
          <div class="password-hint">
            密码至少6位，建议包含数字和字母
          </div>
        </div>

        <div class="form-group">
          <label for="confirmPassword">确认密码</label>
          <div class="password-input-wrapper">
            <input
              id="confirmPassword"
              v-model="form.confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              placeholder="再次输入密码"
              :disabled="loading"
            />
            <button
              type="button"
              class="toggle-password"
              @click="showConfirmPassword = !showConfirmPassword"
              :aria-label="showConfirmPassword ? '隐藏密码' : '显示密码'"
            >
              {{ showConfirmPassword ? '🙈' : '👁️' }}
            </button>
          </div>
        </div>

        <div class="actions">
            <button
              type="submit"
              :disabled="loading"
              class="submit-btn"
            >
              <span v-if="loading">注册中...</span>
              <span v-else>立即注册</span>
            </button>
        </div>

        <div v-if="error" class="alert error">
          {{ error }}
        </div>

        <div v-if="success" class="alert success">
          {{ success }}
        </div>
      </form>

      <div class="footer-link">
        <p>已有账户？<router-link to="/login">立即登录</router-link></p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 样式保持不变 */
.auth-page {
  display: flex;
  justify-content: center;
  align-items: flex-start;
  min-height: auto;
  background: transparent;
  padding: 1rem 0.5rem;
}

.card {
  width: 100%;
  max-width: 420px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
  padding: 1.5rem;
  margin: 1rem 0;
}

h1 {
  margin: 0 0 0.5rem 0;
  font-size: 1.75rem;
  font-weight: 600;
  color: #1a1f2d;
  text-align: center;
}

.subtitle {
  margin: 0 0 2rem 0;
  color: #6b7280;
  text-align: center;
  font-size: 0.9rem;
}

.form-group {
  margin-bottom: 1.5rem;
}

label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
  font-size: 0.9rem;
}

input {
  width: 100%;
  padding: 0.75rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 1rem;
  transition: border-color 0.2s, box-shadow 0.2s;
  box-sizing: border-box;
}

input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

input:disabled {
  background-color: #f3f4f6;
  cursor: not-allowed;
}

.password-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.toggle-password {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  font-size: 1.1rem;
  color: #6b7280;
}

.toggle-password:hover {
  color: #374151;
}

.password-hint {
  margin-top: 0.5rem;
  font-size: 0.8rem;
  color: #9ca3af;
}

.actions {
  margin: 1.5rem 0;
}

.submit-btn {
  width: 100%;
  padding: 0.875rem;
  background-color: #3b82f6;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.2s;
}

.submit-btn:hover:not(:disabled) {
  background-color: #2563eb;
}

.submit-btn:disabled {
  background-color: #9ca3af;
  cursor: not-allowed;
}



.alert {
  padding: 0.75rem;
  border-radius: 6px;
  margin-top: 1rem;
  font-size: 0.9rem;
}

.alert.error {
  background-color: #fee2e2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.alert.success {
  background-color: #d1fae5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.footer-link {
  text-align: center;
  margin-top: 1.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid #e5e7eb;
}

.footer-link a {
  color: #3b82f6;
  text-decoration: none;
  font-weight: 500;
}

.footer-link a:hover {
  text-decoration: underline;
}

@media (max-width: 480px) {
  .auth-page {
    padding: 0.5rem;
  }

  .card {
    padding: 1.5rem;
  }
}
</style>
