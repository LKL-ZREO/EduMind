<template>
  <div class="pre-lesson-page">
    <header class="page-header">
      <div class="header-left">
        <h1>📋 备课学情仪表盘</h1>
        <p class="subtitle">数据驱动备课，精准定位薄弱环节</p>
      </div>
      <div class="header-right">
        <select v-model="selectedClass" class="class-select" @change="load">
          <option v-for="c in classList" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <button class="btn-refresh" @click="load" :disabled="loading">🔄 刷新</button>
      </div>
    </header>

    <div v-if="loading" class="loading-state">加载中...</div>

    <template v-else-if="data">
      <!-- 概览卡片 -->
      <section class="overview-cards">
        <div class="card"><span class="card-val">{{ data.totalStudents }}</span><span class="card-label">学生总数</span></div>
        <div class="card"><span class="card-val">{{ data.avgScore }}%</span><span class="card-label">作业均分</span></div>
        <div class="card warn"><span class="card-val">{{ data.warningCount }}</span><span class="card-label">需关注学生</span></div>
        <div class="card"><span class="card-val">{{ data.liveSessionCount }}</span><span class="card-label">历史课堂</span></div>
        <div class="card"><span class="card-val">{{ data.liveAvgCorrectRate }}%</span><span class="card-label">互动正确率</span></div>
        <div class="card"><span class="card-val">{{ data.participationRate }}%</span><span class="card-label">参与率</span></div>
      </section>

      <div class="main-grid">
        <!-- 教学日历（按周分组） -->
        <section class="panel calendar-panel">
          <div class="calendar-header">
            <h3>📅 教学日历</h3>
            <el-button size="small" type="primary" @click="openCalendarPlanDialog">+ 添加计划</el-button>
          </div>
          <div v-if="!timeline?.weeks?.length" class="empty">暂无记录</div>
          <div v-else class="calendar-weeks">
            <div v-for="w in timeline.weeks" :key="w.weekNumber" class="cal-week">
              <div class="cal-week-label" :class="{ current: w.label === '本周' }">
                {{ w.label }}
                <span v-if="!w.items.length" class="cal-week-empty">—</span>
              </div>
              <div v-for="t in w.items" :key="t.type + '-' + t.id" class="cal-item" :class="'cal-' + t.type">
                <span class="cal-icon">{{ t.icon }}</span>
                <div class="cal-body">
                  <div class="cal-title">
                    <span class="cal-type-tag">{{ t.typeLabel }}</span>
                    {{ t.title }}
                  </div>
                  <div class="cal-meta">
                    <span v-if="t.date" class="cal-date">{{ t.date }}{{ t.time ? ' ' + t.time : '' }}</span>
                    <span v-if="t.status" class="cal-status" :class="statusClass(t.status)">{{ statusLabel(t) }}</span>
                    <span v-if="t.detail" class="cal-detail">{{ t.detail }}</span>
                  </div>
                </div>
                <el-popconfirm v-if="t.type === 'plan'" title="删除此计划？" @confirm="delCalendarPlan(t.id)">
                  <el-button text size="small" type="danger" class="cal-del">×</el-button>
                </el-popconfirm>
              </div>
            </div>
          </div>
        </section>

        <!-- 右侧：AI建议 + 分层 -->
        <div class="right-col">
          <!-- AI 备课建议 -->
          <section class="panel">
            <h3>
              🤖 AI 备课建议
              <button class="btn-ai-inline" :disabled="aiLoading" @click="loadAiSuggestion">
                {{ aiLoading ? '⏳ 分析中...' : '🔄 AI 深度分析' }}
              </button>
            </h3>
            <div class="ai-suggestion">{{ data.aiSuggestion || '暂无建议' }}</div>
          </section>

          <!-- 分层教学建议 -->
          <section class="panel">
            <h3>👥 分层教学建议</h3>
            <div v-for="(t, i) in data.tieredGroups" :key="i" class="tier-card">
              <div class="tier-header">
                <span class="tier-label">{{ t.label }}</span>
                <span class="tier-range">{{ t.range }}</span>
                <span class="tier-count">{{ t.count }}人</span>
              </div>
              <p class="tier-suggestion">{{ t.suggestion }}</p>
            </div>
          </section>
        </div>
      </div>

      <!-- 快捷操作 -->
      <section class="actions">
        <button class="btn-primary" @click="openPlanDialog">📝 生成教案</button>
        <button class="btn-primary" @click="$router.push('/teacher/tasks')">📋 布置作业</button>
        <button class="btn-primary" @click="$router.push('/teacher/preview/create?classId=' + selectedClass)">📖 发布预习任务</button>
        <button class="btn-primary" @click="goLive">🎓 开启课堂</button>
      </section>
    </template>

    <div v-else class="empty-state">请选择班级查看备课数据</div>

    <!-- 教案生成弹窗 -->
    <el-dialog v-model="planDialog" title="📝 生成教案大纲" width="700px" :close-on-click-modal="false">
      <div class="plan-form">
        <p class="plan-hint">根据班级薄弱知识点自动生成教案大纲</p>
        <el-checkbox-group v-model="planGoals" class="plan-goals">
          <el-checkbox label="review">查漏补缺</el-checkbox>
          <el-checkbox label="basic">巩固基础</el-checkbox>
          <el-checkbox label="difficult">突破重难点</el-checkbox>
          <el-checkbox label="extend">拓展提升</el-checkbox>
        </el-checkbox-group>
      </div>
      <template #footer>
        <el-button @click="planDialog = false">取消</el-button>
        <el-button type="primary" :loading="planGenerating" @click="generatePlan">🤖 生成教案</el-button>
      </template>
    </el-dialog>

    <!-- 添加教学计划弹窗 -->
    <el-dialog v-model="planDialogOpen" title="📅 添加教学计划" width="420px">
      <el-form label-width="80px">
        <el-form-item label="主题"><el-input v-model="planForm.topic" placeholder="如：结构体与联合体"/></el-form-item>
        <el-form-item label="知识点"><el-input v-model="planForm.knowledgePoints" placeholder="如：struct, union, typedef"/></el-form-item>
        <el-form-item label="计划日期"><el-input v-model="planForm.plannedDate" type="date"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="planDialogOpen = false">取消</el-button>
        <el-button type="primary" @click="submitPlan">添加</el-button>
      </template>
    </el-dialog>

    <!-- 教案结果弹窗 -->
    <el-dialog v-model="planResultDialog" title="📝 教案大纲" width="750px">
      <div class="plan-result" v-html="planHtml"></div>
      <template #footer>
        <el-button @click="planResultDialog = false">关闭</el-button>
        <el-button type="primary" @click="copyPlan">📋 复制教案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  getDashboardClasses, getPreLessonOverview, getPreLessonSuggestion,
  getTimeline, generateTeachingPlan,
  addCalendarPlan, deleteCalendarPlan,
  type PreLessonOverview, type TimelineDTO
} from '../../api/dashboard'
import { ElMessage } from 'element-plus'

