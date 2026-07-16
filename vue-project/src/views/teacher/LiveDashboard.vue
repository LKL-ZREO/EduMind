<template>
  <div class="live-dashboard">
    <header class="live-header">
      <div class="header-left">
        <el-button text @click="goBack">← 返回</el-button>
        <h2>{{ store.sessionInfo?.title || '课堂实时互动' }}</h2>
        <el-tag :type="store.connectionStatus === 'connected' ? 'success' : 'danger'" size="small">
          {{ store.connectionStatus === 'connected' ? '已连接' : '未连接' }}
        </el-tag>
      </div>
      <div class="header-center">
        <div class="join-code-box">
          <span class="label">加入码</span>
          <span class="code">{{ store.sessionInfo?.sessionCode }}</span>
          <el-button size="small" @click="copyCode">复制</el-button>
        </div>
        <el-button size="small" type="primary" @click="showQR = true">📱 二维码</el-button>
      </div>
      <div class="header-right">
        <span class="student-badge">👥 {{ store.studentCount }} 人在线</span>
        <el-button size="small" @click="exportReport">📄 导出报告</el-button>
        <el-button type="danger" size="small" @click="handleEndSession">结束课堂</el-button>
      </div>
    </header>

    <div class="live-body">
      <aside class="live-sidebar">
        <div class="tool-panel">
          <h3>🎯 发起互动</h3>
          <el-button type="primary" @click="openChoiceDialog">📊 选择题</el-button>
          <el-button type="success" @click="openOpenDialog">📝 简答题</el-button>
          <el-button type="warning" @click="openExerciseDialog">✏️ 随堂练习</el-button>
          <el-button type="info" @click="pickRandom" :disabled="!store.studentList.length">🎲 随机点名</el-button>
          <el-divider />
          <h3>👥 在线学生 ({{ store.studentCount }})</h3>
          <div class="student-list">
            <div v-for="s in store.studentList" :key="s.studentId" class="student-item clickable" @click="viewProfile(s.studentId, s.studentName)">
              <span class="student-dot online"></span>{{ s.studentName }}({{ s.studentId }})
            </div>
            <div v-if="!store.studentList.length" class="no-students">暂无学生加入</div>
          </div>
          <template v-if="store.absentStudents.length">
            <el-divider />
            <h3>🚫 未加入 ({{ store.absentCount }})</h3>
            <div class="student-list">
              <div v-for="s in store.absentStudents" :key="s.studentId" class="student-item absent">
                <span class="student-dot absent"></span>{{ s.studentName }}({{ s.studentId }})
              </div>
            </div>
          </template>
        </div>
      </aside>

      <main class="live-main">
        <!-- 不懂汇总 -->
        <div class="confusion-panel">
          <h3>🤔 学生标记"不懂" <span v-if="confusionTotal">({{ confusionTotal }}次)</span></h3>
          <div v-if="confusionStats.length" class="confusion-bars">
            <div v-for="s in confusionStats" :key="s.name" class="confusion-bar-row">
              <span class="confusion-kp-name">{{ s.name }}</span>
              <span class="confusion-bar-track">
                <span class="confusion-bar-fill" :style="{ width: Math.min(s.count * 25, 100) + '%' }"></span>
              </span>
              <span class="confusion-count">{{ s.count }}人</span>
            </div>
          </div>
          <div v-else class="confusion-empty">暂无学生标记不懂</div>
        </div>
        <h3>📋 互动记录</h3>
        <div class="history-list" v-if="store.interactionHistory.length">
          <div v-for="h in store.interactionHistory" :key="h.interactionId"
               class="history-card" :class="{ active: h.status === 'ACTIVE' }">
            <div class="card-header" @click="toggleDetail(h.interactionId)">
              <el-tag :type="h.type==='CHOICE'?'primary':h.type==='OPEN'?'success':'warning'" size="small">{{ typeLabel(h.type) }}</el-tag>
              <span class="card-title">{{ h.title }}</span>
              <el-tag v-if="h.status==='ACTIVE'" type="danger" size="small" effect="dark">进行中</el-tag>
              <el-tag v-else type="info" size="small">已结束</el-tag>
              <span class="expand-hint">{{ expandedId === h.interactionId ? '▲ 收起' : '▼ 详情' }}</span>
            </div>
            <div class="card-stats">
              <span>已答: {{ h.respondedCount }} / {{ h.totalStudents }}</span>
              <span v-if="h.correctRate !== null">正确率: {{ h.correctRate }}%</span>
              <span v-if="h.timeLimit">限时: {{ h.timeLimit }}s</span>
            </div>
            <!-- 展开详情 -->
            <div v-if="expandedId === h.interactionId" class="card-detail">
              <div v-if="detailLoading && detailId === h.interactionId" class="detail-loading">加载中...</div>
              <template v-else-if="detail">
                <div class="detail-question">
                  <p v-if="detail.description">{{ detail.description }}</p>
                  <div v-if="detail.options" class="detail-options">
                    <span v-for="o in detail.options" :key="o.key" class="detail-opt" :class="{ correct: o.key === detail.correctKey }">{{ o.key }}. {{ o.text }}</span>
                  </div>
                  <p v-if="detail.correctKey" class="detail-key">正确答案: {{ detail.correctKey }}</p>
                </div>
                <!-- 纯文本分布 -->
                <div v-if="detail.distribution && Object.keys(detail.distribution).length" class="dist-bars">
                  <div v-for="(v, k) in detail.distribution" :key="k" class="dist-bar-row">
                    <span class="dist-key">{{ k }}</span>
                    <span class="dist-bar-wrap"><span class="dist-bar" :style="{width:Math.max(v.percent,2)+'%'}"></span></span>
                    <span class="dist-num">{{ v.count }} ({{ v.percent }}%)</span>
                  </div>
                </div>
                <div class="detail-responses">
                  <h4>作答明细 ({{ detail.responses.length }})</h4>
                  <div v-for="r in detail.responses" :key="r.studentId" class="resp-row">
                    <span>{{ r.studentName }}({{ r.studentId }})</span>
                    <span :class="r.isCorrect ? 'resp-correct' : 'resp-wrong'">{{ r.answer || '-' }}</span>
                  </div>
                  <div v-if="detail.unrespondedStudents.length" class="unresp">
                    <span class="unresp-label">未作答:</span>
                    {{ detail.unrespondedStudents.join(', ') }}
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
        <el-empty v-else description="等待发起互动..." :image-size="60" />
        <div v-if="store.reactions.length" class="reaction-bar">
          <span v-for="(r,i) in store.reactions" :key="i" class="reaction-bubble" :title="r.studentName">
            {{ r.emoji }} {{ r.studentName }}
          </span>
          <el-button text size="small" @click="store.reactions=[]">清除</el-button>
        </div>
      </main>

      <aside class="live-qa">
        <h3>🙋 匿名提问</h3>
        <div v-for="q in store.topQuestions" :key="q.id" class="qa-item" :class="{ answered: q.answered }">
          <div class="qa-question">
            <span v-if="q.similarCount > 0" class="qa-count">×{{ q.similarCount + 1 }}</span>
            {{ q.question }}
          </div>
          <div v-if="q.answered" class="qa-answer">{{ q.answerText }}</div>
          <el-button v-else size="small" type="primary" @click="openAnswerDialog(q)">回答</el-button>
        </div>
        <el-empty v-if="!store.topQuestions.length" description="暂无提问" :image-size="40" />
      </aside>
    </div>

    <el-dialog v-model="choice.visible" title="发起选择题" width="500px">
      <el-form label-width="80px">
        <el-form-item label="题目"><el-input v-model="choice.title"><template #append><el-button @click="aiGenerate('CHOICE')" :loading="aiLoading">🤖 AI生成</el-button></template></el-input></el-form-item>
        <el-form-item label="选项">
          <div v-for="(o,i) in choice.options" :key="i" class="option-row">
            <span class="option-key">{{ o.key }}.</span><el-input v-model="o.text" /><el-button text type="danger" @click="choice.options.splice(i,1)">×</el-button>
          </div>
          <el-button size="small" @click="choice.options.push({key:String.fromCharCode(65+choice.options.length),text:''})">+ 添加选项</el-button>
        </el-form-item>
        <el-form-item label="正确答案"><el-select v-model="choice.correctKey"><el-option v-for="o in choice.options" :key="o.key" :label="o.key" :value="o.key"/></el-select></el-form-item>
        <el-form-item label="限时(秒)"><el-input-number v-model="choice.timeLimit" :min="0" :max="300"/></el-form-item>
        <el-form-item label="知识点"><el-input v-model="choice.knowledgePoint" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="choice.visible=false">取消</el-button><el-button type="primary" @click="submitChoice">发起</el-button></template>
    </el-dialog>

    <el-dialog v-model="open.visible" title="发起简答题" width="500px">
      <el-form label-width="80px"><el-form-item label="题目"><el-input v-model="open.title" type="textarea" :rows="3"><template #append><el-button @click="aiGenerate('OPEN')" :loading="aiLoading">🤖</el-button></template></el-input></el-form-item><el-form-item label="限时"><el-input-number v-model="open.timeLimit" :min="0" :max="600"/></el-form-item></el-form>
      <template #footer><el-button @click="open.visible=false">取消</el-button><el-button type="primary" @click="submitOpen">发起</el-button></template>
    </el-dialog>

    <el-dialog v-model="ex.visible" title="随堂练习" width="500px">
      <el-form label-width="80px"><el-form-item label="题目"><el-input v-model="ex.title" type="textarea" :rows="4"/></el-form-item><el-form-item label="限时"><el-input-number v-model="ex.timeLimit" :min="0" :max="600"/></el-form-item></el-form>
      <template #footer><el-button @click="ex.visible=false">取消</el-button><el-button type="primary" @click="submitEx">发起</el-button></template>
    </el-dialog>

    <el-dialog v-model="ans.visible" title="回答提问" width="400px">
      <p>{{ ans.q?.question }}</p><el-input v-model="ans.text" type="textarea" :rows="3"/>
      <template #footer><el-button @click="ans.visible=false">取消</el-button><el-button type="primary" @click="submitAns">回答</el-button></template>
    </el-dialog>

    <el-dialog v-model="showQR" title="扫码加入" width="300px" align-center>
      <div class="qr-container"><canvas ref="qrCanvas"></canvas></div>
    </el-dialog>

    <!-- 随机点名 -->
    <el-dialog v-model="picker.visible" title="🎲 随机点名" width="360px" align-center>
      <div class="picker-display">
        <div v-if="picker.picking" class="picker-rolling">{{ picker.current }}</div>
        <div v-else class="picker-result">{{ picker.result || '—' }}</div>
      </div>
      <template #footer><el-button @click="picker.visible=false">关闭</el-button><el-button type="primary" @click="doPick" :disabled="!store.studentList.length">开始抽选</el-button></template>
    </el-dialog>

    <!-- 学生画像 -->
    <el-dialog v-model="profile.visible" :title="'📊 ' + profile.name" width="420px">
      <div v-if="store.studentProfile" class="profile-body">
        <p>📚 参与课堂: <b>{{ store.studentProfile.totalSessions }}</b> 次</p>
        <p>📋 总互动: <b>{{ store.studentProfile.totalInteractions }}</b> 题</p>
        <p>✍️ 已作答: <b>{{ store.studentProfile.totalAnswers }}</b> 题 ({{ store.studentProfile.participationRate }}%)</p>
        <p>✅ 正确率: <b>{{ store.studentProfile.correctRate }}%</b> ({{ store.studentProfile.correctAnswers }}/{{ store.studentProfile.totalAnswers }})</p>
      </div>
      <div v-else class="detail-loading">加载中...</div>
    </el-dialog>

    <!-- 课程总结 -->
    <el-dialog v-model="showSummary" title="📊 课程总结" width="560px" :close-on-click-modal="false" @closed="goBack">
      <div class="summary-body" v-if="summary">
        <div class="summary-header">
          <p><strong>课程:</strong> {{ summary.title }}</p>
          <p><strong>时长:</strong> {{ summary.duration }}</p>
          <p><strong>互动总数:</strong> {{ summary.totalInteractions }} 次</p>
        </div>
        <el-divider />
        <div v-if="summary.interactions.length" class="summary-interactions">
          <h4>📋 互动详情</h4>
          <div v-for="(h, i) in summary.interactions" :key="i" class="sum-item">
            <span class="sum-idx">#{{ i + 1 }}</span>
            <el-tag :type="h.type==='CHOICE'?'primary':h.type==='OPEN'?'success':'warning'" size="small">{{ typeLabel(h.type) }}</el-tag>
            <span class="sum-title">{{ h.title }}</span>
            <span class="sum-stat">已答 {{ h.respondedCount }}/{{ h.totalStudents }}<span v-if="h.correctRate!==null"> · {{ h.correctRate }}% 正确</span></span>
          </div>
        </div>
        <el-divider />
        <div class="summary-attendance">
          <p>✅ 已加入: <b>{{ summary.onlineCount }}</b> 人</p>
          <p v-if="summary.absentCount > 0">❌ 未加入: <b>{{ summary.absentCount }}</b> 人</p>
          <p>🙋 提问: <b>{{ summary.qaCount }}</b> 条</p>
          <p v-if="summary.confusionTotal > 0">🤔 标记不懂: <b>{{ summary.confusionTotal }}</b> 次</p>
        </div>
        <div v-if="summary.confusionStats && summary.confusionStats.length" class="summary-confusions">
          <h4>🤔 学生不懂的知识点</h4>
          <div v-for="s in summary.confusionStats" :key="s.name" class="sum-confusion-row">
            <span>{{ s.name }}</span><span class="sum-confusion-count">{{ s.count }}人</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="showSummary = false; goBack()">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch, nextTick, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useLiveSessionStore } from '../../stores/live-session'
