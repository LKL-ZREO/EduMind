<template>
  <div class="kb-container">
    <!-- 工具栏 -->
    <div class="kb-toolbar">
      <div class="toolbar-left">
        <el-icon class="toolbar-icon" :size="20"><FolderOpened /></el-icon>
        <span class="toolbar-title">知识库</span>
      </div>
      <div class="toolbar-center">
        <el-input v-model="searchQuery" placeholder="搜索目录..." prefix-icon="Search" clearable size="small" class="search-input" @input="filterTree" />
      </div>
      <div class="toolbar-right">
        <el-button size="small" @click="showJoinDialog = true"><el-icon><Link /></el-icon>加入共享</el-button>
        <el-button size="small" @click="handleAddRootFolder"><el-icon><FolderAdd /></el-icon>新建文件夹</el-button>
        <el-button size="small" @click="handleUploadFile"><el-icon><Upload /></el-icon>上传文件</el-button>
        <el-button type="primary" size="small" @click="showCreateKbDialog = true"><el-icon><FolderAdd /></el-icon>新建知识库</el-button>
      </div>
    </div>

    <div class="kb-body">
      <aside class="kb-sidebar">
        <div v-if="loading" class="tree-loading"><el-icon class="is-loading" :size="20"><Loading /></el-icon><span>加载中...</span></div>
        <template v-else>
          <!-- 我的知识库 -->
          <div class="section-header"><el-icon :size="14"><FolderOpened /></el-icon><span>我的知识库</span></div>
          <el-tree ref="treeRef" :data="myTreeData" :props="treeProps" node-key="id"
            :filter-node-method="filterNode" draggable :allow-drop="allowDrop"
            :allow-drag="() => true" :highlight-current="true" :expand-on-click-node="false"
            default-expand-all @node-click="handleNodeClick" @node-drop="handleNodeDrop"
            @node-contextmenu="handleContextMenu"
            :drop-indicator="true" class="my-tree">
            <template #default="{ node, data }">
              <div class="custom-tree-node" @dblclick="handleDoubleClick(data)">
                <template v-if="renamingNode?.id === data.id">
                  <el-input v-model="renameValue" size="small" @blur="confirmRename(data)" @keydown.enter="confirmRename(data)" @keydown.escape="cancelRename" @click.stop autofocus />
                </template>
                <template v-else>
                  <span class="node-icon"><el-icon v-if="data.type === 'folder'" :size="18" :class="{ 'is-expanded': node.expanded }"><FolderOpened /></el-icon><el-icon v-else :size="16" color="#409eff"><Document /></el-icon></span>
                  <span class="node-label" :title="data.label">{{ data.label }}</span>
                </template>
              </div>
            </template>
          </el-tree>

          <!-- 我创建的 -->
          <div v-if="myKbs.length > 0" class="section-header shared-header"><el-icon :size="14"><Star /></el-icon><span>我创建的</span></div>
          <template v-for="kb in myKbs" :key="kb.id">
            <div class="kb-list-item" @click="toggleKbExpand(kb.id)" @contextmenu.prevent="openKbSettings(kb)">
              <el-icon :size="14" color="#999" class="kb-expand-icon" :class="{ expanded: expandedKbs.has(kb.id) }"><ArrowRight /></el-icon>
              <el-icon :size="16" color="#e6a23c"><FolderOpened /></el-icon>
              <span class="kb-list-name">{{ kb.name }}</span>
              <el-button link size="small" class="kb-settings-btn" @click.stop="openKbSettings(kb)"><el-icon><Setting /></el-icon></el-button>
            </div>
            <div v-if="expandedKbs.has(kb.id)" class="kb-sub-tree">
              <div v-if="kbLoading.get(kb.id)" class="tree-loading" style="padding:12px 0"><el-icon class="is-loading" :size="14"><Loading /></el-icon></div>
              <div v-else-if="kbTreeCache.get(kb.id)?.length === 0" class="tree-empty" style="padding:12px 8px;font-size:12px"><p>暂无文件</p></div>
              <el-tree v-else :data="kbTreeCache.get(kb.id)!" :props="treeProps" node-key="id"
                :draggable="false" :highlight-current="true" :expand-on-click-node="false" default-expand-all
                @node-click="handleNodeClick" @node-contextmenu="handleContextMenu" class="kb-inline-tree">
                <template #default="{ node, data }">
                  <div class="custom-tree-node">
                    <span class="node-icon"><el-icon v-if="data.type === 'folder'" :size="16" :class="{ 'is-expanded': node.expanded }"><FolderOpened /></el-icon><el-icon v-else :size="14" color="#409eff"><Document /></el-icon></span>
                    <span class="node-label" :title="data.label">{{ data.label }}</span>
                  </div>
                </template>
              </el-tree>
            </div>
          </template>

          <!-- 我加入的 -->
          <div v-if="joinedKbs.length > 0" class="section-header shared-header"><el-icon :size="14"><Share /></el-icon><span>我加入的</span></div>
          <template v-for="kb in joinedKbs" :key="kb.id">
            <div class="kb-list-item" @click="toggleKbExpand(kb.id)" @contextmenu.prevent="null">
              <el-icon :size="14" color="#999" class="kb-expand-icon" :class="{ expanded: expandedKbs.has(kb.id) }"><ArrowRight /></el-icon>
              <el-icon :size="16" color="#67c23a"><FolderOpened /></el-icon>
              <span class="kb-list-name">{{ kb.name }}</span>
            </div>
            <div v-if="expandedKbs.has(kb.id)" class="kb-sub-tree">
              <div v-if="kbLoading.get(kb.id)" class="tree-loading" style="padding:12px 0"><el-icon class="is-loading" :size="14"><Loading /></el-icon></div>
              <div v-else-if="kbTreeCache.get(kb.id)?.length === 0" class="tree-empty" style="padding:12px 8px;font-size:12px"><p>暂无文件</p></div>
              <el-tree v-else :data="kbTreeCache.get(kb.id)!" :props="treeProps" node-key="id"
                :draggable="false" :highlight-current="true" :expand-on-click-node="false" default-expand-all
                @node-click="handleNodeClick" @node-contextmenu="handleContextMenu" class="kb-inline-tree">
                <template #default="{ node, data }">
                  <div class="custom-tree-node">
                    <span class="node-icon"><el-icon v-if="data.type === 'folder'" :size="16" :class="{ 'is-expanded': node.expanded }"><FolderOpened /></el-icon><el-icon v-else :size="14" color="#409eff"><Document /></el-icon></span>
                    <span class="node-label" :title="data.label">{{ data.label }}</span>
                  </div>
                </template>
              </el-tree>
            </div>
          </template>

          <div v-if="!loading && myTreeData.length === 0 && myKbs.length === 0 && joinedKbs.length === 0" class="tree-empty"><p>暂无内容</p></div>
        </template>
      </aside>

      <main class="kb-content">
        <div v-if="!selectedNode" class="content-empty">
          <el-icon :size="64" color="#dcdfe6"><FolderOpened /></el-icon>
          <p>从左侧目录选择一个文件查看内容</p>
        </div>
        <div v-else-if="selectedNode.type === 'folder'" class="content-folder">
          <div class="folder-header"><el-icon :size="28" color="#e6a23c"><FolderOpened /></el-icon><h2>{{ selectedNode.label }}</h2></div>
          <div class="folder-stats">
            <div class="stat-card"><span class="stat-num">{{ countItems(selectedNode, 'folder') }}</span><span class="stat-label">子文件夹</span></div>
            <div class="stat-card"><span class="stat-num">{{ countItems(selectedNode, 'file') }}</span><span class="stat-label">文档</span></div>
          </div>
        </div>
        <div v-else class="content-file">
          <div class="file-header">
            <div class="file-header-left"><el-icon :size="24" color="#409eff"><Document /></el-icon><h2>{{ selectedNode.label }}</h2></div>
            <el-tag size="small" type="info" effect="plain">预览</el-tag>
          </div>
          <el-divider />
          <div class="file-body">
            <div v-if="selectedNode.content" class="markdown-preview" v-html="renderContent(selectedNode.content)" />
            <div v-else class="content-empty"><el-icon :size="48" color="#dcdfe6"><Tickets /></el-icon><p>暂无内容</p></div>
          </div>
        </div>
      </main>
    </div>

    <!-- 右键菜单 -->
    <div v-show="contextMenu.visible" class="context-menu" :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }" @click.stop>
      <div v-if="contextMenu.node?.type === 'folder'" class="context-menu-item" @click="contextUploadFile"><el-icon><Upload /></el-icon>上传文件到此</div>
      <div class="context-menu-item" @click="contextAddFolder"><el-icon><FolderAdd /></el-icon>新建子文件夹</div>
      <div class="context-menu-item" @click="contextRename"><el-icon><Edit /></el-icon>重命名</div>
      <el-divider class="context-divider" />
      <div class="context-menu-item danger" @click="contextDelete"><el-icon><Delete /></el-icon>删除</div>
    </div>

    <!-- 新建文件夹 -->
    <el-dialog v-model="addDialog.visible" title="新建文件夹" width="360px" :close-on-click-modal="false" append-to-body>
      <el-input v-model="addDialog.name" placeholder="请输入文件夹名称" maxlength="50" show-word-limit @keydown.enter="confirmAddFolder" />
      <template #footer><el-button @click="addDialog.visible = false">取消</el-button><el-button type="primary" :loading="addDialog.loading" @click="confirmAddFolder">确定</el-button></template>
    </el-dialog>

    <!-- 新建知识库 -->
    <el-dialog v-model="showCreateKbDialog" title="新建共享知识库" width="400px" :close-on-click-modal="false" append-to-body>
      <el-form label-position="top">
        <el-form-item label="知识库名称"><el-input v-model="createKbForm.name" placeholder="例如：高一数学备课组" maxlength="100" /></el-form-item>
        <el-form-item label="简介（选填）"><el-input v-model="createKbForm.description" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="showCreateKbDialog = false">取消</el-button><el-button type="primary" :loading="createKbLoading" @click="confirmCreateKb">创建</el-button></template>
    </el-dialog>

    <!-- 知识库设置 -->
    <el-dialog v-model="showKbSettings" :title="'设置 - ' + (settingsKb?.name || '')" width="500px" :close-on-click-modal="false" append-to-body>
      <el-tabs>
        <el-tab-pane label="基本信息">
          <el-form label-position="top">
            <el-form-item label="名称"><el-input v-model="settingsForm.name" maxlength="100" /></el-form-item>
            <el-form-item label="简介"><el-input v-model="settingsForm.description" type="textarea" :rows="3" maxlength="500" /></el-form-item>
          </el-form>
          <el-button type="primary" size="small" :loading="settingsSaveLoading" @click="saveKbSettings">保存</el-button>
          <el-divider />
          <el-button type="danger" size="small" plain @click="confirmDeleteKb">解散知识库</el-button>
        </el-tab-pane>
        <el-tab-pane label="邀请">
          <p style="color:#888;font-size:13px;margin-bottom:8px;">分享下面的链接，其他人可以加入此知识库</p>
          <el-input v-model="inviteLink" readonly :model-value="inviteLink">
            <template #append><el-button @click="copyInviteLink">复制</el-button></template>
          </el-input>
          <el-button size="small" style="margin-top:8px" @click="regenerateInvite">重新生成链接</el-button>
        </el-tab-pane>
        <el-tab-pane label="成员">
          <div v-if="members.length === 0" style="color:#666;padding:12px">加载中...</div>
          <div v-for="m in members" :key="m.userId || m.user_id" class="member-row">
            <div class="member-info">
              <span class="member-name">{{ m.username }}</span>
              <el-tag size="small" :type="m.role === 'owner' ? 'warning' : m.role === 'admin' ? 'primary' : 'info'">{{ {owner:'创建者',admin:'管理员',member:'成员'}[m.role] || m.role }}</el-tag>
            </div>
            <el-button v-if="m.role !== 'owner'" link type="danger" size="small" @click="removeMember(m.userId || m.user_id)">移除</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 加入 -->
    <el-dialog v-model="showJoinDialog" title="加入共享知识库" width="400px" :close-on-click-modal="false" append-to-body>
      <p style="color:#888;font-size:13px;margin-bottom:12px;">输入邀请链接或邀请码</p>
      <el-input v-model="joinToken" placeholder="粘贴邀请链接或输入token" @keydown.enter="confirmJoin" />
      <template #footer><el-button @click="showJoinDialog = false">取消</el-button><el-button type="primary" :loading="joinLoading" @click="confirmJoin">加入</el-button></template>
    </el-dialog>

    <!-- 上传文件 -->
    <el-dialog v-model="uploadVisible" title="上传文件" width="480px" :close-on-click-modal="false" append-to-body @closed="uploadFileList = []">
      <div class="upload-target">
        <span class="upload-target-label">上传到：</span>
        <el-select v-model="uploadKbId" size="small" style="width:160px" placeholder="我的知识库">
          <el-option :value="null" label="我的知识库" />
          <el-option v-for="kb in [...myKbs, ...joinedKbs]" :key="kb.id" :value="kb.id" :label="kb.name" />
        </el-select>
      </div>
      <el-upload ref="uploadRef" v-model:file-list="uploadFileList" :auto-upload="false" drag multiple accept=".txt,.md,.pdf,.doc,.docx" :on-change="(_, files) => uploadFileList = files" class="kb-upload">
        <el-icon class="el-icon--upload" :size="40"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
        <template #tip><div class="el-upload__tip">支持 .txt .md .pdf .doc .docx</div></template>
      </el-upload>
      <template #footer><el-button @click="uploadVisible = false">取消</el-button><el-button type="primary" :loading="uploadLoading" :disabled="uploadFileList.length === 0" @click="submitUpload">上传 {{ uploadFileList.length ? `${uploadFileList.length} 个文件` : '' }}</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Document, FolderAdd, Upload, UploadFilled, Edit, Delete, Tickets, Loading, Share, Link, Setting, Star, ArrowRight } from '@element-plus/icons-vue'
