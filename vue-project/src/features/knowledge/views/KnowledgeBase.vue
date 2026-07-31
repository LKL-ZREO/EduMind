<template>
  <div class="kb-container">
    <!-- 顶部工作区 -->
    <div class="kb-toolbar">
      <div class="toolbar-left">
        <div class="toolbar-brand-icon">
          <el-icon :size="22"><FolderOpened /></el-icon>
        </div>
        <div>
          <div class="toolbar-title">知识库管理</div>
          <div class="toolbar-subtitle">集中整理资料、预览内容和生成教学材料</div>
        </div>
      </div>
      <div class="toolbar-center">
        <el-input
          v-model="searchQuery"
          placeholder="搜索当前目录和文件..."
          prefix-icon="Search"
          clearable
          class="search-input"
          @input="filterTree"
        />
      </div>
      <div class="toolbar-right">
        <el-button type="primary" @click="handleUploadToCurrent">
          <el-icon><Upload /></el-icon>上传文件
        </el-button>
        <el-dropdown trigger="click" @command="handleCreateCommand">
          <el-button
            ><el-icon><FolderAdd /></el-icon>新建</el-button
          >
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="folder">新建文件夹</el-dropdown-item>
              <el-dropdown-item command="knowledge-base">新建团队知识库</el-dropdown-item>
              <el-dropdown-item command="join" divided>加入团队知识库</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="kb-body">
      <aside class="kb-sidebar">
        <div v-if="loading" class="tree-loading">
          <el-icon class="is-loading" :size="20"><Loading /></el-icon><span>加载中...</span>
        </div>
        <template v-else>
          <!-- 个人空间 -->
          <div class="section-header">
            <el-icon :size="14"><FolderOpened /></el-icon><span>个人空间</span>
            <span class="section-count">{{ personalDocumentCount }} 个文档</span>
          </div>
          <el-tree
            ref="treeRef"
            :data="myTreeData"
            :props="treeProps"
            node-key="id"
            :filter-node-method="filterNode"
            draggable
            :allow-drop="allowDrop"
            :allow-drag="() => true"
            :highlight-current="true"
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
            @node-drop="handleNodeDrop"
            @node-contextmenu="handleContextMenu"
            :drop-indicator="true"
            class="my-tree"
          >
            <template #default="{ node, data }">
              <div class="custom-tree-node" @dblclick="handleDoubleClick(data)">
                <template v-if="renamingNode?.id === data.id">
                  <el-input
                    v-model="renameValue"
                    size="small"
                    @blur="confirmRename(data)"
                    @keydown.enter="confirmRename(data)"
                    @keydown.escape="cancelRename"
                    @click.stop
                    autofocus
                  />
                </template>
                <template v-else>
                  <span class="node-icon"
                    ><el-icon
                      v-if="data.type === 'folder'"
                      :size="18"
                      :class="{ 'is-expanded': node.expanded }"
                      ><FolderOpened /></el-icon
                    ><el-icon v-else :size="16" color="#409eff"><Document /></el-icon
                  ></span>
                  <span class="node-label" :title="data.label">{{ data.label }}</span>
                </template>
              </div>
            </template>
          </el-tree>

          <div class="section-header shared-header team-header">
            <el-icon :size="14"><Share /></el-icon><span>团队知识库</span>
            <el-button link size="small" @click="showJoinDialog = true">加入</el-button>
          </div>
          <!-- 我管理的 -->
          <div v-if="myKbs.length > 0" class="subsection-label">
            <el-icon :size="13"><Star /></el-icon><span>我管理的</span>
          </div>
          <template v-for="kb in myKbs" :key="kb.id">
            <div
              class="kb-list-item"
              :class="{ active: activeSpaceKbId === kb.id }"
              @click="selectKnowledgeBase(kb.id)"
              @contextmenu.prevent="openKbSettings(kb)"
            >
              <el-icon
                :size="14"
                color="#999"
                class="kb-expand-icon"
                :class="{ expanded: expandedKbs.has(kb.id) }"
                ><ArrowRight
              /></el-icon>
              <el-icon :size="16" color="#e6a23c"><FolderOpened /></el-icon>
              <span class="kb-list-name">{{ kb.name }}</span>
              <el-button link size="small" class="kb-settings-btn" @click.stop="openKbSettings(kb)"
                ><el-icon><Setting /></el-icon
              ></el-button>
            </div>
            <div v-if="expandedKbs.has(kb.id)" class="kb-sub-tree">
              <div v-if="kbLoading.get(kb.id)" class="tree-loading" style="padding: 12px 0">
                <el-icon class="is-loading" :size="14"><Loading /></el-icon>
              </div>
              <div
                v-else-if="kbTreeCache.get(kb.id)?.length === 0"
                class="tree-empty"
                style="padding: 12px 8px; font-size: 12px"
              >
                <p>暂无文件</p>
              </div>
              <el-tree
                v-else
                :data="kbTreeCache.get(kb.id)!"
                :props="treeProps"
                node-key="id"
                :draggable="false"
                :highlight-current="true"
                :expand-on-click-node="false"
                @node-click="handleNodeClick"
                @node-contextmenu="handleContextMenu"
                class="kb-inline-tree"
              >
                <template #default="{ node, data }">
                  <div class="custom-tree-node">
                    <span class="node-icon"
                      ><el-icon
                        v-if="data.type === 'folder'"
                        :size="16"
                        :class="{ 'is-expanded': node.expanded }"
                        ><FolderOpened /></el-icon
                      ><el-icon v-else :size="14" color="#409eff"><Document /></el-icon
                    ></span>
                    <span class="node-label" :title="data.label">{{ data.label }}</span>
                  </div>
                </template>
              </el-tree>
            </div>
          </template>

          <!-- 我加入的 -->
          <div v-if="joinedKbs.length > 0" class="subsection-label joined-label">
            <el-icon :size="13"><Link /></el-icon><span>我加入的</span>
          </div>
          <template v-for="kb in joinedKbs" :key="kb.id">
            <div
              class="kb-list-item"
              :class="{ active: activeSpaceKbId === kb.id }"
              @click="selectKnowledgeBase(kb.id)"
              @contextmenu.prevent="null"
            >
              <el-icon
                :size="14"
                color="#999"
                class="kb-expand-icon"
                :class="{ expanded: expandedKbs.has(kb.id) }"
                ><ArrowRight
              /></el-icon>
              <el-icon :size="16" color="#67c23a"><FolderOpened /></el-icon>
              <span class="kb-list-name">{{ kb.name }}</span>
            </div>
            <div v-if="expandedKbs.has(kb.id)" class="kb-sub-tree">
              <div v-if="kbLoading.get(kb.id)" class="tree-loading" style="padding: 12px 0">
                <el-icon class="is-loading" :size="14"><Loading /></el-icon>
              </div>
              <div
                v-else-if="kbTreeCache.get(kb.id)?.length === 0"
                class="tree-empty"
                style="padding: 12px 8px; font-size: 12px"
              >
                <p>暂无文件</p>
              </div>
              <el-tree
                v-else
                :data="kbTreeCache.get(kb.id)!"
                :props="treeProps"
                node-key="id"
                :draggable="false"
                :highlight-current="true"
                :expand-on-click-node="false"
                @node-click="handleNodeClick"
                @node-contextmenu="handleContextMenu"
                class="kb-inline-tree"
              >
                <template #default="{ node, data }">
                  <div class="custom-tree-node">
                    <span class="node-icon"
                      ><el-icon
                        v-if="data.type === 'folder'"
                        :size="16"
                        :class="{ 'is-expanded': node.expanded }"
                        ><FolderOpened /></el-icon
                      ><el-icon v-else :size="14" color="#409eff"><Document /></el-icon
                    ></span>
                    <span class="node-label" :title="data.label">{{ data.label }}</span>
                  </div>
                </template>
              </el-tree>
            </div>
          </template>

          <div
            v-if="
              !loading && myTreeData.length === 0 && myKbs.length === 0 && joinedKbs.length === 0
            "
            class="tree-empty"
          >
            <p>暂无内容</p>
          </div>
        </template>
      </aside>

      <main class="kb-content">
        <section class="directory-pane">
          <div class="directory-header">
            <el-breadcrumb separator="/" class="kb-breadcrumb">
              <el-breadcrumb-item>
                <span class="breadcrumb-link" @click="clearSelection">{{ selectedSpaceName }}</span>
              </el-breadcrumb-item>
              <el-breadcrumb-item v-for="item in currentPath" :key="item.id">
                <span class="breadcrumb-link" @click="handleNodeClick(item)">{{ item.label }}</span>
              </el-breadcrumb-item>
            </el-breadcrumb>
            <div class="directory-title-row">
              <div>
                <h2>{{ currentDirectoryTitle }}</h2>
                <p>{{ visibleCurrentItems.length }} 项内容</p>
              </div>
              <div class="directory-actions">
                <el-button size="small" @click="handleAddToCurrent">
                  <el-icon><FolderAdd /></el-icon>文件夹
                </el-button>
                <el-button size="small" type="primary" @click="handleUploadToCurrent">
                  <el-icon><Upload /></el-icon>上传到这里
                </el-button>
              </div>
            </div>
          </div>

          <div class="resource-table">
            <div class="resource-table-head">
              <span>名称</span><span>状态</span><span>更新时间</span><span>操作</span>
            </div>
            <div
              v-for="item in visibleCurrentItems"
              :key="item.id"
              class="resource-row"
              :class="{ selected: selectedNode?.id === item.id }"
              @click="handleNodeClick(item)"
              @dblclick="item.type === 'folder' && handleNodeClick(item)"
              @contextmenu="handleContextMenu($event, item)"
            >
              <div class="resource-name">
                <span class="resource-icon" :class="item.type">
                  <el-icon v-if="item.type === 'folder'" :size="19"><FolderOpened /></el-icon>
                  <el-icon v-else :size="18"><Document /></el-icon>
                </span>
                <div>
                  <strong :title="item.label">{{ item.label }}</strong>
                  <small>{{
                    item.type === 'folder'
                      ? `${item.children?.length || 0} 项`
                      : fileType(item.label)
                  }}</small>
                </div>
              </div>
              <div>
                <el-tag v-if="item.type === 'folder'" size="small" type="info" effect="plain"
                  >目录</el-tag
                >
                <el-tag v-else size="small" :type="resourceStatusType(item)" effect="plain">
                  {{ resourceStatus(item) }}
                </el-tag>
              </div>
              <span class="resource-time">{{ formatUpdatedAt(item.updatedAt) }}</span>
              <div class="resource-actions">
                <el-button
                  v-if="item.type === 'folder'"
                  link
                  size="small"
                  @click.stop="openUploadForFolder(item)"
                  >上传</el-button
                >
                <el-button link size="small" @click.stop="openNodeMenu($event, item)"
                  >更多</el-button
                >
              </div>
            </div>
            <div v-if="!visibleCurrentItems.length" class="directory-empty">
              <el-icon :size="42"><FolderOpened /></el-icon>
              <p>{{ searchQuery ? '没有匹配的文件或文件夹' : '这个目录还是空的' }}</p>
              <el-button
                v-if="!searchQuery"
                type="primary"
                size="small"
                @click="handleUploadToCurrent"
                >上传第一个文件</el-button
              >
            </div>
          </div>
        </section>

        <aside class="preview-pane">
          <template v-if="selectedNode?.type === 'file'">
            <div class="preview-header">
              <div class="preview-file-title">
                <span class="resource-icon file"
                  ><el-icon :size="20"><Document /></el-icon
                ></span>
                <div>
                  <h3>{{ selectedNode.label }}</h3>
                  <p>{{ fileType(selectedNode.label) }} · {{ resourceStatus(selectedNode) }}</p>
                </div>
              </div>
              <el-button
                v-if="isPptFile(selectedNode.label)"
                size="small"
                type="warning"
                :icon="MagicStick"
                @click="openGenerateDialog"
                >AI 生成材料</el-button
              >
            </div>

            <el-tabs v-model="previewTab" class="preview-tabs">
              <el-tab-pane label="内容预览" name="content">
                <div class="preview-scroll file-body">
                  <div
                    v-if="selectedNode.loadState === 'ready' && selectedNode.content"
                    class="markdown-preview"
                    v-html="renderMarkdown(selectedNode.content)"
                  />
                  <div v-else class="content-empty compact">
                    <el-icon :size="42" color="#c0c4cc"><Loading class="is-loading" /></el-icon>
                    <p>{{ resourceStatus(selectedNode) }}</p>
                  </div>
                </div>
              </el-tab-pane>
              <el-tab-pane
                :label="`教学材料 (${materials.previews.length + materials.questions.length})`"
                name="materials"
              >
                <div class="preview-scroll material-panel">
                  <div class="material-panel-header">
                    <div>
                      <strong>已生成材料</strong>
                      <p>预习内容和课堂题目统一放在这里</p>
                    </div>
                    <el-button
                      v-if="isPptFile(selectedNode.label)"
                      size="small"
                      type="warning"
                      @click="openGenerateDialog"
                      >生成材料</el-button
                    >
                  </div>
                  <div
                    v-if="materials.previews.length || materials.questions.length"
                    class="file-drafts"
                  >
                    <div
                      v-for="p in materials.previews"
                      :key="'p' + p.id"
                      class="draft-item"
                      @click="viewMaterial('preview', p)"
                    >
                      <div class="draft-info">
                        <span class="draft-tag preview">预习</span><span>{{ p.title }}</span>
                      </div>
                      <el-button
                        text
                        size="small"
                        type="danger"
                        @click.stop="deleteMaterial('preview', p.id)"
                        >删除</el-button
                      >
                    </div>
                    <div
                      v-for="q in materials.questions"
                      :key="'q' + q.id"
                      class="draft-item"
                      @click="viewMaterial('quiz', q)"
                    >
                      <div class="draft-info">
                        <el-tag
                          size="small"
                          :type="
                            q.quizType === 'CHOICE'
                              ? 'primary'
                              : q.quizType === 'OPEN'
                                ? 'success'
                                : 'warning'
                          "
                        >
                          {{ typeLabelZh(q.quizType || q.type) }}
                        </el-tag>
                        <span>{{ q.title }}</span>
                      </div>
                      <el-button
                        text
                        size="small"
                        type="danger"
                        @click.stop="deleteMaterial('quiz', q.id)"
                        >删除</el-button
                      >
                    </div>
                  </div>
                  <el-empty v-else description="暂未生成教学材料" :image-size="64" />
                </div>
              </el-tab-pane>
              <el-tab-pane label="文件信息" name="info">
                <div class="preview-scroll file-info-panel">
                  <div>
                    <span>文件名称</span><strong>{{ selectedNode.label }}</strong>
                  </div>
                  <div>
                    <span>文件类型</span><strong>{{ fileType(selectedNode.label) }}</strong>
                  </div>
                  <div>
                    <span>当前状态</span
                    ><el-tag size="small" :type="resourceStatusType(selectedNode)">{{
                      resourceStatus(selectedNode)
                    }}</el-tag>
                  </div>
                  <div>
                    <span>更新时间</span
                    ><strong>{{ formatUpdatedAt(selectedNode.updatedAt) }}</strong>
                  </div>
                  <div>
                    <span>所属空间</span><strong>{{ selectedSpaceName }}</strong>
                  </div>
                </div>
              </el-tab-pane>
            </el-tabs>
          </template>

          <template v-else>
            <div class="overview-panel">
              <span class="overview-icon"
                ><el-icon :size="28"><FolderOpened /></el-icon
              ></span>
              <h3>{{ selectedNode?.label || '知识库概览' }}</h3>
              <p>
                {{
                  selectedNode
                    ? '从中间选择文档即可在这里预览'
                    : '选择左侧知识库或目录开始整理教学资料'
                }}
              </p>
              <div class="overview-stats">
                <div>
                  <strong>{{
                    selectedNode ? countItems(selectedNode, 'folder') : personalFolderCount
                  }}</strong
                  ><span>文件夹</span>
                </div>
                <div>
                  <strong>{{
                    selectedNode ? countItems(selectedNode, 'file') : personalDocumentCount
                  }}</strong
                  ><span>文档</span>
                </div>
                <div>
                  <strong>{{ myKbs.length + joinedKbs.length }}</strong
                  ><span>团队空间</span>
                </div>
              </div>
              <div class="overview-tips">
                <strong>快速开始</strong>
                <button type="button" @click="handleUploadToCurrent">
                  <el-icon><Upload /></el-icon>上传教学资料
                </button>
                <button type="button" @click="handleAddToCurrent">
                  <el-icon><FolderAdd /></el-icon>创建课程文件夹
                </button>
              </div>
            </div>
          </template>
        </aside>
      </main>
    </div>

    <!-- 右键菜单 -->
    <div
      v-show="contextMenu.visible"
      class="context-menu"
      :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
      @click.stop
    >
      <div
        v-if="contextMenu.node?.type === 'folder'"
        class="context-menu-item"
        @click="contextUploadFile"
      >
        <el-icon><Upload /></el-icon>上传文件到此
      </div>
      <div
        v-if="contextMenu.node?.type === 'folder'"
        class="context-menu-item"
        @click="contextAddFolder"
      >
        <el-icon><FolderAdd /></el-icon>新建子文件夹
      </div>
      <div class="context-menu-item" @click="contextRename">
        <el-icon><Edit /></el-icon>重命名
      </div>
      <el-divider class="context-divider" />
      <div class="context-menu-item danger" @click="contextDelete">
        <el-icon><Delete /></el-icon>删除
      </div>
    </div>

    <!-- 新建文件夹 -->
    <el-dialog
      v-model="addDialog.visible"
      title="新建文件夹"
      width="360px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-input
        v-model="addDialog.name"
        placeholder="请输入文件夹名称"
        maxlength="50"
        show-word-limit
        @keydown.enter="confirmAddFolder"
      />
      <template #footer
        ><el-button @click="addDialog.visible = false">取消</el-button
        ><el-button type="primary" :loading="addDialog.loading" @click="confirmAddFolder"
          >确定</el-button
        ></template
      >
    </el-dialog>

    <!-- 新建知识库 -->
    <el-dialog
      v-model="showCreateKbDialog"
      title="新建共享知识库"
      width="400px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form label-position="top">
        <el-form-item label="知识库名称"
          ><el-input v-model="createKbForm.name" placeholder="例如：高一数学备课组" maxlength="100"
        /></el-form-item>
        <el-form-item label="简介（选填）"
          ><el-input v-model="createKbForm.description" type="textarea" :rows="3" maxlength="500"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="showCreateKbDialog = false">取消</el-button
        ><el-button type="primary" :loading="createKbLoading" @click="confirmCreateKb"
          >创建</el-button
        ></template
      >
    </el-dialog>

    <!-- 知识库设置 -->
    <el-dialog
      v-model="showKbSettings"
      :title="'设置 - ' + (settingsKb?.name || '')"
      width="500px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-tabs>
        <el-tab-pane label="基本信息">
          <el-form label-position="top">
            <el-form-item label="名称"
              ><el-input v-model="settingsForm.name" maxlength="100"
            /></el-form-item>
            <el-form-item label="简介"
              ><el-input
                v-model="settingsForm.description"
                type="textarea"
                :rows="3"
                maxlength="500"
            /></el-form-item>
          </el-form>
          <el-button
            type="primary"
            size="small"
            :loading="settingsSaveLoading"
            @click="saveKbSettings"
            >保存</el-button
          >
          <el-divider />
          <el-button type="danger" size="small" plain @click="confirmDeleteKb"
            >解散知识库</el-button
          >
        </el-tab-pane>
        <el-tab-pane label="邀请">
          <p style="color: #888; font-size: 13px; margin-bottom: 8px">
            分享下面的链接，其他人可以加入此知识库
          </p>
          <el-input v-model="inviteLink" readonly>
            <template #append><el-button @click="copyInviteLink">复制</el-button></template>
          </el-input>
          <el-button size="small" style="margin-top: 8px" @click="regenerateInvite"
            >重新生成链接</el-button
          >
        </el-tab-pane>
        <el-tab-pane label="成员">
          <div v-if="members.length === 0" style="color: #666; padding: 12px">加载中...</div>
          <div v-for="m in members" :key="m.userId || m.user_id" class="member-row">
            <div class="member-info">
              <span class="member-name">{{ m.username }}</span>
              <el-tag
                size="small"
                :type="m.role === 'owner' ? 'warning' : m.role === 'admin' ? 'primary' : 'info'"
                >{{ memberRoleLabel(m.role) }}</el-tag
              >
            </div>
            <el-button
              v-if="m.role !== 'owner'"
              link
              type="danger"
              size="small"
              @click="removeMember(m)"
              >移除</el-button
            >
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 加入 -->
    <el-dialog
      v-model="showJoinDialog"
      title="加入共享知识库"
      width="400px"
      :close-on-click-modal="false"
      append-to-body
    >
      <p style="color: #888; font-size: 13px; margin-bottom: 12px">输入邀请链接或邀请码</p>
      <el-input
        v-model="joinToken"
        placeholder="粘贴邀请链接或输入token"
        @keydown.enter="confirmJoin"
      />
      <template #footer
        ><el-button @click="showJoinDialog = false">取消</el-button
        ><el-button type="primary" :loading="joinLoading" @click="confirmJoin"
          >加入</el-button
        ></template
      >
    </el-dialog>

    <!-- 上传文件 -->
    <el-dialog
      v-model="uploadVisible"
      title="上传文件"
      width="480px"
      :close-on-click-modal="false"
      append-to-body
      @closed="uploadFileList = []"
    >
      <div class="upload-target">
        <span class="upload-target-label">上传到：</span>
        <el-select
          v-model="uploadKbId"
          size="small"
          style="width: 180px"
          placeholder="个人空间"
          @change="uploadParentNode = null"
        >
          <el-option :value="null" label="我的知识库" />
          <el-option
            v-for="kb in [...myKbs, ...joinedKbs]"
            :key="kb.id"
            :value="kb.id"
            :label="kb.name"
          />
        </el-select>
        <span v-if="uploadParentNode" class="upload-folder-path">
          / {{ uploadParentNode.label }}
        </span>
      </div>
      <el-upload
        ref="uploadRef"
        v-model:file-list="uploadFileList"
        :auto-upload="false"
        drag
        multiple
        accept=".txt,.md,.pdf,.doc,.docx,.ppt,.pptx"
        :on-change="handleUploadChange"
        class="kb-upload"
      >
        <el-icon class="el-icon--upload" :size="40"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
        <template #tip
          ><div class="el-upload__tip">支持 .txt .md .pdf .doc .docx .ppt .pptx</div></template
        >
      </el-upload>
      <template #footer
        ><el-button @click="uploadVisible = false">取消</el-button
        ><el-button
          type="primary"
          :loading="uploadLoading"
          :disabled="uploadFileList.length === 0"
          @click="submitUpload"
          >上传 {{ uploadFileList.length ? `${uploadFileList.length} 个文件` : '' }}</el-button
        ></template
      >
    </el-dialog>

    <!-- 教学材料详情 -->
    <el-drawer
      v-model="showDraftDetail"
      class="teaching-detail-drawer"
      size="min(760px, 94vw)"
      :with-header="false"
      append-to-body
      destroy-on-close
      @closed="draftDetail = null"
    >
      <div v-if="draftDetail" class="teaching-detail">
        <header class="detail-drawer-header">
          <div>
            <span class="detail-eyebrow">教学材料详情</span>
            <h2>{{ draftDetail.type === 'preview' ? '预习材料' : '课堂试题' }}</h2>
          </div>
          <el-button
            class="detail-close"
            text
            circle
            aria-label="关闭详情"
            @click="showDraftDetail = false"
          >
            <el-icon><Close /></el-icon>
          </el-button>
        </header>

        <main class="detail-drawer-content">
          <div class="detail-source-row">
            <el-icon><Document /></el-icon>
            <span>来源文件</span>
            <strong>{{ selectedNode?.label || '当前教学材料' }}</strong>
            <span v-if="draftDetail.data.createdAt" class="detail-created-at">
              {{ formatUpdatedAt(draftDetail.data.createdAt) }}
            </span>
          </div>

          <template v-if="draftDetail.type === 'quiz'">
            <div class="detail-tag-row">
              <el-tag effect="light" type="primary">
                {{ typeLabelZh(draftDetail.data.quizType || draftDetail.data.type) }}
              </el-tag>
              <el-tag v-if="draftDetail.data.difficulty" effect="plain" type="warning">
                {{ diffLabel(draftDetail.data.difficulty) }}
              </el-tag>
              <el-tag :type="draftStatusType(draftDetail.data)" effect="plain">
                {{ draftStatusLabel(draftDetail.data) }}
              </el-tag>
            </div>

            <section class="question-heading">
              <span class="question-sequence">第 {{ quizDisplayPosition }} 题</span>
              <h3>{{ draftDetail.data.question || draftDetail.data.title }}</h3>
            </section>

            <div v-if="draftDetail.data.knowledgePoint" class="knowledge-focus">
              <span>考查知识点</span>
              <strong>{{ draftDetail.data.knowledgePoint }}</strong>
            </div>

            <section v-if="draftDetail.data.options?.length" class="detail-section">
              <div class="detail-section-title">
                <span>题目选项</span>
                <small>正确选项已标记</small>
              </div>
              <div class="detail-option-list">
                <div
                  v-for="option in draftDetail.data.options"
                  :key="option.key"
                  class="detail-option"
                  :class="{ correct: isCorrectOption(draftDetail.data, option.key) }"
                >
                  <span class="detail-option-key">{{ option.key }}</span>
                  <span class="detail-option-text">{{ option.text }}</span>
                  <span v-if="isCorrectOption(draftDetail.data, option.key)" class="correct-mark">
                    <el-icon><CircleCheckFilled /></el-icon>正确答案
                  </span>
                </div>
              </div>
            </section>

            <section class="answer-panel">
              <div class="answer-panel-icon">
                <el-icon><CircleCheckFilled /></el-icon>
              </div>
              <div>
                <span>参考答案</span>
                <strong>{{ draftDetail.data.correctKey || '暂未设置参考答案' }}</strong>
                <p v-if="correctOptionText">{{ correctOptionText }}</p>
              </div>
            </section>

            <section class="detail-meta-grid">
              <div class="detail-meta-card">
                <el-icon><Clock /></el-icon>
                <span>建议作答时间</span>
                <strong>{{ formatTimeLimit(draftDetail.data.timeLimit) }}</strong>
              </div>
              <div class="detail-meta-card">
                <el-icon><Star /></el-icon>
                <span>题目状态</span>
                <strong>{{ draftStatusLabel(draftDetail.data) }}</strong>
              </div>
            </section>
          </template>

          <template v-else>
            <div class="detail-tag-row">
              <el-tag type="warning" effect="light">预习材料</el-tag>
              <el-tag :type="draftStatusType(draftDetail.data)" effect="plain">
                {{ draftStatusLabel(draftDetail.data) }}
              </el-tag>
              <el-tag v-if="draftDetail.data.questions?.length" type="info" effect="plain">
                {{ draftDetail.data.questions.length }} 道自测题
              </el-tag>
            </div>

            <section class="question-heading preview-heading">
              <span class="question-sequence">课前导学</span>
              <h3>{{ draftDetail.data.title || draftDetail.data.topic || '预习材料' }}</h3>
            </section>

            <div
              v-if="draftDetail.data.knowledgePoint || draftDetail.data.topic"
              class="knowledge-focus"
            >
              <span>核心知识点</span>
              <strong>{{ draftDetail.data.knowledgePoint || draftDetail.data.topic }}</strong>
            </div>

            <section v-if="draftDetail.data.guideText" class="detail-section guide-section">
              <div class="detail-section-title">
                <span>预习导读</span>
                <small>课前快速了解重点内容</small>
              </div>
              <div class="detail-guide" v-html="renderMarkdown(draftDetail.data.guideText)"></div>
            </section>

            <section v-if="draftDetail.data.discussionQuestion" class="discussion-callout">
              <span class="discussion-label">课堂讨论</span>
              <p>{{ draftDetail.data.discussionQuestion }}</p>
            </section>

            <section v-if="draftDetail.data.questions?.length" class="detail-section">
              <div class="detail-section-title">
                <span>配套自测</span>
                <small>共 {{ draftDetail.data.questions.length }} 题</small>
              </div>
              <div class="self-test-list">
                <article
                  v-for="(question, index) in draftDetail.data.questions"
                  :key="`${question.question || question.title}-${index}`"
                  class="self-test-card"
                >
                  <div class="self-test-number">{{ index + 1 }}</div>
                  <div class="self-test-content">
                    <h4>{{ question.question || question.title }}</h4>
                    <div v-if="question.options?.length" class="self-test-options">
                      <span
                        v-for="option in question.options"
                        :key="option.key"
                        :class="{ correct: isCorrectOption(question, option.key) }"
                      >
                        {{ option.key }}. {{ option.text }}
                      </span>
                    </div>
                    <div v-if="question.correctKey" class="self-test-answer">
                      参考答案：{{ question.correctKey }}
                    </div>
                    <p v-if="question.explanation" class="self-test-explanation">
                      {{ question.explanation }}
                    </p>
                  </div>
                </article>
              </div>
            </section>
          </template>
        </main>

        <footer class="detail-drawer-footer">
          <template v-if="draftDetail.type === 'quiz'">
            <el-button :disabled="currentQuizIndex <= 0" @click="navigateQuiz(-1)">
              <el-icon><ArrowLeft /></el-icon>上一题
            </el-button>
            <span>第 {{ quizDisplayPosition }} / {{ materials.questions.length }} 题</span>
            <el-button
              :disabled="currentQuizIndex < 0 || currentQuizIndex >= materials.questions.length - 1"
              @click="navigateQuiz(1)"
            >
              下一题<el-icon><ArrowRight /></el-icon>
            </el-button>
          </template>
          <template v-else>
            <span>预习材料阅读预览</span>
            <el-button type="primary" @click="showDraftDetail = false">完成查看</el-button>
          </template>
        </footer>
      </div>
    </el-drawer>

    <!-- AI 生成教学材料 -->
    <el-dialog
      v-model="gen.visible"
      title="🤖 AI 生成教学材料"
      width="700px"
      :close-on-click-modal="false"
      append-to-body
      @closed="resetGen"
    >
      <!-- 生成前 -->
      <div v-if="!gen.result" class="gen-step">
        <p style="color: #666; margin-bottom: 12px">
          将根据 PPT
          内容自动生成预习作业和课堂试题。生成后可在面板中<strong>编辑</strong>调整，再选择班级发布。
        </p>

        <!-- PPT 解析内容预览 -->
        <el-collapse v-if="selectedNode?.content" style="margin-bottom: 12px">
          <el-collapse-item title="📄 查看 PPT 解析内容">
            <div
              style="
                max-height: 200px;
                overflow-y: auto;
                font-size: 12px;
                line-height: 1.6;
                white-space: pre-wrap;
                background: #f5f7fa;
                padding: 10px;
                border-radius: 4px;
                color: #606266;
              "
            >
              {{ selectedNode.content }}
            </div>
          </el-collapse-item>
        </el-collapse>

        <el-form label-width="80px">
          <el-form-item label="PPT 文件"
            ><span style="color: #409eff">{{ selectedNode?.label }}</span></el-form-item
          >
        </el-form>

        <div
          v-if="gen.error"
          style="
            text-align: center;
            padding: 12px;
            background: #fef0f0;
            border-radius: 6px;
            margin-bottom: 12px;
          "
        >
          <p style="color: #f56c6c; margin: 0 0 8px">❌ {{ gen.error }}</p>
          <el-button size="small" type="primary" @click="doGenerate">🔄 重新生成</el-button>
        </div>

        <div style="text-align: center; padding: 12px">
          <el-button type="primary" size="large" :loading="gen.loading" @click="doGenerate">
            🤖 {{ gen.loading ? 'AI 生成中...' : '开始生成教学材料' }}
          </el-button>
        </div>

        <div v-if="gen.loading" style="text-align: center; padding: 16px; color: #909399">
          <el-icon class="is-loading" :size="20"><Loading /></el-icon> {{ gen.stage }}
        </div>
      </div>

      <!-- 生成结果 -->
      <div v-else class="gen-result">
        <el-tabs>
          <!-- 预习作业 Tab -->
          <el-tab-pane label="📖 预习作业">
            <template v-if="gen.result.preview">
              <div class="gen-preview-section">
                <!-- 知识点/主题 可编辑 -->
                <el-form label-width="70px" size="small">
                  <el-form-item label="知识点">
                    <el-input v-model="genEdit.previewTopic" placeholder="知识点主题" />
                  </el-form-item>
                  <el-form-item label="导读材料">
                    <el-input
                      v-model="genEdit.previewGuide"
                      type="textarea"
                      :rows="8"
                      placeholder="课前导读内容（Markdown）"
                    />
                  </el-form-item>
                </el-form>

                <!-- 预览导读 -->
                <details style="margin-top: 4px">
                  <summary style="font-size: 13px; color: #409eff; cursor: pointer">
                    📋 预览渲染效果
                  </summary>
                  <div
                    class="gen-guide"
                    style="margin-top: 8px; padding: 8px; background: #fafafa; border-radius: 4px"
                    v-html="renderMarkdown(genEdit.previewGuide)"
                  ></div>
                </details>

                <div v-if="gen.result.preview.questions?.length" style="margin-top: 12px">
                  <p>
                    <strong>预习自测题 ({{ gen.result.preview.questions.length }}道)：</strong>
                  </p>
                  <div
                    v-for="(q, i) in gen.result.preview.questions"
                    :key="i"
                    class="gen-quiz-card"
                  >
                    <el-tag size="small" :type="q.type === 'CHOICE' ? 'primary' : 'success'">{{
                      q.type === 'CHOICE' ? '选择题' : '简答题'
                    }}</el-tag>
                    <p>{{ q.question }}</p>
                    <div
                      v-if="q.options?.length"
                      style="margin: 4px 0; font-size: 13px; color: #666"
                    >
                      <span v-for="o in q.options" :key="o.key" style="margin-right: 12px"
                        >{{ o.key }}. {{ o.text }}</span
                      >
                    </div>
                    <p style="font-size: 12px; color: #67c23a">答案: {{ q.correctKey }}</p>
                  </div>
                </div>

                <el-form size="small" style="margin-top: 8px">
                  <el-form-item label="讨论题">
                    <el-input
                      v-model="genEdit.previewDiscussion"
                      type="textarea"
                      :rows="2"
                      placeholder="课堂讨论题"
                    />
                  </el-form-item>
                </el-form>

                <!-- 发布区：选择班级 -->
                <div
                  style="margin-top: 12px; padding: 10px; background: #f0f9eb; border-radius: 6px"
                >
                  <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap">
                    <span style="font-size: 13px; color: #606266">发布到班级：</span>
                    <el-select
                      v-model="gen.publishClassId"
                      placeholder="选择班级"
                      size="small"
                      style="width: 180px"
                    >
                      <el-option v-for="c in classList" :key="c.id" :label="c.name" :value="c.id" />
                    </el-select>
                    <el-button
                      size="small"
                      type="primary"
                      :loading="gen.savingPreview"
                      :disabled="!gen.publishClassId"
                      @click="savePreview"
                      >📢 发布预习作业</el-button
                    >
                    <span
                      v-if="gen.result.preview.published"
                      style="color: #67c23a; font-size: 13px"
                      >✅ 已发布</span
                    >
                  </div>
                </div>
              </div>
            </template>
            <div v-else style="padding: 20px; text-align: center; color: #f56c6c">
              ⚠️ {{ gen.result.previewError || '预习作业生成失败' }}
            </div>
          </el-tab-pane>

          <!-- 课堂试题 Tab -->
          <el-tab-pane label="✏️ 课堂试题 ({{ gen.result.quizzes?.length || 0 }})">
            <div class="gen-preview-section" v-if="gen.result.quizzes?.length">
              <div v-for="(q, i) in gen.result.quizzes" :key="i" class="gen-quiz-card">
                <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px">
                  <el-tag
                    size="small"
                    :type="
                      q.type === 'CHOICE' ? 'primary' : q.type === 'OPEN' ? 'success' : 'warning'
                    "
                    >{{ typeLabel(q.type) }}</el-tag
                  >
                  <el-tag
                    size="small"
                    :type="
                      q.difficulty === 'easy' ? 'success' : q.difficulty === 'hard' ? 'danger' : ''
                    "
                    >{{ diffLabel(q.difficulty || '') }}</el-tag
                  >
                  <span style="margin-left: auto; font-size: 12px; color: #909399">{{
                    q.knowledgePoint
                  }}</span>
                </div>
                <p>{{ q.title }}</p>
                <div v-if="q.options?.length" style="font-size: 13px; color: #666">
                  <span v-for="o in q.options" :key="o.key" style="margin-right: 12px"
                    >{{ o.key }}. {{ o.text }}</span
                  >
                </div>
                <p style="font-size: 12px; color: #67c23a">
                  答案: {{ q.correctKey }} | 限时: {{ q.timeLimit || '-' }}s
                </p>
                <div style="margin-top: 4px; display: flex; align-items: center; gap: 8px">
                  <el-button size="small" :loading="gen.savingQuiz === i" @click="saveQuiz(i)"
                    >💾 保存到题库</el-button
                  >
                  <span v-if="q.published" style="color: #67c23a; font-size: 12px">✅ 已保存</span>
                </div>
              </div>
            </div>
            <div v-else style="padding: 20px; text-align: center; color: #f56c6c">
              ⚠️ {{ gen.result.quizError || '暂无试题数据' }}
            </div>
          </el-tab-pane>
        </el-tabs>
        <div
          style="
            text-align: center;
            margin-top: 12px;
            display: flex;
            gap: 8px;
            justify-content: center;
          "
        >
          <el-button @click="resetGen">关闭</el-button>
          <el-button type="warning" :loading="gen.loading" @click="doGenerate"
            >🔄 重新生成</el-button
          >
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onMounted, onBeforeUnmount, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  FolderOpened,
  Document,
  FolderAdd,
  Upload,
  UploadFilled,
  Edit,
  Delete,
  Loading,
  Share,
  Link,
  Setting,
  Star,
  Clock,
  Close,
  CircleCheckFilled,
  ArrowLeft,
  ArrowRight,
  MagicStick,
} from '@element-plus/icons-vue'
import type { AllowDropType, ElTree, UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import request from '@/shared/api/request'
import { getApiErrorMessage } from '@/shared/api/errors'
import { renderMarkdown } from '@/shared/utils/safeHtml'
import {
  buildTree,
  countTreeItems,
  fileType,
  findNodePath,
  formatUpdatedAt,
  resourceStatus,
  resourceStatusType,
} from '@/features/knowledge/model/tree'
import {
  countItems,
  diffLabel,
  draftStatusLabel,
  draftStatusType,
  formatTimeLimit,
  isCorrectOption,
  isPptFile,
  memberRoleLabel,
  typeLabel,
  typeLabelZh,
} from '@/features/knowledge/model/presentation'
import type {
  DraftDetail,
  GeneratedPreview,
  GeneratedQuestion,
  GenResult,
  KbMember,
  SharedKb,
  TeachingMaterials,
  TreeNode,
} from '@/features/knowledge/model/types'

/* ===== State ===== */
const treeRef = ref<InstanceType<typeof ElTree>>()
const searchQuery = ref('')
const myTreeData = ref<TreeNode[]>([])
const selectedNode = ref<TreeNode | null>(null)
const activeSpaceKbId = ref<number | null>(null)
const previewTab = ref('content')
const loading = ref(true)
const expandedKbs = ref(new Set<number>())
const kbTreeCache = ref(new Map<number, TreeNode[]>())
const kbLoading = ref(new Map<number, boolean>())
const myKbs = ref<SharedKb[]>([])
const joinedKbs = ref<SharedKb[]>([])
const treeProps = { children: 'children', label: 'label' }

const currentRoots = computed(() =>
  activeSpaceKbId.value == null
    ? myTreeData.value
    : (kbTreeCache.value.get(activeSpaceKbId.value) ?? []),
)
const selectedSpaceName = computed(() => {
  if (activeSpaceKbId.value == null) return '个人空间'
  return (
    [...myKbs.value, ...joinedKbs.value].find((kb) => kb.id === activeSpaceKbId.value)?.name ??
    '团队知识库'
  )
})
const selectedPath = computed(() =>
  selectedNode.value ? findNodePath(currentRoots.value, selectedNode.value.id) : [],
)
const currentPath = computed(() =>
  selectedNode.value?.type === 'file' ? selectedPath.value.slice(0, -1) : selectedPath.value,
)
const currentFolder = computed<TreeNode | null>(() => {
  if (selectedNode.value?.type === 'folder') return selectedNode.value
  const path = selectedPath.value
  return path.length > 1 ? (path[path.length - 2] ?? null) : null
})
const currentItems = computed(() => {
  const items = currentFolder.value?.children ?? currentRoots.value
  return [...items].sort((a, b) => {
    if (a.type !== b.type) return a.type === 'folder' ? -1 : 1
    return a.label.localeCompare(b.label, 'zh-CN')
  })
})
const visibleCurrentItems = computed(() => {
  const query = searchQuery.value.trim().toLowerCase()
  return query
    ? currentItems.value.filter((item) => item.label.toLowerCase().includes(query))
    : currentItems.value
})
const currentDirectoryTitle = computed(() => currentFolder.value?.label ?? selectedSpaceName.value)
const personalDocumentCount = computed(() => countTreeItems(myTreeData.value, 'file'))
const personalFolderCount = computed(() => countTreeItems(myTreeData.value, 'folder'))

function restoreSelection(nodeId: number | undefined, kbId: number | null) {
  if (nodeId == null) return
  const roots = kbId == null ? myTreeData.value : (kbTreeCache.value.get(kbId) ?? [])
  const path = findNodePath(roots, nodeId)
  if (path.length) selectedNode.value = path[path.length - 1] ?? null
}

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

async function refreshKbTree(kbId: number) {
  if (!expandedKbs.value.has(kbId)) return
  try {
    const res = await request.get(`/documents/directory/tree?kbId=${kbId}`)
    kbTreeCache.value.set(kbId, buildTree(res.data))
  } catch {}
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
      } catch {
        kbTreeCache.value.set(kbId, [])
      } finally {
        kbLoading.value.set(kbId, false)
      }
    }
  }
}

