<template>
  <div class="preparation-page">
    <header class="workspace-header">
      <div>
        <span class="page-eyebrow">LESSON PREPARATION</span>
        <h1>备课工作台</h1>
        <p>从班级学情出发，把下一节课的目标、流程和教学材料一次准备完整。</p>
      </div>
      <div class="header-actions">
        <label class="class-selector">
          <span>当前班级</span>
          <select v-model="selectedClass" @change="handleClassChange">
            <option v-for="classItem in classList" :key="classItem.id" :value="classItem.id">
              {{ classItem.name }}
            </option>
          </select>
        </label>
        <button class="button secondary" :disabled="!data || loading" @click="load">
          {{ loading ? '正在同步' : '刷新学情' }}
        </button>
        <button class="button primary" :disabled="!data" @click="saveLocalDraft(true)">
          保存本机草稿
        </button>
      </div>
    </header>

    <div v-if="loading && !data" class="workspace-loading">
      <span class="loading-spinner"></span>
      <strong>正在整理班级学情与教学安排</strong>
      <p>首次加载会汇总作业、错题和实时课堂数据。</p>
    </div>

    <template v-else-if="data">
      <section class="lesson-brief">
        <div class="brief-main">
          <div class="brief-status-row">
            <span class="status-chip" :class="lessonDraft.status.toLowerCase()">
              <i></i>{{ lessonDraft.status === 'READY' ? '已就绪' : '备课草稿' }}
            </span>
            <span class="save-state">
              {{ draftDirty ? '有修改尚未保存' : lastSavedLabel }}
            </span>
          </div>

          <div class="lesson-title-field">
            <label for="lesson-topic">下一节课</label>
            <input
              id="lesson-topic"
              v-model="lessonDraft.topic"
              type="text"
              maxlength="80"
              placeholder="输入本节课主题，例如：指针与数组的关系"
            />
          </div>

          <div class="brief-fields">
            <label>
              <span>上课日期</span>
              <input v-model="lessonDraft.plannedDate" type="date" />
            </label>
            <label>
              <span>课时时长</span>
              <span class="input-with-unit">
                <input v-model.number="lessonDraft.duration" type="number" min="10" max="180" />
                <b>分钟</b>
              </span>
            </label>
            <label class="knowledge-field">
              <span>本节知识点</span>
              <input
                :value="lessonDraft.knowledgePoints.join('、')"
                type="text"
                placeholder="使用顿号分隔知识点"
                @input="updateKnowledgePoints"
              />
            </label>
          </div>

          <div class="objectives-row">
            <div>
              <span class="field-label">教学目标</span>
              <p>目标应可观察、可检验，AI 建议也会以此为约束。</p>
            </div>
            <div class="objective-tags">
              <span v-for="(objective, index) in lessonDraft.objectives" :key="index">
                <input v-model="lessonDraft.objectives[index]" maxlength="100" />
                <button type="button" aria-label="删除教学目标" @click="removeObjective(index)">
                  ×
                </button>
              </span>
              <button type="button" class="add-objective" @click="addObjective">+ 添加目标</button>
            </div>
          </div>
        </div>

        <aside class="readiness-summary">
          <div class="readiness-ring" :style="readinessStyle">
            <span
              ><strong>{{ readinessPercent }}</strong
              ><small>%</small></span
            >
          </div>
          <div>
            <span class="summary-label">备课完成度</span>
            <strong>{{ readyCount }} / {{ readinessItems.length }} 项已完成</strong>
            <p>{{ readinessMessage }}</p>
          </div>
          <button
            type="button"
            class="ready-button"
            :disabled="readinessPercent < 100"
            @click="markLessonReady"
          >
            {{ lessonDraft.status === 'READY' ? '本节课已准备就绪' : '标记为准备完成' }}
          </button>
        </aside>
      </section>

      <div class="workspace-grid">
        <main class="workspace-main">
          <section class="workspace-card evidence-section">
            <div class="section-heading">
              <div>
                <span class="section-eyebrow">EVIDENCE FOR THIS LESSON</span>
                <h2>本节课需要处理的学情依据</h2>
                <p>只保留会影响本节教学决策的信号，完整分析仍在数据中心查看。</p>
              </div>
              <div class="heading-actions">
                <span>{{ lessonDraft.evidenceIds.length }} 项已纳入</span>
                <button type="button" @click="goToDataCenter">查看完整学情 →</button>
              </div>
            </div>

            <div v-if="evidenceSignals.length" class="evidence-grid">
              <button
                v-for="signal in evidenceSignals"
                :key="signal.id"
                type="button"
                class="evidence-card"
                :class="[
                  `is-${signal.tone}`,
                  { selected: lessonDraft.evidenceIds.includes(signal.id) },
                ]"
                @click="toggleEvidence(signal.id)"
              >
                <span class="evidence-check">✓</span>
                <div class="evidence-topline">
                  <span>{{ signal.category }}</span>
                  <b>{{ signal.value }}</b>
                </div>
                <strong>{{ signal.title }}</strong>
                <p>{{ signal.detail }}</p>
                <small>{{ signal.action }}</small>
              </button>
            </div>
            <div v-else class="compact-empty">
              当前还没有足够的历史学情，本节课可以先按教学进度建立基础方案。
            </div>
          </section>

          <section class="workspace-card flow-section">
            <div class="section-heading flow-heading">
              <div>
                <span class="section-eyebrow">LESSON FLOW</span>
                <h2>课时流程编排</h2>
                <p>明确每个教学环节中老师做什么、学生做什么，以及需要准备什么。</p>
              </div>
              <div class="flow-total" :class="durationTone">
                <span>已安排</span>
                <strong>{{ totalMinutes }} / {{ lessonDraft.duration }}</strong>
                <small>分钟</small>
              </div>
            </div>

            <div class="duration-track">
              <span :style="{ width: durationProgress + '%' }"></span>
            </div>
            <p class="duration-message" :class="durationTone">{{ durationMessage }}</p>

            <div class="lesson-stages">
              <article
                v-for="(stage, index) in lessonDraft.stages"
                :key="stage.id"
                class="stage-card"
              >
                <div class="stage-order">
                  <span>{{ String(index + 1).padStart(2, '0') }}</span>
                  <i></i>
                </div>
                <div class="stage-content">
                  <div class="stage-header">
                    <select v-model="stage.phase">
                      <option v-for="phase in stagePhases" :key="phase" :value="phase">
                        {{ phase }}
                      </option>
                    </select>
                    <input v-model="stage.title" class="stage-title-input" maxlength="80" />
                    <label class="minute-input">
                      <input v-model.number="stage.minutes" type="number" min="1" max="90" />
                      <span>分钟</span>
                    </label>
                    <div class="stage-actions">
                      <button
                        type="button"
                        :disabled="index === 0"
                        aria-label="上移"
                        @click="moveStage(index, -1)"
                      >
                        ↑
                      </button>
                      <button
                        type="button"
                        :disabled="index === lessonDraft.stages.length - 1"
                        aria-label="下移"
                        @click="moveStage(index, 1)"
                      >
                        ↓
                      </button>
                      <button
                        type="button"
                        :disabled="lessonDraft.stages.length <= 1"
                        class="danger"
                        aria-label="删除环节"
                        @click="removeStage(index)"
                      >
                        ×
                      </button>
                    </div>
                  </div>
                  <div class="stage-detail-grid">
                    <label>
                      <span>教师活动</span>
                      <textarea
                        v-model="stage.teacherAction"
                        rows="2"
                        placeholder="教师如何讲解、演示或追问"
                      ></textarea>
                    </label>
                    <label>
                      <span>学生活动</span>
                      <textarea
                        v-model="stage.studentAction"
                        rows="2"
                        placeholder="学生观察、讨论、推导或作答"
                      ></textarea>
                    </label>
                    <label>
                      <span>材料与检查点</span>
                      <textarea
                        v-model="stage.resource"
                        rows="2"
                        placeholder="课件页、例题、课堂题或板书"
                      ></textarea>
                    </label>
                  </div>
                </div>
              </article>
            </div>

            <div class="flow-footer">
              <button type="button" class="button secondary" @click="addStage">
                + 添加教学环节
              </button>
              <button type="button" class="button soft-primary" @click="regenerateFlow">
                根据当前学情生成流程
              </button>
            </div>
          </section>

          <section v-if="lessonDraft.differentiation.length" class="workspace-card tier-section">
            <div class="section-heading">
              <div>
                <span class="section-eyebrow">DIFFERENTIATED INSTRUCTION</span>
                <h2>分层教学安排</h2>
                <p>不是只给学生贴分数标签，而是明确不同层次在本节课中的任务差异。</p>
              </div>
            </div>
            <div class="tier-grid">
              <label
                v-for="tier in lessonDraft.differentiation"
                :key="tier.label"
                class="tier-card"
              >
                <span class="tier-topline">
                  <strong>{{ tier.label }}</strong>
                  <small>{{ tier.range }} · {{ tier.count }} 人</small>
                </span>
                <textarea v-model="tier.strategy" rows="4"></textarea>
              </label>
            </div>
          </section>
        </main>

        <aside class="workspace-sidebar">
          <section class="side-card checklist-card">
            <div class="side-heading">
              <div>
                <span>READINESS</span>
                <h3>课前检查</h3>
              </div>
              <b>{{ readinessPercent }}%</b>
            </div>
            <div class="checklist">
              <div v-for="item in readinessItems" :key="item.key" :class="{ done: item.done }">
                <span>{{ item.done ? '✓' : '' }}</span>
                <p>
                  <strong>{{ item.label }}</strong
                  ><small>{{ item.description }}</small>
                </p>
              </div>
            </div>
          </section>

          <section class="side-card agent-card">
            <div class="side-heading">
              <div>
                <span>AI COPILOT</span>
                <h3>AI 备课建议</h3>
              </div>
              <button type="button" :disabled="aiLoading" @click="loadAiSuggestion">
                {{ aiLoading ? '分析中' : '重新分析' }}
              </button>
            </div>
            <div v-if="aiSuggestionItems.length" class="suggestion-list">
              <article v-for="(suggestion, index) in aiSuggestionItems" :key="index">
                <span>{{ index + 1 }}</span>
                <p>{{ suggestion }}</p>
                <button type="button" @click="applySuggestion(suggestion)">加入备课备注</button>
              </article>
            </div>
            <div v-else class="side-empty">暂时没有 AI 建议，可点击“重新分析”。</div>
            <label class="lesson-notes">
              <span>备课备注</span>
              <textarea
                v-model="lessonDraft.notes"
                rows="4"
                placeholder="记录需要特别提醒自己的教学细节"
              ></textarea>
            </label>
          </section>

          <section class="side-card package-card">
            <div class="side-heading">
              <div>
                <span>LESSON PACKAGE</span>
                <h3>本节课材料包</h3>
              </div>
            </div>
            <div class="material-list">
              <article v-for="material in materialItems" :key="material.key">
                <span class="material-icon">{{ material.icon }}</span>
                <div>
                  <strong>{{ material.title }}</strong>
                  <small>{{ material.description }}</small>
                </div>
                <span class="material-state" :class="{ ready: material.ready }">
                  {{ material.ready ? '已准备' : '待准备' }}
                </span>
                <button type="button" @click="openMaterial(material.key)">
                  {{ material.action }}
                </button>
                <button
                  type="button"
                  class="material-check"
                  @click="toggleMaterialReady(material.key)"
                >
                  {{ material.ready ? '取消完成' : '标记完成' }}
                </button>
              </article>
            </div>
          </section>

          <section class="side-card schedule-card">
            <div class="side-heading">
              <div>
                <span>SCHEDULE</span>
                <h3>近期教学安排</h3>
              </div>
              <button type="button" :disabled="calendarSyncing" @click="addToCalendar">
                {{ calendarSyncing ? '添加中' : '+ 加入日历' }}
              </button>
            </div>
            <div v-if="recentTimeline.length" class="timeline-list">
              <article v-for="item in recentTimeline" :key="item.type + '-' + item.id">
                <span>{{ item.date ? formatShortDate(item.date) : '待定' }}</span>
                <div>
                  <strong>{{ item.title }}</strong
                  ><small>{{ item.typeLabel }} · {{ item.statusLabel }}</small>
                </div>
              </article>
            </div>
            <div v-else class="side-empty">还没有近期教学安排。</div>
          </section>

          <div class="sidebar-actions">
            <button type="button" class="button secondary" @click="openExport">预览完整教案</button>
            <button type="button" class="button primary" @click="goLive">进入实时课堂</button>
          </div>
        </aside>
      </div>
    </template>

    <div v-else class="workspace-empty">
      <span>—</span>
      <strong>还没有可用于备课的班级</strong>
      <p>请先创建班级并导入学生，再开始建立备课方案。</p>
    </div>

    <el-dialog v-model="exportDialog" title="本节课教案预览" width="760px" class="plan-dialog">
      <pre class="plan-preview">{{ exportText }}</pre>
      <template #footer>
        <el-button @click="exportDialog = false">关闭</el-button>
        <el-button type="primary" @click="copyPlan">复制教案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addCalendarPlan,
  getDashboardClasses,
  getPreLessonOverview,
  getPreLessonSuggestion,
  getTimeline,
  type PreLessonOverview,
  type TimelineDTO,
  type WeekItem,
} from '@/features/teaching/api/dashboard'
import { getApiErrorMessage } from '@/shared/api/errors'

