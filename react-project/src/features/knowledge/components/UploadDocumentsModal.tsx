import { useState } from 'react'
import { Button, Modal, Progress, Space, Typography, Upload } from 'antd'
import type { UploadFile } from 'antd'
import type { TreeNode } from '@/features/knowledge/model/types'

type UploadDocumentsModalProps = {
  spaceName: string
  parent: TreeNode | null
  uploading: boolean
  onClose: () => void
  onUpload: (
    files: File[],
    onProgress: (percent: number, fileName: string) => void,
  ) => Promise<void>
}

export function UploadDocumentsModal({
  spaceName,
  parent,
  uploading,
  onClose,
  onUpload,
}: UploadDocumentsModalProps) {
  const [fileList, setFileList] = useState<UploadFile[]>([])
  const [progress, setProgress] = useState(0)
  const [currentFile, setCurrentFile] = useState('')
  const files = fileList.reduce<File[]>((selectedFiles, file) => {
    if (file.originFileObj) selectedFiles.push(file.originFileObj)
    return selectedFiles
  }, [])

  return (
    <Modal
      open
      width={640}
      title="上传教学资料"
      okText={`上传${files.length ? ` ${files.length} 个文件` : ''}`}
      cancelText="取消"
      confirmLoading={uploading}
      okButtonProps={{ disabled: files.length === 0 }}
      onCancel={onClose}
      onOk={() =>
        void onUpload(files, (percent, fileName) => {
          setProgress(percent)
          setCurrentFile(fileName)
        })
      }
    >
      <Space direction="vertical" size={14} style={{ width: '100%' }}>
        <Typography.Text>
          上传到：<strong>{spaceName}</strong>
          {parent ? ` / ${parent.label}` : ' / 根目录'}
        </Typography.Text>
        <Upload.Dragger
          multiple
          accept=".txt,.md,.pdf,.doc,.docx,.ppt,.pptx"
          fileList={fileList}
          beforeUpload={() => false}
          onChange={({ fileList: next }) => setFileList(next.slice(0, 10))}
          disabled={uploading}
        >
          <Typography.Title level={4}>拖拽文件到这里，或点击选择</Typography.Title>
          <Typography.Paragraph type="secondary">
            支持 TXT、Markdown、PDF、Word 和 PowerPoint，单次最多 10 个文件。
          </Typography.Paragraph>
        </Upload.Dragger>
        {uploading && (
          <div>
            <Typography.Text type="secondary">正在上传：{currentFile}</Typography.Text>
            <Progress percent={progress} status="active" />
          </div>
        )}
        {fileList.length > 0 && !uploading && (
          <Button onClick={() => setFileList([])}>清空列表</Button>
        )}
      </Space>
    </Modal>
  )
}
