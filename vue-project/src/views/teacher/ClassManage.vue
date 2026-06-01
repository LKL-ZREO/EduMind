<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import InviteDialog from './InviteDialog.vue'
import StudentImport from './StudentImport.vue'

const router = useRouter(); const route = useRoute(); const classId = Number(route.params.id)

function formatDate(dateStr: string): string {
  if (!dateStr) return ''; const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
}

interface Student { studentId: string; studentName: string; joinedAt: string; source?: string }
interface ClassDetail { id: number; name: string; courseGroup: string; description: string; inviteCode: string; status: 'ACTIVE' | 'ARCHIVED'; createdAt: string; updatedAt?: string }

const classData = ref<ClassDetail>({ id: classId, name: '', courseGroup: '', description: '', inviteCode: '', status: 'ACTIVE', createdAt: '' })
const students = ref<Student[]>([])
const loading = ref(true); const error = ref('')

async function fetchDetail() {
  loading.value = true; error.value = ''
  try {
    const res = await request.get(`/teacher/classes/${classId}`)
    if (res.data.code === 200) {
      const d = res.data.data
      classData.value = { id: d.class.id, name: d.class.name, courseGroup: d.class.courseGroup || '', description: d.class.description || '', inviteCode: d.class.inviteCode, status: d.class.status, createdAt: d.class.createdAt || '', updatedAt: d.class.updatedAt }
      students.value = (d.students || []).map((s: any) => ({ studentId: s.studentId, studentName: s.studentName, joinedAt: s.createdAt || s.joinedAt || '', source: s.source }))
    } else { error.value = res.data.message || '加载失败' }
  } catch (e: any) { error.value = e.response?.data?.message || e.message || '网络错误' }
  finally { loading.value = false }
}

const courseNames = ref<string[]>([])
async function fetchCourseNames() {
  try {
    const res = await request.get('/teacher/classes')
    if (res.data.code === 200) { const s = new Set<string>(); for (const g of res.data.data || []) { if (g.courseGroup) s.add(g.courseGroup); for (const c of g.classes || []) { if (c.courseGroup) s.add(c.courseGroup) } } courseNames.value = [...s].sort() }
  } catch { /* ignore */ }
}

function queryCourse(q: string, cb: (r: Array<{ value: string }>) => void) { cb(courseNames.value.filter(n => n.toLowerCase().includes((q || '').toLowerCase())).map(n => ({ value: n }))) }

onMounted(() => { fetchDetail(); fetchCourseNames() })

const showInvite = ref(false); const showImport = ref(false)
const isEditing = ref(false)
const editForm = ref({ name: '', description: '', courseGroup: '' })
const saving = ref(false)

function startEdit() { editForm.value = { name: classData.value.name, description: classData.value.description, courseGroup: classData.value.courseGroup }; isEditing.value = true }
function cancelEdit() { isEditing.value = false }

async function saveEdit() {
  if (!editForm.value.name.trim()) return; saving.value = true
  try {
    const res = await request.put(`/teacher/classes/${classId}`, { name: editForm.value.name.trim(), description: editForm.value.description.trim(), courseGroup: editForm.value.courseGroup.trim() || undefined })
    if (res.data.code === 200) { classData.value.name = editForm.value.name.trim(); classData.value.description = editForm.value.description.trim(); classData.value.courseGroup = editForm.value.courseGroup.trim(); isEditing.value = false }
    else ElMessage.error(res.data.message || '保存失败')
  } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message || '保存失败') }
  finally { saving.value = false }
}

const togglingArchive = ref(false)
async function toggleArchive() {
  togglingArchive.value = true
  try {
    const res = await request.post(`/teacher/classes/${classId}/archive`)
    if (res.data.code === 200) classData.value.status = classData.value.status === 'ACTIVE' ? 'ARCHIVED' : 'ACTIVE'
    else ElMessage.error(res.data.message || '操作失败')
  } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message || '操作失败') }
  finally { togglingArchive.value = false }
}