type LessonStatus = 'DRAFT' | 'READY'
type MaterialKey = 'preview' | 'questions' | 'homework'

interface LessonStage {
  id: string
  phase: string
  title: string
  minutes: number
  teacherAction: string
  studentAction: string
  resource: string
}

interface DifferentiationItem {
  label: string
  range: string
  count: number
  strategy: string
}

interface LessonDraft {
  classId: number
  topic: string
  plannedDate: string
  duration: number
  knowledgePoints: string[]
  objectives: string[]
  evidenceIds: string[]
  stages: LessonStage[]
  differentiation: DifferentiationItem[]
  materialReady: Record<MaterialKey, boolean>
  notes: string
  status: LessonStatus
}

interface EvidenceSignal {
  id: string
  category: string
  title: string
  value: string
  detail: string
  action: string
  tone: 'critical' | 'warning' | 'info' | 'positive'
}

const route = useRoute()
const router = useRouter()
const classList = ref<Array<{ id: number; name: string }>>([])
const selectedClass = ref<number | null>(null)
const data = ref<PreLessonOverview | null>(null)
const timeline = ref<TimelineDTO | null>(null)
const loading = ref(false)
const aiLoading = ref(false)
const calendarSyncing = ref(false)
const aiSuggestion = ref('')
const draftDirty = ref(false)
const hydratingDraft = ref(false)
const lastSavedAt = ref<Date | null>(null)
const exportDialog = ref(false)
let saveTimer: ReturnType<typeof setTimeout> | null = null

