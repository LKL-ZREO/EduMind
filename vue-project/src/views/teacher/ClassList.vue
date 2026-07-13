<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { createCourse as apiCreateCourse, updateCourse, deleteCourse, type Course, type PresetTemplate } from '@/api/course'
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
  id: number; name: string; courseGroup: string; courseId: number | null
  qqGroupId: string; description: string
  studentCount: number; inviteCode: string; status: 'ACTIVE' | 'ARCHIVED'; createdAt: string
}

const classList = ref<ClassItem[]>([])
const courseList = ref<Course[]>([])
const presets = ref<Record<string, PresetTemplate>>({})
const loading = ref(true)
const error = ref('')

async function fetchClasses() {
  loading.value = true; error.value = ''
  try {
    const res = await request.get('/teacher/classes')
    if (res.data.code === 200) {
      const groups: Array<{ courseGroup: string | null; courseId: number | null; classes: ClassItem[] }> = res.data.data || []
      classList.value = groups.flatMap(g => g.classes.map(c => ({
        ...c,
        courseGroup: c.courseGroup || g.courseGroup || '',
        courseId: c.courseId || g.courseId || null,
        qqGroupId: c.qqGroupId || ''
      })))
    } else { error.value = res.data.message || '加载失败' }
  } catch (e: any) { error.value = e.response?.data?.message || e.message || '网络错误' }
  finally { loading.value = false }
}

async function fetchCourses() {
  try {
    const res = await request.get('/courses')
    if (res.data.code === 200) courseList.value = res.data.data || []
  } catch { /* ignore */ }
  try {
    const res = await request.get('/courses/presets')
    if (res.data.code === 200) presets.value = res.data.data || {}
  } catch { /* ignore */ }
}

function courseIdToName(id: number | null): string {
  if (!id) return ''
  const c = courseList.value.find(c => c.id === id)
  return c ? c.name : ''
}

onMounted(() => { fetchClasses(); fetchCourses() })

interface CourseGroup { name: string; courseId: number | null; classes: ClassItem[]; totalStudents: number; activeCount: number }

const courseGroups = computed<CourseGroup[]>(() => {
  const map = new Map<string, { id: number | null; classes: ClassItem[] }>()
  // 班级分组：优先 courseGroup，其次 courseId 映射到课程名
  for (const c of classList.value) {
    let key = c.courseGroup
    if (!key && c.courseId) key = courseIdToName(c.courseId)
    if (!key) key = '__ungrouped__'
    if (!map.has(key)) map.set(key, { id: c.courseId, classes: [] })
    map.get(key)!.classes.push(c)
  }
  // 后端课程表中没有班级的课程也要显示
  for (const c of courseList.value) {
    if (!map.has(c.name)) map.set(c.name, { id: c.id, classes: [] })
    else if (map.get(c.name)!.id == null) map.get(c.name)!.id = c.id
  }
  const entries = [...map.entries()].sort((a, b) => {
    if (a[0] === '__ungrouped__') return 1
    if (b[0] === '__ungrouped__') return -1
    return a[0].localeCompare(b[0])
  })
  return entries.map(([key, v]) => ({
    name: key === '__ungrouped__' ? '未分组班级' : key,
    courseId: v.id,
    classes: v.classes.sort((a, b) => {
      if (a.status !== b.status) return a.status === 'ACTIVE' ? -1 : 1
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    }),
    totalStudents: v.classes.reduce((s, c) => s + c.studentCount, 0),
    activeCount: v.classes.filter(c => c.status === 'ACTIVE').length,
  }))
})

// ==================== 创建/编辑课程 ====================
const showCreateCourseDialog = ref(false)
const editingCourse = ref(false)
const courseForm = ref({ id: 0, name: '', presetKey: '', systemPrompt: '', knowledgeScope: '' })
const creatingCourse = ref(false)

function openCreateCourseDialogFn() {
  editingCourse.value = false
  courseForm.value = { id: 0, name: '', presetKey: 'generic', systemPrompt: '', knowledgeScope: '' }
  onPresetChange('generic')
  showCreateCourseDialog.value = true
}

function openEditCourseDialog(group: CourseGroup) {
  editingCourse.value = true
  courseForm.value = {
    id: group.courseId || 0,
    name: group.name,
    presetKey: '',
    systemPrompt: '',
    knowledgeScope: ''
  }
  showCreateCourseDialog.value = true
}