const showDeleteConfirm = ref(false); const deleting = ref(false)
function confirmDelete() { if (students.value.length > 0) { ElMessage.warning('班级下还有学生，请先移除所有学生后再删除'); return }; showDeleteConfirm.value = true }
async function doDelete() {
  deleting.value = true
  try {
    const res = await request.delete(`/teacher/classes/${classId}`)
    if (res.data.code === 200) { showDeleteConfirm.value = false; router.push('/teacher/classes') }
    else ElMessage.error(res.data.message || '删除失败')
  } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message || '删除失败') }
  finally { deleting.value = false }
}

const searchKeyword = ref('')
const showRemoveConfirm = ref(false); const removeTarget = ref<Student | null>(null)
const filteredStudents = computed(() => { if (!searchKeyword.value) return students.value; const kw = searchKeyword.value.toLowerCase(); return students.value.filter(s => s.studentId.includes(kw) || s.studentName.toLowerCase().includes(kw)) })
function confirmRemove(s: Student) { removeTarget.value = s; showRemoveConfirm.value = true }
async function doRemove() {
  if (!removeTarget.value) return
  try {
    const sid = encodeURIComponent(removeTarget.value.studentId)
    const res = await request.delete(`/teacher/classes/${classId}/students/${sid}`)
    if (res.data.code === 200) { const i = students.value.findIndex(s => s.studentId === removeTarget.value!.studentId); if (i > -1) students.value.splice(i, 1); showRemoveConfirm.value = false; removeTarget.value = null }
    else ElMessage.error(res.data.message || '移除失败')
  } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message || '移除失败') }
}

function copyCode() { navigator.clipboard.writeText(classData.value.inviteCode).then(() => ElMessage.success('邀请码已复制')) }
function goBack() { router.push('/teacher/classes') }
function onImported() { showImport.value = false; fetchDetail() }
</script>