const router = useRouter()
const classList = ref<{ id: number; name: string }[]>([])
const selectedClass = ref<number | null>(null)
const data = ref<PreLessonOverview | null>(null)
const loading = ref(false)
const aiLoading = ref(false)
const timeline = ref<TimelineDTO | null>(null)

// ── 教案弹窗 ──
const planDialog = ref(false)
const planResultDialog = ref(false)
const planGoals = ref<string[]>(['review', 'basic'])
const planGenerating = ref(false)
const planHtml = ref('')

// ── 数据加载 ──

async function loadClasses() {
  try {
    const res = await getDashboardClasses()
    classList.value = res.data || []
    if (classList.value.length && !selectedClass.value) {
      selectedClass.value = classList.value[0].id
      load()
    }
  } catch { /* ignore */ }
}

async function load() {
  if (!selectedClass.value) return
  loading.value = true
  try {
    const [preRes, tlRes] = await Promise.all([
      getPreLessonOverview(selectedClass.value),
      getTimeline(selectedClass.value, 10)
    ])
    data.value = preRes.data
    timeline.value = tlRes.data
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally { loading.value = false }
}

async function loadAiSuggestion() {
  if (!selectedClass.value) return
  aiLoading.value = true
  try {
    const res = await getPreLessonSuggestion(selectedClass.value)
    if (data.value && res.data?.suggestion) {
      data.value = { ...data.value, aiSuggestion: res.data.suggestion }
    }
  } catch { /* ignore */ }
  finally { aiLoading.value = false }
}

// ── 教案生成 ──

function openPlanDialog() {
  planGoals.value = ['review', 'basic']
  planDialog.value = true
}

async function generatePlan() {
  if (!selectedClass.value || !planGoals.value.length) return
  planGenerating.value = true
  try {
    const weakPoints = data.value?.weakPoints?.map((w: any) => w.name) || []
    const res = await generateTeachingPlan({
      classId: selectedClass.value,
      goals: planGoals.value,
      weakKnowledgePoints: weakPoints.length ? weakPoints : ['基础知识'],
    })
    planHtml.value = res.data || ''
    planDialog.value = false
    planResultDialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '生成失败')
  } finally { planGenerating.value = false }
}

