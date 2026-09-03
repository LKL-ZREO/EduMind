import { lazy, Suspense, useMemo, useState } from 'react'
import { Alert, App, Button, Form, Input, Modal, Spin } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import {
  directoryTreeQueryOptions,
  knowledgeClassesQueryOptions,
  knowledgeSpacesQueryOptions,
} from '@/features/knowledge/api/knowledgeQueries'
import { DirectoryPane } from '@/features/knowledge/components/DirectoryPane'
import { DocumentPreview } from '@/features/knowledge/components/DocumentPreview'
import { KnowledgeSidebar } from '@/features/knowledge/components/KnowledgeSidebar'
import { UploadDocumentsModal } from '@/features/knowledge/components/UploadDocumentsModal'
import { useKnowledgeMutations } from '@/features/knowledge/hooks/useKnowledgeMutations'
import {
  currentDirectoryItems,
  extractJoinToken,
  findNodePath,
  findTreeNode,
} from '@/features/knowledge/model/tree'
import type {
  GeneratedQuestion,
  GenResult,
  KbMember,
  KnowledgeSpaces,
  SavePreviewPayload,
  SharedKb,
  TreeNode,
} from '@/features/knowledge/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'
import styles from './KnowledgePage.module.css'

const KnowledgeBaseSettingsModal = lazy(() =>
  import('@/features/knowledge/components/KnowledgeBaseSettingsModal').then((module) => ({
    default: module.KnowledgeBaseSettingsModal,
  })),
)
const GenerateMaterialsModal = lazy(() =>
  import('@/features/knowledge/components/GenerateMaterialsModal').then((module) => ({
    default: module.GenerateMaterialsModal,
  })),
)
const MaterialDetailModal = lazy(() =>
  import('@/features/knowledge/components/MaterialDetailModal').then((module) => ({
    default: module.MaterialDetailModal,
  })),
)

type NameDialogState = { node: TreeNode; value: string } | null
type FolderDialogState = { parent: TreeNode | null; value: string } | null
type CreateKbFields = { name: string; description: string }
const EMPTY_SPACES: KnowledgeSpaces = { owned: [], joined: [] }
const EMPTY_TREE: TreeNode[] = []