import type { QAQuestion, InteractionDetail } from '../../api/live'
import { getInteractionDetail, generateQuestion, getReport } from '../../api/live'
import QRCode from 'qrcode'

const route = useRoute(); const router = useRouter(); const store = useLiveSessionStore()
const classId = computed(() => Number(route.params.classId))

function typeLabel(t: string) { return ({ CHOICE: '选择题', OPEN: '简答题', EXERCISE: '随堂练习' } as any)[t] || t }

// === 不懂标记统计 ===
const confusionStats = ref<{ name: string; count: number }[]>([])
const confusionTotal = ref(0)
let confusionTimer: any = null
async function loadConfusionStats() {
  if (!store.sessionId) return
  try {
    const res = await fetch(`/api/live/session/${store.sessionId}/confusion-stats`)
    const data = await res.json()
    if (data.code === 200) {
      confusionStats.value = data.data.stats || []
      confusionTotal.value = data.data.total || 0
    }
  } catch {}
}

const expandedId = ref<number | null>(null)
const detail = ref<InteractionDetail | null>(null)
const detailLoading = ref(false)
const detailId = ref<number | null>(null)

async function toggleDetail(interactionId: number) {
  if (expandedId.value === interactionId) {
    expandedId.value = null; detail.value = null; return
  }
  expandedId.value = interactionId
  detailId.value = interactionId
  detailLoading.value = true
  try {
    const r = await getInteractionDetail(store.sessionId!, interactionId)
    detail.value = r.data.data
  } catch { detail.value = null; expandedId.value = null }
  finally { detailLoading.value = false }
}

