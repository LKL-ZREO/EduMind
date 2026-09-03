import DOMPurify from 'dompurify'
import { marked } from 'marked'

const FORBIDDEN_TAGS = [
  'style',
  'form',
  'input',
  'button',
  'textarea',
  'select',
  'option',
  'iframe',
  'object',
  'embed',
]

export function sanitizeHtml(input: string | null | undefined): string {
  if (!input) return ''
  return DOMPurify.sanitize(input, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: FORBIDDEN_TAGS,
    FORBID_ATTR: ['style', 'srcset'],
  })
}

export function sanitizeRenderedMathHtml(input: string | null | undefined): string {
  if (!input) return ''
  return DOMPurify.sanitize(input, {
    USE_PROFILES: { html: true, mathMl: true },
    FORBID_TAGS: FORBIDDEN_TAGS,
    FORBID_ATTR: ['srcset'],
  })
}

export function renderMarkdown(input: string | null | undefined): string {
  if (!input) return ''
  const html = marked.parse(input, { async: false })
  return sanitizeHtml(html)
}

export function renderTextWithBreaks(input: string | null | undefined): string {
  if (!input) return ''
  return escapeHtml(input).replace(/\r?\n/g, '<br>')
}

function escapeHtml(input: string): string {
  return input
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