async function selectKnowledgeBase(kbId: number) {
  activeSpaceKbId.value = kbId
  selectedNode.value = null
  previewTab.value = 'content'
  if (!expandedKbs.value.has(kbId)) await toggleKbExpand(kbId)
}

/* ===== Init ===== */
onMounted(async () => {
  try {
    await Promise.all([fetchMyKbs(), fetchJoinedKbs(), fetchTree()])
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '加载失败'))
  } finally {
    loading.value = false
  }
})

/* ===== Rename ===== */
const renamingNode = ref<TreeNode | null>(null)
const renameValue = ref('')
function handleDoubleClick(data: TreeNode) {
  if (data.type !== 'folder') return
  startRename(data)
}
function startRename(node: TreeNode) {
  renamingNode.value = node
  renameValue.value = node.label
  nextTick(() => {
    const input = document.querySelector<HTMLInputElement>('.custom-tree-node .el-input__inner')
    input?.focus()
    input?.select()
  })
}
async function confirmRename(data: TreeNode) {
  const name = renameValue.value.trim()
  renamingNode.value = null
  if (!name || name === data.label) return
  try {
    await request.put(`/documents/directory/${data.id}/rename`, { label: name })
    data.label = name
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '重命名失败'))
    await fetchTree()
  }
}
function cancelRename() {
  renamingNode.value = null
}