import type { ElTree, UploadUserFile } from 'element-plus'
import type { DropType } from 'element-plus/es/components/tree/src/tree.type'

/* ===== Types ===== */
interface FlatNode { id: number; userId: number; parentId: number | null; label: string; nodeType: 'folder' | 'file'; docId: string | null; sortOrder: number; createdAt: string; updatedAt: string; kbId?: number }
interface TreeNode { id: number; label: string; type: 'folder' | 'file'; docId?: string; children?: TreeNode[]; content?: string; kbId?: number }
interface SharedKb { id: number; name: string; description: string; ownerId: number; inviteToken?: string; createdAt: string }

const API_BASE = 'http://localhost:8080/api'
function token() { return localStorage.getItem('token') || '' }
function auth(): Record<string, string> { const t = token(); return t ? { Authorization: `Bearer ${t}` } : {} }

/* ===== State ===== */
const treeRef = ref<InstanceType<typeof ElTree>>()
const searchQuery = ref('')
const myTreeData = ref<TreeNode[]>([])
const selectedNode = ref<TreeNode | null>(null)
const loading = ref(true)
const expandedKbs = ref(new Set<number>())
const kbTreeCache = ref(new Map<number, TreeNode[]>())
const kbLoading = ref(new Map<number, boolean>())
const myKbs = ref<SharedKb[]>([])
const joinedKbs = ref<SharedKb[]>([])
const treeProps = { children: 'children', label: 'label' }
/* ===== Shared KB API ===== */
async function fetchMyKbs() {
  const res = await fetch(`${API_BASE}/shared-kb/my`, { headers: auth() })
  if (res.ok) myKbs.value = await res.json()
}
async function fetchJoinedKbs() {
  const res = await fetch(`${API_BASE}/shared-kb/joined`, { headers: auth() })
  if (res.ok) joinedKbs.value = await res.json()
}