const stagePhases = ['导入', '复习', '讲解', '示例', '练习', '互动', '总结', '作业']

const lessonDraft = ref<LessonDraft>(createBlankDraft(0))

function createStage(
  phase: string,
  title: string,
  minutes: number,
  teacherAction: string,
  studentAction: string,
  resource: string,
): LessonStage {
  return {
    id: `${Date.now()}-${Math.random().toString(16).slice(2)}`,
    phase,
    title,
    minutes,
    teacherAction,
    studentAction,
    resource,
  }
}

function tomorrowString() {
  const date = new Date()
  date.setDate(date.getDate() + 1)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function createBlankDraft(classId: number): LessonDraft {
  return {
    classId,
    topic: '',
    plannedDate: tomorrowString(),
    duration: 45,
    knowledgePoints: [],
    objectives: ['理解本节课核心概念并能准确表述', '能够运用知识点解决一道典型问题'],
    evidenceIds: [],
    stages: [],
    differentiation: [],
    materialReady: { preview: false, questions: false, homework: false },
    notes: '',
    status: 'DRAFT',
  }
}

function createDefaultDraft(
  classId: number,
  overview: PreLessonOverview,
  schedule: TimelineDTO | null,
) {
  const draft = createBlankDraft(classId)
  const weakPoints = overview.weakPoints.slice(0, 2)
  const plannedItem = schedule?.weeks
    ?.flatMap((week) => week.items)
    .find((item) => item.type === 'plan' && item.status === 'PLANNED')
  const mainPoint = weakPoints[0]?.name || '本单元核心知识'
  const secondaryPoint = weakPoints[1]?.name

  draft.topic = plannedItem?.title || `${mainPoint}巩固与应用`
  draft.plannedDate = plannedItem?.date || tomorrowString()
  draft.knowledgePoints = weakPoints.map((item) => item.name)
  if (!draft.knowledgePoints.length) draft.knowledgePoints = [mainPoint]
  draft.evidenceIds = weakPoints.map((_, index) => `knowledge-${index}`)
  if (overview.warningCount > 0) draft.evidenceIds.push('warning-students')
  draft.objectives = [
    `能够解释${mainPoint}的核心概念与常见误区`,
    `能够独立完成一道${secondaryPoint || mainPoint}的典型应用题`,
  ]
  draft.stages = buildSuggestedStages(mainPoint, secondaryPoint)
  draft.differentiation = overview.tieredGroups.map((tier) => ({
    label: tier.label,
    range: tier.range,
    count: tier.count,
    strategy: tier.suggestion,
  }))
  return draft
}

function buildSuggestedStages(mainPoint: string, secondaryPoint?: string) {
  return [
    createStage(
      '导入',
      '从典型错误进入本节课',
      5,
      `展示一条${mainPoint}的高频错误，追问错误原因。`,
      '独立判断后与同桌交流，给出修改意见。',
      '数据中心典型错题 1 条',
    ),
    createStage(
      '复习',
      `${mainPoint}关键概念回顾`,
      8,
      '用对比例子梳理前置概念，明确容易混淆的边界。',
      '完成概念对照表，并用自己的话解释差异。',
      '知识点对照卡',
    ),
    createStage(
      '讲解',
      `${secondaryPoint || mainPoint}重点突破`,
      15,
      '分步骤讲解典型问题，显式展示思考过程与检查方法。',
      '跟随推导并在关键步骤作出预测。',
      '课件、板书与示例代码',
    ),
    createStage(
      '互动',
      '随堂检测与即时纠偏',
      12,
      '发布两道梯度题，根据实时正确率决定是否补讲。',
      '独立作答，提交后标记仍不理解的知识点。',
      '课堂题组 2—3 题',
    ),
    createStage(
      '总结',
      '方法归纳与课后任务',
      5,
      '总结本节判断方法，说明分层作业要求。',
      '用一句话总结最容易出错的步骤。',
      '小结卡与课后作业草稿',
    ),
  ]
}

function draftStorageKey(classId: number) {
  return `edumind:lesson-preparation:${classId}`
}

function hydrateDraft() {
  if (!selectedClass.value || !data.value) return
  hydratingDraft.value = true
  const stored = localStorage.getItem(draftStorageKey(selectedClass.value))
  if (stored) {
    try {
      const parsed = JSON.parse(stored) as LessonDraft
      lessonDraft.value = {
        ...createBlankDraft(selectedClass.value),
        ...parsed,
        classId: selectedClass.value,
        materialReady: {
          ...createBlankDraft(selectedClass.value).materialReady,
          ...parsed.materialReady,
        },
      }
    } catch {
      lessonDraft.value = createDefaultDraft(selectedClass.value, data.value, timeline.value)
    }
  } else {
    lessonDraft.value = createDefaultDraft(selectedClass.value, data.value, timeline.value)
  }
  aiSuggestion.value = data.value.aiSuggestion || ''
  draftDirty.value = false
  queueMicrotask(() => {
    hydratingDraft.value = false
  })
}

async function loadClasses() {
  try {
    const response = await getDashboardClasses()
    classList.value = response.data || []
    if (!classList.value.length) return
    const queryClassId = Number(route.query.classId)
    const matchedClass = classList.value.find((item) => item.id === queryClassId)
    selectedClass.value = matchedClass?.id || classList.value[0]?.id || null
    await load()
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '班级列表加载失败'))
  }
}