/* ===== Context Menu ===== */
const contextMenu = reactive<{ visible: boolean; x: number; y: number; node: TreeNode | null }>({
  visible: false,
  x: 0,
  y: 0,
  node: null,
})
function handleContextMenu(event: MouseEvent, data: TreeNode) {
  event.preventDefault()
  Object.assign(contextMenu, { visible: true, x: event.clientX, y: event.clientY, node: data })
}
function openNodeMenu(event: MouseEvent, data: TreeNode) {
  handleContextMenu(event, data)
}
function hideContextMenu() {
  contextMenu.visible = false
  contextMenu.node = null
}
function onDocumentClick() {
  if (contextMenu.visible) hideContextMenu()
}
onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))

function contextUploadFile() {
  const n = contextMenu.node
  hideContextMenu()
  if (!n || n.type !== 'folder') return
  uploadParentNode.value = n
  uploadKbId.value = n.kbId ?? null
  uploadFileList.value = []
  uploadVisible.value = true
}
function contextAddFolder() {
  addDialog.parentNode = contextMenu.node
  addDialog.kbId = contextMenu.node?.kbId ?? activeSpaceKbId.value
  hideContextMenu()
  addDialog.name = ''
  addDialog.visible = true
}
async function contextRename() {
  const n = contextMenu.node
  hideContextMenu()
  if (!n) return
  try {
    const { value } = await ElMessageBox.prompt('请输入新的名称', '重命名', {
      inputValue: n.label,
      inputValidator: (input) => Boolean(input?.trim()) || '名称不能为空',
    })
    const name = value.trim()
    if (name === n.label) return
    await request.put(`/documents/directory/${n.id}/rename`, { label: name })
    n.label = name
    ElMessage.success('已重命名')
  } catch (error: unknown) {
    if (error !== 'cancel') ElMessage.error(getApiErrorMessage(error, '重命名失败'))
  }
}
async function contextDelete() {
  const n = contextMenu.node
  hideContextMenu()
  if (!n) return
  try {
    await ElMessageBox.confirm(
      n.type === 'folder' ? `确定删除「${n.label}」及其所有内容？` : `确定删除「${n.label}」？`,
      '确认',
      { type: 'warning' },
    )
    await request.delete(`/documents/directory/${n.id}`)
    if (selectedNode.value?.id === n.id) selectedNode.value = null
    if (n.kbId != null) {
      kbTreeCache.value.delete(n.kbId)
      await refreshKbTree(n.kbId)
    }
    await fetchTree()
    ElMessage.success('已删除')
  } catch (error: unknown) {
    if (error !== 'cancel') ElMessage.error(getApiErrorMessage(error, '删除失败'))
  }
}

