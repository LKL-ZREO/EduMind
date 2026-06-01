<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import InviteDialog from './InviteDialog.vue'

const router = useRouter()

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

// ==================== 数据 ====================
interface ClassItem {
  id: number; name: string; courseGroup: string; description: string
  studentCount: number; inviteCode: string; status: 'ACTIVE' | 'ARCHIVED'; createdAt: string
}

const classList = ref<ClassItem[]>([])
const loading = ref(true)
const error = ref('')

async function fetchClasses() {
  loading.value = true; error.value = ''
  try {
    const res = await request.get('/teacher/classes')
    if (res.data.code === 200) {
      const groups: Array<{ courseGroup: string | null; classes: ClassItem[] }> = res.data.data || []
      classList.value = groups.flatMap(g => g.classes.map(c => ({ ...c, courseGroup: c.courseGroup || g.courseGroup || '' })))
    } else { error.value = res.data.message || '加载失败' }
  } catch (e: any) { error.value = e.response?.data?.message || e.message || '网络错误' }
  finally { loading.value = false }
}

onMounted(fetchClasses)

interface CourseGroup { name: string; classes: ClassItem[]; totalStudents: number; activeCount: number }

const courseGroups = computed<CourseGroup[]>(() => {
  const map = new Map<string, ClassItem[]>()
  for (const c of classList.value) { const key = c.courseGroup || '__ungrouped__'; if (!map.has(key)) map.set(key, []); map.get(key)!.push(c) }
  for (const name of emptyCourses.value) { if (!map.has(name)) map.set(name, []) }
  const entries = [...map.entries()].sort((a, b) => { if (a[0] === '__ungrouped__') return 1; if (b[0] === '__ungrouped__') return -1; return a[0].localeCompare(b[0]) })
  return entries.map(([key, classes]) => ({
    name: key === '__ungrouped__' ? '未分组班级' : key,
    classes: classes.sort((a, b) => { if (a.status !== b.status) return a.status === 'ACTIVE' ? -1 : 1; return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime() }),
    totalStudents: classes.reduce((s, c) => s + c.studentCount, 0), activeCount: classes.filter(c => c.status === 'ACTIVE').length,
  }))
})

// ==================== 创建班级 ====================
const showCreateDialog = ref(false)
const createForm = ref({ name: '', courseGroup: '', description: '' })
const creating = ref(false)
const existingCourseNames = computed(() => [...new Set(classList.value.map(c => c.courseGroup).filter(Boolean))].sort())

function openCreateDialog() { createForm.value = { name: '', courseGroup: '', description: '' }; showCreateDialog.value = true }
function closeCreateDialog() { showCreateDialog.value = false }

async function createClass() {
  if (!createForm.value.name.trim()) return
  creating.value = true
  try {
    const res = await request.post('/teacher/classes', { name: createForm.value.name.trim(), courseGroup: createForm.value.courseGroup.trim() || undefined, description: createForm.value.description.trim() || undefined })
    if (res.data.code === 200) { closeCreateDialog(); fetchClasses() }
    else { ElMessage.error(res.data.message || '创建失败') }
  } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message || '创建失败') }
  finally { creating.value = false }
}

const showCreateCourseDialog = ref(false)
const newCourseName = ref('')
const creatingCourse = ref(false)
const emptyCourses = ref<string[]>([])

function openCreateCourseDialog() { newCourseName.value = ''; showCreateCourseDialog.value = true }
function closeCreateCourseDialog() { showCreateCourseDialog.value = false }

function createCourse() {
  if (!newCourseName.value.trim()) return
  const name = newCourseName.value.trim()
  if (existingCourseNames.value.includes(name) || emptyCourses.value.includes(name)) { ElMessage.warning(`课程「${name}」已存在`); return }
  creatingCourse.value = true
  setTimeout(() => { emptyCourses.value.push(name); creatingCourse.value = false; closeCreateCourseDialog() }, 300)
}