function copyPlan() {
  const text = document.querySelector('.plan-result')?.textContent || ''
  navigator.clipboard.writeText(text).then(() => ElMessage.success('已复制'))
    .catch(() => ElMessage.warning('复制失败，请手动选择'))
}

function goLive() {
  if (selectedClass.value) router.push(`/teacher/live/${selectedClass.value}`)
}

// ── 教学日历计划弹窗 ──
const planDialogOpen = ref(false)
const planForm = ref({ topic: '', knowledgePoints: '', plannedDate: '', weekNumber: 0 })
function openCalendarPlanDialog() {
  planForm.value = { topic: '', knowledgePoints: '', plannedDate: new Date().toISOString().slice(0, 10), weekNumber: 0 }
  planDialogOpen.value = true
}
async function submitPlan() {
  if (!selectedClass.value || !planForm.value.topic.trim()) return
  try {
    await addCalendarPlan({
      classId: selectedClass.value,
      topic: planForm.value.topic.trim(),
      knowledgePoints: planForm.value.knowledgePoints.trim() || null,
      plannedDate: planForm.value.plannedDate || null,
      weekNumber: planForm.value.weekNumber,
    } as any)
    ElMessage.success('计划已添加')
    planDialogOpen.value = false
    load() // 刷新时间线
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '添加失败')
  }
}
async function delCalendarPlan(id: number) {
  try {
    await deleteCalendarPlan(id)
    ElMessage.success('已删除')
    load()
  } catch { ElMessage.error('删除失败') }
}

function statusClass(s: string) {
  if (!s) return ''
  if (s === 'COMPLETED') return 'ok'
  if (s === 'ACTIVE') return 'active'
  if (s === 'PLANNED') return 'planned'
  return ''
}
function statusLabel(t: any) {
  if (t.type === 'plan' && t.status === 'PLANNED') return '📅 待备课'
  if (t.status === 'COMPLETED') return '✅ 已完成'
  if (t.status === 'ACTIVE') return '🟢 进行中'
  return ''
}

onMounted(loadClasses)
</script>