/* ===== Folder Dialog ===== */
const addDialog = reactive({
  visible: false,
  name: '',
  loading: false,
  parentNode: null as TreeNode | null,
  kbId: null as number | null,
})
function handleCreateCommand(command: string) {
  if (command === 'folder') handleAddToCurrent()
  if (command === 'knowledge-base') showCreateKbDialog.value = true
  if (command === 'join') showJoinDialog.value = true
}
function handleAddToCurrent() {
  addDialog.parentNode = currentFolder.value
  addDialog.kbId = activeSpaceKbId.value
  addDialog.name = ''
  addDialog.visible = true
}
async function confirmAddFolder() {
  const name = addDialog.name.trim()
  if (!name) return
  addDialog.loading = true
  try {
    const kbId = addDialog.parentNode?.kbId ?? addDialog.kbId
    const parentId = addDialog.parentNode?.id
    const body: { label: string; kbId: number | null; parentId?: number } = { label: name, kbId }
    if (addDialog.parentNode) body.parentId = addDialog.parentNode.id
    await request.post(`/documents/directory/folder`, body)
    if (kbId != null) {
      kbTreeCache.value.delete(kbId)
      await refreshKbTree(kbId)
    }
    await fetchTree()
    restoreSelection(parentId, kbId)
    addDialog.visible = false
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '创建文件夹失败'))
  } finally {
    addDialog.loading = false
  }
}