function queryCourse(queryString: string, cb: (results: Array<{ value: string }>) => void) {
  const results = existingCourseNames.value.filter(n => n.toLowerCase().includes((queryString || '').toLowerCase())).map(n => ({ value: n }))
  cb(results)
}

const searchKeyword = ref('')
const filteredGroups = computed(() => {
  if (!searchKeyword.value.trim()) return courseGroups.value
  const kw = searchKeyword.value.toLowerCase()
  return courseGroups.value.map(g => ({ ...g, classes: g.classes.filter(c => c.name.toLowerCase().includes(kw) || g.name.toLowerCase().includes(kw)) })).filter(g => g.classes.length > 0)
})

function goManage(id: number) { router.push(`/teacher/classes/${id}`) }

const showInvite = ref(false)
const currentInviteCode = ref('')
function openInvite(code: string) { currentInviteCode.value = code; showInvite.value = true }
</script>

<template>
  <div class="class-list-page">
    <div class="page-header">
      <div class="header-left"><h2>📚 班级管理</h2><span class="header-sub">{{ courseGroups.length }} 门课程 / {{ classList.length }} 个班级</span></div>
      <div class="header-right">
        <input v-model="searchKeyword" placeholder="搜索班级或课程..." class="search-input" />
        <button class="btn-secondary" @click="openCreateCourseDialog">+ 创建课程</button>
        <button class="btn-create" @click="openCreateDialog">+ 创建班级</button>
      </div>
    </div>

    <div v-if="loading" class="empty-state"><p>加载中...</p></div>
    <div v-else-if="error" class="empty-state"><div class="empty-icon">⚠️</div><p>{{ error }}</p><button class="btn-retry" @click="fetchClasses">重试</button></div>
    <div v-else-if="classList.length === 0" class="empty-state"><div class="empty-icon">📭</div><p>暂无班级</p><p class="empty-sub">点击「创建课程」或「创建班级」开始管理你的教学班级</p></div>
    <div v-else-if="filteredGroups.length === 0" class="empty-state"><div class="empty-icon">🔍</div><p>没有匹配的班级</p></div>

    <template v-for="group in filteredGroups" :key="group.name">
      <section class="course-section">
        <div class="course-header">
          <div class="course-info"><div class="course-icon">{{ group.name === '未分组班级' ? '📦' : '📘' }}</div><div class="course-meta"><h3 class="course-name">{{ group.name }}</h3><span class="course-stats">{{ group.classes.length }} 个班级<template v-if="group.activeCount > 0"> · {{ group.activeCount }} 个进行中</template> · {{ group.totalStudents }} 名学生</span></div></div>
        </div>
        <div v-if="group.classes.length === 0" class="empty-course"><p>这门课程下还没有班级</p><button class="btn-add-class" @click="createForm.courseGroup = group.name; openCreateDialog()">+ 添加班级</button></div>
        <div v-else class="class-grid">
          <div v-for="cls in group.classes" :key="cls.id" class="class-card" :class="{ archived: cls.status === 'ARCHIVED' }">
            <div class="card-header">
              <div class="card-avatar" :class="{ 'archived-avatar': cls.status === 'ARCHIVED' }">{{ cls.name.charAt(0) }}</div>
              <div class="card-title-group"><div class="card-title-row"><h4 class="card-title">{{ cls.name }}</h4><span class="status-dot" :class="cls.status === 'ACTIVE' ? 'active' : 'archived'">{{ cls.status === 'ACTIVE' ? '进行中' : '已归档' }}</span></div><span class="card-date">创建于 {{ formatDate(cls.createdAt) }}</span></div>
            </div>
            <p class="card-desc">{{ cls.description }}</p>
            <div class="card-stats">
              <div class="stat"><span class="stat-icon">👥</span><span class="stat-value">{{ cls.studentCount }}</span><span class="stat-label">位学生</span></div>
              <div class="stat invite-stat" @click.stop="openInvite(cls.inviteCode)"><span class="stat-icon">🔗</span><span class="stat-value code">{{ cls.inviteCode }}</span><span class="stat-label invite-hint">点击邀请</span></div>
            </div>
            <button class="btn-manage" :class="{ 'btn-ghost': cls.status === 'ARCHIVED' }" @click="goManage(cls.id)">{{ cls.status === 'ACTIVE' ? '管理班级 →' : '查看详情 →' }}</button>
          </div>
        </div>
      </section>
    </template>

    <InviteDialog :visible="showInvite" :invite-code="currentInviteCode" @close="showInvite = false" />

    <!-- 创建课程弹窗 -->
    <div v-if="showCreateCourseDialog" class="dialog-overlay" @click.self="closeCreateCourseDialog">
      <div class="dialog-content dialog-sm">
        <div class="dialog-header"><h3>📘 创建新课程</h3><button class="dialog-close" @click="closeCreateCourseDialog">×</button></div>
        <div class="dialog-body">
          <p class="dialog-tip">课程是对多个班级的逻辑分组。同一门课的不同班级可归入同一个课程下，方便统一管理。</p>
          <div class="form-group"><label>课程名称 <span class="required">*</span></label><input v-model="newCourseName" placeholder="例如：数据结构" class="form-input" maxlength="30" @keyup.enter="createCourse" /></div>
        </div>
        <div class="dialog-footer"><button class="btn-cancel" @click="closeCreateCourseDialog">取消</button><button class="btn-primary" @click="createCourse" :disabled="!newCourseName.trim() || creatingCourse">{{ creatingCourse ? '创建中...' : '确认创建' }}</button></div>
      </div>
    </div>

    <!-- 创建班级弹窗 -->
    <div v-if="showCreateDialog" class="dialog-overlay" @click.self="closeCreateDialog">
      <div class="dialog-content">
        <div class="dialog-header"><h3>📝 创建新班级</h3><button class="dialog-close" @click="closeCreateDialog">×</button></div>
        <div class="dialog-body">
          <div class="form-group"><label>班级名称 <span class="required">*</span></label><input v-model="createForm.name" placeholder="例如：计科2101班" class="form-input" maxlength="30" @keyup.enter="createClass" /></div>
          <div class="form-group"><label>所属课程</label><el-autocomplete v-model="createForm.courseGroup" :fetch-suggestions="queryCourse" :trigger-on-focus="true" placeholder="输入或选择已有课程（选填）" class="course-autocomplete" maxlength="30" clearable popper-class="course-popper" /><p class="form-hint">输入已有的课程名可自动归入该课程下</p></div>
          <div class="form-group"><label>班级描述</label><textarea v-model="createForm.description" placeholder="选填，例如：2024-2025学年第一学期数据结构课程" rows="3" class="form-textarea" maxlength="200"></textarea></div>
        </div>
        <div class="dialog-footer"><button class="btn-cancel" @click="closeCreateDialog">取消</button><button class="btn-primary" @click="createClass" :disabled="!createForm.name.trim() || creating">{{ creating ? '创建中...' : '确认创建' }}</button></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.class-list-page { max-width: 1100px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 28px; flex-wrap: wrap; gap: 12px; }
