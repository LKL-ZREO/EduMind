/** API 调用缓存工具 */
const cache = new Map<string, { data: unknown; time: number }>()

const TTL: Record<string, number> = {
  health: 180_000, // 3 分钟
}

export function getCached<T>(key: string): T | null {
  const entry = cache.get(key)
  if (!entry) return null
  if (Date.now() - entry.time > (TTL[key] ?? 0)) {
    cache.delete(key)
    return null
  }
  return entry.data as T
}

export function setCache<T>(key: string, data: T) {
  cache.set(key, { data, time: Date.now() })
}
