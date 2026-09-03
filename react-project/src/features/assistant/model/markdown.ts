import hljs from 'highlight.js/lib/core'
import cLanguage from 'highlight.js/lib/languages/c'
import cppLanguage from 'highlight.js/lib/languages/cpp'
import javaLanguage from 'highlight.js/lib/languages/java'
import javascriptLanguage from 'highlight.js/lib/languages/javascript'
import jsonLanguage from 'highlight.js/lib/languages/json'
import pythonLanguage from 'highlight.js/lib/languages/python'
import typescriptLanguage from 'highlight.js/lib/languages/typescript'
import 'highlight.js/styles/github-dark.css'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import { renderMarkdown } from '@/shared/utils/safeHtml'

hljs.registerLanguage('c', cLanguage)
hljs.registerLanguage('cpp', cppLanguage)
hljs.registerLanguage('java', javaLanguage)
hljs.registerLanguage('javascript', javascriptLanguage)
hljs.registerLanguage('json', jsonLanguage)
hljs.registerLanguage('python', pythonLanguage)
hljs.registerLanguage('typescript', typescriptLanguage)

marked.use(
  markedHighlight({
    langPrefix: 'hljs language-',
    highlight(code, language) {
      return language && hljs.getLanguage(language)
        ? hljs.highlight(code, { language }).value
        : hljs.highlightAuto(code).value
    },
  }),
)
marked.setOptions({ breaks: true, gfm: true })

export function renderAssistantMarkdown(content: string) {
  return renderMarkdown(content)
}
