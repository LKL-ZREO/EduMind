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
} from '../../api/dashboard'
import { getApiErrorMessage } from '../../api/errors'

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

<style scoped>
.preparation-page {
  --ink: #182039;
  --muted: #7f879c;
  --line: #e3e6ef;
  --surface: #ffffff;
  --primary: #625bd4;
  min-height: 100%;
  padding: 29px clamp(20px, 3vw, 42px) 56px;
  color: var(--ink);
  background: radial-gradient(circle at 87% 2%, rgba(102, 92, 225, 0.08), transparent 22%), #f5f7fb;
  font-family:
    Inter,
    'PingFang SC',
    'Microsoft YaHei',
    -apple-system,
    BlinkMacSystemFont,
    sans-serif;
}

button,
input,
select,
textarea {
  font: inherit;
}

.workspace-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 25px;
  max-width: 1440px;
  margin: 0 auto 24px;
}

.page-eyebrow,
.section-eyebrow,
.side-heading > div > span {
  color: #7169d8;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.17em;
}

.workspace-header h1 {
  margin: 6px 0 5px;
  font-size: 27px;
  letter-spacing: -0.035em;
}

.workspace-header p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: flex-end;
  gap: 9px;
}

.class-selector {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.class-selector > span {
  color: #8c93a6;
  font-size: 9px;
}

.class-selector select,
.button {
  height: 38px;
  border-radius: 9px;
}

.class-selector select {
  min-width: 170px;
  padding: 0 34px 0 12px;
  border: 1px solid #dfe2ec;
  color: #3e465a;
  font-size: 11px;
  background: #fff;
}

.button {
  padding: 0 15px;
  border: 1px solid transparent;
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
  transition:
    transform 0.16s,
    box-shadow 0.16s,
    opacity 0.16s;
}

.button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.button:not(:disabled):hover {
  transform: translateY(-1px);
}

.button.primary {
  color: #fff;
  background: linear-gradient(135deg, #675de1, #526edb);
  box-shadow: 0 7px 17px rgba(91, 84, 207, 0.18);
}

.button.secondary {
  border-color: #dde0e9;
  color: #586074;
  background: #fff;
}

.button.soft-primary {
  color: #5c56c5;
  background: #eeedfc;
}

.workspace-loading,
.workspace-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 430px;
  text-align: center;
}

.loading-spinner {
  width: 34px;
  height: 34px;
  margin-bottom: 17px;
  border: 3px solid #e2e1f7;
  border-top-color: #665ed5;
  border-radius: 50%;
  animation: spin 0.85s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.workspace-loading strong,
.workspace-empty strong {
  font-size: 15px;
}

.workspace-loading p,
.workspace-empty p {
  margin: 7px 0 0;
  color: #9299aa;
  font-size: 11px;
}

.lesson-brief {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  max-width: 1440px;
  margin: 0 auto 20px;
  overflow: hidden;
  border: 1px solid #e0e3ed;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 10px 35px rgba(40, 48, 85, 0.045);
}

.brief-main {
  min-width: 0;
  padding: 25px 28px;
}

.brief-status-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 15px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 6px 9px;
  border-radius: 999px;
  color: #777f91;
  font-size: 9px;
  font-weight: 750;
  background: #f0f1f5;
}

.status-chip i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #9ca2af;
}

.status-chip.ready {
  color: #2e7c64;
  background: #e9f7f2;
}

.status-chip.ready i {
  background: #3ca783;
  box-shadow: 0 0 0 3px rgba(60, 167, 131, 0.12);
}

.save-state {
  color: #9aa0b0;
  font-size: 9px;
}

.lesson-title-field {
  display: flex;
  align-items: center;
  gap: 16px;
}

.lesson-title-field label {
  flex: 0 0 auto;
  color: #7770d7;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.05em;
}

.lesson-title-field input {
  width: 100%;
  padding: 2px 0 7px;
  border: 0;
  border-bottom: 1px solid #e1e3eb;
  outline: none;
  color: #20283f;
  font-size: clamp(19px, 2vw, 25px);
  font-weight: 720;
  letter-spacing: -0.025em;
  background: transparent;
}

