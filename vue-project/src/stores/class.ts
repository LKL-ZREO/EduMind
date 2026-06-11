import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import request from '@/api/request'

interface ClassInfo {
  id: number
  name: string
}

export const useClassStore = defineStore('class', () => {
  // ========== 客户端状态 ==========
  const currentClassId = ref<number | null>(null)

  // ========== 服务端缓存 ==========
  const classList = ref<ClassInfo[]>([])
  const lastFetchTime = ref(0)
  const CACHE_TTL = 5 * 60 * 1000 // 5 分钟

  // ========== 派生状态 ==========
  const currentClass = computed(() =>
    classList.value.find(c => c.id === currentClassId.value) ?? null
  )
  const currentClassName = computed(() => currentClass.value?.name ?? '')

  // ========== Action ==========
  async function fetchClassList() {
    if (!isExpired() && classList.value.length > 0) {
      return classList.value
    }
    try {
      const res = await request.get('/dashboard/classes')
      classList.value = res?.data?.data ?? []
      lastFetchTime.value = Date.now()
      // 自动选中第一个
      if (!currentClassId.value && classList.value.length > 0) {
        currentClassId.value = classList.value[0].id
      }
      return classList.value
    } catch (e) {
      console.error('加载班级列表失败', e)
      return classList.value
    }
  }

  function isExpired(): boolean {
    return Date.now() - lastFetchTime.value > CACHE_TTL
  }

  /** 新建班级或数据变更后清除缓存 */
  function invalidate() {
    lastFetchTime.value = 0
    classList.value = []
  }

  return {
    currentClassId,
    classList,
    currentClass,
    currentClassName,
    fetchClassList,
    invalidate,
  }
})