// 实时刷新已展开的详情
watch(() => store.lastStats, async s => {
  if (!s || expandedId.value !== s.interactionId) return
  try {
    const r = await getInteractionDetail(store.sessionId!, s.interactionId)
    if (r.data.data) detail.value = r.data.data
  } catch {}
})

const choice = reactive({ visible: false, title: '', options: [{key:'A',text:''},{key:'B',text:''},{key:'C',text:''},{key:'D',text:''}], correctKey: 'A', timeLimit: 30, knowledgePoint: '' })
const open = reactive({ visible: false, title: '', timeLimit: 120 })
const ex = reactive({ visible: false, title: '', timeLimit: 300 })
const ans = reactive({ visible: false, q: null as QAQuestion | null, text: '' })

function openChoiceDialog() { choice.title = ''; choice.options = [{key:'A',text:''},{key:'B',text:''},{key:'C',text:''},{key:'D',text:''}]; choice.visible = true }
function openOpenDialog() { open.title = ''; open.visible = true }
function openExerciseDialog() { ex.title = ''; ex.visible = true }
function openAnswerDialog(q: QAQuestion) { ans.q = q; ans.text = ''; ans.visible = true }

function submitChoice() {
  const valid = choice.options.filter(o => o.text.trim())
  if (valid.length < 2) { ElMessage.warning('至少2个选项'); return }
  store.createInteraction({ type: 'CHOICE', title: choice.title, options: valid.map(o=>({key:o.key,text:o.text})), correctKey: choice.correctKey, timeLimit: choice.timeLimit>0?choice.timeLimit:undefined, knowledgePoint: choice.knowledgePoint||undefined })
  choice.visible = false; ElMessage.success('已发起')
}
function submitOpen() { store.createInteraction({ type: 'OPEN', title: open.title, timeLimit: open.timeLimit>0?open.timeLimit:undefined }); open.visible = false; ElMessage.success('已发起') }
function submitEx() { store.createInteraction({ type: 'EXERCISE', title: ex.title, timeLimit: ex.timeLimit>0?ex.timeLimit:undefined }); ex.visible = false; ElMessage.success('已发起') }
function submitAns() { if (ans.q) { store.answerQuestion(ans.q.id, ans.text); ans.visible = false } }