.lesson-title-field input:focus {
  border-color: #6b63d8;
}

.brief-fields {
  display: grid;
  grid-template-columns: 150px 120px minmax(220px, 1fr);
  gap: 13px;
  margin-top: 21px;
}

.brief-fields label,
.stage-detail-grid label,
.lesson-notes,
.tier-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.brief-fields label > span:first-child,
.field-label,
.stage-detail-grid label > span,
.lesson-notes > span {
  color: #81889a;
  font-size: 9px;
  font-weight: 700;
}

.brief-fields input,
.input-with-unit,
.stage-header select,
.stage-title-input,
.minute-input,
.stage-detail-grid textarea,
.tier-card textarea,
.lesson-notes textarea {
  border: 1px solid #dfe2ea;
  border-radius: 8px;
  color: #444c60;
  background: #fafbfc;
  outline: none;
}

.brief-fields > label > input,
.input-with-unit {
  height: 34px;
  padding: 0 10px;
  box-sizing: border-box;
  font-size: 10px;
}

.input-with-unit {
  display: flex;
  align-items: center;
}

.input-with-unit input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: none;
  background: transparent;
}

.input-with-unit b {
  color: #999faf;
  font-size: 9px;
  font-weight: 500;
}

.objectives-row {
  display: grid;
  grid-template-columns: 155px minmax(0, 1fr);
  gap: 12px;
  margin-top: 19px;
  padding-top: 17px;
  border-top: 1px solid #eceef3;
}

.objectives-row > div:first-child p {
  margin: 4px 0 0;
  color: #a0a5b4;
  font-size: 8px;
  line-height: 1.5;
}

.objective-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.objective-tags > span {
  display: flex;
  align-items: center;
  min-width: 260px;
  padding: 6px 7px 6px 10px;
  border-radius: 8px;
  background: #f2f2fa;
}

.objective-tags input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  color: #585474;
  font-size: 9px;
  background: transparent;
}

.objective-tags button,
.heading-actions button,
.side-heading button,
.stage-actions button,
.confusion-link {
  border: 0;
  background: transparent;
  cursor: pointer;
}

.objective-tags > span button {
  color: #a3a7b4;
}

.objective-tags .add-objective {
  padding: 6px 9px;
  border: 1px dashed #c9c7e8;
  border-radius: 8px;
  color: #625cc1;
  font-size: 9px;
  background: #fafaff;
}