async function fetchTree() {
  const res = await fetch(`${API_BASE}/documents/directory/tree`, { headers: auth() })
  if (!res.ok) throw new Error('获取目录树失败')
  myTreeData.value = buildTree(await res.json())
}

function buildTree(flat: FlatNode[]): TreeNode[] {
  const map = new Map<number, TreeNode>()
  for (const n of flat) map.set(n.id, { id: n.id, label: n.label, type: n.nodeType, docId: n.docId ?? undefined, children: [], kbId: n.kbId })
  const roots: TreeNode[] = []
  for (const n of flat) {
    const node = map.get(n.id)!
    if (n.parentId == null) roots.push(node)
    else { const p = map.get(n.parentId); if (p) p.children!.push(node) }
  }
  return roots
}

async function refreshKbTree(kbId: number) {
  if (!expandedKbs.value.has(kbId)) return
  try { const res = await fetch(`${API_BASE}/documents/directory/tree?kbId=${kbId}`, { headers: auth() }); if (res.ok) kbTreeCache.value.set(kbId, buildTree(await res.json())) } catch {}
}

async function toggleKbExpand(kbId: number) {
  if (expandedKbs.value.has(kbId)) {
    expandedKbs.value.delete(kbId)
  } else {
    expandedKbs.value.add(kbId)
    if (!kbTreeCache.value.has(kbId)) {
      kbLoading.value.set(kbId, true)
      try {
        const res = await fetch(`${API_BASE}/documents/directory/tree?kbId=${kbId}`, { headers: auth() })
        if (res.ok) kbTreeCache.value.set(kbId, buildTree(await res.json()))
        else kbTreeCache.value.set(kbId, [])
      } catch { kbTreeCache.value.set(kbId, []) }
      finally { kbLoading.value.set(kbId, false) }
    }
  }
}

