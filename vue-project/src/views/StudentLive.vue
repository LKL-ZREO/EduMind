<template>
  <div class="student-live">
    <div v-if="!joined" class="join-container">
      <div class="join-card">
        <h2>📚 加入课堂</h2>
        <div v-if="preview" class="preview-info">
          <p><strong>{{ preview.title }}</strong></p><p>班级: {{ preview.className }}</p><p>教师: {{ preview.teacherName }}</p>
        </div>
        <el-form label-width="60px">
          <el-form-item label="学号"><el-input v-model="form.studentId"/></el-form-item>
          <el-form-item label="姓名"><el-input v-model="form.studentName"/></el-form-item>
        </el-form>
        <el-button type="primary" size="large" block :loading="joining" @click="handleJoin">加入课堂</el-button>
      </div>
    </div>

    <div v-else class="interaction-container">
      <header class="student-header"><h3>{{ store.sessionInfo?.title }}</h3><span>👤 {{ store.sessionInfo?.studentName }}</span></header>
      <main class="student-main">
        <template v-if="store.currentInteraction?.status === 'ACTIVE'">
          <div class="question-card">
            <el-tag size="small">{{ typeLabel }}</el-tag>
            <h3>{{ store.currentInteraction.title }}</h3>
            <div v-if="store.currentInteraction.timeLimit" class="countdown">⏱ {{ remaining }}s<el-progress :percentage="pct" :status="remaining<10?'exception':undefined" :stroke-width="6"/></div>
            <div v-if="store.currentInteraction.type==='CHOICE'" class="choice-options">
              <div v-for="o in store.currentInteraction.options" :key="o.key" class="choice-option" :class="{selected:sel===o.key}" @click="sel=o.key"><span class="opt-key">{{ o.key }}</span>{{ o.text }}</div>
            </div>
            <el-input v-else v-model="textAns" type="textarea" :rows="4" placeholder="输入你的答案..."/>
            <el-button type="primary" size="large" :disabled="!canSubmit" :loading="submitting" @click="handleSubmit">{{ done?'✓ 重新提交':'提交答案' }}</el-button>
            <el-button type="warning" size="large" :disabled="confused" :loading="confusing" @click="handleConfused" style="margin-left:8px">🤔 不懂</el-button>
            <!-- AI 解析弹出 -->
            <div v-if="confused && explanation" class="ai-explanation">
              <div class="explanation-header">
                <span>🤖 AI 解析 · {{ confusionKp }}</span>
                <el-button text size="small" @click="confused=false;explanation=''">✕</el-button>
              </div>
              <div class="explanation-body" v-html="explanation"></div>
            </div>
          </div>
        </template>
        <div v-else-if="store.currentInteraction?.status==='CLOSED'" class="question-card result-card">
          <h3>📊 答题结果</h3><p>{{ store.currentInteraction.title }}</p>
          <div v-if="store.currentInteraction.type==='CHOICE'" class="choice-options result">
            <div v-for="o in store.currentInteraction.options" :key="o.key" class="choice-option" :class="{correct:o.key===store.currentInteraction.correctKey, wrong:sel===o.key&&o.key!==store.currentInteraction.correctKey}"><span class="opt-key">{{ o.key }}</span>{{ o.text }}</div>
          </div>
        </div>
        <el-empty v-else description="等待教师发起互动..." :image-size="80"/>
        <!-- 历史互动 -->
        <div v-if="store.interactionHistory.length" class="history-section">
          <h4>📋 历史记录</h4>
          <div v-for="h in store.interactionHistory.filter(x => x.status !== 'ACTIVE')" :key="h.interactionId" class="history-card">
            <div class="hist-header">
              <el-tag :type="h.type==='CHOICE'?'primary':h.type==='OPEN'?'success':'warning'" size="small">{{ (['CHOICE','选择题','OPEN','简答题','EXERCISE','随堂练习'] as any)[h.type]||h.type }}</el-tag>
              <span class="hist-title">{{ h.title }}</span>
              <span v-if="h.myCorrect === true" class="hist-badge correct">✓</span>
              <span v-else-if="h.myCorrect === false" class="hist-badge wrong">✗</span>
            </div>
            <div v-if="h.myAnswer" class="hist-answer">你的答案: <b>{{ h.myAnswer }}</b></div>
            <div v-if="h.correctKey" class="hist-answer correct-key">正确答案: <b>{{ h.correctKey }}</b></div>
          </div>
        </div>
      </main>
      <footer class="student-qa">
        <div class="emoji-bar">
          <span v-for="e in ['👍','👎','❓','😮','✋']" :key="e" class="emoji-btn" @click="sendEmoji(e)">{{ e }}</span>
        </div>
        <div class="qa-input-row"><el-input v-model="qaText" placeholder="🙋 匿名提问" @keyup.enter="handleQA"><template #append><el-button @click="handleQA" :disabled="!qaText.trim()">发送</el-button></template></el-input></div>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useLiveSessionStore } from '../stores/live-session'