export function KnowledgePage() {
  const { message, modal } = App.useApp()
  const [searchParams, setSearchParams] = useSearchParams()
  const spacesQuery = useQuery(knowledgeSpacesQueryOptions())
  const [activeKbId, setActiveKbId] = useState<number | null>(null)
  const treeQuery = useQuery(directoryTreeQueryOptions(activeKbId))
  const mutations = useKnowledgeMutations()
  const [selectedNodeId, setSelectedNodeId] = useState<number | null>(null)
  const [search, setSearch] = useState('')
  const [folderDialog, setFolderDialog] = useState<FolderDialogState>(null)
  const [renameDialog, setRenameDialog] = useState<NameDialogState>(null)
  const [uploadParent, setUploadParent] = useState<TreeNode | null | undefined>(undefined)
  const [createKbOpen, setCreateKbOpen] = useState(false)
  const [createKbForm] = Form.useForm<CreateKbFields>()
  const initialJoinToken = searchParams.get('joinToken') || ''
  const [joinOpen, setJoinOpen] = useState(Boolean(initialJoinToken))
  const [joinValue, setJoinValue] = useState(initialJoinToken)
  const [settingsKb, setSettingsKb] = useState<SharedKb | null>(null)
  const [generateDocument, setGenerateDocument] = useState<TreeNode | null>(null)
  const [materialDetail, setMaterialDetail] = useState<{
    type: 'preview' | 'quiz'
    id: number
    questionIds: number[]
  } | null>(null)

  const spaces = spacesQuery.data || EMPTY_SPACES
  const roots = treeQuery.data || EMPTY_TREE
  const selectedNode = selectedNodeId === null ? null : findTreeNode(roots, selectedNodeId)
  const selectedPath = selectedNode ? findNodePath(roots, selectedNode.id) : []
  const directoryPath = selectedNode?.type === 'file' ? selectedPath.slice(0, -1) : selectedPath
  const currentFolder = directoryPath[directoryPath.length - 1] || null
  const items = useMemo(() => currentDirectoryItems(roots, selectedNode), [roots, selectedNode])
  const activeKnowledgeBase = [...spaces.owned, ...spaces.joined].find((kb) => kb.id === activeKbId)
  const spaceName = activeKbId === null ? '个人空间' : activeKnowledgeBase?.name || '团队知识库'
  const classesQuery = useQuery({
    ...knowledgeClassesQueryOptions(),
    enabled: generateDocument !== null,
  })

  function selectSpace(kbId: number | null) {
    setActiveKbId(kbId)
    setSelectedNodeId(null)
    setSearch('')
  }

  async function moveDirectoryNode(nodeId: number, targetParentId: number | null) {
    if (nodeId === targetParentId) return
    try {
      await mutations.moveNode.mutateAsync({ nodeId, targetParentId, kbId: activeKbId })
      message.success('资源已移动')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '移动失败'))
    }
  }

  async function createDirectoryFolder() {
    const name = folderDialog?.value.trim()
    if (!folderDialog || !name) return
    try {
      await mutations.createFolder.mutateAsync({
        label: name,
        parentId: folderDialog.parent?.id,
        kbId: activeKbId,
      })
      setFolderDialog(null)
      message.success('文件夹已创建')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '创建文件夹失败'))
    }
  }

  async function renameResource() {
    const name = renameDialog?.value.trim()
    if (!renameDialog || !name) return
    try {
      await mutations.renameNode.mutateAsync({
        nodeId: renameDialog.node.id,
        label: name,
        kbId: activeKbId,
      })
      setRenameDialog(null)
      message.success('已重命名')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '重命名失败'))
    }
  }

  function confirmDeleteNode(node: TreeNode) {
    modal.confirm({
      title:
        node.type === 'folder' ? `删除「${node.label}」及其全部内容？` : `删除「${node.label}」？`,
      content: '删除后无法恢复。',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await mutations.deleteNode.mutateAsync({ nodeId: node.id, kbId: activeKbId })
          if (selectedPath.some((item) => item.id === node.id)) setSelectedNodeId(null)
          message.success('已删除')
        } catch (error: unknown) {
          message.error(getApiErrorMessage(error, '删除失败'))
          throw error
        }
      },
    })
  }

  async function uploadFiles(
    files: File[],
    onProgress: (percent: number, fileName: string) => void,
  ) {
    try {
      const count = await mutations.uploadDocuments.mutateAsync({
        files,
        parentNodeId: uploadParent?.id,
        kbId: activeKbId,
        onProgress,
      })
      setUploadParent(undefined)
      message.success(`成功上传 ${count} 个文件，后台正在解析和向量化`)
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '上传失败'))
    }
  }

  async function createTeamKnowledgeBase(fields: CreateKbFields) {
    try {
      await mutations.createKnowledgeBase.mutateAsync({
        name: fields.name.trim(),
        description: fields.description?.trim() || '',
      })
      setCreateKbOpen(false)
      createKbForm.resetFields()
      message.success('团队知识库创建成功')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '创建知识库失败'))
    }
  }

  async function joinTeamKnowledgeBase() {
    const token = extractJoinToken(joinValue)
    if (!token) return
    try {
      await mutations.joinKnowledgeBase.mutateAsync(token)
      setJoinOpen(false)
      setJoinValue('')
      setSearchParams(
        (current) => {
          const next = new URLSearchParams(current)
          next.delete('joinToken')
          return next
        },
        { replace: true },
      )
      message.success('已加入团队知识库')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '加入失败'))
    }
  }

  async function updateTeamKnowledgeBase(fields: { name: string; description: string }) {
    if (!settingsKb) return
    try {
      await mutations.updateKnowledgeBase.mutateAsync({
        kbId: settingsKb.id,
        name: fields.name.trim(),
        description: fields.description?.trim() || '',
      })
      message.success('知识库设置已保存')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '保存失败'))
    }
  }

  async function generateKnowledgeInvite() {
    if (!settingsKb) return ''
    try {
      const token = await mutations.generateInvite.mutateAsync(settingsKb.id)
      message.success('邀请链接已生成')
      return token
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '生成邀请链接失败'))
      return ''
    }
  }

  async function removeKnowledgeMember(member: KbMember) {
    if (!settingsKb) return
    try {
      await mutations.removeMember.mutateAsync({ kbId: settingsKb.id, userId: member.userId })
      message.success('成员已移除')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '移除成员失败'))
    }
  }

  async function deleteTeamKnowledgeBase() {
    if (!settingsKb) return
    try {
      await mutations.deleteKnowledgeBase.mutateAsync(settingsKb.id)
      if (activeKbId === settingsKb.id) selectSpace(null)
      setSettingsKb(null)
      message.success('团队知识库已解散')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '解散失败'))
      throw error
    }
  }

  async function generateTeachingMaterials(docId: string): Promise<GenResult | null> {
    try {
      const result = await mutations.generateMaterials.mutateAsync({ docId })
      if (result.previewError) message.warning(`预习材料：${result.previewError}`)
      if (result.quizError) message.warning(`课堂试题：${result.quizError}`)
      return result
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, 'AI 材料生成失败'))
      return null
    }
  }

  async function saveGeneratedPreview(payload: SavePreviewPayload) {
    try {
      await mutations.savePreview.mutateAsync(payload)
      message.success('预习材料已保存')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '保存预习材料失败'))
      throw error
    }
  }

  async function saveGeneratedQuestion(question: GeneratedQuestion, docId: string) {
    try {
      await mutations.saveQuestion.mutateAsync({ question, docId })
      message.success('试题已保存到题库')
    } catch (error: unknown) {
      message.error(getApiErrorMessage(error, '保存试题失败'))
      throw error
    }
  }

  function confirmDeleteMaterial(type: 'preview' | 'quiz', id: number) {
    if (!selectedNode?.docId) return
    modal.confirm({
      title: type === 'preview' ? '删除这份预习材料？' : '归档这道题目？',
      okText: type === 'preview' ? '删除' : '归档',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        await mutations.deleteMaterial.mutateAsync({ type, id, docId: selectedNode.docId! })
        message.success(type === 'preview' ? '预习材料已删除' : '题目已归档')
      },
    })
  }

  const initialLoading = spacesQuery.isPending || treeQuery.isPending
  const loadError = spacesQuery.error || treeQuery.error

  return (
    <main className={styles.page}>
      {loadError && (
        <Alert
          className={styles.error}
          showIcon
          type="error"
          message="知识库加载失败"
          description={getApiErrorMessage(loadError, '无法加载知识库')}
          action={
            <Button
              onClick={() => {
                void spacesQuery.refetch()
                void treeQuery.refetch()
              }}
            >
              重试
            </Button>
          }
        />
      )}
      <div className={styles.workspace}>
        <KnowledgeSidebar
          spaces={spaces}
          activeKbId={activeKbId}
          tree={roots}
          selectedNodeId={selectedNodeId}
          loading={treeQuery.isPending}
          onSelectSpace={selectSpace}
          onSelectNode={setSelectedNodeId}
          onMoveNode={(nodeId, parentId) => void moveDirectoryNode(nodeId, parentId)}
          onCreateKnowledgeBase={() => setCreateKbOpen(true)}
          onJoinKnowledgeBase={() => setJoinOpen(true)}
          onOpenSettings={setSettingsKb}
        />

        {initialLoading && !loadError ? (
          <div className={styles.loading}>
            <Spin size="large" />
            <span>正在加载知识库……</span>
          </div>
        ) : (
          <>
            <DirectoryPane
              title={currentFolder?.label || spaceName}
              path={directoryPath}
              items={items}
              selectedNodeId={selectedNodeId}
              search={search}
              onSearch={setSearch}
              onSelect={(node) => setSelectedNodeId(node.id)}
              onNavigatePath={(node) => setSelectedNodeId(node?.id || null)}
              onCreateFolder={(parent) => setFolderDialog({ parent, value: '' })}
              onUpload={setUploadParent}
              onRename={(node) => setRenameDialog({ node, value: node.label })}
              onDelete={confirmDeleteNode}
            />
            <DocumentPreview
              selectedNode={selectedNode}
              roots={roots}
              spaceName={spaceName}
              teamSpaceCount={spaces.owned.length + spaces.joined.length}
              onGenerate={() => selectedNode?.docId && setGenerateDocument(selectedNode)}
              onViewMaterial={(type, item, questionIds) =>
                item.id && setMaterialDetail({ type, id: item.id, questionIds })
              }
              onDeleteMaterial={confirmDeleteMaterial}
              onUpload={() => setUploadParent(currentFolder)}
              onCreateFolder={() => setFolderDialog({ parent: currentFolder, value: '' })}
            />
          </>
        )}
      </div>

      <Modal
        open={folderDialog !== null}
        title={
          folderDialog?.parent ? `在「${folderDialog.parent.label}」中新建文件夹` : '新建文件夹'
        }
        okText="创建"
        cancelText="取消"
        confirmLoading={mutations.createFolder.isPending}
        onCancel={() => setFolderDialog(null)}
        onOk={() => void createDirectoryFolder()}
      >
        <Input
          autoFocus
          value={folderDialog?.value || ''}
          placeholder="文件夹名称"
          onChange={(event) =>
            setFolderDialog((current) =>
              current ? { ...current, value: event.target.value } : current,
            )
          }
          onPressEnter={() => void createDirectoryFolder()}
        />
      </Modal>

      <Modal
        open={renameDialog !== null}
        title="重命名资源"
        okText="保存"
        cancelText="取消"
        confirmLoading={mutations.renameNode.isPending}
        onCancel={() => setRenameDialog(null)}
        onOk={() => void renameResource()}
      >
        <Input
          autoFocus
          value={renameDialog?.value || ''}
          onChange={(event) =>
            setRenameDialog((current) =>
              current ? { ...current, value: event.target.value } : current,
            )
          }
          onPressEnter={() => void renameResource()}
        />
      </Modal>

      <Modal
        open={createKbOpen}
        title="创建团队知识库"
        okText="创建"
        cancelText="取消"
        confirmLoading={mutations.createKnowledgeBase.isPending}
        onCancel={() => setCreateKbOpen(false)}
        onOk={() => createKbForm.submit()}
      >
        <Form<CreateKbFields>
          form={createKbForm}
          layout="vertical"
          onFinish={(fields) => void createTeamKnowledgeBase(fields)}
        >
          <Form.Item
            label="名称"
            name="name"
            rules={[{ required: true, whitespace: true, message: '请输入名称' }]}
          >
            <Input maxLength={80} />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} maxLength={300} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={joinOpen}
        title="加入团队知识库"
        okText="加入"
        cancelText="取消"
        confirmLoading={mutations.joinKnowledgeBase.isPending}
        onCancel={() => setJoinOpen(false)}
        onOk={() => void joinTeamKnowledgeBase()}
      >
        <Input.TextArea
          autoFocus
          rows={3}
          value={joinValue}
          placeholder="粘贴邀请链接或 token"
          onChange={(event) => setJoinValue(event.target.value)}
        />
      </Modal>

      {uploadParent !== undefined && (
        <UploadDocumentsModal
          key={`${activeKbId ?? 'personal'}-${uploadParent?.id ?? 'root'}`}
          spaceName={spaceName}
          parent={uploadParent}
          uploading={mutations.uploadDocuments.isPending}
          onClose={() => setUploadParent(undefined)}
          onUpload={uploadFiles}
        />
      )}

      {settingsKb && (
        <Suspense fallback={<Spin className={styles.modalSpin} />}>
          <KnowledgeBaseSettingsModal
            key={settingsKb.id}
            knowledgeBase={settingsKb}
            saving={mutations.updateKnowledgeBase.isPending}
            generatingInvite={mutations.generateInvite.isPending}
            deleting={mutations.deleteKnowledgeBase.isPending}
            removingMember={mutations.removeMember.isPending}
            onClose={() => setSettingsKb(null)}
            onSave={updateTeamKnowledgeBase}
            onGenerateInvite={generateKnowledgeInvite}
            onRemoveMember={removeKnowledgeMember}
            onDelete={deleteTeamKnowledgeBase}
          />
        </Suspense>
      )}

      {generateDocument && (
        <Suspense fallback={<Spin className={styles.modalSpin} />}>
          <GenerateMaterialsModal
            key={generateDocument.docId}
            document={generateDocument}
            classes={classesQuery.data || []}
            generating={mutations.generateMaterials.isPending}
            savingPreview={mutations.savePreview.isPending}
            savingQuestion={mutations.saveQuestion.isPending}
            onClose={() => setGenerateDocument(null)}
            onGenerate={generateTeachingMaterials}
            onSavePreview={saveGeneratedPreview}
            onSaveQuestion={saveGeneratedQuestion}
          />
        </Suspense>
      )}

      {materialDetail && (
        <Suspense fallback={<Spin className={styles.modalSpin} />}>
          <MaterialDetailModal
            type={materialDetail.type}
            id={materialDetail.id}
            questionIds={materialDetail.questionIds}
            onClose={() => setMaterialDetail(null)}
          />
        </Suspense>
      )}
    </main>
  )
}