/* ===== Create KB ===== */
const showCreateKbDialog = ref(false)
const createKbLoading = ref(false)
const createKbForm = reactive({ name: '', description: '' })
async function confirmCreateKb() {
  if (!createKbForm.name.trim()) {
    ElMessage.warning('请输入知识库名称')
    return
  }
  createKbLoading.value = true
  try {
    await request.post(`/shared-kb/create`, createKbForm)
    showCreateKbDialog.value = false
    createKbForm.name = ''
    createKbForm.description = ''
    await fetchMyKbs()
    ElMessage.success('创建成功')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '创建知识库失败'))
  } finally {
    createKbLoading.value = false
  }
}

/* ===== KB Settings ===== */
const showKbSettings = ref(false)
const settingsKb = ref<SharedKb | null>(null)
const settingsForm = reactive({ name: '', description: '' })
const settingsSaveLoading = ref(false)
const members = ref<KbMember[]>([])
const inviteLink = ref('')

async function openKbSettings(kb: SharedKb) {
  settingsKb.value = kb
  settingsForm.name = kb.name
  settingsForm.description = kb.description
  showKbSettings.value = true
  inviteLink.value = `${window.location.origin}/invite?token=${kb.inviteToken || ''}`
  try {
    const r = await request.get(`/shared-kb/${kb.id}/members`)
    members.value = r.data
  } catch {}
}
async function saveKbSettings() {
  if (!settingsKb.value) return
  settingsSaveLoading.value = true
  try {
    await request.put(`/shared-kb/${settingsKb.value.id}`, {
      name: settingsForm.name,
      description: settingsForm.description,
    })
    ElMessage.success('已保存')
    await fetchMyKbs()
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '保存失败'))
  } finally {
    settingsSaveLoading.value = false
  }
}
async function regenerateInvite() {
  if (!settingsKb.value) return
  try {
    const r = await request.post(`/shared-kb/${settingsKb.value.id}/invite`, {})
    const d = r.data
    inviteLink.value = `${window.location.origin}/invite?token=${d.token}`
    settingsKb.value!.inviteToken = d.token
    ElMessage.success('已重新生成')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '生成邀请链接失败'))
  }
}
function copyInviteLink() {
  navigator.clipboard.writeText(inviteLink.value)
  ElMessage.success('已复制')
}
async function removeMember(member: KbMember) {
  if (!settingsKb.value) return
  const targetId = member.userId ?? member.user_id
  if (!targetId) return
  try {
    await request.delete(`/shared-kb/${settingsKb.value.id}/members/${targetId}`)
    members.value = members.value.filter((m) => (m.userId || m.user_id) !== targetId)
    ElMessage.success('已移除')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '移除成员失败'))
  }
}
async function confirmDeleteKb() {
  if (!settingsKb.value) return
  try {
    await ElMessageBox.confirm(
      `确定解散「${settingsKb.value.name}」？所有文件将被删除且不可恢复。`,
      '确认解散',
      { type: 'warning', confirmButtonClass: 'el-button--danger' },
    )
    await request.delete(`/shared-kb/${settingsKb.value.id}`)
    showKbSettings.value = false
    await fetchMyKbs()
    ElMessage.success('已解散')
  } catch {}
}

