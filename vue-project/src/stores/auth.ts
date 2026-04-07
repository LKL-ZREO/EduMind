import { ref } from 'vue'
import { defineStore } from 'pinia'
import { useRouter } from 'vue-router'

type User = {
  username: string
  email: string
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)

  // load persisted user
  try {
    const raw = localStorage.getItem('auth:user')
    if (raw) user.value = JSON.parse(raw) as User
  } catch (err) {
    // ignore malformed data
    localStorage.removeItem('auth:user')
  }

  function persist() {
    if (user.value) localStorage.setItem('auth:user', JSON.stringify(user.value))
    else localStorage.removeItem('auth:user')
  }

  async function login(payload: { email: string; password: string }) {
    if (!payload.email || !payload.password) throw new Error('请输入邮箱和密码')
    const email = String(payload.email)
    const username = (email.split('@')[0] || '')
    user.value = { username, email }
    persist()
  }

  async function register(payload: { username?: string; email: string; password: string }) {
    if (!payload.email || !payload.password) throw new Error('请输入邮箱和密码')
    if (payload.password.length < 6) throw new Error('密码至少 6 位')
    const email = String(payload.email)
    const username = payload.username ? String(payload.username) : (email.split('@')[0] || '')
    user.value = { username, email }
    persist()
  }

  function logout() {
    user.value = null
    persist()
    try {
      const router = useRouter()
      router.push('/')
    } catch (e) {
      // ignore if called outside setup
    }
  }

  return { user, login, register, logout }
})