const showQR = ref(false); const qrCanvas = ref<HTMLCanvasElement | null>(null)
const liveUrl = computed(() => `${window.location.origin}/live/${store.sessionInfo?.sessionCode}`)
watch(showQR, async v => { if (v && qrCanvas.value) { await nextTick(); await QRCode.toCanvas(qrCanvas.value, liveUrl.value, {width:200}) } })

function copyCode() { navigator.clipboard.writeText(store.sessionInfo?.sessionCode||'').then(()=>ElMessage.success('已复制')) }
function goBack() { router.push({ name: 'classManage', params: { id: classId.value } }) }

const showSummary = ref(false)
const summary = ref<any>(null)
function buildSummary() {
  const start = store.sessionInfo?.startedAt ? new Date(store.sessionInfo.startedAt) : null
  const now = new Date()
  const diffMin = start ? Math.round((now.getTime() - start.getTime()) / 60000) : 0
  return {
    title: store.sessionInfo?.title || '',
    duration: diffMin >= 60 ? `${Math.floor(diffMin/60)}h${diffMin%60}m` : `${diffMin} 分钟`,
    totalInteractions: store.interactionHistory.length,
    interactions: store.interactionHistory.map(h => ({
      type: h.type, title: h.title, respondedCount: h.respondedCount,
      totalStudents: h.totalStudents, correctRate: h.correctRate,
    })),
    onlineCount: store.studentCount,
    absentCount: store.absentCount,
    qaCount: store.topQuestions.length,
    confusionTotal: confusionTotal.value,
    confusionStats: confusionStats.value,
  }
}
async function handleEndSession() {
  await ElMessageBox.confirm('确定结束？','确认',{confirmButtonText:'结束',type:'warning'})
  await loadConfusionStats()  // 结课前拉一次最新数据
  summary.value = buildSummary()
  await store.endLiveSession()
  showSummary.value = true
}

