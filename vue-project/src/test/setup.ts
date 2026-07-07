/**
 * Vitest 全局 setup — 在每次测试运行前执行。
 * Mock 浏览器 API 和第三方 UI 库。
 */
import { config } from '@vue/test-utils'
import { vi } from 'vitest'

// ==================== Mock Element Plus ====================
// 避免组件测试中 Element Plus 全局注册报错
config.global.stubs = {
  // 按需 stub Element Plus 组件
}

// ==================== Mock 浏览器 API ====================
// jsdom 未实现的 API
if (typeof window !== 'undefined') {
  // IntersectionObserver
  if (!window.IntersectionObserver) {
    window.IntersectionObserver = vi.fn().mockImplementation(() => ({
      observe: vi.fn(),
      unobserve: vi.fn(),
      disconnect: vi.fn(),
    })) as unknown as typeof IntersectionObserver
  }

  // matchMedia (Element Plus responsive)
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })

  // scrollTo (Element Plus 内部调用)
  window.scrollTo = vi.fn() as unknown as typeof window.scrollTo

  // ResizeObserver
  if (!window.ResizeObserver) {
    window.ResizeObserver = vi.fn().mockImplementation(() => ({
      observe: vi.fn(),
      unobserve: vi.fn(),
      disconnect: vi.fn(),
    })) as unknown as typeof ResizeObserver
  }
}

// ==================== Mock Axios ====================
// 避免测试中真实发起网络请求
vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>()
  return {
    ...actual,
    default: {
      ...actual.default,
      create: vi.fn(() => ({
        get: vi.fn().mockResolvedValue({ data: {} }),
        post: vi.fn().mockResolvedValue({ data: {} }),
        put: vi.fn().mockResolvedValue({ data: {} }),
        delete: vi.fn().mockResolvedValue({ data: {} }),
        interceptors: {
          request: { use: vi.fn(), eject: vi.fn() },
          response: { use: vi.fn(), eject: vi.fn() },
        },
      })),
    },
  }
})