<template>
  <div class="class-manage-page">
    <div v-if="loading" class="empty-state">加载中...</div>
    <div v-else-if="error" class="empty-state"><div class="empty-icon">⚠️</div><p>{{ error }}</p></div>
    <template v-else>
      <div class="breadcrumb"><a @click="goBack" class="bread-link">📚 班级列表</a><span class="bread-sep">/</span><span class="bread-current">{{ classData.name }}</span><span v-if="classData.status === 'ARCHIVED'" class="archived-badge">已归档</span></div>

      <section class="info-card">
        <div class="info-header">
          <div class="info-title-row">
            <template v-if="!isEditing">
              <div class="class-avatar">{{ classData.name.charAt(0) }}</div>
              <div class="class-main"><h2>{{ classData.name }}</h2><p class="class-desc">{{ classData.description }}</p></div>
              <button class="btn-icon" @click="startEdit" title="编辑">✏️</button>
            </template>
            <template v-else>
              <div class="edit-form">
                <div class="form-row"><input v-model="editForm.name" class="form-input inline-input" placeholder="班级名称" maxlength="30" /></div>
                <div class="form-row"><el-autocomplete v-model="editForm.courseGroup" :fetch-suggestions="queryCourse" :trigger-on-focus="true" placeholder="所属课程（选填）" class="inline-autocomplete" maxlength="30" clearable popper-class="course-popper" /></div>
                <div class="form-row"><textarea v-model="editForm.description" class="form-textarea inline-textarea" placeholder="班级描述" rows="2" maxlength="200"></textarea></div>
                <div class="edit-actions"><button class="btn-sm btn-cancel-sm" @click="cancelEdit">取消</button><button class="btn-sm btn-save" @click="saveEdit" :disabled="!editForm.name.trim() || saving">{{ saving ? '保存中...' : '保存' }}</button></div>
              </div>
            </template>
          </div>
        </div>

        <div class="info-meta">
          <div class="meta-item"><span class="meta-label">邀请码</span><span class="meta-value code-value">{{ classData.inviteCode }}<button class="copy-btn" @click="copyCode" title="复制">📋</button></span></div>
          <div class="meta-item"><span class="meta-label">学生人数</span><span class="meta-value">{{ students.length }} 人</span></div>
          <div class="meta-item"><span class="meta-label">创建时间</span><span class="meta-value">{{ formatDate(classData.createdAt) }}</span></div>
          <div class="meta-item"><span class="meta-label">状态</span><span class="status-tag" :class="classData.status === 'ACTIVE' ? 'active' : 'archived'">{{ classData.status === 'ACTIVE' ? '进行中' : '已归档' }}</span></div>
        </div>

        <div class="info-actions">
          <button class="btn-action btn-action-highlight" @click="showInvite = true">🔗 邀请学生加入</button>
          <button class="btn-action" @click="showImport = true">📥 批量导入学生</button>
          <button class="btn-action" @click="toggleArchive" :disabled="togglingArchive">{{ classData.status === 'ACTIVE' ? '📦 归档班级' : '📂 取消归档' }}</button>
          <button v-if="students.length === 0" class="btn-action btn-danger" @click="confirmDelete">🗑️ 删除班级</button>
          <button v-else class="btn-action btn-danger" disabled title="请先移除所有学生">🗑️ 删除班级（需先清空学生）</button>
        </div>
      </section>

      <section class="students-section">
        <div class="students-header"><h3>👥 学生名单（{{ students.length }} 人）</h3><div class="students-tools"><input v-model="searchKeyword" placeholder="搜索学号或姓名..." class="search-input" /></div></div>
        <div v-if="filteredStudents.length === 0" class="empty-students"><p v-if="searchKeyword">没有匹配的学生</p><p v-else>暂无学生加入，分享邀请码让学生加入吧</p></div>
        <div v-else class="students-table-wrap">
          <table class="students-table">
            <thead><tr><th style="width:60px">序号</th><th>学号</th><th>姓名</th><th>加入时间</th><th style="width:80px">操作</th></tr></thead>
            <tbody>
              <tr v-for="(s, idx) in filteredStudents" :key="s.studentId"><td class="td-center">{{ idx + 1 }}</td><td class="td-mono">{{ s.studentId }}</td><td>{{ s.studentName }}</td><td class="td-date">{{ s.joinedAt ? formatDate(s.joinedAt) : '-' }}</td><td class="td-center"><button class="btn-remove" @click="confirmRemove(s)" title="移除">🗑️</button></td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <InviteDialog :visible="showInvite" :invite-code="classData.inviteCode" @close="showInvite = false" />
      <StudentImport :visible="showImport" @close="showImport = false" @imported="onImported" />
    </template>

    <div v-if="showDeleteConfirm" class="dialog-overlay" @click.self="showDeleteConfirm = false">
      <div class="dialog-content dialog-sm"><div class="dialog-header"><h3>⚠️ 确认删除</h3><button class="dialog-close" @click="showDeleteConfirm = false">×</button></div><div class="dialog-body"><p class="confirm-text">确定要删除班级 <strong>{{ classData.name }}</strong> 吗？</p><p class="confirm-warn">删除后无法恢复，请谨慎操作。</p></div><div class="dialog-footer"><button class="btn-cancel" @click="showDeleteConfirm = false">取消</button><button class="btn-danger-solid" @click="doDelete" :disabled="deleting">{{ deleting ? '删除中...' : '确认删除' }}</button></div></div>
    </div>

    <div v-if="showRemoveConfirm" class="dialog-overlay" @click.self="showRemoveConfirm = false">
      <div class="dialog-content dialog-sm"><div class="dialog-header"><h3>⚠️ 移除学生</h3><button class="dialog-close" @click="showRemoveConfirm = false">×</button></div><div class="dialog-body"><p class="confirm-text">确定要将 <strong>{{ removeTarget?.studentName }}</strong>（{{ removeTarget?.studentId }}）从本班移除吗？</p><p class="confirm-warn">移除后该学生的提交记录仍保留，但不再属于本班级。</p></div><div class="dialog-footer"><button class="btn-cancel" @click="showRemoveConfirm = false">取消</button><button class="btn-danger-solid" @click="doRemove">确认移除</button></div></div>
    </div>
  </div>
</template>

