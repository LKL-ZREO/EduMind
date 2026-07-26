<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

interface FormData {
  username: string
  password: string
}

const form = reactive<FormData>({
  username: '',
  password: '',
})

const error = ref<string | null>(null)
const loading = ref(false)
const showPassword = ref(false)

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const validateForm = (): string | null => {
  if (!form.username.trim()) return '请输入用户名'
  if (!form.password) return '请输入密码'
  if (form.password.length < 6) return '密码至少6位'
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
    await authStore.login({ username: form.username, password: form.password })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect as string
    router.push(redirect || '/teacher/chat')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-shell">
    <section class="briefing-panel" aria-label="产品概览">
      <div class="brand-row">
        <span class="brand-mark">EM</span>
        <span class="brand-name">EduMind</span>
      </div>

      <div class="briefing-copy">
        <p class="eyebrow">Teacher Workspace</p>
        <h1>把课堂、作业和知识库收束到一张清晰的教学桌面。</h1>
        <p class="summary">
          面向真实教学流程设计：先看班级状态，再处理材料、互动和批改结果。
          少一点噱头，多一点老师每天会用到的确定感。
        </p>
      </div>

      <div class="desk-card">
        <div class="desk-card-header">
          <span>今日备课便签</span>
          <strong>08:40</strong>
        </div>
        <div class="note-lines">
          <span class="line wide"></span>
          <span class="line"></span>
          <span class="line short"></span>
        </div>
        <div class="metric-strip">
          <div>
            <b>3</b>
            <span>待处理作业</span>
          </div>
          <div>
            <b>12</b>
            <span>课堂困惑点</span>
          </div>
          <div>
            <b>86%</b>
            <span>互动参与率</span>
          </div>
        </div>
      </div>

      <div class="principles">
        <span>知识库优先</span>
        <span>证据化反馈</span>
        <span>课堂节奏可见</span>
      </div>
    </section>

    <section class="login-panel" aria-label="教师登录">
      <div class="login-card">
        <div class="form-heading">
          <p class="kicker">教师入口</p>
          <h2>欢迎回来</h2>
          <p>登录后继续管理课程、知识库和课堂互动。</p>
        </div>

        <form class="form" @submit.prevent="submit">
          <label class="field" for="username">
            <span>用户名</span>
            <input
              id="username"
              v-model="form.username"
              type="text"
              placeholder="例如：li_teacher"
              autocomplete="username"
              :disabled="loading"
            />
          </label>

          <label class="field" for="password">
            <span>密码</span>
            <div class="password-input-wrapper">
              <input
                id="password"
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="输入登录密码"
                autocomplete="current-password"
                :disabled="loading"
              />
              <button
                type="button"
                class="toggle-password"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                {{ showPassword ? '隐藏' : '显示' }}
              </button>
            </div>
          </label>

          <p v-if="error" class="alert error" role="alert">
            {{ error }}
          </p>

          <button type="submit" :disabled="loading" class="submit-btn">
            <span>{{ loading ? '正在进入工作台...' : '进入教学工作台' }}</span>
          </button>
        </form>

        <div class="footer-actions">
          <router-link to="/register">创建教师账号</router-link>
          <router-link to="/" class="muted-link">返回学生提交页</router-link>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-shell {
  --ink: #17231f;
  --muted: #66736d;
  --paper: #fbf6ec;
  --paper-deep: #efe3d0;
  --moss: #365b49;
  --moss-dark: #213c31;
  --chalk: #edf3ee;
  --line: rgba(54, 91, 73, 0.18);

  position: relative;
  min-height: calc(100vh - 72px);
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(360px, 0.95fr);
  gap: clamp(28px, 5vw, 80px);
  padding: clamp(28px, 6vw, 76px);
  overflow: hidden;
  color: var(--ink);
  background:
    radial-gradient(circle at 16% 22%, rgba(151, 118, 72, 0.15), transparent 28%),
    linear-gradient(135deg, #f9f1e4 0%, #f4eadb 48%, #e8eee7 100%);
}

.login-shell::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(54, 91, 73, 0.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(54, 91, 73, 0.045) 1px, transparent 1px);
  background-size: 34px 34px;
  mask-image: linear-gradient(90deg, black, transparent 82%);
}

.briefing-panel,
.login-panel {
  position: relative;
  z-index: 1;
}

.briefing-panel {
  display: flex;
  min-height: 620px;
  flex-direction: column;
  justify-content: space-between;
}

.brand-row {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  width: fit-content;
  padding: 8px 12px 8px 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.36);
  backdrop-filter: blur(12px);
}

.brand-mark {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 50%;
  background: var(--moss-dark);
  color: #f7efe2;
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 0.72rem;
  letter-spacing: 0.08em;
}

.brand-name {
  color: var(--moss-dark);
  font-weight: 700;
  letter-spacing: 0.08em;
}

.briefing-copy {
  max-width: 720px;
  padding: 64px 0 36px;
}

.eyebrow,
.kicker {
  margin: 0 0 14px;
  color: var(--moss);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.briefing-copy h1 {
  max-width: 780px;
  margin: 0;
  color: var(--ink);
  font-family: Georgia, 'Noto Serif SC', 'Songti SC', serif;
  font-size: clamp(2.6rem, 5.8vw, 5.5rem);
  font-weight: 700;
  letter-spacing: -0.07em;
  line-height: 0.98;
}

.summary {
  max-width: 570px;
  margin: 24px 0 0;
  color: var(--muted);
  font-size: 1.02rem;
  line-height: 1.9;
}

.desk-card {
  width: min(560px, 100%);
  padding: 24px;
  border: 1px solid rgba(33, 60, 49, 0.16);
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(255, 255, 255, 0.36)), var(--paper);
  box-shadow: 0 24px 70px rgba(55, 41, 24, 0.14);
  transform: rotate(-1.5deg);
  animation: riseIn 620ms ease-out both;
}