/* ===== Join KB ===== */
const showJoinDialog = ref(false)
const joinToken = ref('')
const joinLoading = ref(false)
async function confirmJoin() {
  let token = joinToken.value.trim()
  if (!token) {
    ElMessage.warning('请输入邀请链接或token')
    return
  }
  // 提取 token：如果贴的是完整链接，从 ?token= 后面取
  const m = token.match(/[?&]token=([^&]+)/)
  if (m?.[1]) token = m[1]
  joinLoading.value = true
  try {
    await request.post(`/shared-kb/join?token=${encodeURIComponent(token)}`)
    showJoinDialog.value = false
    joinToken.value = ''
    await Promise.all([fetchMyKbs(), fetchJoinedKbs()])
    ElMessage.success('加入成功')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '加入失败'))
  } finally {
    joinLoading.value = false
  }
}

/* ===== Upload ===== */
const uploadVisible = ref(false)
const uploadLoading = ref(false)
const uploadParentNode = ref<TreeNode | null>(null)
const uploadFileList = ref<UploadUserFile[]>([])
const uploadKbId = ref<number | null>(null)
function handleUploadToCurrent() {
  uploadParentNode.value = currentFolder.value
  uploadFileList.value = []
  uploadKbId.value = activeSpaceKbId.value
  uploadVisible.value = true
}
function openUploadForFolder(folder: TreeNode) {
  uploadParentNode.value = folder
  uploadFileList.value = []
  uploadKbId.value = folder.kbId ?? activeSpaceKbId.value
  uploadVisible.value = true
}
function handleUploadChange(_uploadFile: UploadFile, uploadFiles: UploadFiles) {
  uploadFileList.value = uploadFiles
}
async function submitUpload() {
  if (uploadFileList.value.length === 0) return
  uploadLoading.value = true
  const targetFolderId = uploadParentNode.value?.id
  const targetKbId = uploadKbId.value
  let ok = 0
  try {
    for (const f of uploadFileList.value) {
      if (!f.raw) continue
      const fd = new FormData()
      fd.append('file', f.raw)
      if (uploadParentNode.value) fd.append('parentNodeId', String(uploadParentNode.value.id))
      if (uploadKbId.value != null) fd.append('kbId', String(uploadKbId.value))
      await request.post(`/documents/upload`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      ok++
    }
    await fetchTree()
    if (uploadKbId.value != null) {
      kbTreeCache.value.delete(uploadKbId.value)
      await refreshKbTree(uploadKbId.value)
    }
    restoreSelection(targetFolderId, targetKbId)
    uploadVisible.value = false
    ElMessage.success(`成功上传 ${ok} 个文件`)
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '上传失败'))
  } finally {
    uploadLoading.value = false
  }
}

