<template>
  <div class="preview-create-page">
    <header class="page-header">
      <button class="btn-back" @click="$router.back()">← 返回</button>
      <h1>📖 发布预习任务</h1>
      <p class="subtitle">AI 自动生成导读材料 + 自测题 + 讨论题，一键推送学生</p>
    </header>

    <div class="form-card">
      <el-form label-width="80px" :model="form" @submit.prevent>
        <el-form-item label="班级">
          <el-select v-model="form.classId" placeholder="选择班级" style="width: 100%">
            <el-option v-for="c in classList" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识点">
          <el-input
            v-model="form.knowledgePoint"
            placeholder="例：二次函数、指针与内存管理、光合作用"
          />
        </el-form-item>
        <el-form-item label="主题">
          <el-input v-model="form.topic" placeholder="可选，自定义预习标题，留空则自动生成" />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="generating"
            :disabled="!canSubmit"
            @click="handleGenerate"
          >
            🤖 {{ generating ? 'AI 生成中...' : 'AI 生成预习任务' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 生成结果预览 -->
    <div v-if="result" class="result-section">
      <h2>✅ 预习任务已生成</h2>

      <div class="preview-card">
        <div class="card-header">
          <h3>{{ result.title }}</h3>
          <el-tag size="small">{{ result.knowledgePoint }}</el-tag>
        </div>

        <!-- 导读 -->
        <section class="guide-section">
          <h4>📖 课前导读</h4>
          <div class="guide-text markdown-body" v-html="renderMarkdown(result.guideText)"></div>
        </section>

        <!-- 自测题 -->
        <section class="questions-section">
          <h4>📝 自测题（{{ result.questions?.length || 0 }}题）</h4>
          <div v-for="(q, qi) in result.questions" :key="qi" class="question-card">
            <p class="q-title">{{ qi + 1 }}. {{ q.question }}</p>
            <div v-if="q.options" class="q-options">
              <span v-for="o in q.options" :key="o.key" class="q-opt">
                <b>{{ o.key }}.</b> {{ o.text }}
              </span>
            </div>
            <div class="q-answer">
              <span class="answer-label">✓ 答案：</span>
              <b>{{ q.correctKey }}</b>
              <span v-if="q.explanation" class="answer-exp"> — {{ q.explanation }}</span>
            </div>
          </div>
        </section>

        <!-- 讨论题 -->
        <section v-if="result.discussionQuestion" class="discussion-section">
          <h4>💬 课堂讨论</h4>
          <p>{{ result.discussionQuestion }}</p>
        </section>

        <div class="card-footer">
          <span class="create-time">创建于 {{ result.createdAt }}</span>
          <el-tag type="success">已推送到QQ群</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getDashboardClasses } from '@/features/teaching/api/dashboard'
import { createPreviewTask, type PreviewTaskDTO } from '@/features/teaching/api/preview'
import { ElMessage } from 'element-plus'
import { getApiErrorMessage } from '@/shared/api/errors'
import { renderMarkdown } from '@/shared/utils/safeHtml'

const route = useRoute()

const classList = ref<{ id: number; name: string }[]>([])
const form = ref({
  classId: Number(route.query.classId) || (null as number | null),
  knowledgePoint: String(route.query.knowledgePoint || ''),
  topic: '',
})
const generating = ref(false)
const result = ref<PreviewTaskDTO | null>(null)

const canSubmit = computed(() => form.value.classId && form.value.knowledgePoint.trim())

async function handleGenerate() {
  if (!canSubmit.value) return
  generating.value = true
  result.value = null
  try {
    const res = await createPreviewTask({
      classId: form.value.classId!,
      knowledgePoint: form.value.knowledgePoint.trim(),
      topic: form.value.topic.trim() || undefined,
    })
    result.value = res.data
    ElMessage.success('预习任务已生成并推送到QQ群')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '生成失败'))
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  try {
    const res = await getDashboardClasses()
    classList.value = res.data || []
  } catch {
    /* ignore */
  }
})
</script>

<style scoped>
.preview-create-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}
.page-header {
  margin-bottom: 20px;
}
.page-header h1 {
  font-size: 20px;
  margin: 8px 0 0;
}
.subtitle {
  color: #909399;
  font-size: 13px;
}
.btn-back {
  border: none;
  background: none;
  color: #409eff;
  cursor: pointer;
  font-size: 14px;
  padding: 0;
}
.form-card {
  background: #fff;
  border-radius: 10px;
  padding: 24px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}

.result-section {
  margin-top: 24px;
}
.result-section h2 {
  font-size: 18px;
  margin-bottom: 16px;
}
.preview-card {
  background: #fff;
  border-radius: 10px;
  padding: 24px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.04);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.card-header h3 {
  font-size: 17px;
  margin: 0;
}

.guide-section,
.questions-section,
.discussion-section {
  margin-bottom: 20px;
}
.guide-section h4,
.questions-section h4,
.discussion-section h4 {
  font-size: 14px;
  color: #606266;
  margin: 0 0 10px;
}
.guide-text {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
}
.guide-text :deep(p) {
  margin: 0 0 8px;
}
.guide-text :deep(ul) {
  padding-left: 20px;
}

.question-card {
  padding: 12px;
  margin-bottom: 10px;
  background: #fafafa;
  border-radius: 8px;
  border-left: 3px solid #409eff;
}
.q-title {
  font-size: 14px;
  font-weight: 500;
  margin: 0 0 8px;
}
.q-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 8px;
}
.q-opt {
  font-size: 13px;
  color: #606266;
}
.q-answer {
  font-size: 13px;
  color: #67c23a;
}
.answer-label {
  color: #67c23a;
}
.answer-exp {
  color: #909399;
  font-size: 12px;
}

.discussion-section p {
  font-size: 14px;
  color: #e6a23c;
  margin: 0;
  padding: 10px;
  background: #fdf6ec;
  border-radius: 6px;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid #eee;
}
.create-time {
  font-size: 12px;
  color: #909399;
}
</style>