.desk-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--moss-dark);
  font-size: 0.9rem;
  font-weight: 700;
}

.desk-card-header strong {
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 1.4rem;
  font-weight: 700;
}

.note-lines {
  display: grid;
  gap: 10px;
  margin: 26px 0;
}

.line {
  display: block;
  width: 78%;
  height: 10px;
  border-radius: 999px;
  background: rgba(54, 91, 73, 0.13);
}

.line.wide {
  width: 100%;
}

.line.short {
  width: 46%;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.metric-strip div {
  padding: 16px 14px;
  border: 1px solid rgba(54, 91, 73, 0.12);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.48);
}

.metric-strip b {
  display: block;
  color: var(--moss-dark);
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 1.65rem;
  line-height: 1;
}

.metric-strip span {
  display: block;
  margin-top: 8px;
  color: var(--muted);
  font-size: 0.78rem;
}

.principles {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.principles span {
  padding: 9px 12px;
  border: 1px solid rgba(33, 60, 49, 0.18);
  border-radius: 999px;
  color: var(--moss-dark);
  background: rgba(251, 246, 236, 0.58);
  font-size: 0.82rem;
  font-weight: 700;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: min(450px, 100%);
  padding: clamp(28px, 4vw, 42px);
  border: 1px solid rgba(33, 60, 49, 0.16);
  border-radius: 34px;
  background: rgba(255, 252, 246, 0.88);
  box-shadow: 0 30px 90px rgba(33, 60, 49, 0.16);
  backdrop-filter: blur(18px);
  animation: riseIn 520ms ease-out both;
}

.form-heading h2 {
  margin: 0;
  color: var(--ink);
  font-family: Georgia, 'Noto Serif SC', 'Songti SC', serif;
  font-size: 2.2rem;
  font-weight: 700;
  letter-spacing: -0.04em;
}

.form-heading p:last-child {
  margin: 10px 0 0;
  color: var(--muted);
  line-height: 1.8;
}

.form {
  display: grid;
  gap: 18px;
  margin-top: 34px;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: var(--moss-dark);
  font-size: 0.88rem;
  font-weight: 700;
}

input {
  width: 100%;
  height: 52px;
  padding: 0 15px;
  border: 1px solid rgba(33, 60, 49, 0.22);
  border-radius: 16px;
  color: var(--ink);
  background: rgba(255, 255, 255, 0.66);
  font: inherit;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    background 160ms ease;
}

input::placeholder {
  color: #9aa59e;
}

input:focus {
  outline: none;
  border-color: rgba(54, 91, 73, 0.72);
  background: #fffdf8;
  box-shadow: 0 0 0 4px rgba(54, 91, 73, 0.1);
}

input:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.password-input-wrapper {
  position: relative;
}

.password-input-wrapper input {
  padding-right: 72px;
}

.toggle-password {
  position: absolute;
  top: 50%;
  right: 8px;
  min-width: 54px;
  height: 36px;
  border: 0;
  border-radius: 12px;
  color: var(--moss);
  background: rgba(54, 91, 73, 0.1);
  font-size: 0.82rem;
  font-weight: 800;
  cursor: pointer;
  transform: translateY(-50%);
}

.submit-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 54px;
  margin-top: 4px;
  border: 0;
  border-radius: 18px;
  color: #fffaf0;
  background: linear-gradient(135deg, var(--moss-dark), #476f58);
  box-shadow: 0 16px 34px rgba(33, 60, 49, 0.24);
  font: inherit;
  font-weight: 800;
  cursor: pointer;
  transition:
    transform 160ms ease,
    box-shadow 160ms ease,
    opacity 160ms ease;
}

.submit-btn:hover:not(:disabled) {
  box-shadow: 0 18px 40px rgba(33, 60, 49, 0.3);
  transform: translateY(-1px);
}

.submit-btn:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}

.alert {
  margin: 0;
  padding: 12px 14px;
  border-radius: 14px;
  font-size: 0.9rem;
}

.alert.error {
  border: 1px solid rgba(157, 56, 45, 0.22);
  color: #8e2f26;
  background: #fae8e4;
}

.footer-actions {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 26px;
  padding-top: 22px;
  border-top: 1px solid rgba(33, 60, 49, 0.12);
  font-size: 0.9rem;
}

.footer-actions a {
  color: var(--moss-dark);
  font-weight: 800;
  text-decoration: none;
}

.footer-actions .muted-link {
  color: var(--muted);
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
  }
}

@media (max-width: 980px) {
  .login-shell {
    grid-template-columns: 1fr;
    padding: 24px;
  }

  .briefing-panel {
    min-height: auto;
    gap: 28px;
  }

  .briefing-copy {
    padding: 28px 0 0;
  }

  .desk-card {
    transform: none;
  }
}

@media (max-width: 560px) {
  .login-shell {
    min-height: calc(100vh - 56px);
    padding: 18px;
  }

  .briefing-copy h1 {
    font-size: 2.35rem;
  }

  .metric-strip {
    grid-template-columns: 1fr;
  }

  .login-card {
    border-radius: 26px;
  }

  .footer-actions {
    flex-direction: column;
  }
}
</style>