/* ===== Drag ===== */
function allowDrop(_draggingNode: unknown, dropNode: { data: TreeNode }, type: AllowDropType) {
  if (type === 'inner') return dropNode.data.type === 'folder'
  return true
}
async function handleNodeDrop(
  draggingNode: { data: TreeNode },
  dropNode: { data: TreeNode },
  type: AllowDropType,
) {
  const targetParentId = type === 'inner' ? dropNode.data.id : null
  try {
    await request.put(`/documents/directory/${draggingNode.data.id}/move`, { targetParentId })
    await fetchTree()
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '移动失败'))
    await fetchTree()
  }
}

/* ===== Search ===== */
function filterTree() {
  treeRef.value?.filter(searchQuery.value)
}
function filterNode(value: string, data: TreeNode) {
  if (!value) return true
  return data.label.toLowerCase().includes(value.toLowerCase())
}

/* ===== Click ===== */
function handleNodeClick(data: TreeNode) {
  if (findNodePath(myTreeData.value, data.id).length) activeSpaceKbId.value = null
  else if (data.kbId != null) activeSpaceKbId.value = data.kbId
  selectedNode.value = data
  previewTab.value = 'content'
  materials.value = { previews: [], questions: [] }
  if (data.type === 'file' && data.docId) {
    loadFileContent(data)
    loadMaterials(data.docId)
  }
}
function clearSelection() {
  selectedNode.value = null
  previewTab.value = 'content'
}
const contentCache = new Map<string, string>()
async function loadFileContent(node: TreeNode) {
  if (!node.docId) return
  const cached = contentCache.get(node.docId)
  if (cached !== undefined) {
    node.content = cached
    node.loadState = 'ready'
    return
  }
  node.loadState = 'loading'
  try {
    const r = await request.get(`/documents/${node.docId}/content`)
    if (typeof r.data === 'string') {
      node.content = r.data
      node.loadState = 'ready'
      contentCache.set(node.docId, r.data)
    } else {
      node.content = undefined
      node.loadState = 'processing'
    }
  } catch {
    node.content = undefined
    node.loadState = 'error'
  }
}