<style scoped>
.pre-lesson-page { max-width: 1200px; margin: 0 auto; padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px; }
.page-header h1 { font-size: 22px; margin: 0; }
.subtitle { color: #909399; font-size: 13px; margin: 4px 0 0; }
.header-right { display: flex; gap: 10px; align-items: center; }
.class-select { padding: 8px 12px; border: 1px solid #dcdfe6; border-radius: 6px; font-size: 14px; }
.btn-refresh { padding: 8px 16px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; cursor: pointer; font-size: 13px; }
.btn-refresh:disabled { opacity: .6; }
.loading-state { text-align: center; padding: 60px 0; color: #909399; }

/* 概览卡片 */
.overview-cards { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; margin-bottom: 20px; }
@media (max-width: 900px) { .overview-cards { grid-template-columns: repeat(3, 1fr); } }
.card { background: #fff; border-radius: 10px; padding: 16px; text-align: center; box-shadow: 0 1px 6px rgba(0,0,0,.04); }
.card.warn { border-left: 3px solid #f56c6c; }
.card-val { display: block; font-size: 26px; font-weight: 700; color: #303133; }
.card-label { font-size: 12px; color: #909399; margin-top: 4px; display: block; }

/* 主布局 */
.main-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px; }
@media (max-width: 768px) { .main-grid { grid-template-columns: 1fr; } }
.panel { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 6px rgba(0,0,0,.04); }
.panel h3 { margin: 0 0 14px; font-size: 15px; }
.empty { color: #909399; font-size: 13px; text-align: center; padding: 20px 0; }
.right-col { display: flex; flex-direction: column; gap: 16px; }

/* 教学日历 */
.calendar-panel { max-height: 500px; overflow-y: auto; }
.calendar-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.calendar-header h3 { margin: 0 !important; }
.calendar-weeks { display: flex; flex-direction: column; gap: 4px; }
.cal-week { margin-bottom: 6px; }
.cal-week-label { font-size: 12px; font-weight: 600; color: #909399; padding: 8px 10px 4px; border-bottom: 1px solid #ebeef5; display: flex; justify-content: space-between; align-items: center; }
.cal-week-label.current { color: #409eff; border-bottom-color: #409eff; }
.cal-week-empty { font-weight: 400; color: #c0c4cc; }
.cal-item { display: flex; align-items: flex-start; gap: 8px; padding: 8px 10px; border-left: 3px solid transparent; margin-left: 4px; transition: background .15s; }
.cal-item:hover { background: #fafafa; }
.cal-item.cal-plan { border-left-color: #c0c4cc; }
.cal-item.cal-session { border-left-color: #409eff; }
.cal-item.cal-homework { border-left-color: #e6a23c; }
.cal-item.cal-preview { border-left-color: #67c23a; }
.cal-icon { font-size: 16px; flex-shrink: 0; margin-top: 1px; }
.cal-body { flex: 1; min-width: 0; }
.cal-title { font-size: 13px; font-weight: 500; display: flex; align-items: center; gap: 6px; }
.cal-type-tag { font-size: 10px; color: #909399; background: #f0f0f0; padding: 1px 5px; border-radius: 3px; flex-shrink: 0; }
.cal-meta { display: flex; align-items: center; gap: 8px; margin-top: 2px; flex-wrap: wrap; }
.cal-date { font-size: 11px; color: #c0c4cc; }
.cal-status { font-size: 11px; padding: 1px 6px; border-radius: 4px; }
.cal-status.ok { background: #f0f9eb; color: #67c23a; }
.cal-status.active { background: #ecf5ff; color: #409eff; }
.cal-status.planned { background: #fdf6ec; color: #e6a23c; }
.cal-detail { font-size: 11px; color: #666; }
.cal-del { opacity: 0; transition: opacity .15s; }
.cal-item:hover .cal-del { opacity: 1; }

/* AI 建议 */
.ai-suggestion { font-size: 14px; line-height: 1.8; color: #303133; white-space: pre-line; }
.btn-ai-inline { margin-left: 12px; padding: 4px 12px; border: 1px solid #409eff; border-radius: 6px; background: #fff; color: #409eff; font-size: 12px; cursor: pointer; transition: all .2s; }
.btn-ai-inline:hover:not(:disabled) { background: #409eff; color: #fff; }
.btn-ai-inline:disabled { opacity: .6; cursor: not-allowed; }

/* 分层 */
.tier-card { padding: 12px; margin-bottom: 10px; background: #fafafa; border-radius: 8px; border-left: 4px solid #409eff; }
.tier-card:last-child { margin-bottom: 0; }
.tier-header { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.tier-label { font-weight: 600; font-size: 14px; }
.tier-range { font-size: 12px; color: #909399; }
.tier-count { font-size: 13px; color: #409eff; font-weight: 600; margin-left: auto; }
.tier-suggestion { font-size: 13px; color: #606266; margin: 0; line-height: 1.6; }

/* 操作按钮 */
.actions { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; }
.btn-primary { padding: 10px 24px; border: none; border-radius: 8px; background: #409eff; color: #fff; font-size: 14px; cursor: pointer; transition: background .2s; }
.btn-primary:hover { background: #337ecc; }
.empty-state { text-align: center; padding: 80px 0; color: #909399; font-size: 15px; }

/* 教案弹窗 */
.plan-form { padding: 10px 0; }
.plan-hint { color: #909399; font-size: 13px; margin-bottom: 14px; }
.plan-goals { display: flex; gap: 16px; }
.plan-result { max-height: 500px; overflow-y: auto; font-size: 14px; line-height: 1.8; }
.plan-result :deep(h2) { font-size: 17px; margin: 14px 0 8px; }
.plan-result :deep(h3) { font-size: 15px; margin: 12px 0 6px; color: #409eff; }
.plan-result :deep(ul), .plan-result :deep(ol) { padding-left: 20px; }
.plan-result :deep(li) { margin-bottom: 4px; }
.plan-result :deep(table) { border-collapse: collapse; width: 100%; margin: 10px 0; }
.plan-result :deep(th), .plan-result :deep(td) { border: 1px solid #e4e7ed; padding: 8px 12px; text-align: left; font-size: 13px; }
.plan-result :deep(th) { background: #f5f7fa; }
</style>
