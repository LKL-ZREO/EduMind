<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import * as XLSX from 'xlsx'

const route = useRoute()
const classId = Number(route.params.id)

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
  imported: []
}>()

// ==================== 状态 ====================
interface StudentRow {
  studentId: string
  studentName: string
  valid: boolean
  error?: string
}

const students = ref<StudentRow[]>([])
const fileName = ref('')
const importing = ref(false)
const dragOver = ref(false)
const showConfirm = ref(false)

const validCount = computed(() => students.value.filter(s => s.valid).length)
const invalidCount = computed(() => students.value.filter(s => !s.valid).length)


// ==================== 文件解析 ====================
function handleFile(file: File) {
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!['xlsx', 'xls', 'csv'].includes(ext || '')) {
    ElMessage.warning('仅支持 .xlsx / .xls / .csv 格式文件')
    return
  }

  fileName.value = file.name
  students.value = []

  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const data = new Uint8Array(e.target!.result as ArrayBuffer)
      const workbook = XLSX.read(data, { type: 'array' })
      const sheet = workbook.Sheets[workbook.SheetNames[0]]
      const rows: any[][] = XLSX.utils.sheet_to_json(sheet, { header: 1 })

      if (rows.length < 2) {
        ElMessage.warning('文件为空或格式不正确，请确保第一行为表头（学号, 姓名）')
        return
      }

      // 智能识别列：找"学号"和"姓名"列
      const header = rows[0].map((h: any) => String(h || '').trim())
      const idColIdx = header.findIndex((h: string) =>
        ['学号', 'studentid', 'student_id', 'student id', 'id', '编号'].includes(h.toLowerCase())
      )
      const nameColIdx = header.findIndex((h: string) =>
        ['姓名', 'name', 'studentname', 'student_name', 'student name', '名字'].includes(h.toLowerCase())
      )

      if (idColIdx === -1) {
        ElMessage.warning('未找到"学号"列，请确保表头包含「学号」')
        return
      }
      if (nameColIdx === -1) {
        ElMessage.warning('未找到"姓名"列，请确保表头包含「姓名」')
        return
      }

      const seen = new Set<string>()
      const result: StudentRow[] = []

      for (let i = 1; i < rows.length; i++) {
        const row = rows[i]
        if (!row || row.every((cell: any) => !String(cell).trim())) continue

        const sid = String(row[idColIdx] || '').trim()
        const sname = String(row[nameColIdx] || '').trim()

        const errors: string[] = []
        if (!sid) errors.push('学号为空')
        if (!sname) errors.push('姓名为空')
        if (sid && seen.has(sid)) errors.push('学号重复')
        if (sid) seen.add(sid)

        result.push({
          studentId: sid || '',
          studentName: sname || '',
          valid: errors.length === 0,
          error: errors.join('；'),
        })
      }

      students.value = result
      if (result.length === 0) {
        ElMessage.warning('未解析到任何数据行')
      }
    } catch (err) {
      ElMessage.error('文件解析失败，请检查文件格式')
      console.error(err)
    }
  }

  reader.readAsArrayBuffer(file)
}

function onFileInput(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files?.length) return
  handleFile(input.files[0])
  input.value = ''
}

function onDrop(e: DragEvent) {
  e.preventDefault()
  dragOver.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) handleFile(file)
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  dragOver.value = true
}

function onDragLeave() {
  dragOver.value = false
}

function clearFile() {
  fileName.value = ''
  students.value = []
}

// ==================== 确认导入 ====================
function confirmImport() {
  showConfirm.value = true
}

async function doImport() {
  importing.value = true
  try {
    const payload = {
      students: students.value.filter(s => s.valid).map(s => ({
        studentId: s.studentId,
        studentName: s.studentName,
      })),
    }
    const res = await request.post(`/teacher/classes/${classId}/students/import`, payload)
    if (res.data.code === 200) {
      const r = res.data.data
      ElMessage.success(`成功导入 ${r.imported} 名学生（${r.skipped} 条跳过）`)
      showConfirm.value = false
      emit('imported')
      clearFile()
    } else {
      ElMessage.error(res.data.message || '导入失败')
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || '导入失败')
  } finally {
    importing.value = false
  }
}

// ==================== 下载模板 ====================
function downloadTemplate() {
  const wb = XLSX.utils.book_new()
  const data = [
    ['学号', '姓名'],
    ['2021001001', '张三'],
    ['2021001002', '李四'],
    ['2021001003', '王五'],
  ]
  const ws = XLSX.utils.aoa_to_sheet(data)
  XLSX.utils.book_append_sheet(wb, ws, '学生名单')
  XLSX.writeFile(wb, '学生导入模板.xlsx')
}
</script>

