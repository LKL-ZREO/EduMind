import { describe, expect, it } from 'vitest'
import { safeRedirectPath } from './safeRedirect'

describe('safeRedirectPath', () => {
  it('keeps an internal path with query and hash', () => {
    expect(safeRedirectPath('/teacher/classes?tab=active#students', '/teacher/chat')).toBe(
      '/teacher/classes?tab=active#students',
    )
  })

  it('rejects absolute and protocol-relative destinations', () => {
    expect(safeRedirectPath('https://evil.example/path', '/teacher/chat')).toBe('/teacher/chat')
    expect(safeRedirectPath('//evil.example/path', '/teacher/chat')).toBe('/teacher/chat')
  })

  it('uses the fallback when no redirect is supplied', () => {
    expect(safeRedirectPath(null, '/teacher/chat')).toBe('/teacher/chat')
  })
})
