import { describe, expect, it } from 'vitest'
import {
  renderMarkdown,
  renderTextWithBreaks,
  sanitizeHtml,
  sanitizeRenderedMathHtml,
} from '@/utils/safeHtml'

describe('safeHtml', () => {
  it('removes executable elements and event handlers', () => {
    const html = sanitizeHtml(
      '<p onclick="alert(1)">safe</p><img src="x" onerror="alert(2)"><script>alert(3)</script>',
    )

    expect(html).toContain('<p>safe</p>')
    expect(html).not.toMatch(/onclick|onerror|script/i)
  })

  it('removes dangerous URL schemes and inline styles', () => {
    const html = sanitizeHtml(
      '<a href="javascript:alert(1)" style="position:fixed">open</a><iframe src="https://evil.test"></iframe>',
    )

    expect(html).toContain('<a>open</a>')
    expect(html).not.toMatch(/javascript:|style=|iframe/i)
  })

  it('sanitizes raw HTML embedded in Markdown', () => {
    const html = renderMarkdown('# Title\n\n<img src=x onerror=alert(1)> **safe**')

    expect(html).toContain('<h1>Title</h1>')
    expect(html).toContain('<strong>safe</strong>')
    expect(html).not.toContain('onerror')
  })

  it('renders plain text line breaks without interpreting HTML', () => {
    const html = renderTextWithBreaks('line 1\n<img src=x onerror=alert(1)>')

    expect(html).toBe('line 1<br>&lt;img src=x onerror=alert(1)&gt;')
  })

  it('preserves trusted KaTeX layout while still removing handlers', () => {
    const html = sanitizeRenderedMathHtml(
      '<span class="katex" style="height:1em" onclick="alert(1)"><math><mi>x</mi></math></span>',
    )

    expect(html).toContain('class="katex"')
    expect(html).toContain('style="height:1em"')
    expect(html).toContain('<math>')
    expect(html).not.toContain('onclick')
  })
})