import { previewSession } from '../api/live'
import { ElMessage } from 'element-plus'

const route = useRoute(); const store = useLiveSessionStore()
const code = computed(() => String(route.params.sessionCode || ''))

const joined = ref(false); const joining = ref(false); const preview = ref<any>(null)
const form = ref({ studentId: localStorage.getItem('live_student_id')||'', studentName: localStorage.getItem('live_student_name')||'' })

async function handleJoin() {
  if (!form.value.studentId.trim() || !form.value.studentName.trim()) { ElMessage.warning('请输入学号和姓名'); return }
  joining.value = true
  try {
    await store.joinSession(code.value, form.value.studentId.trim(), form.value.studentName.trim())
    localStorage.setItem('live_student_id', form.value.studentId.trim()); localStorage.setItem('live_student_name', form.value.studentName.trim())
    joined.value = true
  } catch (e: any) { ElMessage.error(e?.response?.data?.message||'加入失败') }
  finally { joining.value = false }
}

const sel = ref(''); const textAns = ref(''); const done = ref(false); const submitting = ref(false)
const canSubmit = computed(() => { if (!store.currentInteraction||store.currentInteraction.status!=='ACTIVE') return false; return store.currentInteraction.type==='CHOICE'?!!sel.value:textAns.value.trim().length>0 })
async function handleSubmit() { if(!canSubmit.value) return; submitting.value=true; store.submitResponse(store.currentInteraction?.type==='CHOICE'?sel.value:textAns.value.trim()); done.value=true; submitting.value=false; ElMessage.success('已提交') }

const remaining = ref(0); const pct = ref(100); let timer: any = null
watch(()=>store.currentInteraction, i=>{ if(timer)clearInterval(timer); done.value=false; sel.value=''; textAns.value=''; confused.value=false; explanation.value=''; if(i?.timeLimit&&i.status==='ACTIVE'){ remaining.value=i.timeLimit; pct.value=100; timer=setInterval(()=>{remaining.value--; pct.value=Math.round(remaining.value/i!.timeLimit!*10000)/100; if(remaining.value<=0)clearInterval(timer)},1000) } })

const qaText = ref('')
function handleQA() { if(!qaText.value.trim()) return; store.askQuestion(qaText.value.trim()); qaText.value=''; ElMessage.success('已发送') }
function sendEmoji(e: string) { store.sendReaction(e); ElMessage({ message: e, duration: 600, center: true }) }

const typeLabel = computed(()=>({CHOICE:'选择题',OPEN:'简答题',EXERCISE:'随堂练习'} as any)[store.currentInteraction?.type||'']||'')

// 不懂标记
const confused = ref(false); const confusing = ref(false); const explanation = ref(''); const confusionKp = ref('')
async function handleConfused() {
  if (!store.currentInteraction || !store.sessionInfo) return
  confusing.value = true
  try {
    const res = await fetch('/api/live/confusion/mark', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sessionId: store.sessionInfo.sessionId,
        interactionId: store.currentInteraction.interactionId,
        studentId: form.value.studentId,
        studentName: form.value.studentName
      })
    })
    const data = await res.json()
    if (data.code === 200) {
      confusionKp.value = data.data.knowledgePoint
      explanation.value = data.data.explanation.replace(/\n/g, '<br>')
      confused.value = true
    }
  } catch(e) { console.error('标记不懂失败', e) }
  finally { confusing.value = false }
}

