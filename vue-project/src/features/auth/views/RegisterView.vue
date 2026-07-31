<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth/store'

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
  confirmPassword: '',
})

const error = ref<string | null>(null)
const success = ref<string | null>(null)
const loading = ref(false)
const showPassword = ref(false)
const showConfirmPassword = ref(false)

const router = useRouter()
const authStore = useAuthStore()

const validateForm = (): string | null => {
  if (!form.username.trim()) return '请输入用户名'
  if (form.username.length < 3) return '用户名至少需要3个字符'
  if (!form.email.trim()) return '请输入邮箱地址'
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRegex.test(form.email)) return '请输入有效的邮箱地址'
  if (!form.password) return '请输入密码'
  if (form.password.length < 6) return '密码至少需要6个字符'
  if (form.password !== form.confirmPassword) return '两次输入的密码不一致'
  return null
}

async function submit() {
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
    await authStore.register({
      username: form.username,
      email: form.email,
      password: form.password,
    })
    success.value = '注册成功！正在跳转...'
    setTimeout(() => router.push('/login'), 1500)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '注册失败，请稍后重试'
    success.value = null
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="register-shell">
    <section class="orientation-panel" aria-label="EduMind onboarding">
      <router-link to="/" class="return-link">返回学生提交台</router-link>

      <div class="brand-lockup">
        <span class="brand-mark">EM</span>
        <div>
          <p class="eyebrow">Teacher Onboarding</p>
          <h1>给新老师开一张干净的教学桌面。</h1>
        </div>
      </div>

      <p class="panel-copy">
        注册后可以管理班级、布置作业、接入知识库，并把 AI 助手放在清晰可控的教学流程里。
      </p>

      <div class="onboarding-board">
        <div class="board-row">
          <span>01</span>
          <strong>建立教师身份</strong>
          <small>用户名、邮箱和密码用于进入工作台。</small>
        </div>
        <div class="board-row">
          <span>02</span>
          <strong>创建班级空间</strong>
          <small>后续可以发放班级加入码和作业任务。</small>
        </div>
        <div class="board-row">
          <span>03</span>
          <strong>接入知识库</strong>
          <small>让课程资料成为 AI 可检索的上下文。</small>
        </div>
      </div>
    </section>

    <section class="register-card" aria-label="注册表单">
      <p class="card-kicker">创建账户</p>
      <h2>开始搭建你的教学工作流</h2>
      <p class="subtitle">信息尽量少，但每一项都明确有用。</p>

      <form @submit.prevent="submit" class="form">
        <label class="form-group" for="username">
          <span>用户名</span>
          <input
            id="username"
            v-model="form.username"
            type="text"
            placeholder="例如：wang-laoshi"
            :disabled="loading"
          />
        </label>

        <label class="form-group" for="email">
          <span>邮箱</span>
          <input
            id="email"
            v-model="form.email"
            type="email"
            placeholder="teacher@example.com"
            :disabled="loading"
          />
        </label>

        <label class="form-group" for="password">
          <span>密码</span>
          <div class="password-input-wrapper">
            <input
              id="password"
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              placeholder="至少 6 位"
              :disabled="loading"
            />
            <button type="button" class="toggle-password" @click="showPassword = !showPassword">
              {{ showPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </label>

        <label class="form-group" for="confirmPassword">
          <span>确认密码</span>
          <div class="password-input-wrapper">
            <input
              id="confirmPassword"
              v-model="form.confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              placeholder="再输入一次密码"
              :disabled="loading"
            />
            <button
              type="button"
              class="toggle-password"
              @click="showConfirmPassword = !showConfirmPassword"
            >
              {{ showConfirmPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </label>

        <div v-if="error" class="alert error">{{ error }}</div>
        <div v-if="success" class="alert success">{{ success }}</div>

        <button type="submit" :disabled="loading" class="submit-btn">
          <span v-if="loading">正在创建账户...</span>
          <span v-else>创建教师账户</span>
        </button>
      </form>

      <p class="footer-link">
        已有账户？
        <router-link to="/login">去登录</router-link>
      </p>
    </section>
  </main>
</template>

<style scoped>
.register-shell {
  --ink: #243126;
  --muted: #6f796d;
  --paper: #f5efe2;
  --paper-deep: #e8dcc6;
  --moss: #3f5d46;
  --moss-dark: #273b2c;
  --line: rgba(63, 93, 70, 0.18);
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(420px, 0.95fr);
  align-items: center;
  gap: clamp(2rem, 5vw, 5rem);
  padding: clamp(1.5rem, 4vw, 4.5rem);
  color: var(--ink);
  background:
    radial-gradient(circle at 12% 10%, rgba(195, 151, 84, 0.2), transparent 30%),
    linear-gradient(135deg, rgba(39, 59, 44, 0.08) 0 1px, transparent 1px 24px), var(--paper);
}

.orientation-panel {
  min-height: min(760px, 86vh);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: clamp(1.4rem, 3vw, 2.4rem);
  border: 1px solid var(--line);
  border-radius: 34px;
  background:
    linear-gradient(145deg, rgba(255, 252, 244, 0.86), rgba(224, 213, 193, 0.72)),
    repeating-linear-gradient(0deg, rgba(63, 93, 70, 0.05) 0 1px, transparent 1px 34px);
  box-shadow: 0 28px 80px rgba(48, 43, 33, 0.13);
}

.return-link {
  width: fit-content;
  color: var(--moss-dark);
  text-decoration: none;
  font-weight: 700;
  letter-spacing: 0.04em;
  border-bottom: 1px solid rgba(39, 59, 44, 0.38);
}

.brand-lockup {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 1.1rem;
  align-items: start;
  margin-top: 7vh;
}

.brand-mark {
  display: grid;
  width: 4.8rem;
  height: 4.8rem;
  place-items: center;
  border: 1px solid var(--moss-dark);
  border-radius: 24px 24px 10px 24px;
  background: #fdf9ee;
  color: var(--moss-dark);
  font-weight: 900;
  letter-spacing: -0.08em;
}

.eyebrow,
.card-kicker {
  margin: 0 0 0.8rem;
  color: var(--moss);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.orientation-panel h1,
.register-card h2 {
  margin: 0;
  font-family: Georgia, 'Times New Roman', 'Noto Serif SC', serif;
  font-weight: 700;
  letter-spacing: -0.045em;
}

.orientation-panel h1 {
  max-width: 720px;
  font-size: clamp(3rem, 7vw, 6.4rem);
  line-height: 0.94;
}

.panel-copy {
  max-width: 620px;
  margin: 2rem 0 0;
  color: var(--muted);
  font-size: clamp(1rem, 1.7vw, 1.2rem);
  line-height: 1.85;
}

.onboarding-board {
  display: grid;
  gap: 0.8rem;
  margin-top: 2.5rem;
}

.board-row {
  display: grid;
  grid-template-columns: 3.4rem minmax(0, 0.72fr) minmax(0, 1fr);
  gap: 1rem;
  align-items: center;
  padding: 1rem;
  border: 1px solid rgba(63, 93, 70, 0.16);
  border-radius: 20px;
  background: rgba(255, 252, 244, 0.68);
}

.board-row span {
  color: #9b7540;
  font-weight: 900;
}

.board-row strong {
  font-size: 1rem;
}

.board-row small {
  color: var(--muted);
  line-height: 1.6;
}

.register-card {
  width: 100%;
  max-width: 500px;
  justify-self: center;
  padding: clamp(1.5rem, 4vw, 2.5rem);
  border: 1px solid rgba(63, 93, 70, 0.18);
  border-radius: 30px;
  background: rgba(255, 252, 244, 0.94);
  box-shadow: 0 24px 60px rgba(48, 43, 33, 0.14);
}

.register-card h2 {
  font-size: clamp(2rem, 4vw, 3.4rem);
  line-height: 1.02;
}

.subtitle {
  color: var(--muted);
  margin: 0.8rem 0 1.5rem;
  line-height: 1.7;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.form-group span {
  font-weight: 800;
  color: var(--moss-dark);
  font-size: 0.85rem;
  letter-spacing: 0.04em;
}

.form-group input {
  width: 100%;
  padding: 0.95rem 1rem;
  border: 1px solid rgba(63, 93, 70, 0.2);
  border-radius: 16px;
  background: #fbf7ed;
  color: var(--ink);
  font-size: 1rem;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    background 0.2s;
}

.form-group input:focus {
  outline: none;
  border-color: var(--moss);
  background: #fffdf7;
  box-shadow: 0 0 0 4px rgba(63, 93, 70, 0.12);
}

.form-group input:disabled {
  background-color: #eee7d8;
  cursor: not-allowed;
}

.password-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.password-input-wrapper input {
  width: 100%;
  padding-right: 4.2rem;
}

.toggle-password {
  position: absolute;
  right: 0.85rem;
  background: none;
  border: none;
  color: var(--moss);
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 800;
}

.submit-btn {
  width: 100%;
  margin-top: 0.25rem;
  padding: 1rem 1.1rem;
  background: var(--moss-dark);
  color: white;
  border: none;
  border-radius: 18px;
  font-size: 1rem;
  font-weight: 900;
  letter-spacing: 0.04em;
  box-shadow: 0 16px 30px rgba(39, 59, 44, 0.22);
  transition:
    transform 0.18s,
    box-shadow 0.18s,
    background 0.18s;
}

.submit-btn:hover:not(:disabled) {
  background: #1e2f23;
  box-shadow: 0 20px 38px rgba(39, 59, 44, 0.26);
  transform: translateY(-1px);
}

.submit-btn:disabled {
  background: #aeb8a8;
  cursor: not-allowed;
  box-shadow: none;
}

.alert {
  padding: 0.8rem 1rem;
  border-radius: 14px;
  font-size: 0.85rem;
  line-height: 1.5;
}

.alert.error {
  background: #fff1ec;
  color: #9f3f29;
  border: 1px solid rgba(159, 63, 41, 0.2);
}

.alert.success {
  background: #edf7e8;
  color: #2f6738;
  border: 1px solid rgba(47, 103, 56, 0.18);
}

.footer-link {
  text-align: center;
  margin: 1.2rem 0 0;
  color: var(--muted);
  font-size: 0.9rem;
}

.footer-link a {
  color: var(--moss-dark);
  text-decoration: none;
  font-weight: 900;
}

@media (max-width: 960px) {
  .register-shell {
    grid-template-columns: 1fr;
    gap: 1.2rem;
  }

  .orientation-panel {
    min-height: auto;
  }

  .brand-lockup {
    margin-top: 2rem;
  }

  .board-row {
    grid-template-columns: 2.4rem 1fr;
  }

  .board-row small {
    grid-column: 2;
  }
}

@media (max-width: 560px) {
  .register-shell {
    padding: 1rem;
  }

  .orientation-panel,
  .register-card {
    border-radius: 24px;
  }

  .brand-lockup {
    grid-template-columns: 1fr;
  }

  .brand-mark {
    width: 4rem;
    height: 4rem;
  }
}
</style>