/* ===== Init ===== */
onMounted(async () => {
  try {
    await Promise.all([fetchMyKbs(), fetchJoinedKbs(), fetchTree()])
  } catch (e: any) { ElMessage.error(e.message || '加载失败')
  } finally { loading.value = false }
})

/* ===== Rename ===== */
const renamingNode = ref<TreeNode | null>(null)
const renameValue = ref('')
function handleDoubleClick(data: TreeNode) { if (data.type !== 'folder') return; startRename(data) }
function startRename(node: TreeNode) { renamingNode.value = node; renameValue.value = node.label; nextTick(() => { (document.querySelector('.custom-tree-node .el-input__inner') as HTMLInputElement)?.focus()?.select() }) }
async function confirmRename(data: TreeNode) { const name = renameValue.value.trim(); renamingNode.value = null; if (!name || name === data.label) return; try { await fetch(`${API_BASE}/documents/directory/${data.id}/rename`, { method: 'PUT', headers: { 'Content-Type': 'application/json', ...auth() }, body: JSON.stringify({ label: name }) }); data.label = name } catch (e: any) { ElMessage.error(e.message); await fetchTree() } }
function cancelRename() { renamingNode.value = null }

/* ===== Context Menu ===== */
const contextMenu = reactive<{ visible: boolean; x: number; y: number; node: TreeNode | null }>({ visible: false, x: 0, y: 0, node: null })
function handleContextMenu(event: MouseEvent, data: TreeNode) {
  event.preventDefault(); Object.assign(contextMenu, { visible: true, x: event.clientX, y: event.clientY, node: data })
}
function hideContextMenu() { contextMenu.visible = false; contextMenu.node = null }
function onDocumentClick() { if (contextMenu.visible) hideContextMenu() }
onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))

