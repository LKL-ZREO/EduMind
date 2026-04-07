<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'

interface FormData {
  username: string
  password: string
}

const form = reactive<FormData>({
  username: '',
  password: ''
})

const error = ref<string | null>(null)
const loading = ref(false)
const showPassword = ref(false)

const router = useRouter()
const route = useRoute()

const validateForm = (): string | null => {
  if (!form.username.trim()) {
    return '请输入用户名'
  }
  if (!form.password) {
    return '请输入密码'
  }
  if (form.password.length < 6) {
    return '密码至少6位'
  }
  return null
}

async function submit() {
  const validationError = validateForm()
  if (validationError) {
    error.value = validationError
    return
  }

  error.value = null
  loading.value = true

  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        username: form.username,
        password: form.password
      })
    })

    const result = await response.json()

    if (!response.ok || result.code !== 200) {
      throw new Error(result.message || result.msg || '登录失败，用户名或密码错误')
    }

    // 存 token 和 user
    const { id, username, email, token } = result.data

    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify({
      id: String(id),
      username,
      email
    }))

    alert('登录成功')

    // 跳转
    const redirect = route.query.redirect as string
    router.push(redirect || '/page1')

  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="card">
      <div class="logo-section">
        <div class="logo">🔒</div>
        <h1>欢迎回来</h1>
        <p class="subtitle">请登录您的账户</p>
      </div>

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
          <label for="password">密码</label>
          <div class="password-input-wrapper">
            <input
              id="password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="输入密码"
              :disabled="loading"
            />
            <button
              type="button"
              class="toggle-password"
              @click="showPassword = !showPassword"
            >
              {{ showPassword ? '🙈' : '👁️' }}
            </button>
          </div>
        </div>

        <div class="actions">
          <button
            type="submit"
            :disabled="loading"
            class="submit-btn"
          >
            <span v-if="loading">登录中...</span>
            <span v-else>登录</span>
          </button>
        </div>

        <div v-if="error" class="alert error">
          {{ error }}
        </div>
      </form>

      <div class="footer-link">
        <p>还没有账户？<router-link to="/register">立即注册</router-link></p>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
  position: relative;
}

.card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
}

.logo-section {
  text-align: center;
  margin-bottom: 2rem;
}

.logo {
  font-size: 3rem;
  margin-bottom: 1rem;
}

h1 {
  margin: 0 0 0.5rem 0;
  font-size: 1.75rem;
  font-weight: 700;
  color: #1a1f2d;
}

.subtitle {
  margin: 0;
  color: #6b7280;
  font-size: 0.95rem;
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
  padding: 0.875rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 1rem;
  transition: all 0.2s;
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
  font-size: 1.2rem;
  color: #6b7280;
  transition: color 0.2s;
}

.toggle-password:hover {
  color: #374151;
}

.actions {
  margin: 1.5rem 0;
}

.submit-btn {
  width: 100%;
  padding: 0.875rem;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 0.5rem;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.submit-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.alert {
  padding: 0.75rem;
  border-radius: 6px;
  margin-top: 1rem;
  font-size: 0.9rem;
  text-align: center;
}

.alert.error {
  background-color: #fee2e2;
  color: #dc2626;
  border: 1px solid #fecaca;
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