.readiness-summary {
  display: grid;
  grid-template-columns: 75px 1fr;
  align-content: center;
  align-items: center;
  gap: 15px;
  padding: 25px;
  border-left: 1px solid #e5e7ef;
  background: linear-gradient(145deg, #f8f8fe, #f3f5fc);
}

.readiness-ring {
  --readiness-progress: 0deg;
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: conic-gradient(#655dd5 var(--readiness-progress), #e2e4f1 0);
}

.readiness-ring::before {
  grid-area: 1 / 1;
  width: 58px;
  height: 58px;
  border-radius: 50%;
  content: '';
  background: #f7f8fd;
}

.readiness-ring span {
  z-index: 1;
  display: flex;
  align-items: baseline;
}

.readiness-ring strong {
  font-size: 20px;
}

.readiness-ring small {
  color: #8e94a6;
  font-size: 8px;
}

.readiness-summary > div:nth-child(2) {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-label {
  color: #858c9e;
  font-size: 9px;
}

.readiness-summary > div strong {
  font-size: 12px;
}

.readiness-summary p {
  margin: 0;
  color: #9097a9;
  font-size: 8px;
  line-height: 1.55;
}

.ready-button {
  grid-column: 1 / -1;
  height: 35px;
  border: 0;
  border-radius: 8px;
  color: #fff;
  font-size: 9px;
  font-weight: 700;
  background: #615ad0;
  cursor: pointer;
}

.ready-button:disabled {
  color: #9ca1b0;
  background: #e5e7ee;
  cursor: not-allowed;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  align-items: start;
  gap: 19px;
  max-width: 1440px;
  margin: 0 auto;
}

.workspace-main,
.workspace-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.workspace-card,
.side-card {
  border: 1px solid var(--line);
  background: var(--surface);
  box-shadow: 0 8px 28px rgba(38, 46, 82, 0.04);
}

.workspace-card {
  padding: 23px;
  border-radius: 16px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.section-heading h2 {
  margin: 5px 0 4px;
  font-size: 17px;
  letter-spacing: -0.02em;
}

.section-heading p {
  margin: 0;
  color: #8b92a5;
  font-size: 9px;
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 11px;
  color: #9298a9;
  font-size: 9px;
}

.heading-actions button {
  padding: 0;
  color: #615bca;
  font-size: 9px;
}

.evidence-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.evidence-card {
  position: relative;
  min-height: 145px;
  padding: 15px;
  overflow: hidden;
  border: 1px solid #e4e6ed;
  border-radius: 12px;
  color: #333b50;
  text-align: left;
  background: #fafbfc;
  cursor: pointer;
  transition:
    transform 0.16s,
    border-color 0.16s,
    box-shadow 0.16s;
}

.evidence-card:hover {
  transform: translateY(-2px);
}

.evidence-card.selected {
  border-color: #7972dc;
  box-shadow: 0 0 0 3px rgba(105, 97, 210, 0.08);
}

.evidence-card.is-critical.selected {
  border-color: #dc7a78;
  box-shadow: 0 0 0 3px rgba(211, 99, 97, 0.07);
}

.evidence-check {
  position: absolute;
  top: 11px;
  right: 11px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  border: 1px solid #d7dae4;
  border-radius: 50%;
  color: transparent;
  font-size: 8px;
  background: #fff;
}

.evidence-card.selected .evidence-check {
  border-color: #675fd2;
  color: #fff;
  background: #675fd2;
}

.evidence-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 25px;
}

.evidence-topline span {
  color: #969cad;
  font-size: 8px;
  font-weight: 750;
  letter-spacing: 0.04em;
}

.evidence-topline b {
  color: #565fc1;
  font-size: 12px;
}

.evidence-card.is-critical .evidence-topline b,
.evidence-card.is-warning .evidence-topline b {
  color: #c25d5d;
}

.evidence-card > strong {
  display: block;
  margin-top: 13px;
  font-size: 11px;
  line-height: 1.45;
}

.evidence-card > p {
  margin: 7px 0;
  color: #7f8799;
  font-size: 9px;
  line-height: 1.55;
}

.evidence-card > small {
  display: block;
  padding-top: 7px;
  border-top: 1px solid #eceef3;
  color: #6e7392;
  font-size: 8px;
  line-height: 1.5;
}

.flow-heading {
  align-items: center;
}

.flow-total {
  display: flex;
  align-items: baseline;
  gap: 4px;
  padding: 9px 12px;
  border-radius: 9px;
  color: #4b806f;
  background: #edf8f4;
}

.flow-total span,
.flow-total small {
  font-size: 8px;
}

.flow-total strong {
  font-size: 15px;
}

.flow-total.over {
  color: #b75a5a;
  background: #fff0ef;
}

.flow-total.under {
  color: #9c6a25;
  background: #fff6e8;
}

.duration-track {
  height: 4px;
  margin-bottom: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #eceef3;
}

.duration-track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #5eae8f;
  transition: width 0.2s;
}

.duration-message {
  margin: 0 0 19px;
  color: #43816a;
  font-size: 8px;
  text-align: right;
}

.duration-message.over {
  color: #bd6262;
}

.duration-message.under {
  color: #a77731;
}

.lesson-stages {
  display: flex;
  flex-direction: column;
}

.stage-card {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
}

.stage-order {
  display: flex;
  align-items: center;
  flex-direction: column;
}

.stage-order span {
  display: grid;
  flex: 0 0 26px;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  color: #625cc3;
  font:
    750 8px/1 ui-monospace,
    monospace;
  background: #ecebfb;
}

.stage-order i {
  width: 1px;
  height: 100%;
  min-height: 25px;
  background: #e2e4eb;
}

.stage-card:last-child .stage-order i {
  display: none;
}

.stage-content {
  margin-bottom: 13px;
  padding: 13px;
  border: 1px solid #e3e6ed;
  border-radius: 11px;
  background: #fbfcfd;
}

.stage-header {
  display: grid;
  grid-template-columns: 70px minmax(150px, 1fr) 86px auto;
  align-items: center;
  gap: 8px;
}

.stage-header select,
.stage-title-input,
.minute-input {
  height: 31px;
  box-sizing: border-box;
  font-size: 9px;
}

.stage-header select,
.stage-title-input {
  padding: 0 8px;
}

.stage-title-input {
  font-weight: 700;
}

.minute-input {
  display: flex;
  align-items: center;
  padding: 0 7px;
}

.minute-input input {
  min-width: 0;
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
}

.minute-input span {
  color: #9aa0af;
  font-size: 8px;
}

.stage-actions {
  display: flex;
  gap: 3px;
}

.stage-actions button {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  color: #858c9d;
  font-size: 10px;
}

.stage-actions button:hover:not(:disabled) {
  background: #eeeff4;
}

.stage-actions button.danger {
  color: #bf6666;
}

.stage-actions button:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.stage-detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.stage-detail-grid textarea,
.tier-card textarea,
.lesson-notes textarea {
  padding: 8px;
  resize: vertical;
  font-size: 9px;
  line-height: 1.55;
}

.flow-footer {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-top: 5px;
  padding-top: 16px;
  border-top: 1px solid #eceef3;
}

.tier-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.tier-card {
  padding: 13px;
  border: 1px solid #e3e5ed;
  border-radius: 11px;
  background: #fafbfc;
}

.tier-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.tier-topline strong {
  font-size: 10px;
}

.tier-topline small {
  color: #969cad;
  font-size: 8px;
}

.side-card {
  padding: 18px;
  border-radius: 14px;
}

.side-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 15px;
}

.side-heading h3 {
  margin: 4px 0 0;
  font-size: 13px;
}

.side-heading > b {
  color: #625bd0;
  font-size: 17px;
}

.side-heading button {
  padding: 3px 0;
  color: #625bc8;
  font-size: 8px;
}

.checklist {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.checklist > div {
  display: flex;
  align-items: center;
  gap: 9px;
}

.checklist > div > span {
  display: grid;
  flex: 0 0 18px;
  place-items: center;
  width: 18px;
  height: 18px;
  border: 1px solid #d8dbe5;
  border-radius: 6px;
  color: transparent;
  font-size: 8px;
  background: #fafbfc;
}

.checklist > div.done > span {
  border-color: #4aaa88;
  color: #fff;
  background: #4aaa88;
}

.checklist p {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
}

.checklist strong {
  font-size: 9px;
}

.checklist small {
  color: #9ba1b0;
  font-size: 8px;
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.suggestion-list article {
  display: grid;
  grid-template-columns: 21px minmax(0, 1fr);
  gap: 7px;
  padding: 10px;
  border: 1px solid #e5e4f2;
  border-radius: 9px;
  background: #fafaff;
}

.suggestion-list article > span {
  display: grid;
  place-items: center;
  width: 21px;
  height: 21px;
  border-radius: 6px;
  color: #625bc7;
  font:
    700 8px/1 ui-monospace,
    monospace;
  background: #ebeafd;
}

.suggestion-list p {
  margin: 1px 0 0;
  color: #5d6477;
  font-size: 8px;
  line-height: 1.6;
}

.suggestion-list button {
  grid-column: 2;
  justify-self: start;
  padding: 0;
  border: 0;
  color: #6a64c7;
  font-size: 8px;
  background: transparent;
  cursor: pointer;
}

.lesson-notes {
  margin-top: 13px;
  padding-top: 13px;
  border-top: 1px solid #eceef3;
}

.material-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.material-list article {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e5e7ee;
  border-radius: 10px;
  background: #fafbfc;
}

.material-icon {
  display: grid;
  grid-row: 1 / 3;
  place-items: center;
  width: 31px;
  height: 31px;
  border-radius: 9px;
  color: #5f59bf;
  font-size: 10px;
  font-weight: 750;
  background: #ecebfb;
}

.material-list article > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.material-list strong {
  font-size: 9px;
}

.material-list small {
  color: #999faf;
  font-size: 7px;
}

.material-state {
  padding: 4px 6px;
  border-radius: 999px;
  color: #a06e2a;
  font-size: 7px;
  background: #fff2df;
}

.material-state.ready {
  color: #2e7d64;
  background: #e6f6f0;
}

.material-list article > button {
  grid-column: 2;
  justify-self: start;
  padding: 0;
  border: 0;
  color: #615bc5;
  font-size: 8px;
  background: transparent;
  cursor: pointer;
}

.material-list article > button.material-check {
  grid-column: 3;
  color: #8e95a6;
}

.timeline-list {
  display: flex;
  flex-direction: column;
}

.timeline-list article {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 9px;
  padding: 10px 0;
  border-top: 1px solid #eceef3;
}

.timeline-list article:first-child {
  border-top: 0;
}

.timeline-list article > span {
  padding-top: 2px;
  color: #6c65ca;
  font:
    700 8px/1 ui-monospace,
    monospace;
}

.timeline-list article > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.timeline-list strong {
  overflow: hidden;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-list small {
  color: #9ba1b1;
  font-size: 7px;
}

.side-empty,
.compact-empty {
  padding: 25px 10px;
  color: #9ba1b0;
  font-size: 9px;
  line-height: 1.6;
  text-align: center;
}

.sidebar-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.plan-preview {
  max-height: 62vh;
  margin: 0;
  padding: 18px;
  overflow: auto;
  border: 1px solid #e2e4ec;
  border-radius: 10px;
  color: #394157;
  font:
    12px/1.8 'Microsoft YaHei',
    sans-serif;
  white-space: pre-wrap;
  background: #fafbfc;
}

@media (max-width: 1180px) {
  .evidence-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stage-detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 980px) {
  .workspace-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .lesson-brief,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .readiness-summary {
    grid-template-columns: 70px minmax(0, 1fr) 180px;
    border-top: 1px solid #e5e7ef;
    border-left: 0;
  }

  .ready-button {
    grid-column: auto;
  }

  .workspace-sidebar {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .sidebar-actions {
    grid-column: 1 / -1;
  }
}

@media (max-width: 680px) {
  .preparation-page {
    padding: 20px 12px 40px;
  }

  .header-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }

  .class-selector {
    grid-column: 1 / -1;
  }

  .class-selector select,
  .header-actions .button {
    width: 100%;
  }

  .brief-main {
    padding: 20px 16px;
  }

  .lesson-title-field {
    align-items: flex-start;
    flex-direction: column;
    gap: 7px;
  }

  .brief-fields,
  .objectives-row {
    grid-template-columns: 1fr;
  }

  .readiness-summary {
    grid-template-columns: 65px minmax(0, 1fr);
    padding: 18px 16px;
  }

  .ready-button {
    grid-column: 1 / -1;
  }

  .workspace-card {
    padding: 17px 14px;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .heading-actions {
    width: 100%;
    justify-content: space-between;
  }

  .evidence-grid,
  .tier-grid,
  .workspace-sidebar {
    grid-template-columns: 1fr;
  }

  .stage-card {
    grid-template-columns: 28px minmax(0, 1fr);
  }

  .stage-header {
    grid-template-columns: 72px minmax(0, 1fr) 75px;
  }

  .stage-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }

  .flow-footer {
    flex-direction: column;
  }

  .flow-footer .button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