/* ===== AI 生成教学材料 ===== */
const gen = reactive({
  visible: false,
  loading: false,
  result: null as GenResult | null,
  stage: '', // 进度文本
  error: '', // 全量失败时的错误信息
  publishClassId: null as number | null, // 发布时选择的班级
  savingPreview: false,
  savingQuiz: null as number | null,
})
// 编辑中的预习作业内容
const genEdit = reactive({
  previewTopic: '',
  previewGuide: '',
  previewDiscussion: '',
})
const classList = ref<{ id: number; name: string }[]>([])

async function fetchClassList() {
  try {
    const r = await request.get('/dashboard/classes')
    classList.value = r.data?.data || []
  } catch {}
}
fetchClassList()

async function openGenerateDialog() {
  if (!selectedNode.value?.docId) {
    ElMessage.warning('请先选择一个文件')
    return
  }
  if (classList.value.length === 0) {
    await fetchClassList()
  }
  gen.visible = true
  gen.result = null
  gen.error = ''
  gen.publishClassId = null
  // 加载PPT解析内容（如果还没加载）
  if (!selectedNode.value.content && selectedNode.value.docId) {
    await loadFileContent(selectedNode.value)
  }
}

// 进度模拟定时器
let stageTimer: ReturnType<typeof setInterval> | null = null
const STAGES = [
  { at: 0, text: '正在读取 PPT 内容...' },
  { at: 2000, text: '正在 AI 生成预习作业和课堂试题...' },
  { at: 8000, text: 'AI 正在深入分析 PPT 内容，请耐心等待...' },
  { at: 20000, text: '内容较多，仍在处理中...' },
]

async function doGenerate() {
  if (!selectedNode.value?.docId) return
  gen.loading = true
  gen.result = null
  gen.error = ''
  gen.stage = STAGES[0]?.text ?? ''
  // 启动进度提示定时器
  const startTime = Date.now()
  stageTimer = setInterval(() => {
    const elapsed = Date.now() - startTime
    const stage = [...STAGES].reverse().find((s) => elapsed >= s.at)
    if (stage) gen.stage = stage.text
  }, 1000)
  try {
    const r = await request.post<GenResult>(
      '/documents/generate-materials',
      { docId: selectedNode.value.docId },
      { timeout: 180000 },
    )
    const result = r.data
    gen.result = result
    // 初始化编辑字段
    if (result.preview) {
      genEdit.previewTopic = result.preview.topic || ''
      genEdit.previewGuide = result.preview.guideText || ''
      genEdit.previewDiscussion = result.preview.discussionQuestion || ''
    }
    // 有部分错误时提示
    if (result.previewError) ElMessage.warning('预习作业: ' + result.previewError)
    if (result.quizError) ElMessage.warning('课堂试题: ' + result.quizError)
  } catch (error: unknown) {
    gen.error = getApiErrorMessage(error, '生成失败，请重试')
    ElMessage.error(gen.error)
  } finally {
    gen.loading = false
    if (stageTimer) {
      clearInterval(stageTimer)
      stageTimer = null
    }
  }
}

async function savePreview() {
  if (!gen.result?.preview || !gen.publishClassId) return
  gen.savingPreview = true
  try {
    // 使用编辑后的内容保存
    const payload = {
      ...gen.result.preview,
      topic: genEdit.previewTopic,
      guideText: genEdit.previewGuide,
      discussionQuestion: genEdit.previewDiscussion,
      classId: gen.publishClassId,
      docId: selectedNode.value?.docId,
    }
    await request.post('/documents/generate-materials/save-preview', payload)
    gen.result.preview.published = true
    ElMessage.success('预习作业已发布')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '保存失败'))
  } finally {
    gen.savingPreview = false
  }
}

async function saveQuiz(index: number) {
  if (!gen.result) return
  const quiz = gen.result.quizzes[index]
  if (!quiz) return
  gen.savingQuiz = index
  try {
    await request.post('/questions', {
      ...quiz,
      sourceDocId: selectedNode.value?.docId,
      aiGenerated: true,
      score: 10,
      uploadRequired: false,
    })
    quiz.published = true
    if (selectedNode.value?.docId) await loadMaterials(selectedNode.value.docId)
    ElMessage.success('试题已保存到题库')
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error, '保存失败'))
  } finally {
    gen.savingQuiz = null
  }
}

function resetGen() {
  gen.visible = false
  gen.result = null
  gen.loading = false
  gen.error = ''
  gen.stage = ''
  gen.publishClassId = null
  genEdit.previewTopic = ''
  genEdit.previewGuide = ''
  genEdit.previewDiscussion = ''
  if (stageTimer) {
    clearInterval(stageTimer)
    stageTimer = null
  }
}

/* ===== 教学材料 ===== */
const materials = ref<TeachingMaterials>({ previews: [], questions: [] })

async function loadMaterials(docId: string) {
  try {
    const r = await request.get(`/documents/${docId}/materials`)
    materials.value = r.data || { previews: [], questions: [] }
  } catch {
    materials.value = { previews: [], questions: [] }
  }
}

const draftDetail = ref<DraftDetail | null>(null)
const showDraftDetail = ref(false)

const currentQuizIndex = computed(() => {
  if (draftDetail.value?.type !== 'quiz') return -1
  const currentId = draftDetail.value.data.id
  if (currentId != null)
    return materials.value.questions.findIndex((question) => question.id === currentId)
  return materials.value.questions.findIndex((question) => question === draftDetail.value?.data)
})

const quizDisplayPosition = computed(() =>
  currentQuizIndex.value >= 0 ? currentQuizIndex.value + 1 : 1,
)

const correctOptionText = computed(() => {
  if (draftDetail.value?.type !== 'quiz') return ''
  const question = draftDetail.value.data
  const correctKey = question.correctKey?.trim().toUpperCase()
  if (!correctKey) return ''
  return (
    question.options?.find((option) => option.key.trim().toUpperCase() === correctKey)?.text ?? ''
  )
})

async function viewMaterial(type: 'preview' | 'quiz', item: GeneratedPreview | GeneratedQuestion) {
  if (type === 'preview') {
    try {
      const r = await request.get(`/preview/${item.id}`)
      draftDetail.value = { type: 'preview', data: r.data.data || r.data }
    } catch {
      draftDetail.value = { type: 'preview', data: item as GeneratedPreview }
    }
  } else {
    try {
      const response = await request.get(`/questions/${item.id}`)
      draftDetail.value = { type: 'quiz', data: response.data.data || response.data }
    } catch {
      draftDetail.value = { type: 'quiz', data: item as GeneratedQuestion }
    }
  }
  showDraftDetail.value = true
}

function navigateQuiz(offset: -1 | 1) {
  const targetIndex = currentQuizIndex.value + offset
  const target = materials.value.questions[targetIndex]
  if (!target) return
  draftDetail.value = { type: 'quiz', data: target }
}

async function deleteMaterial(type: string, id: number) {
  try {
    if (type === 'preview') {
      await request.delete(`/documents/materials/previews/${id}`)
      materials.value.previews = materials.value.previews.filter((preview) => preview.id !== id)
      ElMessage.success('预习材料已删除')
    } else {
      await request.delete(`/questions/${id}`)
      materials.value.questions = materials.value.questions.filter((question) => question.id !== id)
      ElMessage.success('题目已归档')
    }
  } catch {
    ElMessage.error('删除失败')
  }
}
</script>

<style scoped src="../styles/KnowledgeBase.css"></style>
