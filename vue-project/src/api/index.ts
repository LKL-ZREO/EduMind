// src/api/dataService.ts
import axios from 'axios'

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'https://api.example.com',
  timeout: 10000
})


export interface PasswordItem {
  name: string
  code: string
}

export interface ProfitItem {
  station: string
  name: string
  total: string
  hour: string
}



// 获取每日密码
export const getDailyPasswords = async () => {
  try {
    const response = await apiClient.get('/api/daily-passwords')
    return response.data
  } catch (error) {
    console.error('获取每日密码失败:', error)
    throw error
  }
}

// 获取制造台利润
export const getProductionProfits = async () => {
  try {
    const response = await apiClient.get('/api/production-profits')
    return response.data
  } catch (error) {
    console.error('获取利润数据失败:', error)
    throw error
  }
}