async function load() {
  if (!selectedClass.value) return
  if (draftDirty.value && lessonDraft.value.classId === selectedClass.value) {
    saveLocalDraft(false)
  }
  loading.value = true
  try {
    const [overviewResponse, timelineResponse] = await Promise.all([
      getPreLessonOverview(selectedClass.value),
      getTimeline(selectedClass.value, 12),
    ])
    data.value = overviewResponse.data
    timeline.value = timelineResponse.data
    hydrateDraft()
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '备课数据加载失败'))
  } finally {
    loading.value = false
  }
}

async function handleClassChange() {
  data.value = null
  timeline.value = null
  await router.replace({ query: { ...route.query, classId: selectedClass.value || undefined } })
  await load()
}

watch(
  lessonDraft,
  () => {
    if (hydratingDraft.value || !selectedClass.value) return
    draftDirty.value = true
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(() => saveLocalDraft(false), 1200)
  },
  { deep: true },
)

function saveLocalDraft(showMessage: boolean) {
  if (!selectedClass.value) return
  localStorage.setItem(draftStorageKey(selectedClass.value), JSON.stringify(lessonDraft.value))
  lastSavedAt.value = new Date()
  draftDirty.value = false
  if (showMessage) ElMessage.success('备课草稿已保存到当前浏览器')
}

