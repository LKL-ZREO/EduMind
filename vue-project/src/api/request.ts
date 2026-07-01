import axios from 'axios'
import { getCached, setCache } from './cache'

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器 - 自动添加 token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器 - 处理 401
request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 未登录，清除 token 并跳转到登录页
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

/** 健康检查（带 3 分钟缓存） */
export async function checkHealth() {
  const cached = getCached('health')
  if (cached) return cached

  const res = await request.get('/chat/health')
  const status = res.data.status
  setCache('health', status)
  return status
}

export default request
