<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Placeholder from '@tiptap/extension-placeholder'
import { mathExtensions } from '@/extensions/MathNode'

const props = withDefaults(
  defineProps<{
    modelValue: string
    placeholder?: string
    minHeight?: string
  }>(),
  {
    placeholder: '请输入作业要求、题目说明...',
    minHeight: '180px',
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const editor = useEditor({
  content: props.modelValue,
  extensions: [
    StarterKit.configure({
      heading: { levels: [2, 3, 4] },
    }),
    Placeholder.configure({ placeholder: props.placeholder }),
    ...mathExtensions,
  ],
  editorProps: {
    attributes: {
      class: 'rich-editor-body',
    },
  },
  onUpdate: ({ editor }) => {
    emit('update:modelValue', editor.getHTML())
  },
})

// 外部 modelValue 变化时同步到编辑器
watch(
  () => props.modelValue,
  (val) => {
    if (editor.value && editor.value.getHTML() !== val) {
      editor.value.commands.setContent(val, { emitUpdate: false })
    }
  }
)

onBeforeUnmount(() => {
  editor.value?.destroy()
})

// ===== 工具栏动作 =====
function insertFormula() {
  const latex = prompt('输入 LaTeX 公式:', 'x^2')
  if (latex) {
    editor.value?.chain().focus().insertContent({
      type: 'mathInline',
      attrs: { latex },
    }).run()
  }
}
</script>

<template>
  <div class="rich-editor" v-if="editor">
    <!-- 工具栏 -->
    <div class="rich-toolbar">
      <button
        title="加粗 (Ctrl+B)"
        :class="{ active: editor.isActive('bold') }"
        @click="editor.chain().focus().toggleBold().run()"
      >
        <strong>B</strong>
      </button>
      <button
        title="斜体 (Ctrl+I)"
        :class="{ active: editor.isActive('italic') }"
        @click="editor.chain().focus().toggleItalic().run()"
      >
        <em>I</em>
      </button>
      <span class="sep"></span>
      <button
        title="标题"
        :class="{ active: editor.isActive('heading', { level: 3 }) }"
        @click="editor.chain().focus().toggleHeading({ level: 3 }).run()"
      >
        H
      </button>
      <span class="sep"></span>
      <button
        title="无序列表"
        :class="{ active: editor.isActive('bulletList') }"
        @click="editor.chain().focus().toggleBulletList().run()"
      >
        •≡
      </button>
      <button
        title="有序列表"
        :class="{ active: editor.isActive('orderedList') }"
        @click="editor.chain().focus().toggleOrderedList().run()"
      >
        1.
      </button>
      <button
        title="引用块"
        :class="{ active: editor.isActive('blockquote') }"
        @click="editor.chain().focus().toggleBlockquote().run()"
      >
        ❝
      </button>
      <button
        title="代码块"
        :class="{ active: editor.isActive('codeBlock') }"
        @click="editor.chain().focus().toggleCodeBlock().run()"
      >
        &lt;/&gt;
      </button>
      <span class="sep"></span>
      <button
        title="插入公式"
        class="btn-formula"
        @click="insertFormula"
      >
        Σ 公式
      </button>
    </div>

    <!-- 编辑区 -->
    <EditorContent :editor="editor" class="rich-editor-content" />
  </div>
</template>

<style>
/* 非 scoped：Tiptap 渲染的 DOM 在组件 scope 外 */
.rich-editor {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.2s;
}

.rich-editor:focus-within {
  border-color: #409eff;
}

.rich-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  flex-wrap: wrap;
}

.rich-toolbar button {
  width: 32px;
  height: 32px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #303133;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.rich-toolbar button:hover {
  background: #e4e7ed;
}

.rich-toolbar button.active {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}

.rich-toolbar .btn-formula {
  width: auto;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 500;
  color: #409eff;
}

.rich-toolbar .btn-formula:hover {
  color: #fff;
  background: #409eff;
}

.rich-toolbar .sep {
  width: 1px;
  height: 20px;
  background: #dcdfe6;
  margin: 0 4px;
}

.rich-editor-content .rich-editor-body {
  padding: 10px 12px;
  min-height: v-bind(minHeight);
  max-height: 400px;
  overflow-y: auto;
  outline: none;
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
}

.rich-editor-body p.is-editor-empty:first-child::before {
  color: #c0c4cc;
  content: attr(data-placeholder);
  float: left;
  height: 0;
  pointer-events: none;
}

.rich-editor-body h2 { font-size: 1.3em; margin: 0.6em 0 0.3em; }
.rich-editor-body h3 { font-size: 1.15em; margin: 0.5em 0 0.25em; }
.rich-editor-body h4 { font-size: 1.05em; margin: 0.4em 0 0.2em; }

.rich-editor-body blockquote {
  border-left: 3px solid #409eff;
  padding-left: 12px;
  margin: 0.5em 0;
  color: #606266;
}

.rich-editor-body pre {
  background: #f5f7fa;
  border-radius: 4px;
  padding: 10px 14px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  overflow-x: auto;
}

.rich-editor-body ul,
.rich-editor-body ol {
  padding-left: 1.5em;
}

/* 公式渲染样式 */
.math-inline.math-rendered {
  display: inline-block;
  vertical-align: middle;
  padding: 0 2px;
  cursor: pointer;
  border-radius: 3px;
  transition: background 0.15s;
}

.math-inline.math-rendered:hover {
  background: #ecf5ff;
}
</style>