const lastSavedLabel = computed(() => {
  if (!lastSavedAt.value) return '本机草稿会自动保存'
  return `已于 ${lastSavedAt.value.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })} 保存到本机`
})

const evidenceSignals = computed<EvidenceSignal[]>(() => {
  const overview = data.value
  if (!overview) return []
  const signals: EvidenceSignal[] = overview.weakPoints.slice(0, 3).map((point, index) => ({
    id: `knowledge-${index}`,
    category: index === 0 ? '首要薄弱点' : '知识点信号',
    title: point.name,
    value: `${point.mastery}%`,
    detail: `掌握度 ${point.mastery}%，历史错误 ${point.errorCount} 次。`,
    action: point.mastery < 60 ? '建议安排概念复习与当堂检测' : '建议通过例题确认是否真正掌握',
    tone: point.mastery < 50 ? 'critical' : point.mastery < 70 ? 'warning' : 'info',
  }))

  if (overview.warningCount > 0) {
    signals.push({
      id: 'warning-students',
      category: '学生分层',
      title: `${overview.warningCount} 名学生需要额外支架`,
      value: `${overview.warningCount} 人`,
      detail: `班级共 ${overview.totalStudents} 人，部分学生累计成绩低于当前关注线。`,
      action: '建议准备基础任务和可选提示卡',
      tone: 'critical',
    })
  }
  if (overview.liveSessionCount > 0) {
    signals.push({
      id: 'live-feedback',
      category: '课堂反馈',
      title: '上次课堂互动表现',
      value: `${overview.liveAvgCorrectRate}%`,
      detail: `累计 ${overview.liveSessionCount} 次课堂，平均参与率 ${overview.participationRate}%。`,
      action:
        overview.liveAvgCorrectRate < 70
          ? '建议降低首道检测题难度并增加反馈时间'
          : '可以增加迁移型问题',
      tone: overview.liveAvgCorrectRate < 60 ? 'warning' : 'positive',
    })
  }
  return signals
})

