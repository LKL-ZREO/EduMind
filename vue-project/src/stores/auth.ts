import { ref } from 'vue'
import { defineStore } from 'pinia'
import request from '@/api/request'

type User = {
  id: number
  username: string
  email: string
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))

  // load persisted user
  try {
    const raw = localStorage.getItem('auth:user')
    if (raw) user.value = JSON.parse(raw) as User
  } catch {
    localStorage.removeItem('auth:user')
  }

  function persistUser() {
    if (user.value) localStorage.setItem('auth:user', JSON.stringify(user.value))
    else localStorage.removeItem('auth:user')
  }

  function persistToken(value: string | null) {
    token.value = value
    if (value) localStorage.setItem('token', value)
    else localStorage.removeItem('token')
  }

  async function login(payload: { username: string; password: string }) {
    const res = await request.post('/auth/login', payload)
    const data = res.data
    if (data.code !== 200) throw new Error(data.message || '登录失败')

    const { id, username, email, token: jwt, sessionId } = data.data
    user.value = { id, username, email }
    persistUser()
    persistToken(jwt)
    if (sessionId) localStorage.setItem('sessionId', sessionId)
  }

  async function register(payload: { username: string; email: string; password: string }) {
    const res = await request.post('/auth/register', payload)
    const data = res.data
    if (data.code !== 200) throw new Error(data.message || '注册失败')
  }

  function logout() {
    user.value = null
    persistUser()
    persistToken(null)
    localStorage.removeItem('sessionId')
  }

  return { user, token, login, register, logout }
})