// === AI 生成 ===
const aiLoading = ref(false)
async function aiGenerate(type: string) {
  const topic = prompt('请输入知识点/主题：')
  if (!topic) return
  aiLoading.value = true
  try {
    const r = await generateQuestion(topic, type)
    const d = r.data.data
    if (type === 'CHOICE') {
      choice.title = d.title
      if (d.options) choice.options = d.options.map((o: any) => ({ key: o.key, text: o.text }))
      if (d.correctKey) choice.correctKey = d.correctKey
    } else {
      open.title = d.title
    }
    ElMessage.success('AI 已生成题目')
  } catch { ElMessage.error('AI 生成失败') }
  finally { aiLoading.value = false }
}

// === 随机点名 ===
const picker = reactive({ visible: false, picking: false, current: '', result: '', timer: 0 as any })
function pickRandom() { picker.visible = true; picker.result = '' }
function doPick() {
  picker.picking = true; picker.result = ''
  let i = 0
  picker.timer = setInterval(() => {
    picker.current = store.studentList[i % store.studentList.length]?.studentName || ''
    i++
  }, 80)
  setTimeout(() => {
    clearInterval(picker.timer)
    picker.picking = false
    picker.result = store.studentList[Math.floor(Math.random() * store.studentList.length)]?.studentName || ''
  }, 1500)
}

