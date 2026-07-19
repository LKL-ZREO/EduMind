<template>
  <div class="preview-view-page">
    <div v-if="loading" class="loading-state">加载中...</div>
    <template v-else-if="task">
      <header class="view-header">
        <h1>📖 {{ task.title }}</h1>
        <div class="header-meta">
          <el-tag size="small">{{ task.knowledgePoint }}</el-tag>
          <span class="create-time">{{ task.createdAt }}</span>
        </div>
      </header>

      <!-- 导读材料 -->
      <section class="guide-card">
        <h2>📋 课前导读</h2>
        <div class="guide-body markdown-body" v-html="renderMd(task.guideText)"></div>
      </section>

      <!-- 自测题 -->
      <section class="quiz-card">
        <h2>📝 自测题（{{ task.questions?.length || 0 }}题）</h2>
        <p class="quiz-hint">完成导读后，测试一下你的理解吧。点击选项查看答案。</p>

        <div v-for="(q, qi) in task.questions" :key="qi" class="quiz-item">
          <p class="quiz-q">{{ qi + 1 }}. {{ q.question }}</p>

          <!-- 选择题 -->
          <div v-if="q.options" class="quiz-opts">
            <button
              v-for="o in q.options" :key="o.key"
              class="quiz-opt"
              :class="{
                selected: selections[qi] === o.key,
                correct: revealed[qi] && o.key === q.correctKey,
                wrong: revealed[qi] && selections[qi] === o.key && o.key !== q.correctKey
              }"
              @click="selectOption(qi, o.key)"
            >
              <b>{{ o.key }}</b>. {{ o.text }}
            </button>
          </div>

          <!-- 简答题：直接展示参考答案 -->
          <div v-else class="quiz-open">
            <p class="open-answer"><b>参考答案：</b>{{ q.correctKey }}</p>
          </div>

          <!-- 解析（选题后展示） -->
          <div v-if="revealed[qi] && q.explanation" class="quiz-explanation">
            💡 {{ q.explanation }}
          </div>
        </div>
      </section>

      <!-- 讨论题 -->
      <section v-if="task.discussionQuestion" class="discuss-card">
        <h2>💬 课堂讨论</h2>
        <p class="discuss-text">{{ task.discussionQuestion }}</p>
        <p class="discuss-hint">请带着你的思考来上课，课上会和大家一起讨论。</p>
      </section>
    </template>
    <div v-else class="error-state">预习任务不存在或已关闭</div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPreviewTask, type PreviewTaskDTO } from '../api/preview'
import { marked } from 'marked'

const route = useRoute()
const task = ref<PreviewTaskDTO | null>(null)
const loading = ref(true)
const selections = reactive<Record<number, string>>({})
const revealed = reactive<Record<number, boolean>>({})

function renderMd(text: string) {
  if (!text) return ''
  return marked.parse(text) as string
}

function selectOption(qi: number, key: string) {
  if (revealed[qi]) return
  selections[qi] = key
  revealed[qi] = true
}

onMounted(async () => {
  try {
    const id = Number(route.params.taskId)
    if (id) {
      const res = await getPreviewTask(id)
      task.value = res.data
    }
  } catch { /* 404 */ }
  finally { loading.value = false }
})
</script>

<style scoped>
.preview-view-page { max-width: 720px; margin: 0 auto; padding: 20px; }
.loading-state, .error-state { text-align: center; padding: 80px 0; color: #909399; font-size: 15px; }

.view-header { margin-bottom: 20px; }
.view-header h1 { font-size: 22px; margin: 0 0 8px; }
.header-meta { display: flex; gap: 10px; align-items: center; }
.create-time { font-size: 12px; color: #909399; }

.guide-card, .quiz-card, .discuss-card {
  background: #fff; border-radius: 10px; padding: 24px; margin-bottom: 16px;
  box-shadow: 0 1px 6px rgba(0,0,0,.04);
}
.guide-card h2, .quiz-card h2, .discuss-card h2 { font-size: 16px; margin: 0 0 12px; }
.guide-body { font-size: 14px; line-height: 1.8; color: #303133; }
.guide-body :deep(p) { margin: 0 0 8px; }
.guide-body :deep(ul) { padding-left: 20px; }
.guide-body :deep(li) { margin-bottom: 4px; }

.quiz-hint { font-size: 13px; color: #909399; margin-bottom: 16px; }
.quiz-item { padding: 12px; margin-bottom: 12px; background: #fafafa; border-radius: 8px; border-left: 3px solid #409eff; }
.quiz-q { font-size: 14px; font-weight: 500; margin: 0 0 10px; }

.quiz-opts { display: flex; flex-direction: column; gap: 6px; }
.quiz-opt { display: flex; align-items: center; gap: 6px; padding: 10px 14px; border: 2px solid #e4e7ed; border-radius: 8px; background: #fff; cursor: pointer; font-size: 13px; text-align: left; transition: all .15s; }
.quiz-opt:hover { border-color: #409eff; }
.quiz-opt.selected { border-color: #409eff; background: #ecf5ff; }
.quiz-opt.correct { border-color: #67c23a; background: #f0f9eb; }
.quiz-opt.wrong { border-color: #f56c6c; background: #fef0f0; }

.quiz-open { margin-top: 6px; }
.open-answer { font-size: 13px; color: #67c23a; margin: 0; }

.quiz-explanation { font-size: 13px; color: #409eff; margin-top: 8px; padding: 8px 10px; background: #ecf5ff; border-radius: 6px; }

.discuss-text { font-size: 15px; color: #e6a23c; margin: 0; padding: 12px; background: #fdf6ec; border-radius: 8px; line-height: 1.6; }
.discuss-hint { font-size: 12px; color: #909399; margin: 10px 0 0; }
</style>