function contextUploadFile() { const n = contextMenu.node; hideContextMenu(); if (!n || n.type !== 'folder') return; uploadParentNode.value = n; uploadKbId.value = n.kbId ?? null; uploadFileList.value = []; uploadVisible.value = true }
function contextAddFolder() { addDialog.parentNode = contextMenu.node; hideContextMenu(); addDialog.name = ''; addDialog.visible = true }
function contextRename() { const n = contextMenu.node; hideContextMenu(); if (n) startRename(n) }
async function contextDelete() {
  const n = contextMenu.node; hideContextMenu(); if (!n) return
  try { await ElMessageBox.confirm(n.type === 'folder' ? `确定删除「${n.label}」及其所有内容？` : `确定删除「${n.label}」？`, '确认', { type: 'warning' })
    await fetch(`${API_BASE}/documents/directory/${n.id}`, { method: 'DELETE', headers: auth() })
    if (selectedNode.value?.id === n.id) selectedNode.value = null; await fetchTree(); ElMessage.success('已删除')
  } catch (e: any) { if (e !== 'cancel') ElMessage.error(e.message || '删除失败') }
}

/* ===== Folder Dialog ===== */
const addDialog = reactive({ visible: false, name: '', loading: false, parentNode: null as TreeNode | null })
function handleAddRootFolder() { addDialog.parentNode = null; addDialog.name = ''; addDialog.visible = true }
async function confirmAddFolder() {
  const name = addDialog.name.trim(); if (!name) return; addDialog.loading = true
  try {
    const kbId = addDialog.parentNode?.kbId ?? null
    const body: any = { label: name, kbId }
    if (addDialog.parentNode) body.parentId = addDialog.parentNode.id
    await fetch(`${API_BASE}/documents/directory/folder`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth() }, body: JSON.stringify(body) })
    if (kbId != null) { kbTreeCache.value.delete(kbId); await refreshKbTree(kbId) }
    await fetchTree(); addDialog.visible = false
  } catch (e: any) { ElMessage.error(e.message) } finally { addDialog.loading = false }
}

/* ===== Create KB ===== */
const showCreateKbDialog = ref(false)
const createKbLoading = ref(false)
const createKbForm = reactive({ name: '', description: '' })
async function confirmCreateKb() {
  if (!createKbForm.name.trim()) { ElMessage.warning('请输入知识库名称'); return }
  createKbLoading.value = true
  try {
    await fetch(`${API_BASE}/shared-kb/create`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth() }, body: JSON.stringify(createKbForm) })
    showCreateKbDialog.value = false; createKbForm.name = ''; createKbForm.description = ''
    await fetchMyKbs(); ElMessage.success('创建成功')
  } catch (e: any) { ElMessage.error(e.message) } finally { createKbLoading.value = false }
}

/* ===== KB Settings ===== */
const showKbSettings = ref(false)
const settingsKb = ref<SharedKb | null>(null)
const settingsForm = reactive({ name: '', description: '' })
const settingsSaveLoading = ref(false)
const members = ref<any[]>([])
const inviteLink = ref('')