function toggleEvidence(id: string) {
  const index = lessonDraft.value.evidenceIds.indexOf(id)
  if (index >= 0) lessonDraft.value.evidenceIds.splice(index, 1)
  else lessonDraft.value.evidenceIds.push(id)
}

function updateKnowledgePoints(event: Event) {
  lessonDraft.value.knowledgePoints = (event.target as HTMLInputElement).value
    .split(/[、,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function addObjective() {
  lessonDraft.value.objectives.push('')
}

function removeObjective(index: number) {
  lessonDraft.value.objectives.splice(index, 1)
}

function addStage() {
  lessonDraft.value.stages.push(createStage('练习', '新的教学环节', 5, '', '', ''))
}

function removeStage(index: number) {
  lessonDraft.value.stages.splice(index, 1)
}

function moveStage(index: number, direction: number) {
  const target = index + direction
  if (target < 0 || target >= lessonDraft.value.stages.length) return
  const [stage] = lessonDraft.value.stages.splice(index, 1)
  if (stage) lessonDraft.value.stages.splice(target, 0, stage)
}

async function regenerateFlow() {
  try {
    await ElMessageBox.confirm(
      '将按当前薄弱知识点重新生成课时流程，现有流程会被替换。是否继续？',
      '重新生成流程',
      { confirmButtonText: '重新生成', cancelButtonText: '保留现有流程', type: 'warning' },
    )
    const [mainPoint, secondaryPoint] = lessonDraft.value.knowledgePoints
    lessonDraft.value.stages = buildSuggestedStages(mainPoint || '本单元核心知识', secondaryPoint)
    ElMessage.success('已根据当前学情生成新的课时流程')
  } catch {
    // 取消时保持当前流程。
  }
}

const totalMinutes = computed(() =>
  lessonDraft.value.stages.reduce((sum, stage) => sum + Math.max(0, Number(stage.minutes) || 0), 0),
)
const durationProgress = computed(() => {
  if (!lessonDraft.value.duration) return 0
  return Math.min(100, Math.round((totalMinutes.value / lessonDraft.value.duration) * 100))
})
const durationTone = computed(() => {
  if (totalMinutes.value > lessonDraft.value.duration) return 'over'
  if (totalMinutes.value < lessonDraft.value.duration) return 'under'
  return 'balanced'
})
const durationMessage = computed(() => {
  const difference = lessonDraft.value.duration - totalMinutes.value
  if (difference > 0) return `还有 ${difference} 分钟尚未安排，可以留给讨论、纠错或机动处理。`
  if (difference < 0)
    return `当前流程超出课时 ${Math.abs(difference)} 分钟，建议压缩或删除部分环节。`
  return '课时分配刚好覆盖当前设置的上课时长。'
})

const materialItems = computed(() => [
  {
    key: 'preview' as MaterialKey,
    icon: '预',
    title: '课前预习',
    description: '导读、自测题与讨论问题',
    action: '创建预习',
    ready: lessonDraft.value.materialReady.preview,
  },
  {
    key: 'questions' as MaterialKey,
    icon: '题',
    title: '课堂题组',
    description: '导入题、检测题与迁移题',
    action: '准备题目',
    ready: lessonDraft.value.materialReady.questions,
  },
  {
    key: 'homework' as MaterialKey,
    icon: '练',
    title: '课后作业',
    description: '分层练习与截止安排',
    action: '创建草稿',
    ready: lessonDraft.value.materialReady.homework,
  },
])

function toggleMaterialReady(key: MaterialKey) {
  lessonDraft.value.materialReady[key] = !lessonDraft.value.materialReady[key]
}

function openMaterial(key: MaterialKey) {
  const classId = selectedClass.value
  const knowledgePoint = lessonDraft.value.knowledgePoints[0] || ''
  if (key === 'preview') {
    void router.push({
      path: '/teacher/preview/create',
      query: { classId: classId || undefined, knowledgePoint: knowledgePoint || undefined },
    })
    return
  }
  if (key === 'questions') {
    void router.push({ path: '/teacher/docs', query: { classId: classId || undefined } })
    return
  }
  void router.push({ path: '/teacher/tasks', query: { classId: classId || undefined } })
}

const readinessItems = computed(() => [
  {
    key: 'context',
    label: '课程信息',
    description: '主题、日期和时长完整',
    done:
      !!lessonDraft.value.topic.trim() &&
      !!lessonDraft.value.plannedDate &&
      lessonDraft.value.duration > 0,
  },
  {
    key: 'objectives',
    label: '教学目标',
    description: '至少有一个可检验目标',
    done: lessonDraft.value.objectives.some((item) => item.trim()),
  },
  {
    key: 'evidence',
    label: '学情依据',
    description: '至少纳入一项真实信号',
    done: lessonDraft.value.evidenceIds.length > 0 || evidenceSignals.value.length === 0,
  },
  {
    key: 'flow',
    label: '教学流程',
    description: '所有环节均写明师生活动',
    done:
      lessonDraft.value.stages.length > 0 &&
      lessonDraft.value.stages.every(
        (stage) => stage.title.trim() && stage.teacherAction.trim() && stage.studentAction.trim(),
      ),
  },
  {
    key: 'duration',
    label: '时间校验',
    description: '流程总时长与课时一致',
    done: totalMinutes.value === lessonDraft.value.duration,
  },
  {
    key: 'materials',
    label: '教学材料',
    description: '至少准备一类配套材料',
    done: Object.values(lessonDraft.value.materialReady).some(Boolean),
  },
])
const readyCount = computed(() => readinessItems.value.filter((item) => item.done).length)
const readinessPercent = computed(() =>
  Math.round((readyCount.value / readinessItems.value.length) * 100),
)
const readinessStyle = computed(() => ({
  '--readiness-progress': `${readinessPercent.value * 3.6}deg`,
}))
const readinessMessage = computed(() => {
  const nextItem = readinessItems.value.find((item) => !item.done)
  return nextItem ? `下一步：完成“${nextItem.label}”` : '目标、流程与材料已经准备完整。'
})

function markLessonReady() {
  if (readinessPercent.value < 100) {
    ElMessage.warning(readinessMessage.value)
    return
  }
  lessonDraft.value.status = 'READY'
  saveLocalDraft(false)
  ElMessage.success('本节课已标记为准备完成')
}

const aiSuggestionItems = computed(() =>
  aiSuggestion.value
    .split(/\n+/)
    .map((item) => item.replace(/^\s*[\d一二三四五]+[.、）)]?\s*/, '').trim())
    .filter(Boolean)
    .slice(0, 5),
)

async function loadAiSuggestion() {
  if (!selectedClass.value) return
  aiLoading.value = true
  try {
    const response = await getPreLessonSuggestion(selectedClass.value)
    aiSuggestion.value = response.data.suggestion || ''
    ElMessage.success('AI 已结合最新班级数据更新建议')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, 'AI 分析暂时不可用'))
  } finally {
    aiLoading.value = false
  }
}

