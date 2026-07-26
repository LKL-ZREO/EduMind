<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { useClassStore } from '@/stores/class'
import {
  getDrafts,
  getTasksByClass,
  publishDraft,
  saveDraft,
  searchQuestions,
  deleteDraft,
  type DraftQuestion,
  type HomeworkDraft,
  type Task,
} from '@/api/tasks'

type Mode = 'draft' | 'published'

interface TaskGroup {
  key: string
  taskName: string
  description: string
  deadline?: string
  allowLate: boolean
  latePenalty: number
  status: string
  createdAt: string
  tasks: Task[]
  classNames: string[]
}

const router = useRouter()
const classStore = useClassStore()

const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const tasks = ref<Task[]>([])
const drafts = ref<HomeworkDraft[]>([])
const bankQuestions = ref<DraftQuestion[]>([])
const mode = ref<Mode>('draft')
const activePublishedKey = ref('')
const draftSearch = ref('')
const publishedSearch = ref('')
const questionSearch = ref('')
const selectedClassIds = ref<number[]>([])

const draftForm = reactive<{
  id: number | null
  taskName: string
  deadline: string
  allowLate: boolean
  latePenalty: number
  questions: DraftQuestion[]
}>({
  id: null,
  taskName: '',
  deadline: '',
  allowLate: true,
  latePenalty: 0,
  questions: [createBlankQuestion()],
})

const classList = computed(() => classStore.classList)

const taskGroups = computed<TaskGroup[]>(() => {
  const grouped = new Map<string, TaskGroup>()
  for (const task of tasks.value) {
    const key = [
      task.taskName,
      normalizeDescription(task.description),
      task.deadline || '',
      task.allowLate ? 'late' : 'strict',
      task.latePenalty ?? 0,
    ].join('::')
    const className = getClassName(task.classId)
    const existing = grouped.get(key)
    if (existing) {
      existing.tasks.push(task)
      if (!existing.classNames.includes(className)) existing.classNames.push(className)
      if (task.status === 'active') existing.status = 'active'
      continue
    }
    grouped.set(key, {
      key,
      taskName: task.taskName,
      description: task.description,
      deadline: task.deadline,
      allowLate: task.allowLate,
      latePenalty: task.latePenalty,
      status: task.status,
      createdAt: task.createdAt,
      tasks: [task],
      classNames: [className],
    })
  }
  return [...grouped.values()].sort((a, b) => {
    if (a.status !== b.status) return a.status === 'active' ? -1 : 1
    return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime()
  })
})

const filteredDrafts = computed(() =>
  drafts.value.filter((item) =>
    draftSearch.value ? item.taskName?.includes(draftSearch.value) : true,
  ),
)

const filteredGroups = computed(() =>
  taskGroups.value.filter((item) =>
    publishedSearch.value ? item.taskName.includes(publishedSearch.value) : true,
  ),
)

const activeGroup = computed(
  () =>
    taskGroups.value.find((item) => item.key === activePublishedKey.value) ||
    taskGroups.value[0] ||
    null,
)

const selectedClasses = computed(() =>
  classList.value.filter((item) => selectedClassIds.value.includes(item.id)),
)

const totalScore = computed(() =>
  draftForm.questions.reduce((sum, question) => sum + Number(question.score || 0), 0),
)

const hasUnsavedDraft = computed(() => draftForm.id === null)

onMounted(async () => {
  await classStore.fetchClassList()
  selectedClassIds.value = classList.value.slice(0, 1).map((item) => item.id)
  await Promise.all([loadTasks(), loadDrafts(), loadQuestionBank()])
  const firstDraft = drafts.value[0]
  if (firstDraft) {
    selectDraft(firstDraft)
  }
})

async function loadTasks() {
  if (!classList.value.length) return
  loading.value = true
  try {
    const results = await Promise.allSettled(classList.value.map((cls) => getTasksByClass(cls.id)))
    tasks.value = results
      .filter(
        (result): result is PromiseFulfilledResult<Awaited<ReturnType<typeof getTasksByClass>>> =>
          result.status === 'fulfilled',
      )
      .filter((result) => result.value?.code === 200)
      .flatMap((result) => result.value.data || [])
    const firstGroup = taskGroups.value[0]
    if (!activePublishedKey.value && firstGroup) {
      activePublishedKey.value = firstGroup.key
    }
  } catch (error) {
    console.error('load tasks failed', error)
    ElMessage.error('加载已发布作业失败')
  } finally {
    loading.value = false
  }
}

