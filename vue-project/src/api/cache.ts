/** API 调用缓存工具 */
const cache = new Map<string, { data: any; time: number }>()

const TTL: Record<string, number> = {
  health: 180_000, // 3 分钟
}

export function getCached(key: string): any | null {
  const entry = cache.get(key)
  if (!entry) return null
  if (Date.now() - entry.time > (TTL[key] ?? 0)) {
    cache.delete(key)
    return null
  }
  return entry.data
}

export function setCache(key: string, data: any) {
  cache.set(key, { data, time: Date.now() })
}
