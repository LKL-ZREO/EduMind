import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  test: {
    // jsdom 环境模拟浏览器 DOM
    environment: 'jsdom',
    // 测试文件匹配模式
    include: ['src/**/*.{test,spec}.{ts,js}'],
    // 排除 e2e 测试目录
    exclude: ['node_modules', 'e2e'],
    // 全局 setup
    setupFiles: ['./src/test/setup.ts'],
    // 覆盖率配置
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      include: ['src/**/*.{ts,vue}'],
      exclude: ['src/main.ts', 'src/test/**'],
    },
    // CSS 不阻塞测试
    css: false,
    // 测试超时
    testTimeout: 10000,
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