onMounted(async ()=>{ try{const r=await previewSession(code.value);preview.value=r.data.data}catch{}})
onUnmounted(()=>{if(timer)clearInterval(timer);store.disconnect()})
</script>

<style scoped>
.student-live{min-height:100vh;background:#f0f2f5;display:flex;flex-direction:column}
.join-container{display:flex;justify-content:center;align-items:center;min-height:100vh;padding:20px}
.join-card{width:100%;max-width:360px;background:#fff;border-radius:12px;padding:30px 24px;box-shadow:0 2px 12px rgba(0,0,0,.06)}
.join-card h2{text-align:center;margin-bottom:16px}
.preview-info{background:#f5f7fa;padding:12px;border-radius:8px;margin-bottom:16px;font-size:14px}
.interaction-container{flex:1;display:flex;flex-direction:column}
.student-header{display:flex;justify-content:space-between;align-items:center;padding:12px 16px;background:#fff}
.student-main{flex:1;display:flex;flex-direction:column;align-items:center;padding:20px 16px;overflow-y:auto}
.question-card{width:100%;max-width:500px;background:#fff;border-radius:12px;padding:24px;box-shadow:0 2px 8px rgba(0,0,0,.04)}
.question-card h3{margin:10px 0;font-size:17px}
.countdown{margin:12px 0;font-size:14px;color:#e6a23c}
.choice-options{display:flex;flex-direction:column;gap:10px;margin:16px 0}
.choice-option{display:flex;align-items:center;gap:10px;padding:12px 16px;border:2px solid #e4e7ed;border-radius:8px;cursor:pointer}
.choice-option:active{transform:scale(.98)}
.choice-option.selected{border-color:#409eff;background:#ecf5ff}
.choice-option .opt-key{width:28px;height:28px;display:flex;align-items:center;justify-content:center;background:#f0f2f5;border-radius:50%;font-weight:700}
.choice-option.selected .opt-key{background:#409eff;color:#fff}
.choice-option.correct{border-color:#67c23a;background:#f0f9eb}
.choice-option.correct .opt-key{background:#67c23a;color:#fff}
.choice-option.wrong{border-color:#f56c6c;background:#fef0f0}
.student-qa{padding:10px 16px;background:#fff;border-top:1px solid #eee}
.emoji-bar{display:flex;gap:8px;justify-content:center;margin-bottom:8px}
.emoji-btn{font-size:24px;cursor:pointer;padding:4px;transition:transform .15s;user-select:none}
.emoji-btn:active{transform:scale(1.3)}
.qa-input-row{max-width:500px;margin:0 auto}
.result-card{text-align:center}
.history-section{margin-top:24px;width:100%;max-width:500px}
.history-section h4{font-size:14px;color:#909399;margin-bottom:10px}
.history-card{padding:12px 14px;margin-bottom:8px;border:1px solid #ebeef5;border-radius:8px;background:#fafafa}
.hist-header{display:flex;align-items:center;gap:8px}
.hist-title{font-size:14px;font-weight:500;flex:1}
.hist-badge{font-weight:700;font-size:16px}
.hist-badge.correct{color:#67c23a}
.hist-badge.wrong{color:#f56c6c}
.hist-answer{font-size:13px;color:#666;margin-top:6px}
.hist-answer.correct-key{color:#67c23a}
.ai-explanation{margin-top:16px;border:1px solid #e6a23c;border-radius:8px;overflow:hidden}
.explanation-header{display:flex;justify-content:space-between;align-items:center;padding:10px 14px;background:#fdf6ec;font-weight:600;font-size:14px;color:#b88230}
.explanation-body{padding:14px;font-size:14px;line-height:1.7;color:#333;background:#fff;max-height:240px;overflow-y:auto}
</style>
