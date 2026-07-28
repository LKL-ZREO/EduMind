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

/** Sanitize untrusted HTML before passing it to Vue's v-html. */
export function sanitizeHtml(input: string | null | undefined): string {
  if (!input) return ''
  return DOMPurify.sanitize(input, {
    USE_PROFILES: { html: true },
    FORBID_TAGS: FORBIDDEN_TAGS,
    FORBID_ATTR: ['style', 'srcset'],
  })
}

/**
 * Sanitize HTML after trusted KaTeX rendering. Raw rich text must be passed
 * through sanitizeHtml before KaTeX adds its MathML and inline layout styles.
 */
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
  const html = marked.parse(input, { async: false }) as string
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