<style scoped>
.class-manage-page { max-width: 1000px; margin: 0 auto; }
.empty-state { text-align: center; padding: 80px 20px; color: #909399; font-size: 1rem; }
.empty-icon { font-size: 3rem; margin-bottom: 12px; }

.breadcrumb { display: flex; align-items: center; gap: 8px; margin-bottom: 20px; font-size: 0.85rem; color: #909399; }
.bread-link { color: #409EFF; cursor: pointer; text-decoration: none; }
.bread-link:hover { text-decoration: underline; }
.bread-sep { color: #dcdfe6; }
.bread-current { color: #303133; font-weight: 500; }
.archived-badge { margin-left: 8px; padding: 2px 8px; border-radius: 10px; background: #f5f7fa; color: #909399; font-size: 0.75rem; }

.info-card { background: #fff; border: 1px solid #e4e7ed; border-radius: 12px; padding: 24px; margin-bottom: 24px; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
.info-header { margin-bottom: 20px; }
.info-title-row { display: flex; align-items: flex-start; gap: 16px; }
.class-avatar { width: 52px; height: 52px; border-radius: 12px; background: linear-gradient(135deg, #409EFF, #337ecc); display: flex; align-items: center; justify-content: center; font-size: 1.4rem; font-weight: 700; color: #fff; flex-shrink: 0; }
.class-main { flex: 1; }
.class-main h2 { margin: 0 0 6px 0; font-size: 1.3rem; color: #303133; }
.class-desc { margin: 0; font-size: 0.85rem; color: #606266; line-height: 1.5; }
.btn-icon { width: 36px; height: 36px; border-radius: 8px; background: transparent; border: 1px solid #e4e7ed; color: #909399; font-size: 1rem; cursor: pointer; transition: all 0.2s; flex-shrink: 0; }
.btn-icon:hover { background: #ecf5ff; border-color: #409EFF; color: #409EFF; }

.edit-form { flex: 1; display: flex; flex-direction: column; gap: 10px; }
.form-row { width: 100%; }
.inline-input { width: 100%; padding: 8px 12px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #303133; font-size: 1.05rem; outline: none; box-sizing: border-box; }
.inline-input:focus { border-color: #409EFF; box-shadow: 0 0 0 2px rgba(64,158,255,.1); }
.inline-textarea { width: 100%; padding: 8px 12px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #303133; font-size: 0.85rem; outline: none; resize: vertical; font-family: inherit; box-sizing: border-box; }
.inline-textarea:focus { border-color: #409EFF; box-shadow: 0 0 0 2px rgba(64,158,255,.1); }
.inline-autocomplete { width: 100%; }
.edit-actions { display: flex; gap: 8px; }
.btn-sm { padding: 6px 16px; border-radius: 6px; font-size: 0.8rem; cursor: pointer; border: none; transition: all 0.2s; }
.btn-save { background: #409EFF; color: #fff; }
.btn-save:hover { background: #337ecc; }
.btn-save:disabled { background: #a0cfff; cursor: not-allowed; }
.btn-cancel-sm { background: #fff; border: 1px solid #dcdfe6; color: #606266; }
.btn-cancel-sm:hover { background: #f5f7fa; }

.info-meta { display: flex; flex-wrap: wrap; gap: 24px; margin-bottom: 20px; padding: 14px 16px; background: #f5f7fa; border-radius: 8px; border: 1px solid #ebeef5; }
.meta-item { display: flex; flex-direction: column; gap: 4px; }
.meta-label { font-size: 0.75rem; color: #909399; }
.meta-value { font-size: 0.9rem; color: #303133; }
.code-value { font-family: Consolas, monospace; letter-spacing: 2px; font-size: 1rem; color: #409EFF; display: inline-flex; align-items: center; gap: 8px; }
.copy-btn { background: none; border: none; cursor: pointer; font-size: 0.9rem; padding: 2px; }
.copy-btn:hover { transform: scale(1.2); }
.status-tag { padding: 2px 10px; border-radius: 10px; font-size: 0.8rem; font-weight: 600; }
.status-tag.active { background: rgba(103,194,58,.12); color: #67C23A; }
.status-tag.archived { background: #f5f7fa; color: #909399; }

.info-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.btn-action { padding: 8px 16px; border-radius: 8px; border: 1px solid #dcdfe6; background: #fff; color: #606266; font-size: 0.85rem; cursor: pointer; transition: all 0.2s; }
.btn-action:hover { border-color: #409EFF; color: #409EFF; }
.btn-action-highlight { background: #ecf5ff; border-color: #409EFF; color: #409EFF; font-weight: 600; }
.btn-action-highlight:hover { background: #409EFF; color: #fff; }
.btn-action:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-action.btn-danger { border-color: #fde2e2; color: #F56C6C; }
.btn-action.btn-danger:hover { background: #fef0f0; border-color: #F56C6C; }

.students-section { background: #fff; border: 1px solid #e4e7ed; border-radius: 12px; padding: 24px; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
.students-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.students-header h3 { margin: 0; font-size: 1.05rem; color: #303133; }
.students-tools { display: flex; gap: 10px; }
.search-input { width: 220px; padding: 8px 12px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #303133; font-size: 0.85rem; outline: none; }
.search-input:focus { border-color: #409EFF; }
.search-input::placeholder { color: #c0c4cc; }
.empty-students { text-align: center; padding: 40px 20px; color: #909399; font-size: 0.9rem; }

.students-table-wrap { overflow-x: auto; }
.students-table { width: 100%; border-collapse: collapse; font-size: 0.85rem; }
.students-table th { text-align: left; padding: 10px 12px; background: #f5f7fa; color: #909399; font-weight: 600; border-bottom: 1px solid #ebeef5; white-space: nowrap; }
.students-table td { padding: 10px 12px; border-bottom: 1px solid #ebeef5; color: #303133; }
.students-table tbody tr:hover { background: #f5f7fa; }
.td-center { text-align: center; }
.td-mono { font-family: Consolas, monospace; color: #606266; }
.td-date { color: #909399; font-size: 0.8rem; }
.btn-remove { background: none; border: none; cursor: pointer; font-size: 0.9rem; padding: 4px 8px; border-radius: 4px; transition: all 0.15s; }
.btn-remove:hover { background: #fef0f0; }

.dialog-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.dialog-content { background: #fff; border: 1px solid #e4e7ed; border-radius: 14px; width: 90%; box-shadow: 0 8px 32px rgba(0,0,0,.1); }
.dialog-sm { max-width: 420px; }
.dialog-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px 0; }
.dialog-header h3 { margin: 0; font-size: 1.05rem; color: #303133; }
.dialog-close { background: none; border: none; color: #c0c4cc; font-size: 1.4rem; cursor: pointer; padding: 0; line-height: 1; }
.dialog-close:hover { color: #606266; }
.dialog-body { padding: 20px 24px; }
.confirm-text { font-size: 0.95rem; color: #303133; margin: 0 0 8px 0; }
.confirm-text strong { color: #303133; }
.confirm-warn { font-size: 0.82rem; color: #909399; margin: 0; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 12px; padding: 0 24px 20px; }
.btn-cancel { padding: 9px 20px; background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; color: #606266; cursor: pointer; font-size: 0.85rem; transition: all 0.2s; }
.btn-cancel:hover { background: #f5f7fa; }
.btn-danger-solid { padding: 9px 20px; background: #F56C6C; border: none; border-radius: 8px; color: #fff; cursor: pointer; font-size: 0.85rem; font-weight: 600; transition: all 0.2s; }
.btn-danger-solid:hover { background: #e06060; }
.btn-danger-solid:disabled { background: #fab6b6; cursor: not-allowed; }
</style>

<style>
.course-popper { background: #fff !important; border: 1px solid #e4e7ed !important; box-shadow: 0 4px 12px rgba(0,0,0,.08) !important; }
.course-popper .el-autocomplete-suggestion__wrap { background: #fff; }
.course-popper li { color: #303133; padding: 8px 16px; }
.course-popper li:hover, .course-popper li.highlighted { background: #ecf5ff !important; color: #409EFF !important; }
</style>