async function loadDrafts() {
  try {
    const res = await getDrafts()
    if (res?.code === 200) drafts.value = res.data || []
  } catch (error) {
    console.error('load drafts failed', error)
    ElMessage.error('加载草稿失败，请确认后端服务已启动')
  }
}

async function loadQuestionBank() {
  try {
    const res = await searchQuestions(questionSearch.value)
    if (res?.code === 200) bankQuestions.value = res.data || []
  } catch (error) {
    console.error('load question bank failed', error)
    ElMessage.error('加载题库失败，请确认后端服务已启动')
  }
}

function createBlankQuestion(): DraftQuestion {
  return {
    title: '',
    requirement: '',
    score: 20,
    uploadRequired: true,
  }
}

function newDraft() {
  mode.value = 'draft'
  draftForm.id = null
  draftForm.taskName = ''
  draftForm.deadline = ''
  draftForm.allowLate = true
  draftForm.latePenalty = 0
  draftForm.questions = [createBlankQuestion()]
}

function selectDraft(item: HomeworkDraft) {
  mode.value = 'draft'
  draftForm.id = item.id
  draftForm.taskName = item.taskName || ''
  draftForm.deadline = toDatetimeLocal(item.deadline)
  draftForm.allowLate = item.allowLate ?? true
  draftForm.latePenalty = item.latePenalty ?? 0
  draftForm.questions = item.questions?.length
    ? item.questions.map(cloneQuestion)
    : [createBlankQuestion()]
}

function selectPublished(group: TaskGroup) {
  mode.value = 'published'
  activePublishedKey.value = group.key
}

function copyPublishedAsDraft(group: TaskGroup) {
  newDraft()
  draftForm.taskName = `${group.taskName} 副本`
  draftForm.deadline = toDatetimeLocal(group.deadline)
  draftForm.allowLate = group.allowLate
  draftForm.latePenalty = group.latePenalty
  draftForm.questions = [
    {
      title: group.taskName,
      requirement: group.description || '',
      score: 100,
      uploadRequired: true,
    },
  ]
  ElMessage.success('已复制为未保存草稿')
}

function addQuestion() {
  draftForm.questions.push(createBlankQuestion())
}

function addFromBank(question: DraftQuestion) {
  draftForm.questions.push(cloneQuestion(question))
  ElMessage.success('已加入当前草稿')
}

function cloneQuestion(question: DraftQuestion): DraftQuestion {
  return {
    id: question.id,
    title: question.title,
    requirement: question.requirement,
    score: question.score,
    uploadRequired: question.uploadRequired,
  }
}

function removeQuestion(index: number) {
  if (draftForm.questions.length <= 1) {
    ElMessage.warning('至少保留一道题')
    return
  }
  draftForm.questions.splice(index, 1)
}

async function saveCurrentDraft() {
  saving.value = true
  try {
    const res = await saveDraft(
      {
        taskName: draftForm.taskName || '未命名作业',
        description: buildDraftDescription(),
        deadline: draftForm.deadline || null,
        allowLate: draftForm.allowLate,
        latePenalty: draftForm.latePenalty,
        questions: draftForm.questions,
      },
      draftForm.id,
    )
    if (res?.code !== 200) throw new Error(res?.message || 'save failed')
    selectDraft(res.data)
    await Promise.all([loadDrafts(), loadQuestionBank()])
    ElMessage.success('草稿已保存，题目已同步到题库')
  } catch (error) {
    console.error('save draft failed', error)
    ElMessage.error('保存草稿失败')
  } finally {
    saving.value = false
  }
}