function applySuggestion(suggestion: string) {
  if (lessonDraft.value.notes.includes(suggestion)) {
    ElMessage.info('这条建议已经加入备课备注')
    return
  }
  lessonDraft.value.notes = [lessonDraft.value.notes.trim(), `• ${suggestion}`]
    .filter(Boolean)
    .join('\n')
  ElMessage.success('建议已加入备课备注')
}

const recentTimeline = computed(() =>
  (timeline.value?.weeks || [])
    .flatMap((week) =>
      week.items.map((item) => ({
        ...item,
        statusLabel: timelineStatusLabel(item),
      })),
    )
    .slice(0, 5),
)

function timelineStatusLabel(item: WeekItem) {
  if (item.status === 'COMPLETED') return '已完成'
  if (item.status === 'ACTIVE') return '进行中'
  if (item.status === 'PLANNED') return '待准备'
  return item.detail || '已记录'
}

function formatShortDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.slice(5)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

async function addToCalendar() {
  if (!selectedClass.value || !lessonDraft.value.topic.trim()) {
    ElMessage.warning('请先填写本节课主题')
    return
  }
  calendarSyncing.value = true
  try {
    await addCalendarPlan({
      classId: selectedClass.value,
      weekNumber: 0,
      plannedDate: lessonDraft.value.plannedDate || null,
      topic: lessonDraft.value.topic.trim(),
      knowledgePoints: lessonDraft.value.knowledgePoints.join('、') || null,
    })
    const response = await getTimeline(selectedClass.value, 12)
    timeline.value = response.data
    ElMessage.success('已加入教学日历')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '加入教学日历失败'))
  } finally {
    calendarSyncing.value = false
  }
}