async function openKbSettings(kb: SharedKb) {
  settingsKb.value = kb; settingsForm.name = kb.name; settingsForm.description = kb.description; showKbSettings.value = true
  inviteLink.value = `${window.location.origin}/invite?token=${kb.inviteToken || ''}`
  try { const r = await fetch(`${API_BASE}/shared-kb/${kb.id}/members`, { headers: auth() }); if (r.ok) members.value = await r.json() } catch {}
}
async function saveKbSettings() {
  if (!settingsKb.value) return; settingsSaveLoading.value = true
  try { await fetch(`${API_BASE}/shared-kb/${settingsKb.value.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', ...auth() }, body: JSON.stringify({ name: settingsForm.name, description: settingsForm.description }) }); ElMessage.success('已保存'); await fetchMyKbs() }
  catch (e: any) { ElMessage.error(e.message) } finally { settingsSaveLoading.value = false }
}
async function regenerateInvite() {
  if (!settingsKb.value) return
  try { const r = await fetch(`${API_BASE}/shared-kb/${settingsKb.value.id}/invite`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...auth() }, body: '{}' }); const d = await r.json(); inviteLink.value = `${window.location.origin}/invite?token=${d.token}`; settingsKb.value!.inviteToken = d.token; ElMessage.success('已重新生成') }
  catch (e: any) { ElMessage.error(e.message) }
}
function copyInviteLink() { navigator.clipboard.writeText(inviteLink.value); ElMessage.success('已复制') }
async function removeMember(targetId: number) {
  if (!settingsKb.value) return
  try { await fetch(`${API_BASE}/shared-kb/${settingsKb.value.id}/members/${targetId}`, { method: 'DELETE', headers: auth() }); members.value = members.value.filter(m => (m.userId || m.user_id) !== targetId); ElMessage.success('已移除') }
  catch (e: any) { ElMessage.error(e.message) }
}
async function confirmDeleteKb() {
  if (!settingsKb.value) return
  try { await ElMessageBox.confirm(`确定解散「${settingsKb.value.name}」？所有文件将被删除且不可恢复。`, '确认解散', { type: 'warning', confirmButtonClass: 'el-button--danger' }); await fetch(`${API_BASE}/shared-kb/${settingsKb.value.id}`, { method: 'DELETE', headers: auth() }); showKbSettings.value = false; await fetchMyKbs(); ElMessage.success('已解散') }
  catch {}
}

/* ===== Join KB ===== */
const showJoinDialog = ref(false)
const joinToken = ref('')
const joinLoading = ref(false)
async function confirmJoin() {
  let token = joinToken.value.trim()
  if (!token) { ElMessage.warning('请输入邀请链接或token'); return }
  // 提取 token：如果贴的是完整链接，从 ?token= 后面取
  const m = token.match(/[?&]token=([^&]+)/)
  if (m) token = m[1]
  joinLoading.value = true
  try {
    await fetch(`${API_BASE}/shared-kb/join?token=${encodeURIComponent(token)}`, { method: 'POST', headers: auth() })
    showJoinDialog.value = false; joinToken.value = ''
    await Promise.all([fetchMyKbs(), fetchJoinedKbs()]); ElMessage.success('加入成功')
  } catch (e: any) { ElMessage.error(e.message) } finally { joinLoading.value = false }
}

/* ===== Upload ===== */
const uploadVisible = ref(false)
const uploadLoading = ref(false)
const uploadParentNode = ref<TreeNode | null>(null)
const uploadFileList = ref<UploadUserFile[]>([])
const uploadKbId = ref<number | null>(null)
function handleUploadFile() { uploadParentNode.value = null; uploadFileList.value = []; uploadKbId.value = null; uploadVisible.value = true }
async function submitUpload() {
  if (uploadFileList.value.length === 0) return; uploadLoading.value = true; let ok = 0
  try {
    for (const f of uploadFileList.value) {
      if (!f.raw) continue; const fd = new FormData(); fd.append('file', f.raw)
      if (uploadParentNode.value) fd.append('parentNodeId', String(uploadParentNode.value.id))
      if (uploadKbId.value != null) fd.append('kbId', String(uploadKbId.value))
      const r = await fetch(`${API_BASE}/documents/upload`, { method: 'POST', headers: auth(), body: fd })
      if (!r.ok) throw new Error(`上传失败: ${f.name}`); ok++
    }
    await fetchTree()
    if (uploadKbId.value != null) { kbTreeCache.value.delete(uploadKbId.value); await refreshKbTree(uploadKbId.value) }
    uploadVisible.value = false; ElMessage.success(`成功上传 ${ok} 个文件`)
  } catch (e: any) { ElMessage.error(e.message) } finally { uploadLoading.value = false }
}

/* ===== Drag ===== */
function allowDrop(_: any, dropNode: { data: TreeNode }, type: DropType) { if (type === 'inner') return dropNode.data.type === 'folder'; return true }
async function handleNodeDrop(draggingNode: { data: TreeNode }, dropNode: { data: TreeNode }, type: DropType, _: Event) {
  const targetParentId = type === 'inner' ? dropNode.data.id : null
  try { await fetch(`${API_BASE}/documents/directory/${draggingNode.data.id}/move`, { method: 'PUT', headers: { 'Content-Type': 'application/json', ...auth() }, body: JSON.stringify({ targetParentId }) }); await fetchTree() }
  catch (e: any) { ElMessage.error(e.message); await fetchTree() }
}

/* ===== Search ===== */
function filterTree() { treeRef.value?.filter(searchQuery.value) }
function filterNode(value: string, data: TreeNode) { if (!value) return true; return data.label.toLowerCase().includes(value.toLowerCase()) }

/* ===== Click ===== */
function handleNodeClick(data: TreeNode) { selectedNode.value = data; if (data.type === 'file' && data.docId) loadFileContent(data) }
const contentCache = new Map<string, string>()
async function loadFileContent(node: TreeNode) {
  if (!node.docId) return
  if (contentCache.has(node.docId)) { node.content = contentCache.get(node.docId); return }
  try {
    const r = await fetch(`${API_BASE}/documents/${node.docId}/content`, { headers: auth() })
    if (r.ok) { const t = await r.text(); node.content = t; contentCache.set(node.docId, t) } else node.content = `# ${node.label}\n\n文档正在处理中，请稍后查看。`
  } catch { node.content = `# ${node.label}\n\n暂无法加载文档内容。` }
}

/* ===== Utils ===== */
function countItems(node: TreeNode, type: 'folder' | 'file') { if (!node.children) return 0; return node.children.filter(c => c.type === type).length }
function renderContent(content: string): string {
  return '<p>' + content
    .replace(/^### (.+)$/gm, '<h3>$1</h3>').replace(/^## (.+)$/gm, '<h2>$1</h2>').replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replace(/`(.+?)`/g, '<code>$1</code>')
    .replace(/- (.+)/g, '<li>$1</li>').replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>').replace(/\n\n/g, '</p><p>')
    .replace(/^(.+)$/gm, m => m.startsWith('<') ? m : m) + '</p>'
}
</script>

<style scoped>
.kb-container { display: flex; flex-direction: column; height: 100%; background: #1a1a1a; color: #e0e0e0; }
.kb-toolbar { display: flex; align-items: center; padding: 10px 16px; border-bottom: 1px solid #333; gap: 12px; flex-shrink: 0; background: #222; }
.toolbar-left { display: flex; align-items: center; gap: 8px; color: #ff7d00; font-weight: 600; font-size: 15px; flex-shrink: 0; }
.toolbar-icon { color: #ff7d00; }
.toolbar-center { flex: 1; max-width: 320px; }
:deep(.search-input .el-input__wrapper) { background: #2a2a2a; border: 1px solid #444; border-radius: 6px; box-shadow: none; }
:deep(.search-input .el-input__inner) { color: #e0e0e0; }
:deep(.search-input .el-input__inner::placeholder) { color: #666; }
.toolbar-right .el-button { background: #ff7d00; border-color: #ff7d00; color: #fff; }
.toolbar-right .el-button:hover { background: #ff9333; border-color: #ff9333; }
.kb-body { display: flex; flex: 1; overflow: hidden; }

.kb-sidebar { width: 280px; min-width: 280px; border-right: 1px solid #333; overflow-y: auto; padding: 8px 0; background: #222; }
.section-header { display: flex; align-items: center; gap: 6px; padding: 8px 12px 4px; font-size: 12px; color: #888; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; user-select: none; }
.section-header.clickable { cursor: pointer; color: #ccc; }
.section-header.clickable:hover { color: #ff7d00; }
.back-tag { margin-left: auto; }
.section-header.shared-header { border-top: 1px solid #333; margin-top: 8px; padding-top: 12px; }
.tree-loading { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 40px 0; color: #888; font-size: 14px; }
.tree-empty { padding: 32px 16px; text-align: center; color: #666; font-size: 13px; }

.kb-list-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; cursor: pointer; border-radius: 4px; margin: 1px 6px; font-size: 13px; color: #ccc; transition: all 0.15s; }
.kb-list-item:hover { background: #333; }
.kb-list-item.active { background: #2a3a2a; color: #ff7d00; }
.kb-list-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-settings-btn { color: #666; }
.kb-settings-btn:hover { color: #ff7d00; }

:deep(.el-tree) { background: transparent; color: #e0e0e0; }
:deep(.el-tree-node__content) { height: 36px; padding: 0 8px; border-radius: 4px; margin: 1px 4px; }
:deep(.el-tree-node__content:hover) { background: #333; }
:deep(.el-tree-node.is-current > .el-tree-node__content) { background: #2a3a2a; color: #ff7d00; }
:deep(.el-tree-node__expand-icon) { color: #888; font-size: 14px; }
:deep(.el-tree-node__expand-icon.is-leaf) { color: transparent; }

.custom-tree-node { display: flex; align-items: center; gap: 6px; flex: 1; overflow: hidden; height: 100%; }
.node-icon { display: flex; align-items: center; flex-shrink: 0; }
.node-label { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.custom-tree-node .el-input { height: 28px; }
.custom-tree-node .el-input__wrapper { background: #333; border: 1px solid #ff7d00; box-shadow: none; padding: 0 8px; height: 28px; border-radius: 4px; }
.custom-tree-node .el-input__inner { color: #e0e0e0; font-size: 13px; }
.my-tree { min-height: 60px; }

.kb-content { flex: 1; overflow-y: auto; padding: 24px 32px; background: #1a1a1a; }
.kb-breadcrumb { margin-bottom: 12px; }
.content-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; gap: 12px; color: #666; font-size: 14px; }
.folder-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
.folder-header h2 { margin: 0; font-size: 22px; color: #e0e0e0; }
.folder-stats { display: flex; gap: 16px; }
.stat-card { background: #2a2a2a; border: 1px solid #333; border-radius: 8px; padding: 16px 24px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.stat-num { font-size: 24px; font-weight: 700; color: #ff7d00; }
.stat-label { font-size: 13px; color: #888; }
.file-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.file-header-left { display: flex; align-items: center; gap: 10px; }
.file-header-left h2 { margin: 0; font-size: 20px; color: #e0e0e0; }
.file-body { padding-top: 8px; line-height: 1.8; color: #ccc; font-size: 14px; }
.markdown-preview h1 { font-size: 22px; color: #e0e0e0; margin: 16px 0 8px; border-bottom: 1px solid #333; padding-bottom: 6px; }
.markdown-preview h2 { font-size: 18px; color: #e0e0e0; margin: 14px 0 6px; }
.markdown-preview h3 { font-size: 15px; color: #ddd; margin: 12px 0 4px; }
.markdown-preview p { margin: 6px 0; }
.markdown-preview strong { color: #ff7d00; }
.markdown-preview code { background: #2a2a2a; padding: 1px 6px; border-radius: 3px; font-size: 13px; color: #e6a23c; }
.markdown-preview ul { padding-left: 20px; margin: 4px 0; }
.markdown-preview li { margin: 2px 0; }

.context-menu { position: fixed; z-index: 9999; background: #2a2a2a; border: 1px solid #444; border-radius: 6px; padding: 4px 0; min-width: 150px; box-shadow: 0 4px 12px rgba(0,0,0,0.4); }
.context-menu-item { display: flex; align-items: center; gap: 8px; padding: 8px 14px; font-size: 13px; color: #e0e0e0; cursor: pointer; transition: background 0.15s; }
.context-menu-item:hover { background: #333; }
.context-menu-item.danger { color: #f56c6c; }
.context-menu-item.danger:hover { background: #3a2222; }
.context-divider { margin: 4px 0; border-color: #333; }

.upload-target { display: flex; align-items: center; gap: 8px; padding: 0 0 12px; }
.upload-target-label { color: #888; font-size: 13px; white-space: nowrap; }
:deep(.kb-upload .el-upload-dragger) { background: #2a2a2a; border: 2px dashed #444; border-radius: 10px; }
:deep(.kb-upload .el-upload-dragger:hover) { border-color: #ff7d00; background: #333; }
:deep(.kb-upload .el-upload__text) { color: #ccc; }
:deep(.kb-upload .el-upload__text em) { color: #ff7d00; font-style: normal; }
:deep(.kb-upload .el-upload__tip) { color: #666; }
:deep(.kb-upload .el-upload-list__item) { color: #e0e0e0; background: #2a2a2a; border: 1px solid #444; border-radius: 6px; }
:deep(.kb-upload .el-upload-list__item-name) { color: #ccc; }

.member-row { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #333; }
.member-info { display: flex; align-items: center; gap: 8px; }
.member-name { color: #e0e0e0; font-size: 13px; }

:deep(.el-dialog) { background: #2a2a2a; border: 1px solid #444; border-radius: 8px; }
:deep(.el-dialog__title) { color: #e0e0e0; }
:deep(.el-dialog__body) { padding: 16px 20px; }
:deep(.el-dialog__body .el-input__wrapper) { background: #333; border: 1px solid #444; box-shadow: none; }
:deep(.el-dialog__body .el-input__inner) { color: #e0e0e0; }
:deep(.el-dialog__body .el-textarea__inner) { background: #333; border: 1px solid #444; color: #e0e0e0; }
:deep(.el-button--primary) { background: #ff7d00; border-color: #ff7d00; }
:deep(.el-button--primary:hover) { background: #ff9333; border-color: #ff9333; }
:deep(.el-form-item__label) { color: #ccc; font-size: 13px; }
:deep(.el-tabs__item) { color: #888; }
:deep(.el-tabs__item.is-active) { color: #ff7d00; }
:deep(.el-select .el-input__wrapper) { background: #333; border: 1px solid #444; box-shadow: none; }
</style>