async function removeCurrentDraft() {
  if (!draftForm.id) {
    newDraft()
    return
  }
  await ElMessageBox.confirm('删除后不会影响已发布作业，确认删除这个草稿吗？', '删除草稿', {
    type: 'warning',
  })
  const res = await deleteDraft(draftForm.id)
  if (res?.code === 200) {
    ElMessage.success('草稿已删除')
    await loadDrafts()
    const firstDraft = drafts.value[0]
    if (firstDraft) selectDraft(firstDraft)
    else newDraft()
  }
}

async function publishCurrentDraft() {
  if (!draftForm.taskName.trim()) {
    ElMessage.warning('请先填写作业名称')
    return
  }
  if (draftForm.questions.length === 0) {
    ElMessage.warning('请至少添加一道题')
    return
  }
  if (!draftForm.deadline) {
    ElMessage.warning('发布前需要设置截止时间')
    return
  }
  if (selectedClassIds.value.length === 0) {
    ElMessage.warning('请选择至少一个班级')
    return
  }

  publishing.value = true
  try {
    if (!draftForm.id) {
      await saveCurrentDraft()
    }
    if (!draftForm.id) throw new Error('draft id missing')
    const res = await publishDraft(draftForm.id, {
      classIds: selectedClassIds.value,
      taskName: draftForm.taskName,
      deadline: draftForm.deadline,
      allowLate: draftForm.allowLate,
      latePenalty: draftForm.latePenalty,
    })
    if (res?.code !== 200) throw new Error(res?.message || 'publish failed')
    ElMessage.success(`已发布到 ${selectedClassIds.value.length} 个班级`)
    await Promise.all([loadTasks(), loadDrafts()])
  } catch (error) {
    console.error('publish draft failed', error)
    ElMessage.error('发布失败')
  } finally {
    publishing.value = false
  }
}

function toggleClass(classId: number) {
  selectedClassIds.value = selectedClassIds.value.includes(classId)
    ? selectedClassIds.value.filter((id) => id !== classId)
    : [...selectedClassIds.value, classId]
}

function goDetail(group: TaskGroup) {
  const firstTask = group.tasks[0]
  if (firstTask) router.push(`/teacher/tasks/${firstTask.id}`)
}

function buildDraftDescription() {
  return draftForm.questions
    .map((question, index) => {
      return `
        <section class="assignment-question">
          <h3>题目 ${index + 1}: ${escapeHtml(question.title || '未命名题目')}</h3>
          <div class="assignment-question-body">${question.requirement || ''}</div>
          <p><strong>分值:</strong>${Number(question.score || 0)}; <strong>提交方式:</strong>${question.uploadRequired ? '按题上传附件' : '在线作答'}</p>
        </section>
      `
    })
    .join('')
}

function getClassName(classId: number) {
  return classList.value.find((item) => item.id === Number(classId))?.name || `班级 ${classId}`
}

