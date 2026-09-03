import { Node } from '@tiptap/core'
import katex from 'katex'

export const MathInlineNode = Node.create({
  name: 'mathInline',
  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,

  addAttributes() {
    return {
      latex: {
        default: 'x^2',
        parseHTML: (element: HTMLElement) => element.getAttribute('data-latex') || 'x^2',
        renderHTML: (attributes: Record<string, unknown>) => ({
          'data-latex': attributes.latex,
        }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'span.math-inline[data-latex]' }]
  },

  renderHTML({ node }) {
    return ['span', { class: 'math-inline', 'data-latex': String(node.attrs.latex) }]
  },

  addNodeView() {
    return ({ node, getPos, editor }) => {
      const element = document.createElement('span')
      element.className = 'math-inline math-rendered'
      element.contentEditable = 'false'
      element.title = '点击编辑公式'
      let currentLatex = String(node.attrs.latex)

      const render = (latex: string) => {
        katex.render(latex, element, { throwOnError: false, displayMode: false })
        element.dataset.latex = latex
      }
      render(currentLatex)

      element.addEventListener('click', (event) => {
        event.preventDefault()
        const nextLatex = window.prompt('编辑公式（LaTeX）:', currentLatex)
        if (nextLatex === null || nextLatex === currentLatex) return
        const position = getPos()
        if (typeof position !== 'number') return
        editor
          .chain()
          .focus()
          .setNodeSelection(position)
          .updateAttributes('mathInline', { latex: nextLatex })
          .run()
      })

      return {
        dom: element,
        update: (updatedNode) => {
          const nextLatex = String(updatedNode.attrs.latex)
          if (nextLatex === currentLatex) return false
          currentLatex = nextLatex
          render(currentLatex)
          return true
        },
      }
    }
  },
})
