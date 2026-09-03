import { describe, expect, it } from 'vitest'
import { renderAssistantMarkdown } from './markdown'

describe('assistant Markdown rendering', () => {
  it('highlights code while sanitizing untrusted HTML', () => {
    const html = renderAssistantMarkdown(
      '```typescript\nconst answer: number = 42\n```\n<img src=x onerror="alert(1)"><script>alert(1)</script>',
    )

    expect(html).toContain('hljs')
    expect(html).toContain('answer')
    expect(html).not.toContain('onerror')
    expect(html).not.toContain('<script')
  })
})