const exportText = computed(() => {
  const objectives = lessonDraft.value.objectives
    .filter((item) => item.trim())
    .map((item, index) => `${index + 1}. ${item}`)
    .join('\n')
  const stages = lessonDraft.value.stages
    .map(
      (stage, index) =>
        `${index + 1}. 【${stage.phase}｜${stage.minutes}分钟】${stage.title}\n` +
        `   教师活动：${stage.teacherAction || '待补充'}\n` +
        `   学生活动：${stage.studentAction || '待补充'}\n` +
        `   材料与检查点：${stage.resource || '待补充'}`,
    )
    .join('\n\n')
  const tiers = lessonDraft.value.differentiation
    .map((tier) => `- ${tier.label}（${tier.count}人）：${tier.strategy}`)
    .join('\n')
  return [
    `《${lessonDraft.value.topic || '未命名课程'}》备课方案`,
    `班级：${data.value?.className || ''}`,
    `日期：${lessonDraft.value.plannedDate || '待定'}｜课时：${lessonDraft.value.duration}分钟`,
    `知识点：${lessonDraft.value.knowledgePoints.join('、') || '待补充'}`,
    '',
    '一、教学目标',
    objectives || '待补充',
    '',
    '二、教学流程',
    stages || '待补充',
    '',
    '三、分层教学安排',
    tiers || '待补充',
    '',
    '四、备课备注',
    lessonDraft.value.notes || '无',
  ].join('\n')
})

function openExport() {
  exportDialog.value = true
}

async function copyPlan() {
  try {
    await navigator.clipboard.writeText(exportText.value)
    ElMessage.success('完整教案已复制')
  } catch {
    ElMessage.warning('复制失败，请在预览中手动选择文本')
  }
}

function goToDataCenter() {
  void router.push({ path: '/teacher/data', query: { classId: selectedClass.value || undefined } })
}

function goLive() {
  if (!selectedClass.value) return
  saveLocalDraft(false)
  void router.push(`/teacher/live/${selectedClass.value}`)
}

onMounted(loadClasses)
onBeforeUnmount(() => {
  if (saveTimer) clearTimeout(saveTimer)
  if (draftDirty.value) saveLocalDraft(false)
})
</script>

<style scoped src="../styles/PreLessonDashboard.css"></style>