<template>
  <div
    v-if="visible"
    class="import-overlay"
    @click.self="!showConfirm && emit('close')"
  >
    <div class="import-dialog">
      <div class="import-header">
        <h3>📥 批量导入学生</h3>
        <p class="import-sub">上传 Excel 文件，一键导入学生名单</p>
        <button class="import-close" @click="emit('close')">×</button>
      </div>

      <div class="import-body">
        <!-- 上传区域 -->
        <div v-if="!students.length" class="upload-area">
          <div
            class="drop-zone"
            :class="{ 'drag-over': dragOver }"
            @drop="onDrop"
            @dragover="onDragOver"
            @dragleave="onDragLeave"
          >
            <div class="drop-icon">📂</div>
            <p class="drop-text">拖拽 Excel 文件到此处</p>
            <p class="drop-or">或</p>
            <label class="btn-browse">
              选择文件
              <input
                type="file"
                accept=".xlsx,.xls,.csv"
                hidden
                @change="onFileInput"
              />
            </label>
            <p class="drop-hint">支持 .xlsx / .xls / .csv 格式</p>
          </div>

          <div class="template-hint">
            <p>💡 确保 Excel 文件第一行为表头，包含「学号」和「姓名」列</p>
            <button class="btn-template" @click="downloadTemplate">
              📎 下载导入模板
            </button>
          </div>
        </div>

        <!-- 预览表格 -->
        <div v-else class="preview-area">
          <div class="preview-header">
            <div class="preview-info">
              <span class="file-icon">📄</span>
              <span class="file-name">{{ fileName }}</span>
              <span class="badge-count">
                共 {{ students.length }} 行，
                <span class="valid-text">✅ {{ validCount }}</span>
                <span v-if="invalidCount"> / <span class="invalid-text">⚠️ {{ invalidCount }}</span></span>
              </span>
            </div>
            <div class="preview-actions">
              <button class="btn-retry" @click="clearFile">重新选择</button>
            </div>
          </div>

          <div class="table-wrap">
            <table class="preview-table">
              <thead>
                <tr>
                  <th style="width:50px">#</th>
                  <th>学号</th>
                  <th>姓名</th>
                  <th style="width:60px">状态</th>
                  <th>备注</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(s, idx) in students.slice(0, 50)"
                  :key="idx"
                  :class="{ 'row-invalid': !s.valid }"
                >
                  <td class="td-center">{{ idx + 1 }}</td>
                  <td>
                    <span v-if="s.studentId" class="td-mono">{{ s.studentId }}</span>
                    <span v-else class="td-empty">—</span>
                  </td>
                  <td>
                    <span v-if="s.studentName">{{ s.studentName }}</span>
                    <span v-else class="td-empty">—</span>
                  </td>
                  <td class="td-center">
                    <span v-if="s.valid" class="status-ok">✅</span>
                    <span v-else class="status-err">⚠️</span>
                  </td>
                  <td class="td-note">
                    <span v-if="s.error" class="error-text">{{ s.error }}</span>
                    <span v-else class="no-issue">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-if="students.length > 50" class="truncated-hint">
              仅显示前 50 行，共 {{ students.length }} 行
            </p>
          </div>

          <div class="preview-footer">
            <button class="btn-cancel" @click="clearFile">取消</button>
            <button
              class="btn-primary"
              :disabled="validCount === 0"
              @click="confirmImport"
            >
              导入 {{ validCount }} 名学生
            </button>
          </div>
        </div>
      </div>

      <!-- 二次确认 -->
      <div v-if="showConfirm" class="confirm-overlay" @click.self="showConfirm = false">
        <div class="confirm-dialog">
          <h4>确认导入</h4>
          <p>将导入 <strong>{{ validCount }}</strong> 名学生到当前班级</p>
          <p v-if="invalidCount" class="warn-text">⚠️ {{ invalidCount }} 行数据格式异常将被跳过</p>
          <div class="confirm-btns">
            <button class="btn-cancel" @click="showConfirm = false">再检查一下</button>
            <button class="btn-primary" @click="doImport" :disabled="importing">
              {{ importing ? '导入中...' : '确认导入' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.import-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, .4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.import-dialog {
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 16px;
  width: 90%;
  max-width: 680px;
  max-height: 85vh;
  box-shadow: 0 8px 32px rgba(0, 0, 0, .1);
  display: flex;
  flex-direction: column;
}

.import-header {
  text-align: center;
  padding: 28px 28px 0;
  position: relative;
  flex-shrink: 0;
}

.import-header h3 {
  margin: 0 0 4px 0;
  font-size: 1.15rem;
  color: #303133;
}

.import-sub {
  margin: 0;
  font-size: 0.82rem;
  color: #909399;
}

.import-close {
  position: absolute;
  top: 16px;
  right: 16px;
  background: none;
  border: none;
  color: #909399;
  font-size: 1.4rem;
  cursor: pointer;
  line-height: 1;
}

.import-close:hover {
  color: #303133;
}

/* ===== Body ===== */
.import-body {
  padding: 20px 28px 24px;
  overflow-y: auto;
  flex: 1;
}

/* ===== 上传区域 ===== */
.upload-area {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.drop-zone {
  border: 2px dashed #e4e7ed;
  border-radius: 12px;
  padding: 40px 20px;
  text-align: center;
  transition: all 0.2s;
  cursor: pointer;
}

.drop-zone.drag-over {
  border-color: #409EFF;
  background: rgba(255, 125, 0, 0.05);
}

.drop-icon {
  font-size: 2.8rem;
  margin-bottom: 12px;
}

.drop-text {
  margin: 0 0 8px 0;
  color: #303133;
  font-size: 1rem;
}

.drop-or {
  margin: 0 0 12px 0;
  color: #909399;
  font-size: 0.85rem;
}

.btn-browse {
  display: inline-block;
  padding: 9px 24px;
  background: #409EFF;
  color: #fff;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 600;
  transition: background 0.2s;
}

.btn-browse:hover {
  background: #337ecc;
}

.drop-hint {
  margin: 14px 0 0 0;
  font-size: 0.75rem;
  color: #909399;
}

.template-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 10px;
  font-size: 0.8rem;
  color: #606266;
}

.template-hint p {
  margin: 0;
}

.btn-template {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  color: #606266;
  font-size: 0.78rem;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}

.btn-template:hover {
  border-color: #409EFF;
  color: #409EFF;
}

/* ===== 预览区域 ===== */
.preview-area {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.preview-info {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.85rem;
  color: #303133;
}

.file-icon {
  font-size: 1.2rem;
}

.file-name {
  color: #303133;
}

.badge-count {
  color: #909399;
  font-size: 0.8rem;
}

.valid-text {
  color: #48bb78;
}

.invalid-text {
  color: #ed8936;
}

.preview-actions {
  display: flex;
  gap: 6px;
}

.btn-retry {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  color: #606266;
  font-size: 0.78rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-retry:hover {
  border-color: #409EFF;
  color: #409EFF;
}

/* 预览表格 */
.table-wrap {
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.82rem;
}

.preview-table th {
  position: sticky;
  top: 0;
  text-align: left;
  padding: 10px 12px;
  background: #ffffff;
  color: #606266;
  font-weight: 600;
  border-bottom: 1px solid #ebeef5;
  z-index: 1;
}

.preview-table td {
  padding: 8px 12px;
  border-bottom: 1px solid #ffffff;
  color: #303133;
}

.preview-table tbody tr:hover {
  background: #252525;
}

.preview-table tbody tr.row-invalid {
  background: rgba(237, 137, 54, 0.06);
}

.td-center {
  text-align: center;
}

.td-mono {
  font-family: 'Consolas', 'Courier New', monospace;
  color: #606266;
}

.td-empty {
  color: #dcdfe6;
}

.td-note {
  font-size: 0.75rem;
}

.status-ok, .status-err {
  font-size: 0.85rem;
}

.error-text {
  color: #ed8936;
}

.no-issue {
  color: #dcdfe6;
}

.truncated-hint {
  text-align: center;
  padding: 10px;
  margin: 0;
  font-size: 0.75rem;
  color: #909399;
  background: #ffffff;
}

.preview-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* ===== 二次确认 ===== */
.confirm-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
}

.confirm-dialog {
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  padding: 24px;
  max-width: 360px;
  text-align: center;
}

.confirm-dialog h4 {
  margin: 0 0 12px 0;
  font-size: 1.05rem;
  color: #303133;
}

.confirm-dialog p {
  margin: 0 0 8px 0;
  font-size: 0.85rem;
  color: #606266;
}

.confirm-dialog strong {
  color: #409EFF;
}

.warn-text {
  color: #ed8936 !important;
}

.confirm-btns {
  display: flex;
  gap: 10px;
  margin-top: 18px;
  justify-content: center;
}

/* ===== 通用按钮 ===== */
.btn-cancel {
  padding: 8px 18px;
  background: transparent;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  color: #606266;
  font-size: 0.83rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-cancel:hover {
  background: #ebeef5;
  color: #303133;
}

.btn-primary {
  padding: 8px 20px;
  background: #409EFF;
  border: none;
  border-radius: 8px;
  color: #303133;
  font-size: 0.83rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary:hover {
  background: #337ecc;
}

.btn-primary:disabled {
  background: #dcdfe6;
  cursor: not-allowed;
}
</style>
