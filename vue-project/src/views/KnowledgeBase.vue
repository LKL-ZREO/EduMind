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
            <div class="file-header-right">
              <el-tag size="small" type="info" effect="plain">预览</el-tag>
              <el-button v-if="isPptFile(selectedNode.label)" size="small" type="warning" :icon="MagicStick" @click="openGenerateDialog">🤖 AI 生成教学材料</el-button>
            </div>
          </div>
          <!-- 已生成的草稿 -->
          <div v-if="drafts.previews.length || drafts.quizzes.length" class="file-drafts">
            <el-divider />
            <h4>📋 已生成的教学材料</h4>
            <div v-for="p in drafts.previews" :key="'p'+p.id" class="draft-item" style="cursor:pointer" @click="viewDraft('preview', p)">
              <div class="draft-info"><span class="draft-tag preview">预习</span><span>{{ p.title }}</span></div>
              <div class="draft-actions"><el-button text size="small" type="danger" @click.stop="deleteDraft('preview', p.id)">🗑</el-button></div>
            </div>
            <div v-for="q in drafts.quizzes" :key="'q'+q.id" class="draft-item" style="cursor:pointer" @click="viewDraft('quiz', q)">
              <div class="draft-info"><el-tag size="small" :type="q.quizType==='CHOICE'?'primary':q.quizType==='OPEN'?'success':'warning'">{{ typeLabelZh(q.quizType) }}</el-tag><span>{{ q.title }}</span></div>
              <div class="draft-actions"><el-button text size="small" type="danger" @click.stop="deleteDraft('quiz', q.id)">🗑</el-button></div>
            </div>
            <el-divider />
          </div>
          <div class="file-body">
            <div v-if="selectedNode.content" class="markdown-preview" v-html="renderContent(selectedNode.content)" />
            <div v-else class="content-empty"><el-icon :size="48" color="#dcdfe6"><Tickets /></el-icon><p>文档正在处理中，请稍后查看...</p></div>
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
      <el-upload ref="uploadRef" v-model:file-list="uploadFileList" :auto-upload="false" drag multiple accept=".txt,.md,.pdf,.doc,.docx,.ppt,.pptx" :on-change="(_, files) => uploadFileList = files" class="kb-upload">
        <el-icon class="el-icon--upload" :size="40"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
        <template #tip><div class="el-upload__tip">支持 .txt .md .pdf .doc .docx .ppt .pptx</div></template>
      </el-upload>
      <template #footer><el-button @click="uploadVisible = false">取消</el-button><el-button type="primary" :loading="uploadLoading" :disabled="uploadFileList.length === 0" @click="submitUpload">上传 {{ uploadFileList.length ? `${uploadFileList.length} 个文件` : '' }}</el-button></template>
    </el-dialog>

    <!-- 草稿详情 -->
    <el-dialog v-model="showDraftDetail" :title="draftDetail?.type === 'preview' ? '📖 预习作业详情' : '✏️ 试题详情'" width="560px" append-to-body>
      <div v-if="draftDetail?.type === 'preview'" class="draft-detail">
        <p><strong>知识点：</strong>{{ draftDetail.data.topic || draftDetail.data.title }}</p>
        <div v-if="draftDetail.data.guideText" class="gen-guide" v-html="markdownToHtml(draftDetail.data.guideText)"></div>
        <div v-if="draftDetail.data.discussionQuestion"><p><strong>课堂讨论：</strong>{{ draftDetail.data.discussionQuestion }}</p></div>
      </div>
      <div v-else-if="draftDetail?.type === 'quiz'" class="draft-detail">
        <p><strong>知识点：</strong>{{ draftDetail.data.knowledgePoint }}</p>
        <p><strong>类型：</strong>{{ typeLabelZh(draftDetail.data.quizType) }}</p>
        <p><strong>题目：</strong>{{ draftDetail.data.title }}</p>
        <p v-if="draftDetail.data.correctKey"><strong>答案：</strong>{{ draftDetail.data.correctKey }}</p>
      </div>
    </el-dialog>

    <!-- AI 生成教学材料 -->
    <el-dialog v-model="gen.visible" title="🤖 AI 生成教学材料" width="640px" :close-on-click-modal="false" append-to-body @closed="resetGen">
      <div v-if="!gen.result" class="gen-step">
        <p style="color:#666;margin-bottom:16px">将根据 PPT 内容自动生成预习作业和课堂试题，生成后可在面板中编辑和选择保存。</p>
        <el-form label-width="80px">
          <el-form-item label="目标班级">
            <el-select v-model="gen.classId" placeholder="选择班级" style="width:100%">
              <el-option v-for="c in classList" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="PPT 文件"><span style="color:#409eff">{{ selectedNode?.label }}</span></el-form-item>
        </el-form>
        <div style="text-align:center;padding:12px">
          <el-button type="primary" :loading="gen.loading" :disabled="!gen.classId" @click="doGenerate">开始生成</el-button>
        </div>
        <div v-if="gen.loading" style="text-align:center;padding:16px;color:#909399">
          <el-icon class="is-loading" :size="20"><Loading /></el-icon> 正在分析 PPT 内容，生成教学材料...
        </div>
      </div>
      <!-- 生成结果 -->
      <div v-else class="gen-result">
        <el-tabs>
          <el-tab-pane label="📖 预习作业">
            <template v-if="gen.result.preview">
              <div class="gen-preview-section">
                <p><strong>知识点：</strong>{{ gen.result.preview.topic }}</p>
                <div class="gen-guide" v-html="markdownToHtml(gen.result.preview.guideText)"></div>
                <div v-if="gen.result.preview.questions?.length" style="margin-top:12px">
                  <p><strong>预习自测题 ({{ gen.result.preview.questions.length }}道)：</strong></p>
                  <div v-for="(q,i) in gen.result.preview.questions" :key="i" class="gen-quiz-card">
                    <el-tag size="small" :type="q.type==='CHOICE'?'primary':'success'">{{ q.type==='CHOICE'?'选择题':'简答题' }}</el-tag>
                    <p>{{ q.question }}</p>
                    <div v-if="q.options?.length" style="margin:4px 0;font-size:13px;color:#666">
                      <span v-for="o in q.options" :key="o.key" style="margin-right:12px">{{ o.key }}. {{ o.text }}</span>
                    </div>
                    <p style="font-size:12px;color:#67c23a">答案: {{ q.correctKey }}</p>
                  </div>
                </div>
                <p v-if="gen.result.preview.discussionQuestion" style="margin-top:8px"><strong>课堂讨论：</strong>{{ gen.result.preview.discussionQuestion }}</p>
                <div style="margin-top:12px">
                  <el-button size="small" type="primary" :loading="gen.savingPreview" @click="savePreview">📢 发布预习作业</el-button>
                  <span v-if="gen.result.preview.published" style="color:#67c23a;margin-left:8px">✅ 已发布</span>
                </div>
              </div>
            </template>
            <div v-else style="padding:20px;text-align:center;color:#909399">预习作业生成失败，请返回重试</div>
          </el-tab-pane>
          <el-tab-pane label="✏️ 课堂试题 ({{ gen.result.quizzes?.length || 0 }})">
            <div class="gen-preview-section" v-if="gen.result.quizzes?.length">
              <div v-for="(q,i) in gen.result.quizzes" :key="i" class="gen-quiz-card">
                <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
                  <el-tag size="small" :type="q.type==='CHOICE'?'primary':q.type==='OPEN'?'success':'warning'">{{ typeLabel(q.type) }}</el-tag>
                  <el-tag size="small" :type="q.difficulty==='easy'?'success':q.difficulty==='hard'?'danger':''">{{ diffLabel(q.difficulty) }}</el-tag>
                  <span style="margin-left:auto;font-size:12px;color:#909399">{{ q.knowledgePoint }}</span>
                </div>
                <p>{{ q.title }}</p>
                <div v-if="q.options?.length" style="font-size:13px;color:#666">
                  <span v-for="o in q.options" :key="o.key" style="margin-right:12px">{{ o.key }}. {{ o.text }}</span>
                </div>
                <p style="font-size:12px;color:#67c23a">答案: {{ q.correctKey }} | 限时: {{ q.timeLimit || '-' }}s</p>
                <div style="margin-top:4px">
                  <el-button size="small" :loading="gen.savingQuiz === i" @click="saveQuiz(i)">💾 保存到草稿库</el-button>
                  <span v-if="q.published" style="color:#67c23a;margin-left:8px">✅ 已保存</span>
                </div>
              </div>
            </div>
            <div v-else style="padding:20px;text-align:center;color:#909399">暂无试题数据</div>
          </el-tab-pane>
        </el-tabs>
        <div style="text-align:center;margin-top:12px">
          <el-button @click="resetGen">关闭</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FolderOpened, Document, FolderAdd, Upload, UploadFilled, Edit, Delete, Tickets, Loading, Share, Link, Setting, Star, ArrowRight, MagicStick } from '@element-plus/icons-vue'
