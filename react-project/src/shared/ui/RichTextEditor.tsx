import { useEffect, useRef } from 'react'
import Placeholder from '@tiptap/extension-placeholder'
import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import { MathInlineNode } from '@/shared/editor/MathInlineNode'
import './RichTextEditor.css'

type RichTextEditorProps = {
  value: string
  placeholder?: string
  onChange: (value: string) => void
}

export function RichTextEditor({
  value,
  placeholder = '请输入作业要求、题目说明……',
  onChange,
}: RichTextEditorProps) {
  const onChangeRef = useRef(onChange)
  useEffect(() => {
    onChangeRef.current = onChange
  }, [onChange])

  const editor = useEditor({
    immediatelyRender: false,
    content: value,
    extensions: [
      StarterKit.configure({ heading: { levels: [2, 3, 4] } }),
      Placeholder.configure({ placeholder }),
      MathInlineNode,
    ],
    editorProps: { attributes: { class: 'rich-editor-body' } },
    onUpdate: ({ editor: current }) => onChangeRef.current(current.getHTML()),
  })

  useEffect(() => {
    if (editor && editor.getHTML() !== value) {
      editor.commands.setContent(value, { emitUpdate: false })
    }
  }, [editor, value])

  if (!editor) return null

  function insertFormula() {
    const latex = window.prompt('输入 LaTeX 公式:', 'x^2')
    if (!latex) return
    editor?.chain().focus().insertContent({ type: 'mathInline', attrs: { latex } }).run()
  }

  return (
    <div className="rich-editor">
      <div className="rich-toolbar" aria-label="富文本工具栏">
        <button
          type="button"
          aria-label="加粗"
          className={editor.isActive('bold') ? 'active' : ''}
          onClick={() => {
            editor.chain().focus().toggleBold().run()
          }}
        >
          <strong>B</strong>
        </button>
        <button
          type="button"
          aria-label="斜体"
          className={editor.isActive('italic') ? 'active' : ''}
          onClick={() => {
            editor.chain().focus().toggleItalic().run()
          }}
        >
          <em>I</em>
        </button>
        <span className="rich-toolbar-separator" />
        <button
          type="button"
          aria-label="三级标题"
          className={editor.isActive('heading', { level: 3 }) ? 'active' : ''}
          onClick={() => {
            editor.chain().focus().toggleHeading({ level: 3 }).run()
          }}
        >
          H
        </button>
        <button
          type="button"
          aria-label="无序列表"
          className={editor.isActive('bulletList') ? 'active' : ''}
          onClick={() => {
            editor.chain().focus().toggleBulletList().run()
          }}
        >
          •≡
        </button>
        <button
          type="button"
          aria-label="有序列表"
          className={editor.isActive('orderedList') ? 'active' : ''}
          onClick={() => {
            editor.chain().focus().toggleOrderedList().run()
          }}
        >
          1.
        </button>
        <button
          type="button"
          aria-label="引用块"
          className={editor.isActive('blockquote') ? 'active' : ''}
          onClick={() => {
            editor.chain().focus().toggleBlockquote().run()
          }}
        >
          ❝
        </button>
        <button
          type="button"
          aria-label="代码块"
          className={editor.isActive('codeBlock') ? 'active' : ''}
          onClick={() => {
            editor.chain().focus().toggleCodeBlock().run()
          }}
        >
          {'</>'}
        </button>
        <span className="rich-toolbar-separator" />
        <button type="button" className="formula" onClick={insertFormula}>
          Σ 公式
        </button>
      </div>
      <EditorContent editor={editor} />
    </div>
  )
}