// === 导出报告 ===
async function exportReport() {
  const sid = store.sessionId!
  const dur = summary.value?.duration || ''
  const html = await getReport(sid, store.sessionInfo?.title || '', dur, store.studentCount, store.absentCount, store.topQuestions.length).then(r => r.data.data.html).catch(() => '')
  if (html) {
    const w = window.open('', '_blank')!
    w.document.write(html); w.document.close()
  }
}

// === 学生画像 ===
const profile = reactive({ visible: false, name: '' })
function viewProfile(studentId: string, studentName: string) {
  profile.visible = true; profile.name = studentName
  store.fetchStudentProfile(studentId, classId.value)
}

onMounted(async () => {
  try {
    const r = await store.checkActiveSession(classId.value)
    if (r?.hasActive) { store.sessionInfo = { sessionId:r.sessionId, sessionCode:r.sessionCode, title:r.title, className:'', teacherName:'', token:'', studentId:'', studentName:'', hasActive:true, currentInteraction:null, startedAt:r.startedAt }; store.role='teacher'; store.connect(localStorage.getItem('token')||'') }
    else await store.createSession(classId.value)
  } catch { await store.createSession(classId.value) }
  // 30秒轮询不懂统计
  confusionTimer = setInterval(loadConfusionStats, 30000)
  loadConfusionStats()
})

onUnmounted(() => { if (confusionTimer) clearInterval(confusionTimer) })
</script>