function formatDisplayDate(value?: string) {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toDatetimeLocal(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function normalizeDescription(value?: string) {
  return (value || '').replace(/\s+/g, ' ').trim()
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
</script>

<template>
  <main class="tasks-page">
    <section class="page-head">
      <div>
        <p class="eyebrow">Assignment Studio</p>
        <h1>作业管理</h1>
        <p>先沉淀草稿和题库，再选择班级与截止时间发布。</p>
      </div>
      <div class="head-actions">
        <button class="ghost-button" @click="newDraft">新建草稿</button>
        <button class="primary-action" :disabled="saving" @click="saveCurrentDraft">
          {{ saving ? '保存中...' : '保存草稿' }}
        </button>
      </div>
    </section>

    <section class="summary-row">
      <article>
        <span>草稿</span>
        <strong>{{ drafts.length }}</strong>
      </article>
      <article>
        <span>题库题目</span>
        <strong>{{ bankQuestions.length }}</strong>
      </article>
      <article>
        <span>已发布套数</span>
        <strong>{{ taskGroups.length }}</strong>
      </article>
    </section>

    <section class="workspace">
      <aside class="left-panel">
        <div class="panel-tabs">
          <button :class="{ active: mode === 'draft' }" @click="mode = 'draft'">草稿</button>
          <button :class="{ active: mode === 'published' }" @click="mode = 'published'">
            已发布
          </button>
        </div>

        <template v-if="mode === 'draft'">
          <input v-model="draftSearch" class="plain-input" placeholder="搜索草稿" />
          <div class="list-stack">
            <button
              v-for="item in filteredDrafts"
              :key="item.id"
              class="list-row"
              :class="{ active: draftForm.id === item.id }"
              @click="selectDraft(item)"
            >
              <strong>{{ item.taskName || '未命名作业' }}</strong>
              <small
                >{{ item.questions?.length || 0 }} 题 ·
                {{ formatDisplayDate(item.updatedAt) }}</small
              >
            </button>
            <p v-if="!filteredDrafts.length" class="empty-state">还没有保存过的草稿。</p>
          </div>
        </template>

        <template v-else>
          <input v-model="publishedSearch" class="plain-input" placeholder="搜索已发布作业" />
          <div v-if="loading" class="empty-state">正在加载...</div>
          <div v-else class="list-stack">
            <button
              v-for="group in filteredGroups"
              :key="group.key"
              class="list-row"
              :class="{ active: activePublishedKey === group.key }"
              @click="selectPublished(group)"
            >
              <strong>{{ group.taskName }}</strong>
              <small
                >{{ group.classNames.join('、') }} · 截止
                {{ formatDisplayDate(group.deadline) }}</small
              >
            </button>
            <p v-if="!filteredGroups.length" class="empty-state">暂无已发布作业。</p>
          </div>
        </template>
      </aside>

      <section class="editor-panel">
        <template v-if="mode === 'published' && activeGroup">
          <div class="section-head">
            <div>
              <p class="eyebrow">Published</p>
              <h2>{{ activeGroup.taskName }}</h2>
            </div>
            <div class="inline-actions">
              <button class="ghost-button" @click="copyPublishedAsDraft(activeGroup)">
                复制为草稿
              </button>
              <button class="ghost-button" @click="goDetail(activeGroup)">查看统计</button>
            </div>
          </div>
          <div class="meta-grid">
            <div>
              <span>班级</span><strong>{{ activeGroup.classNames.join('、') }}</strong>
            </div>
            <div>
              <span>截止</span><strong>{{ formatDisplayDate(activeGroup.deadline) }}</strong>
            </div>
            <div>
              <span>迟交</span
              ><strong>{{
                activeGroup.allowLate ? `允许，扣 ${activeGroup.latePenalty} 分` : '不允许'
              }}</strong>
            </div>
          </div>
          <div class="question-preview" v-html="activeGroup.description || '暂无作业内容'"></div>
        </template>

        <template v-else>
          <div class="section-head">
            <div>
              <p class="eyebrow">{{ hasUnsavedDraft ? 'New Draft' : 'Saved Draft' }}</p>
              <h2>{{ draftForm.taskName || '未命名草稿' }}</h2>
            </div>
            <div class="inline-actions">
              <button class="ghost-button danger" @click="removeCurrentDraft">删除草稿</button>
              <button class="primary-action" :disabled="saving" @click="saveCurrentDraft">
                保存草稿
              </button>
            </div>
          </div>

          <div class="form-grid">
            <label>
              <span>作业名称</span>
              <input v-model="draftForm.taskName" placeholder="例如：第三次作业：数组与链表" />
            </label>
            <label>
              <span>截止时间（发布前再填也可以）</span>
              <input v-model="draftForm.deadline" type="datetime-local" />
            </label>
          </div>

          <label class="switch-line">
            <input v-model="draftForm.allowLate" type="checkbox" />
            <span>允许迟交，每天扣</span>
            <input
              v-model.number="draftForm.latePenalty"
              class="mini-input"
              type="number"
              min="0"
              max="100"
            />
            <span>分</span>
          </label>

          <div class="question-toolbar">
            <div>
              <p class="eyebrow">Questions</p>
              <h3>{{ draftForm.questions.length }} 题 · {{ totalScore }} 分</h3>
            </div>
            <button class="primary-action compact" @click="addQuestion">+ 新题目</button>
          </div>

          <article
            v-for="(question, index) in draftForm.questions"
            :key="`${question.id || 'new'}-${index}`"
            class="question-block"
          >
            <div class="question-title-row">
              <span>第 {{ index + 1 }} 题</span>
              <button class="text-button danger" @click="removeQuestion(index)">移除</button>
            </div>
            <div class="form-grid compact">
              <label>
                <span>题目标题</span>
                <input v-model="question.title" placeholder="题目标题" />
              </label>
              <label>
                <span>分值</span>
                <input v-model.number="question.score" type="number" min="0" />
              </label>
            </div>
            <div class="rich-block">
              <span>题目要求</span>
              <RichTextEditor
                v-model="question.requirement"
                placeholder="写题干、评分标准、提交说明..."
              />
            </div>
            <label class="switch-line">
              <input v-model="question.uploadRequired" type="checkbox" />
              <span>需要学生上传附件</span>
            </label>
          </article>
        </template>
      </section>

      <aside class="right-panel">
        <section>
          <div class="section-head compact-head">
            <div>
              <p class="eyebrow">Question Bank</p>
              <h2>题库</h2>
            </div>
          </div>
          <div class="search-row">
            <input
              v-model="questionSearch"
              class="plain-input"
              placeholder="搜索题目"
              @keyup.enter="loadQuestionBank"
            />
            <button class="ghost-button" @click="loadQuestionBank">搜索</button>
          </div>
          <div class="bank-list">
            <button
              v-for="question in bankQuestions"
              :key="question.id"
              class="bank-row"
              @click="addFromBank(question)"
            >
              <strong>{{ question.title }}</strong>
              <small
                >{{ question.score }} 分 ·
                {{ question.uploadRequired ? '附件提交' : '在线作答' }}</small
              >
            </button>
            <p v-if="!bankQuestions.length" class="empty-state">保存草稿后，题目会出现在这里。</p>
          </div>
        </section>

        <section class="publish-box">
          <p class="eyebrow">Publish</p>
          <h2>发布设置</h2>
          <div class="field-block">
            <span>接收班级</span>
            <button
              v-for="classItem in classList"
              :key="classItem.id"
              class="class-option"
              :class="{ checked: selectedClassIds.includes(classItem.id) }"
              @click="toggleClass(classItem.id)"
            >
              <span></span>
              <strong>{{ classItem.name }}</strong>
            </button>
          </div>
          <div class="publish-preview">
            <strong>{{ selectedClasses.length }} 个班级</strong>
            <small>{{
              selectedClasses.map((item) => item.name).join('、') || '尚未选择班级'
            }}</small>
            <small>截止 {{ formatDisplayDate(draftForm.deadline) }}</small>
          </div>
          <button class="primary-action full" :disabled="publishing" @click="publishCurrentDraft">
            {{ publishing ? '发布中...' : '发布当前草稿' }}
          </button>
        </section>
      </aside>
    </section>
  </main>
</template>

<style scoped>
.tasks-page {
  --ink: #1f2933;
  --muted: #667085;
  --line: #d9dee7;
  --panel: #ffffff;
  --surface: #f5f7fb;
  --primary: #226b68;
  --primary-dark: #174d4b;
  --danger: #b54747;
  min-height: 100vh;
  padding: clamp(1rem, 2.4vw, 1.8rem);
  color: var(--ink);
  background: var(--surface);
  font-family: 'Avenir Next', 'Noto Sans SC', 'PingFang SC', sans-serif;
}

.page-head,
.summary-row article,
.left-panel,
.editor-panel,
.right-panel {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel);
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.2rem;
}

.page-head p,
.empty-state,
small {
  color: var(--muted);
}

.eyebrow {
  margin: 0 0 0.35rem;
  color: var(--primary);
  font-size: 0.72rem;
  font-weight: 900;
  text-transform: uppercase;
}

h1,
h2,
h3 {
  margin: 0;
  letter-spacing: 0;
}

h1 {
  font-size: clamp(2rem, 5vw, 3.5rem);
  line-height: 1;
}

.head-actions,
.inline-actions,
.search-row,
.panel-tabs {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

button,
input {
  font: inherit;
}

.primary-action,
.ghost-button,
.text-button {
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.primary-action {
  border: 1px solid var(--primary-dark);
  background: var(--primary-dark);
  color: #fff;
  padding: 0.7rem 0.95rem;
}

.primary-action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.primary-action.compact {
  padding: 0.55rem 0.75rem;
}

.primary-action.full {
  width: 100%;
  margin-top: 0.8rem;
}

.ghost-button {
  border: 1px solid var(--line);
  background: #fff;
  color: var(--ink);
  padding: 0.62rem 0.85rem;
}

.ghost-button.danger,
.text-button.danger {
  color: var(--danger);
}

.text-button {
  border: 0;
  background: transparent;
  padding: 0.25rem 0;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 0.8rem 0;
}

.summary-row article {
  display: grid;
  gap: 0.25rem;
  padding: 0.85rem;
}

.summary-row strong {
  font-size: 1.65rem;
}

.workspace {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 340px;
  gap: 0.8rem;
  align-items: start;
}

.left-panel,
.editor-panel,
.right-panel {
  padding: 0.9rem;
}

.right-panel {
  display: grid;
  gap: 1rem;
  position: sticky;
  top: 1rem;
}

.panel-tabs {
  margin-bottom: 0.75rem;
}

.panel-tabs button {
  flex: 1;
  padding: 0.55rem;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 8px;
}

.panel-tabs button.active {
  border-color: var(--primary);
  background: #eaf4f2;
  color: var(--primary-dark);
}

.plain-input,
.form-grid input,
.mini-input {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  color: var(--ink);
  padding: 0.65rem 0.75rem;
}

.list-stack,
.bank-list {
  display: grid;
  gap: 0.5rem;
  margin-top: 0.75rem;
}

.list-row,
.bank-row,
.class-option {
  display: grid;
  gap: 0.25rem;
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  color: var(--ink);
  cursor: pointer;
  padding: 0.7rem;
  text-align: left;
}

.list-row.active,
.class-option.checked {
  border-color: var(--primary);
  background: #eaf4f2;
}

.section-head,
.question-toolbar,
.question-title-row {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
  align-items: flex-start;
}

.compact-head {
  align-items: center;
}

.form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 230px;
  gap: 0.75rem;
  margin-top: 1rem;
}

.form-grid.compact {
  grid-template-columns: minmax(0, 1fr) 110px;
}

.form-grid label,
.rich-block {
  display: grid;
  gap: 0.4rem;
  color: var(--ink);
  font-size: 0.86rem;
  font-weight: 800;
}

.switch-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.85rem;
  font-weight: 700;
}

.mini-input {
  width: 5rem;
}

.question-toolbar {
  margin-top: 1.2rem;
  padding-top: 1rem;
  border-top: 1px solid var(--line);
}

.question-block {
  display: grid;
  gap: 0.85rem;
  margin-top: 0.85rem;
  padding: 0.9rem;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fbfcfe;
}

.rich-block :deep(.rich-editor) {
  border-color: var(--line);
  border-radius: 8px;
}

.rich-block :deep(.rich-editor-content .rich-editor-body) {
  min-height: 140px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.6rem;
  margin: 1rem 0;
}

.meta-grid div,
.publish-preview {
  display: grid;
  gap: 0.25rem;
  padding: 0.75rem;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fbfcfe;
}

.question-preview {
  line-height: 1.75;
  color: var(--ink);
}

.field-block {
  display: grid;
  gap: 0.5rem;
  margin-top: 0.8rem;
}

.class-option {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
}

.class-option span {
  width: 0.9rem;
  height: 0.9rem;
  border: 1px solid var(--primary);
  border-radius: 999px;
}

.class-option.checked span {
  background: var(--primary);
  box-shadow: inset 0 0 0 3px #eaf4f2;
}

.publish-box {
  border-top: 1px solid var(--line);
  padding-top: 1rem;
}

.publish-preview {
  margin-top: 0.8rem;
  background: #1f2933;
  color: #fff;
}

.publish-preview small {
  color: #d6dce5;
}

@media (max-width: 1180px) {
  .workspace {
    grid-template-columns: 260px minmax(0, 1fr);
  }

  .right-panel {
    grid-column: 1 / -1;
    position: static;
  }
}

@media (max-width: 760px) {
  .page-head,
  .section-head,
  .question-toolbar {
    flex-direction: column;
  }

  .summary-row,
  .workspace,
  .form-grid,
  .form-grid.compact,
  .meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