function closeCreateCourseDialog() { showCreateCourseDialog.value = false }

function onPresetChange(key: string) {
  courseForm.value.presetKey = key
  const p = presets.value[key]
  if (p) {
    courseForm.value.systemPrompt = p.prompt.replace('{{courseName}}', courseForm.value.name || '新课程')
  }
}

async function handleCreateOrEditCourse() {
  const f = courseForm.value
  if (!f.name.trim()) return
  creatingCourse.value = true
  try {
    if (editingCourse.value && f.id) {
      await updateCourse(f.id, { name: f.name.trim(), systemPrompt: f.systemPrompt, knowledgeScope: f.knowledgeScope })
      ElMessage.success('课程已更新')
    } else {
      await apiCreateCourse({ name: f.name.trim(), presetKey: f.presetKey || undefined, systemPrompt: f.systemPrompt || undefined, knowledgeScope: f.knowledgeScope || undefined })
      ElMessage.success('课程创建成功')
    }
    closeCreateCourseDialog()
    fetchClasses()
  } catch (e: any) { ElMessage.error(e.message || '操作失败') }
  finally { creatingCourse.value = false }
}

async function handleDeleteCourse() {
  if (!confirm(`确定要删除课程「${courseForm.value.name}」吗？\n课程下的班级不会删除，但会变成未分组。`)) return
  try {
    await deleteCourse(courseForm.value.id)
    ElMessage.success('课程已删除')
    closeCreateCourseDialog()
    fetchClasses()
  } catch (e: any) { ElMessage.error(e.message || '删除失败') }
}

// ==================== 创建班级 ====================
const showCreateDialog = ref(false)
const createForm = ref({ name: '', courseGroup: '', courseId: null as number | null, qqGroupId: '', description: '' })
const creating = ref(false)

function openCreateDialog() {
  createForm.value = { name: '', courseGroup: '', courseId: null, qqGroupId: '', description: '' }
  showCreateDialog.value = true
}

function closeCreateDialog() { showCreateDialog.value = false }