<style scoped>
.live-dashboard{height:100vh;display:flex;flex-direction:column;background:#f0f2f5}
.live-header{display:flex;align-items:center;justify-content:space-between;padding:12px 20px;background:#fff;box-shadow:0 1px 4px rgba(0,0,0,.08);z-index:10}
.header-left,.header-center,.header-right{display:flex;align-items:center;gap:12px}
.join-code-box{display:flex;align-items:center;gap:8px;padding:6px 14px;background:#ecf5ff;border-radius:6px}
.join-code-box .code{font-size:22px;font-weight:700;letter-spacing:4px;color:#409eff;font-family:monospace}
.live-body{flex:1;display:flex;gap:12px;padding:12px;overflow:hidden}
.live-sidebar{width:220px;flex-shrink:0}.tool-panel{background:#fff;border-radius:8px;padding:16px;display:flex;flex-direction:column;gap:10px}
.live-main{flex:1;background:#fff;border-radius:8px;padding:20px;overflow-y:auto}
.history-list{display:flex;flex-direction:column;gap:12px}
.history-card{padding:14px 16px;border:1px solid #ebeef5;border-radius:8px;background:#fafafa;transition:all .2s}
.history-card.active{border-color:#409eff;background:#ecf5ff;box-shadow:0 1px 6px rgba(64,158,255,.12)}
.card-header{display:flex;align-items:center;gap:10px;margin-bottom:8px;cursor:pointer;user-select:none}
.card-title{font-size:15px;font-weight:600;flex:1}
.card-stats{display:flex;gap:20px;font-size:13px;color:#909399}
.expand-hint{font-size:11px;color:#a0c5e8;white-space:nowrap}
.expand-hint:hover{color:#409eff}
.card-detail{margin-top:12px;padding-top:12px;border-top:1px dashed #e4e7ed}
.detail-loading{text-align:center;padding:20px;color:#909399}
.detail-question{margin-bottom:12px;font-size:14px}
.detail-options{display:flex;flex-wrap:wrap;gap:8px;margin:8px 0}
.detail-opt{padding:4px 12px;background:#f0f2f5;border-radius:4px;font-size:13px}
.detail-opt.correct{background:#f0f9eb;color:#67c23a;font-weight:600}
.detail-key{font-size:13px;color:#67c23a;font-weight:600}
.dist-bars{margin:10px 0}
.dist-bar-row{display:flex;align-items:center;gap:8px;margin:4px 0;font-size:13px}
.dist-key{min-width:32px;font-weight:600}
.dist-bar-wrap{flex:1;height:16px;background:#f0f2f5;border-radius:3px;overflow:hidden}
.dist-bar{height:100%;background:linear-gradient(90deg,#409eff,#79bbff);border-radius:3px;min-width:2px;transition:width .3s}
.dist-num{min-width:80px;text-align:right;color:#909399;font-size:12px}
.detail-responses{font-size:13px}
.detail-responses h4{margin:10px 0 6px}
.resp-row{display:flex;justify-content:space-between;padding:4px 0;border-bottom:1px solid #f5f5f5}
.resp-correct{color:#67c23a}
.resp-wrong{color:#f56c6c}
.unresp{margin-top:8px;color:#e6a23c;font-size:12px}
.unresp-label{font-weight:600}
.live-qa{width:270px;flex-shrink:0;background:#fff;border-radius:8px;padding:16px;overflow-y:auto}
.qa-item{padding:10px;margin-bottom:8px;border-radius:6px;background:#f9f9f9;border-left:3px solid #e6a23c}
.qa-item.answered{border-left-color:#67c23a}
.qa-count{display:inline-block;background:#e6a23c;color:#fff;border-radius:10px;padding:0 6px;font-size:11px;margin-right:4px}
.qa-answer{margin-top:6px;padding:6px 8px;background:#f0f9eb;border-radius:4px;font-size:12px;color:#67c23a}
.option-row{display:flex;align-items:center;gap:8px;margin-bottom:6px}
.option-key{font-weight:700;min-width:20px}
.qr-container{text-align:center}
.student-badge{font-size:14px;color:#666}
.student-list{max-height:200px;overflow-y:auto;font-size:13px}
.student-item{display:flex;align-items:center;gap:6px;padding:4px 0;color:#333}
.student-dot{width:8px;height:8px;border-radius:50%;background:#67c23a;flex-shrink:0}
.student-dot.absent{background:#f56c6c}
.student-item.absent{color:#909399}
.no-students{color:#ccc;font-size:12px;text-align:center;padding:8px}
.student-item.clickable{cursor:pointer}.student-item.clickable:hover{background:#f0f2f5;border-radius:4px}
.reaction-bar{display:flex;flex-wrap:wrap;gap:6px;margin-top:16px;padding:10px;background:#fafafa;border-radius:6px;align-items:center}
.reaction-bubble{padding:2px 8px;background:#fff;border-radius:12px;font-size:13px;box-shadow:0 1px 3px rgba(0,0,0,.06)}
.picker-display{text-align:center;padding:30px 0;font-size:36px;font-weight:700}
.picker-rolling{color:#909399}
.picker-result{color:#409eff;animation:pop .3s}
@keyframes pop{0%{transform:scale(.5);opacity:0}100%{transform:scale(1);opacity:1}}
.profile-body p{margin:8px 0;font-size:14px}
.summary-body{font-size:14px}
.summary-header p{margin:4px 0}
.summary-interactions{margin:10px 0}
.sum-item{display:flex;align-items:center;gap:8px;padding:6px 0;border-bottom:1px solid #f5f5f5;font-size:13px}
.sum-idx{color:#c0c4cc;min-width:24px}
.sum-title{flex:1}
.sum-stat{color:#909399;font-size:12px}
.summary-attendance p{margin:4px 0}
.confusion-panel{margin-bottom:16px;padding:14px 16px;background:#fff7f0;border:1px solid #f5dab1;border-radius:8px}
.confusion-panel h3{margin:0 0 10px;font-size:15px;color:#b88230}
.confusion-bars{display:flex;flex-direction:column;gap:6px}
.confusion-bar-row{display:flex;align-items:center;gap:8px;font-size:13px}
.confusion-kp-name{width:80px;text-align:right;color:#666;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.confusion-bar-track{flex:1;height:14px;background:#f5ead6;border-radius:4px;overflow:hidden}
.confusion-bar-fill{height:100%;background:linear-gradient(90deg,#e6a23c,#f56c6c);border-radius:4px;min-width:2px;transition:width .3s}
.confusion-count{min-width:36px;font-weight:600;color:#e6a23c;font-size:12px}
.confusion-empty{text-align:center;color:#c0a87c;font-size:13px;padding:4px 0}
.sum-confusion-row{display:flex;justify-content:space-between;padding:4px 0;font-size:13px}
.sum-confusion-count{color:#e6a23c;font-weight:600}
</style>
