import { Alert, Button, Descriptions, Empty, List, Spin, Tabs, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import {
  documentContentQueryOptions,
  documentMaterialsQueryOptions,
} from '@/features/knowledge/api/knowledgeQueries'
import { draftStatusLabel, typeLabel } from '@/features/knowledge/model/presentation'
import {
  countTreeItems,
  fileType,
  formatUpdatedAt,
  isPptFile,
} from '@/features/knowledge/model/tree'
import type {
  GeneratedPreview,
  GeneratedQuestion,
  TreeNode,
} from '@/features/knowledge/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'
import { renderMarkdown } from '@/shared/utils/safeHtml'
import styles from './DocumentPreview.module.css'

type DocumentPreviewProps = {
  selectedNode: TreeNode | null
  roots: TreeNode[]
  spaceName: string
  teamSpaceCount: number
  onGenerate: () => void
  onViewMaterial: (
    type: 'preview' | 'quiz',
    item: GeneratedPreview | GeneratedQuestion,
    questionIds: number[],
  ) => void
  onDeleteMaterial: (type: 'preview' | 'quiz', id: number) => void
  onUpload: () => void
  onCreateFolder: () => void
}

export function DocumentPreview({
  selectedNode,
  roots,
  spaceName,
  teamSpaceCount,
  onGenerate,
  onViewMaterial,
  onDeleteMaterial,
  onUpload,
  onCreateFolder,
}: DocumentPreviewProps) {
  const docId = selectedNode?.type === 'file' ? selectedNode.docId || '' : ''
  const contentQuery = useQuery({ ...documentContentQueryOptions(docId), enabled: Boolean(docId) })
  const materialsQuery = useQuery({
    ...documentMaterialsQueryOptions(docId),
    enabled: Boolean(docId),
  })
  const materials = materialsQuery.data || { previews: [], questions: [] }
  const questionIds = materials.questions.map((question) => question.id)

  if (!selectedNode || selectedNode.type === 'folder') {
    const overviewRoots = selectedNode?.children || roots
    return (
      <aside className={styles.preview}>
        <div className={styles.overview}>
          <div className={styles.overviewIcon}>📚</div>
          <Typography.Title level={3}>{selectedNode?.label || '知识库概览'}</Typography.Title>
          <Typography.Paragraph type="secondary">
            {selectedNode ? '从中间选择文档即可在这里预览' : '选择目录开始整理教学资料'}
          </Typography.Paragraph>
          <div className={styles.stats}>
            <div>
              <strong>{countTreeItems(overviewRoots, 'folder')}</strong>
              <span>文件夹</span>
            </div>
            <div>
              <strong>{countTreeItems(overviewRoots, 'file')}</strong>
              <span>文档</span>
            </div>
            <div>
              <strong>{teamSpaceCount}</strong>
              <span>团队空间</span>
            </div>
          </div>
          <Button block type="primary" onClick={onUpload}>
            上传教学资料
          </Button>
          <Button block onClick={onCreateFolder}>
            创建课程文件夹
          </Button>
        </div>
      </aside>
    )
  }

  const contentPanel = (
    <div className={styles.scroll}>
      {contentQuery.isPending && (
        <div className={styles.center}>
          <Spin />
          <Typography.Text type="secondary">正在读取文档内容……</Typography.Text>
        </div>
      )}
      {contentQuery.isError && (
        <Alert
          showIcon
          type="warning"
          message="暂时无法预览文档"
          description={getApiErrorMessage(contentQuery.error, '文档可能仍在处理中')}
          action={<Button onClick={() => void contentQuery.refetch()}>重新读取</Button>}
        />
      )}
      {contentQuery.data && (
        <div
          className={styles.markdown}
          dangerouslySetInnerHTML={{ __html: renderMarkdown(contentQuery.data) }}
        />
      )}
      {!contentQuery.isPending && !contentQuery.isError && !contentQuery.data && (
        <Empty description="文档内容为空或仍在处理中" />
      )}
    </div>
  )

  const materialsPanel = (
    <div className={styles.scroll}>
      <div className={styles.materialHeader}>
        <div>
          <strong>已生成材料</strong>
          <p>预习内容和课堂题目统一放在这里</p>
        </div>
        {isPptFile(selectedNode.label) && <Button onClick={onGenerate}>生成材料</Button>}
      </div>
      {materialsQuery.isPending ? (
        <Spin className={styles.materialSpin} />
      ) : materials.previews.length || materials.questions.length ? (
        <List
          dataSource={[
            ...materials.previews.map((item) => ({ type: 'preview' as const, item })),
            ...materials.questions.map((item) => ({ type: 'quiz' as const, item })),
          ]}
          renderItem={({ type, item }) => (
            <List.Item
              actions={[
                <Button
                  key="delete"
                  danger
                  type="link"
                  onClick={() => item.id && onDeleteMaterial(type, item.id)}
                >
                  {type === 'preview' ? '删除' : '归档'}
                </Button>,
              ]}
              onClick={() => onViewMaterial(type, item, questionIds)}
            >
              <List.Item.Meta
                avatar={
                  <Tag color={type === 'preview' ? 'purple' : 'blue'}>
                    {type === 'preview'
                      ? '预习'
                      : typeLabel(item.quizType || ('type' in item ? item.type : ''))}
                  </Tag>
                }
                title={item.title || ('topic' in item ? item.topic : '') || '未命名材料'}
                description={draftStatusLabel(item)}
              />
            </List.Item>
          )}
        />
      ) : (
        <Empty description="暂未生成教学材料" />
      )}
    </div>
  )

  const infoPanel = (
    <div className={styles.scroll}>
      <Descriptions column={1} bordered size="small">
        <Descriptions.Item label="文件名称">{selectedNode.label}</Descriptions.Item>
        <Descriptions.Item label="文件类型">{fileType(selectedNode.label)}</Descriptions.Item>
        <Descriptions.Item label="预览状态">
          <Tag
            color={contentQuery.isSuccess ? 'success' : contentQuery.isError ? 'error' : 'warning'}
          >
            {contentQuery.isSuccess ? '可预览' : contentQuery.isError ? '读取失败' : '读取中'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="更新时间">
          {formatUpdatedAt(selectedNode.updatedAt)}
        </Descriptions.Item>
        <Descriptions.Item label="所属空间">{spaceName}</Descriptions.Item>
      </Descriptions>
    </div>
  )

  return (
    <aside className={styles.preview}>
      <header className={styles.header}>
        <div className={styles.fileTitle}>
          <span>📄</span>
          <div>
            <Typography.Title level={4}>{selectedNode.label}</Typography.Title>
            <Typography.Text type="secondary">{fileType(selectedNode.label)}</Typography.Text>
          </div>
        </div>
        {isPptFile(selectedNode.label) && (
          <Button type="primary" onClick={onGenerate}>
            ✦ AI 生成材料
          </Button>
        )}
      </header>
      <Tabs
        className={styles.tabs}
        items={[
          { key: 'content', label: '内容预览', children: contentPanel },
          {
            key: 'materials',
            label: `教学材料 (${materials.previews.length + materials.questions.length})`,
            children: materialsPanel,
          },
          { key: 'info', label: '文件信息', children: infoPanel },
        ]}
      />
    </aside>
  )
}
