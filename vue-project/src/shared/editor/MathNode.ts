import { Node } from '@tiptap/core'
import katex from 'katex'

/**
 * 内联公式节点
 * - 编辑器中：KaTeX 渲染 + 点击编辑
 * - 序列化输出：<span class="math-inline" data-latex="..."></span>
 */
export const MathInlineNode = Node.create({
  name: 'mathInline',
  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  draggable: false,

  addAttributes() {
    return {
      latex: {
        default: 'x^2',
        parseHTML: (el: HTMLElement) => el.getAttribute('data-latex') || 'x^2',
        renderHTML: (attrs: Record<string, unknown>) => ({
          'data-latex': attrs.latex,
        }),
      },
    }
  },

  parseHTML() {
    return [{ tag: 'span.math-inline[data-latex]' }]
  },

  renderHTML({ node }) {
    return ['span', { class: 'math-inline', 'data-latex': node.attrs.latex }]
  },

  addNodeView() {
    return ({ node, getPos, editor }) => {
      const dom = document.createElement('span')
      dom.className = 'math-inline math-rendered'
      dom.contentEditable = 'false'
      dom.style.cursor = 'pointer'
      dom.title = '点击编辑公式'
      dom.setAttribute('data-latex', node.attrs.latex)

      let currentLatex: string = node.attrs.latex as string

      const renderLatex = (latex: string) => {
        try {
          katex.render(latex, dom, { throwOnError: false, displayMode: false })
        } catch {
          dom.textContent = latex
        }
      }

      renderLatex(currentLatex)

      dom.addEventListener('click', (e) => {
        e.preventDefault()
        e.stopPropagation()
        const newLatex = prompt('编辑公式 (LaTeX):', currentLatex)
        if (newLatex !== null && newLatex !== currentLatex) {
          const pos = getPos()
          if (typeof pos === 'number') {
            editor
              .chain()
              .focus()
              .setNodeSelection(pos)
              .updateAttributes('mathInline', { latex: newLatex })
              .run()
          }
        }
      })

      return {
        dom,
        update: (updatedNode) => {
          if (updatedNode.attrs.latex !== currentLatex) {
            currentLatex = updatedNode.attrs.latex as string
            renderLatex(currentLatex)
            dom.setAttribute('data-latex', currentLatex)
            return true
          }
          return false
        },
      }
    }
  },
})

export const mathExtensions = [MathInlineNode]