.header-left { display: flex; align-items: baseline; gap: 12px; }
.header-left h2 { font-size: 1.5rem; color: #303133; margin: 0; font-weight: 600; }
.header-sub { color: #909399; font-size: 0.85rem; }
.header-right { display: flex; gap: 10px; align-items: center; }
.search-input { padding: 8px 14px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #303133; font-size: 0.85rem; outline: none; width: 200px; box-sizing: border-box; }
.search-input:focus { border-color: #409EFF; box-shadow: 0 0 0 2px rgba(64,158,255,.1); }
.search-input::placeholder { color: #c0c4cc; }
.btn-secondary { padding: 10px 20px; background: #fff; color: #409EFF; border: 1px solid #409EFF; border-radius: 8px; font-size: 0.85rem; font-weight: 600; cursor: pointer; transition: all 0.2s; white-space: nowrap; }
.btn-secondary:hover { background: #ecf5ff; }
.btn-create { padding: 10px 24px; background: #409EFF; color: #fff; border: none; border-radius: 8px; font-size: 0.9rem; font-weight: 600; cursor: pointer; transition: all 0.2s; white-space: nowrap; }
.btn-create:hover { background: #337ecc; }
.empty-course { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 32px 20px; border: 1px dashed #dcdfe6; border-radius: 10px; text-align: center; background: #fff; }
.empty-course p { color: #909399; font-size: 0.85rem; margin: 0; }
.btn-add-class { padding: 8px 18px; background: #409EFF; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 0.82rem; font-weight: 600; transition: all 0.2s; }
.btn-add-class:hover { background: #337ecc; }
.course-section { margin-bottom: 32px; }
.course-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; padding: 14px 18px; background: #ecf5ff; border-radius: 10px; border: 1px solid #d9ecff; }
.course-info { display: flex; align-items: center; gap: 14px; }
.course-icon { font-size: 1.5rem; width: 42px; height: 42px; border-radius: 10px; background: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.course-name { margin: 0; font-size: 1.05rem; color: #303133; font-weight: 600; }
.course-stats { font-size: 0.78rem; color: #909399; }
.class-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 14px; }
.class-card { background: #fff; border: 1px solid #e4e7ed; border-radius: 12px; padding: 20px; transition: all 0.25s; display: flex; flex-direction: column; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
.class-card:hover { border-color: #409EFF; transform: translateY(-2px); box-shadow: 0 6px 24px rgba(64,158,255,.1); }
.class-card.archived { opacity: 0.65; }
.class-card.archived:hover { opacity: 0.85; border-color: #dcdfe6; box-shadow: none; }
.card-header { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.card-avatar { width: 38px; height: 38px; border-radius: 8px; background: linear-gradient(135deg, #409EFF, #337ecc); display: flex; align-items: center; justify-content: center; font-size: 1rem; font-weight: 700; color: #fff; flex-shrink: 0; }
.card-avatar.archived-avatar { background: linear-gradient(135deg, #c0c4cc, #909399); }
.card-title-group { flex: 1; min-width: 0; }
.card-title-row { display: flex; align-items: center; gap: 8px; }
.card-title { font-size: 0.95rem; color: #303133; margin: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.status-dot { flex-shrink: 0; padding: 2px 8px; border-radius: 8px; font-size: 0.68rem; font-weight: 600; }
.status-dot.active { background: rgba(103,194,58,.12); color: #67C23A; }
.status-dot.archived { background: #f5f7fa; color: #909399; }
.card-date { font-size: 0.72rem; color: #909399; }
.card-desc { color: #606266; font-size: 0.8rem; line-height: 1.5; margin: 0 0 12px 0; flex: 1; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.card-stats { display: flex; gap: 16px; margin-bottom: 14px; padding: 10px 12px; background: #f5f7fa; border-radius: 8px; }
.stat { display: flex; align-items: center; gap: 5px; }
.stat-icon { font-size: 0.85rem; }
.stat-value { font-size: 0.95rem; font-weight: 700; color: #303133; }
.stat-value.code { letter-spacing: 2px; font-family: 'Consolas', monospace; font-size: 0.85rem; color: #409EFF; }
.stat-label { font-size: 0.72rem; color: #909399; }
.invite-stat { cursor: pointer; transition: all 0.15s; margin-left: auto; }
.invite-stat:hover { background: #ecf5ff; border-radius: 4px; padding: 0 5px; }
.invite-stat:hover .invite-hint { opacity: 1; }
.invite-hint { opacity: 0; transition: opacity 0.15s; }
.btn-manage { width: 100%; padding: 8px 0; background: transparent; border: 1px solid #409EFF; border-radius: 7px; color: #409EFF; font-size: 0.82rem; cursor: pointer; transition: all 0.2s; }
.btn-manage:hover { background: #409EFF; color: #fff; }
.btn-ghost { border-color: #dcdfe6; color: #909399; }
.btn-ghost:hover { background: #f5f7fa; border-color: #c0c4cc; color: #606266; }
.empty-state { text-align: center; padding: 80px 20px; color: #909399; }
.empty-icon { font-size: 4rem; margin-bottom: 16px; }
.empty-state p { font-size: 1.1rem; margin: 0 0 8px 0; color: #606266; }
.empty-sub { font-size: 0.85rem !important; color: #909399 !important; }
.btn-retry { margin-top: 12px; padding: 8px 20px; background: #fff; border: 1px solid #409EFF; border-radius: 8px; color: #409EFF; cursor: pointer; font-size: 0.85rem; transition: all 0.2s; }
.btn-retry:hover { background: #ecf5ff; }
.dialog-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.dialog-content { background: #fff; border: 1px solid #e4e7ed; border-radius: 14px; width: 90%; max-width: 480px; box-shadow: 0 8px 32px rgba(0,0,0,.1); }
.dialog-sm { max-width: 420px; }
.dialog-tip { font-size: 0.82rem; color: #909399; line-height: 1.6; margin: 0 0 16px 0; padding: 10px 14px; background: #f5f7fa; border-radius: 8px; border-left: 3px solid #409EFF; }
.dialog-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px 0; }
.dialog-header h3 { margin: 0; font-size: 1.1rem; color: #303133; }
.dialog-close { background: none; border: none; color: #c0c4cc; font-size: 1.4rem; cursor: pointer; padding: 0; line-height: 1; }
.dialog-close:hover { color: #606266; }
.dialog-body { padding: 20px 24px; }
.dialog-body .form-group { margin-bottom: 16px; }
.dialog-body .form-group:last-child { margin-bottom: 0; }
.dialog-body label { display: block; margin-bottom: 6px; font-size: 0.85rem; color: #606266; }
.required { color: #F56C6C; }
.form-hint { margin: 4px 0 0 0; font-size: 0.73rem; color: #c0c4cc; }
.form-input, .form-textarea { width: 100%; padding: 10px 12px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #303133; font-size: 0.9rem; outline: none; transition: border-color 0.2s; box-sizing: border-box; }
.form-input:focus, .form-textarea:focus { border-color: #409EFF; box-shadow: 0 0 0 2px rgba(64,158,255,.1); }
.form-input::placeholder, .form-textarea::placeholder { color: #c0c4cc; }
.form-textarea { resize: vertical; font-family: inherit; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 0 24px 20px; }
.btn-cancel { padding: 9px 20px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #606266; cursor: pointer; font-size: 0.85rem; transition: all 0.2s; }
.btn-cancel:hover { background: #f5f7fa; color: #303133; }
.btn-primary { padding: 9px 24px; background: #409EFF; border: none; border-radius: 8px; color: #fff; cursor: pointer; font-size: 0.85rem; font-weight: 600; transition: all 0.2s; }
.btn-primary:hover { background: #337ecc; }
.btn-primary:disabled { background: #a0cfff; cursor: not-allowed; }
.course-autocomplete { width: 100%; }
@media (max-width: 768px) { .class-grid { grid-template-columns: 1fr; } .page-header { flex-direction: column; align-items: flex-start; } .header-right { width: 100%; flex-wrap: wrap; } .search-input { flex: 1; } }
</style>

<style>
.course-popper { background: #fff !important; border: 1px solid #e4e7ed !important; box-shadow: 0 4px 12px rgba(0,0,0,.08) !important; }
.course-popper .el-autocomplete-suggestion__wrap { background: #fff; }
.course-popper li { color: #303133; padding: 8px 16px; }
.course-popper li:hover, .course-popper li.highlighted { background: #ecf5ff !important; color: #409EFF !important; }
</style>