async function createClass() {
  if (!createForm.value.name.trim()) return
  creating.value = true
  try {
    const body: any = { name: createForm.value.name.trim(), description: createForm.value.description.trim() || undefined }
    if (createForm.value.courseId) body.courseId = createForm.value.courseId
    if (createForm.value.courseGroup.trim()) body.courseGroup = createForm.value.courseGroup.trim()
    if (createForm.value.qqGroupId.trim()) body.qqGroupId = createForm.value.qqGroupId.trim()
    const res = await request.post('/teacher/classes', body)
    if (res.data.code === 200) { closeCreateDialog(); fetchClasses(); ElMessage.success('班级创建成功') }
    else { ElMessage.error(res.data.message || '创建失败') }
  } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message || '创建失败') }
  finally { creating.value = false }
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
        <button class="btn-secondary" @click="openCreateCourseDialogFn">+ 创建课程</button>
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
          <button v-if="group.name !== '未分组班级' && group.courseId" class="btn-edit-course" @click="openEditCourseDialog(group)" title="编辑课程">⚙️ 课程设置</button>
        </div>
        <div v-if="group.classes.length === 0" class="empty-course"><p>这门课程下还没有班级</p><button class="btn-add-class" @click="createForm.courseGroup = group.name; createForm.courseId = group.courseId; openCreateDialog()">+ 添加班级</button></div>
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

    <!-- 创建/编辑课程弹窗 -->
    <div v-if="showCreateCourseDialog" class="dialog-overlay" @click.self="closeCreateCourseDialog">
      <div class="dialog-content dialog-md">
        <div class="dialog-header"><h3>{{ editingCourse ? '⚙️ 课程设置' : '📘 创建新课程' }}</h3><button class="dialog-close" @click="closeCreateCourseDialog">×</button></div>
        <div class="dialog-body">
          <div class="form-group"><label>课程名称 <span class="required">*</span></label><input v-model="courseForm.name" placeholder="例如：数据结构" class="form-input" maxlength="30" @keyup.enter="handleCreateOrEditCourse" /></div>
          <div v-if="!editingCourse" class="form-group">
            <label>预设模板</label>
            <select v-model="courseForm.presetKey" @change="onPresetChange(courseForm.presetKey)" class="form-select">
              <option v-for="(p, key) in presets" :key="key" :value="key">{{ p.name }}</option>
              <option value="">自定义（空白模板）</option>
            </select>
          </div>
          <div class="form-group">
            <label>System Prompt <span class="optional">（AI 助教的课程设定）</span></label>
            <textarea v-model="courseForm.systemPrompt" rows="6" class="form-textarea" placeholder="描述 AI 助教在该课程中的行为规则和知识范围..."></textarea>
          </div>
          <div v-if="editingCourse && courseForm.id" class="form-group form-danger">
            <button class="btn-danger-outline" @click="handleDeleteCourse">🗑️ 删除此课程</button>
          </div>
        </div>
        <div class="dialog-footer"><button class="btn-cancel" @click="closeCreateCourseDialog">取消</button><button class="btn-primary" @click="handleCreateOrEditCourse" :disabled="!courseForm.name.trim() || creatingCourse">{{ creatingCourse ? '保存中...' : (editingCourse ? '保存' : '确认创建') }}</button></div>
      </div>
    </div>

    <!-- 创建班级弹窗 -->
    <div v-if="showCreateDialog" class="dialog-overlay" @click.self="closeCreateDialog">
      <div class="dialog-content">
        <div class="dialog-header"><h3>📝 创建新班级</h3><button class="dialog-close" @click="closeCreateDialog">×</button></div>
        <div class="dialog-body">
          <div class="form-group"><label>班级名称 <span class="required">*</span></label><input v-model="createForm.name" placeholder="例如：计科2101班" class="form-input" maxlength="30" @keyup.enter="createClass" /></div>
          <div class="form-group">
            <label>所属课程</label>
            <select v-model="createForm.courseId" class="form-select">
              <option :value="null" selected>无（未分组）</option>
              <option v-for="c in courseList" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <div class="form-group"><label>QQ群号</label><input v-model="createForm.qqGroupId" placeholder="选填，用于 AI 自动识别班级" class="form-input" maxlength="20" /></div>
          <div class="form-group"><label>班级描述</label><textarea v-model="createForm.description" placeholder="选填，例如：2024-2025学年第一学期" rows="2" class="form-textarea" maxlength="200"></textarea></div>
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
.empty-state { text-align: center; padding: 80px 20px; color: #909399; font-size: 1rem; }
.empty-icon { font-size: 3rem; margin-bottom: 12px; }
.empty-sub { font-size: 0.85rem; margin-top: 8px; }
.btn-retry { margin-top: 12px; padding: 8px 20px; background: #409EFF; color: #fff; border: none; border-radius: 8px; cursor: pointer; }

.course-section { margin-bottom: 32px; }
.course-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; padding: 14px 18px; background: #ecf5ff; border-radius: 10px; border: 1px solid #d9ecff; }
.course-info { display: flex; align-items: center; gap: 14px; }
.course-icon { font-size: 1.5rem; width: 42px; height: 42px; border-radius: 10px; background: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.course-meta { display: flex; flex-direction: column; gap: 4px; }
.course-name { margin: 0; font-size: 1.1rem; color: #303133; }
.course-stats { font-size: 0.8rem; color: #909399; }
.btn-edit-course { padding: 6px 14px; background: #fff; color: #409EFF; border: 1px solid #d9ecff; border-radius: 6px; font-size: 0.8rem; cursor: pointer; transition: all 0.2s; white-space: nowrap; }
.btn-edit-course:hover { background: #409EFF; color: #fff; }

.empty-course { display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 32px 20px; border: 1px dashed #dcdfe6; border-radius: 10px; text-align: center; background: #fff; }
.empty-course p { color: #909399; font-size: 0.85rem; margin: 0; }
.btn-add-class { padding: 8px 18px; background: #409EFF; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 0.82rem; font-weight: 600; transition: all 0.2s; }
.btn-add-class:hover { background: #337ecc; }

.class-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px; }
.class-card { background: #fff; border: 1px solid #e4e7ed; border-radius: 10px; padding: 18px; transition: all 0.2s; display: flex; flex-direction: column; gap: 12px; cursor: default; }
.class-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.06); border-color: #c6e2ff; }
.class-card.archived { opacity: 0.7; background: #fafafa; }
.card-header { display: flex; align-items: center; gap: 12px; }
.card-avatar { width: 44px; height: 44px; border-radius: 10px; background: linear-gradient(135deg, #409EFF, #337ecc); display: flex; align-items: center; justify-content: center; font-size: 1.2rem; font-weight: 700; color: #fff; flex-shrink: 0; }
.archived-avatar { background: linear-gradient(135deg, #c0c4cc, #909399) !important; }
.card-title-group { flex: 1; min-width: 0; }
.card-title-row { display: flex; align-items: center; gap: 8px; }
.card-title { margin: 0; font-size: 1rem; color: #303133; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.status-dot { padding: 2px 8px; border-radius: 10px; font-size: 0.7rem; font-weight: 600; white-space: nowrap; }
.status-dot.active { background: rgba(103,194,58,.12); color: #67C23A; }
.status-dot.archived { background: #f5f7fa; color: #909399; }
.card-date { font-size: 0.72rem; color: #909399; }
.card-desc { margin: 0; font-size: 0.82rem; color: #606266; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; min-height: 1.2em; }
.card-stats { display: flex; gap: 20px; }
.stat { display: flex; align-items: center; gap: 4px; }
.stat-icon { font-size: 0.85rem; }
.stat-value { font-size: 0.9rem; color: #303133; font-weight: 600; }
.stat-value.code { font-family: Consolas, monospace; font-size: 0.78rem; color: #409EFF; letter-spacing: 2px; }
.stat-label { font-size: 0.75rem; color: #909399; }
.invite-stat { cursor: pointer; user-select: none; }
.invite-stat:hover .code { text-decoration: underline; }
.invite-hint { font-size: 0.7rem; color: #c0c4cc; }
.btn-manage { padding: 8px 0; border-radius: 8px; background: #ecf5ff; color: #409EFF; border: none; cursor: pointer; font-size: 0.82rem; font-weight: 600; transition: all 0.2s; }
.btn-manage:hover { background: #409EFF; color: #fff; }
.btn-ghost { background: #f5f7fa; color: #909399; }
.btn-ghost:hover { background: #e4e7ed; color: #606266; }

/* dialog */
.dialog-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.dialog-content { background: #fff; border: 1px solid #e4e7ed; border-radius: 14px; width: 90%; max-width: 520px; box-shadow: 0 8px 32px rgba(0,0,0,.1); }
.dialog-md { max-width: 580px; }
.dialog-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px 0; }
.dialog-header h3 { margin: 0; font-size: 1.05rem; color: #303133; }
.dialog-close { background: none; border: none; color: #c0c4cc; font-size: 1.4rem; cursor: pointer; padding: 0; line-height: 1; }
.dialog-close:hover { color: #606266; }
.dialog-body { padding: 20px 24px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 0 24px 20px; }
.dialog-tip { font-size: 0.85rem; color: #909399; margin: 0 0 16px 0; }

.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 6px; font-size: 0.85rem; color: #606266; font-weight: 500; }
.form-group .required { color: #F56C6C; }
.form-group .optional { color: #c0c4cc; font-weight: 400; font-size: 0.78rem; }
.form-input { width: 100%; padding: 9px 12px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #303133; font-size: 0.9rem; outline: none; box-sizing: border-box; }
.form-input:focus { border-color: #409EFF; box-shadow: 0 0 0 2px rgba(64,158,255,.1); }
.form-textarea { width: 100%; padding: 9px 12px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #303133; font-size: 0.82rem; outline: none; resize: vertical; font-family: inherit; box-sizing: border-box; }
.form-textarea:focus { border-color: #409EFF; box-shadow: 0 0 0 2px rgba(64,158,255,.1); }
.form-select { width: 100%; padding: 9px 12px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #303133; font-size: 0.9rem; outline: none; box-sizing: border-box; cursor: pointer; }
.form-select:focus { border-color: #409EFF; }
.form-danger { margin-top: 20px; padding-top: 16px; border-top: 1px solid #ebeef5; }

.btn-cancel { padding: 9px 20px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #606266; cursor: pointer; font-size: 0.85rem; transition: all 0.2s; }
.btn-cancel:hover { background: #f5f7fa; }
.btn-primary { padding: 9px 24px; background: #409EFF; color: #fff; border: none; border-radius: 8px; font-size: 0.85rem; font-weight: 600; cursor: pointer; transition: all 0.2s; }
.btn-primary:hover { background: #337ecc; }
.btn-primary:disabled { background: #a0cfff; cursor: not-allowed; }
.btn-danger-outline { padding: 8px 16px; background: #fff; color: #F56C6C; border: 1px solid #fde2e2; border-radius: 8px; font-size: 0.82rem; cursor: pointer; transition: all 0.2s; }
.btn-danger-outline:hover { background: #fef0f0; }
</style>