import type { ElTree, UploadUserFile } from 'element-plus'
import type { DropType } from 'element-plus/es/components/tree/src/tree.type'
import request from '@/api/request'

/* ===== Types ===== */
interface FlatNode { id: number; userId: number; parentId: number | null; label: string; nodeType: 'folder' | 'file'; docId: string | null; sortOrder: number; createdAt: string; updatedAt: string; kbId?: number }
interface TreeNode { id: number; label: string; type: 'folder' | 'file'; docId?: string; children?: TreeNode[]; content?: string; kbId?: number }
interface SharedKb { id: number; name: string; description: string; ownerId: number; inviteToken?: string; createdAt: string }

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
  const res = await request.get('/shared-kb/my')
  myKbs.value = res.data
}
async function fetchJoinedKbs() {
  const res = await request.get('/shared-kb/joined')
  joinedKbs.value = res.data
}

async function fetchTree() {
  const res = await request.get('/documents/directory/tree')
  myTreeData.value = buildTree(res.data)
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
  try { const res = await request.get(`/documents/directory/tree?kbId=${kbId}`); kbTreeCache.value.set(kbId, buildTree(res.data)) } catch {}
}

async function toggleKbExpand(kbId: number) {
  if (expandedKbs.value.has(kbId)) {
    expandedKbs.value.delete(kbId)
  } else {
    expandedKbs.value.add(kbId)
    if (!kbTreeCache.value.has(kbId)) {
      kbLoading.value.set(kbId, true)
      try {
        const res = await request.get(`/documents/directory/tree?kbId=${kbId}`)
        kbTreeCache.value.set(kbId, buildTree(res.data))
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
async function confirmRename(data: TreeNode) { const name = renameValue.value.trim(); renamingNode.value = null; if (!name || name === data.label) return; try { await request.put(`/documents/directory/${data.id}/rename`, { label: name }); data.label = name } catch (e: any) { ElMessage.error(e.message); await fetchTree() } }
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
    await request.delete(`/documents/directory/${n.id}`)
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
    await request.post(`/documents/directory/folder`, body)
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
    await request.post(`/shared-kb/create`, createKbForm)
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
  try { const r = await request.get(`/shared-kb/${kb.id}/members`); members.value = r.data } catch {}
}
async function saveKbSettings() {
  if (!settingsKb.value) return; settingsSaveLoading.value = true
  try { await request.put(`/shared-kb/${settingsKb.value.id}`, { name: settingsForm.name, description: settingsForm.description }); ElMessage.success('已保存'); await fetchMyKbs() }
  catch (e: any) { ElMessage.error(e.message) } finally { settingsSaveLoading.value = false }
}
async function regenerateInvite() {
  if (!settingsKb.value) return
  try { const r = await request.post(`/shared-kb/${settingsKb.value.id}/invite`, {}); const d = r.data; inviteLink.value = `${window.location.origin}/invite?token=${d.token}`; settingsKb.value!.inviteToken = d.token; ElMessage.success('已重新生成') }
  catch (e: any) { ElMessage.error(e.message) }
}
function copyInviteLink() { navigator.clipboard.writeText(inviteLink.value); ElMessage.success('已复制') }
async function removeMember(targetId: number) {
  if (!settingsKb.value) return
  try { await request.delete(`/shared-kb/${settingsKb.value.id}/members/${targetId}`); members.value = members.value.filter(m => (m.userId || m.user_id) !== targetId); ElMessage.success('已移除') }
  catch (e: any) { ElMessage.error(e.message) }
}
async function confirmDeleteKb() {
  if (!settingsKb.value) return
  try { await ElMessageBox.confirm(`确定解散「${settingsKb.value.name}」？所有文件将被删除且不可恢复。`, '确认解散', { type: 'warning', confirmButtonClass: 'el-button--danger' }); await request.delete(`/shared-kb/${settingsKb.value.id}`); showKbSettings.value = false; await fetchMyKbs(); ElMessage.success('已解散') }
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
    await request.post(`/shared-kb/join?token=${encodeURIComponent(token)}`)
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
      const r = await request.post(`/documents/upload`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })
      ok++
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
  try { await request.put(`/documents/directory/${draggingNode.data.id}/move`, { targetParentId }); await fetchTree() }
  catch (e: any) { ElMessage.error(e.message); await fetchTree() }
}

/* ===== Search ===== */
function filterTree() { treeRef.value?.filter(searchQuery.value) }
function filterNode(value: string, data: TreeNode) { if (!value) return true; return data.label.toLowerCase().includes(value.toLowerCase()) }

/* ===== Click ===== */
function handleNodeClick(data: TreeNode) { selectedNode.value = data; drafts.value = { previews: [], quizzes: [] }; if (data.type === 'file' && data.docId) { loadFileContent(data); loadDrafts(data.docId) } }
const contentCache = new Map<string, string>()
async function loadFileContent(node: TreeNode) {
  if (!node.docId) return
  if (contentCache.has(node.docId)) { node.content = contentCache.get(node.docId); return }
  try {
    const r = await request.get(`/documents/${node.docId}/content`)
    node.content = typeof r.data === 'string' ? r.data : `# ${node.label}\n\n文档正在处理中，请稍后查看。`; if (node.content && !node.content.startsWith('#')) contentCache.set(node.docId, node.content)
  } catch { node.content = `# ${node.label}\n\n暂无法加载文档内容。` }
}

/* ===== AI 生成教学材料 ===== */
interface GenResult { preview: any; quizzes: any[]; pptFileName: string }
const gen = reactive({
  visible: false, loading: false, classId: null as number | null,
  result: null as GenResult | null, savingPreview: false, savingQuiz: null as number | null,
})
const classList = ref<{ id: number; name: string }[]>([])

function isPptFile(label: string) { return /\.(ppt|pptx)$/i.test(label) }
function typeLabel(t: string) { return ({ CHOICE: '选择题', OPEN: '简答题', EXERCISE: '随堂练习' } as any)[t] || t }
function diffLabel(d: string) { return ({ easy: '简单', medium: '中等', hard: '困难' } as any)[d] || d }

async function fetchClassList() {
  try { const r = await request.get('/dashboard/classes'); classList.value = r.data?.data || [] } catch {}
}
fetchClassList()

async function openGenerateDialog() {
  if (!selectedNode.value?.docId) { ElMessage.warning('请先选择一个文件'); return }
  if (classList.value.length === 0) { await fetchClassList() }
  gen.visible = true; gen.classId = null; gen.result = null
}

async function doGenerate() {
  if (!selectedNode.value?.docId || !gen.classId) return
  gen.loading = true; gen.result = null
  try {
    const r = await request.post('/documents/generate-materials', { classId: gen.classId, docId: selectedNode.value.docId }, { timeout: 120000 })
    gen.result = r.data
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '生成失败，请重试') }
  finally { gen.loading = false }
}

async function savePreview() {
  if (!gen.result || !gen.classId) return
  gen.savingPreview = true
  try {
    await request.post('/documents/generate-materials/save-preview', { ...gen.result.preview, classId: gen.classId, docId: selectedNode.value?.docId })
    gen.result.preview.published = true; ElMessage.success('预习作业已发布')
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
  finally { gen.savingPreview = false }
}

async function saveQuiz(index: number) {
  if (!gen.result || !gen.classId) return
  gen.savingQuiz = index
  try {
    await request.post('/documents/generate-materials/save-quiz', { ...gen.result.quizzes[index], classId: gen.classId, docId: selectedNode.value?.docId })
    gen.result.quizzes[index].published = true; ElMessage.success('试题已保存到草稿库')
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
  finally { gen.savingQuiz = null }
}

function resetGen() { gen.visible = false; gen.result = null; gen.loading = false }

/* ===== 教学草稿 ===== */
const drafts = ref<{ previews: any[]; quizzes: any[] }>({ previews: [], quizzes: [] })

async function loadDrafts(docId: string) {
  try {
    const r = await request.get(`/documents/drafts?docId=${docId}`)
    drafts.value = r.data || { previews: [], quizzes: [] }
  } catch { drafts.value = { previews: [], quizzes: [] } }
}

const draftDetail = ref<any>(null)
const showDraftDetail = ref(false)

async function viewDraft(type: string, item: any) {
  if (type === 'preview') {
    try {
      const r = await request.get(`/preview/${item.id}`)
      draftDetail.value = { type: 'preview', data: r.data.data || r.data }
    } catch { draftDetail.value = { type: 'preview', ...item } }
  } else {
    draftDetail.value = { type: 'quiz', data: item }
  }
  showDraftDetail.value = true
}

async function deleteDraft(type: string, id: number) {
  try {
    await request.delete(`/documents/drafts/${type}/${id}`)
    if (type === 'preview') drafts.value.previews = drafts.value.previews.filter((p: any) => p.id !== id)
    else drafts.value.quizzes = drafts.value.quizzes.filter((q: any) => q.id !== id)
    ElMessage.success('已删除')
  } catch { ElMessage.error('删除失败') }
}

function typeLabelZh(t: string) { return ({ CHOICE: '选择题', OPEN: '简答题', EXERCISE: '随堂练习' } as any)[t] || t }

function markdownToHtml(md: string): string {
  if (!md) return ''
  return md
    .replace(/^### (.+)$/gm, '<h3>$1</h3>').replace(/^## (.+)$/gm, '<h2>$1</h2>').replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>').replace(/`(.+?)`/g, '<code>$1</code>')
    .replace(/- (.+)/g, '<li>$1</li>').replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>')
    .replace(/\n\n/g, '</p><p>').replace(/^(.+)$/gm, m => m.startsWith('<') ? m : m)
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
.kb-container { display: flex; flex-direction: column; height: 100%; background: #ffffff; color: #303133; }
.kb-toolbar { display: flex; align-items: center; padding: 10px 16px; border-bottom: 1px solid #ebeef5; gap: 12px; flex-shrink: 0; background: #d9ecff; }
.toolbar-left { display: flex; align-items: center; gap: 8px; color: #409EFF; font-weight: 600; font-size: 15px; flex-shrink: 0; }
.toolbar-icon { color: #409EFF; }
.toolbar-center { flex: 1; max-width: 320px; }
:deep(.search-input .el-input__wrapper) { background: #ffffff; border: 1px solid #e4e7ed; border-radius: 6px; box-shadow: none; }
:deep(.search-input .el-input__inner) { color: #303133; }
:deep(.search-input .el-input__inner::placeholder) { color: #909399; }
.toolbar-right .el-button { background: #409EFF; border-color: #409EFF; color: #fff; }
.toolbar-right .el-button:hover { background: #337ecc; border-color: #337ecc; }
.kb-body { display: flex; flex: 1; overflow: hidden; }

.kb-sidebar { width: 280px; min-width: 280px; border-right: 1px solid #ebeef5; overflow-y: auto; padding: 8px 0; background: #d9ecff; }
.section-header { display: flex; align-items: center; gap: 6px; padding: 8px 12px 4px; font-size: 12px; color: #909399; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; user-select: none; }
.section-header.clickable { cursor: pointer; color: #303133; }
.section-header.clickable:hover { color: #409EFF; }
.back-tag { margin-left: auto; }
.section-header.shared-header { border-top: 1px solid #ebeef5; margin-top: 8px; padding-top: 12px; }
.tree-loading { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 40px 0; color: #909399; font-size: 14px; }
.tree-empty { padding: 32px 16px; text-align: center; color: #909399; font-size: 13px; }

.kb-list-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; cursor: pointer; border-radius: 4px; margin: 1px 6px; font-size: 13px; color: #303133; transition: all 0.15s; }
.kb-list-item:hover { background: #ebeef5; }
.kb-list-item.active { background: #ecf5ff; color: #409EFF; }
.kb-list-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kb-settings-btn { color: #909399; }
.kb-settings-btn:hover { color: #409EFF; }

:deep(.el-tree) { background: transparent; color: #303133; }
:deep(.el-tree-node__content) { height: 36px; padding: 0 8px; border-radius: 4px; margin: 1px 4px; }
:deep(.el-tree-node__content:hover) { background: #ebeef5; }
:deep(.el-tree-node.is-current > .el-tree-node__content) { background: #ecf5ff; color: #409EFF; }
:deep(.el-tree-node__expand-icon) { color: #909399; font-size: 14px; }
:deep(.el-tree-node__expand-icon.is-leaf) { color: transparent; }

.custom-tree-node { display: flex; align-items: center; gap: 6px; flex: 1; overflow: hidden; height: 100%; }
.node-icon { display: flex; align-items: center; flex-shrink: 0; }
.node-label { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.custom-tree-node .el-input { height: 28px; }
.custom-tree-node .el-input__wrapper { background: #ebeef5; border: 1px solid #409EFF; box-shadow: none; padding: 0 8px; height: 28px; border-radius: 4px; }
.custom-tree-node .el-input__inner { color: #303133; font-size: 13px; }
.my-tree { min-height: 60px; }

.kb-content { flex: 1; overflow-y: auto; padding: 24px 32px; background: #ffffff; }
.kb-breadcrumb { margin-bottom: 12px; }
.content-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; gap: 12px; color: #909399; font-size: 14px; }
.folder-header { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
.folder-header h2 { margin: 0; font-size: 22px; color: #303133; }
.folder-stats { display: flex; gap: 16px; }
.stat-card { background: #ffffff; border: 1px solid #ebeef5; border-radius: 8px; padding: 16px 24px; display: flex; flex-direction: column; align-items: center; gap: 4px; }
.stat-num { font-size: 24px; font-weight: 700; color: #409EFF; }
.stat-label { font-size: 13px; color: #909399; }
.file-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.file-header-left { display: flex; align-items: center; gap: 10px; }
.file-header-left h2 { margin: 0; font-size: 20px; color: #303133; }
.file-header-right { display: flex; align-items: center; gap: 10px; }
.file-body { padding-top: 8px; line-height: 1.8; color: #303133; font-size: 14px; }
.markdown-preview h1 { font-size: 22px; color: #303133; margin: 16px 0 8px; border-bottom: 1px solid #ebeef5; padding-bottom: 6px; }
.markdown-preview h2 { font-size: 18px; color: #303133; margin: 14px 0 6px; }
.markdown-preview h3 { font-size: 15px; color: #606266; margin: 12px 0 4px; }
.markdown-preview p { margin: 6px 0; }
.markdown-preview strong { color: #409EFF; }
.markdown-preview code { background: #ffffff; padding: 1px 6px; border-radius: 3px; font-size: 13px; color: #e6a23c; }
.markdown-preview ul { padding-left: 20px; margin: 4px 0; }
.markdown-preview li { margin: 2px 0; }

.context-menu { position: fixed; z-index: 9999; background: #ffffff; border: 1px solid #e4e7ed; border-radius: 6px; padding: 4px 0; min-width: 150px; box-shadow: 0 4px 12px rgba(0,0,0,0.4); }
.context-menu-item { display: flex; align-items: center; gap: 8px; padding: 8px 14px; font-size: 13px; color: #303133; cursor: pointer; transition: background 0.15s; }
.context-menu-item:hover { background: #ebeef5; }
.context-menu-item.danger { color: #f56c6c; }
.context-menu-item.danger:hover { background: #fef0f0; }
.context-divider { margin: 4px 0; border-color: #ebeef5; }

.upload-target { display: flex; align-items: center; gap: 8px; padding: 0 0 12px; }
.upload-target-label { color: #909399; font-size: 13px; white-space: nowrap; }
:deep(.kb-upload .el-upload-dragger) { background: #ffffff; border: 2px dashed #e4e7ed; border-radius: 10px; }
:deep(.kb-upload .el-upload-dragger:hover) { border-color: #409EFF; background: #ebeef5; }
:deep(.kb-upload .el-upload__text) { color: #303133; }
:deep(.kb-upload .el-upload__text em) { color: #409EFF; font-style: normal; }
:deep(.kb-upload .el-upload__tip) { color: #909399; }
:deep(.kb-upload .el-upload-list__item) { color: #303133; background: #ffffff; border: 1px solid #e4e7ed; border-radius: 6px; }
:deep(.kb-upload .el-upload-list__item-name) { color: #303133; }

.member-row { display: flex; align-items: center; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid #ebeef5; }
.member-info { display: flex; align-items: center; gap: 8px; }
.member-name { color: #303133; font-size: 13px; }

:deep(.el-dialog) { background: #ffffff; border: 1px solid #e4e7ed; border-radius: 8px; }
:deep(.el-dialog__title) { color: #303133; }
:deep(.el-dialog__body) { padding: 16px 20px; }
:deep(.el-dialog__body .el-input__wrapper) { background: #ebeef5; border: 1px solid #e4e7ed; box-shadow: none; }
:deep(.el-dialog__body .el-input__inner) { color: #303133; }
:deep(.el-dialog__body .el-textarea__inner) { background: #ebeef5; border: 1px solid #e4e7ed; color: #303133; }
:deep(.el-button--primary) { background: #409EFF; border-color: #409EFF; }
:deep(.el-button--primary:hover) { background: #337ecc; border-color: #337ecc; }
:deep(.el-form-item__label) { color: #303133; font-size: 13px; }
:deep(.el-tabs__item) { color: #909399; }
:deep(.el-tabs__item.is-active) { color: #409EFF; }
:deep(.el-select .el-input__wrapper) { background: #ebeef5; border: 1px solid #e4e7ed; box-shadow: none; }
.gen-preview-section { max-height: 400px; overflow-y: auto; padding: 4px; }
.gen-guide { font-size: 13px; line-height: 1.7; color: #333; margin: 8px 0; padding: 10px 14px; background: #f9fafb; border-radius: 6px; }
.gen-guide h1,.gen-guide h2,.gen-guide h3 { color: #303133; }
.gen-guide strong { color: #409eff; }
.gen-guide code { background: #ebeef5; padding: 1px 5px; border-radius: 3px; font-size: 12px; }
.gen-guide ul { padding-left: 18px; }
.gen-quiz-card { padding: 12px; margin: 8px 0; border: 1px solid #ebeef5; border-radius: 8px; background: #fafafa; }
.gen-quiz-card p { margin: 6px 0; font-size: 14px; color: #333; }
.file-drafts { margin-bottom: 0; }
.file-drafts h4 { font-size: 14px; color: #666; margin: 8px 0; }
.draft-item { display: flex; align-items: center; justify-content: space-between; padding: 6px 10px; border: 1px solid #ebeef5; border-radius: 6px; margin: 4px 0; }
.draft-info { display: flex; align-items: center; gap: 8px; flex: 1; overflow: hidden; }
.draft-info span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.draft-tag { padding: 1px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.draft-tag.preview { background: #fef0e6; color: #e6a23c; }
.draft-actions { flex-shrink: 0; }
</style>
