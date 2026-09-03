import { useState } from 'react'
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  List,
  Modal,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { knowledgeBaseMembersQueryOptions } from '@/features/knowledge/api/knowledgeQueries'
import { memberRoleLabel } from '@/features/knowledge/model/presentation'
import type { KbMember, SharedKb } from '@/features/knowledge/model/types'
import { getApiErrorMessage } from '@/shared/api/errors'

type SettingsFields = { name: string; description: string }

type KnowledgeBaseSettingsModalProps = {
  knowledgeBase: SharedKb
  saving: boolean
  generatingInvite: boolean
  deleting: boolean
  removingMember: boolean
  onClose: () => void
  onSave: (fields: SettingsFields) => Promise<void>
  onGenerateInvite: () => Promise<string>
  onRemoveMember: (member: KbMember) => Promise<void>
  onDelete: () => Promise<void>
}

function inviteUrl(token: string) {
  const url = new URL('/teacher/docs', window.location.origin)
  url.searchParams.set('joinToken', token)
  return url.toString()
}

export function KnowledgeBaseSettingsModal({
  knowledgeBase,
  saving,
  generatingInvite,
  deleting,
  removingMember,
  onClose,
  onSave,
  onGenerateInvite,
  onRemoveMember,
  onDelete,
}: KnowledgeBaseSettingsModalProps) {
  const { message, modal } = App.useApp()
  const [form] = Form.useForm<SettingsFields>()
  const [token, setToken] = useState(knowledgeBase.inviteToken || '')
  const membersQuery = useQuery(knowledgeBaseMembersQueryOptions(knowledgeBase.id))
  const link = token ? inviteUrl(token) : ''

  async function copyLink() {
    try {
      await navigator.clipboard.writeText(link)
      message.success('邀请链接已复制')
    } catch {
      message.warning('复制失败，请手动选择')
    }
  }

  function confirmDelete() {
    modal.confirm({
      title: `解散「${knowledgeBase.name}」？`,
      content: '所有目录和文件将被删除，此操作无法恢复。',
      okText: '确认解散',
      cancelText: '取消',
      okButtonProps: { danger: true, loading: deleting },
      onOk: onDelete,
    })
  }

  return (
    <Modal open width={720} title="团队知识库设置" footer={null} onCancel={onClose}>
      <Tabs
        items={[
          {
            key: 'base',
            label: '基本信息',
            children: (
              <Form<SettingsFields>
                form={form}
                layout="vertical"
                initialValues={{ name: knowledgeBase.name, description: knowledgeBase.description }}
                onFinish={(fields) => void onSave(fields)}
              >
                <Form.Item
                  label="知识库名称"
                  name="name"
                  rules={[{ required: true, whitespace: true, message: '请输入知识库名称' }]}
                >
                  <Input maxLength={80} />
                </Form.Item>
                <Form.Item label="描述" name="description">
                  <Input.TextArea rows={4} maxLength={300} showCount />
                </Form.Item>
                <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Button danger onClick={confirmDelete}>
                    解散知识库
                  </Button>
                  <Button type="primary" htmlType="submit" loading={saving}>
                    保存设置
                  </Button>
                </Space>
              </Form>
            ),
          },
          {
            key: 'members',
            label: `成员 (${membersQuery.data?.length || 0})`,
            children: membersQuery.isPending ? (
              <Spin />
            ) : membersQuery.isError ? (
              <Alert
                showIcon
                type="error"
                message="成员加载失败"
                description={getApiErrorMessage(membersQuery.error, '无法加载成员')}
              />
            ) : (
              <List
                dataSource={membersQuery.data || []}
                renderItem={(member) => (
                  <List.Item
                    actions={
                      member.role === 'owner'
                        ? undefined
                        : [
                            <Button
                              key="remove"
                              danger
                              type="link"
                              loading={removingMember}
                              onClick={() => void onRemoveMember(member)}
                            >
                              移除
                            </Button>,
                          ]
                    }
                  >
                    <List.Item.Meta
                      avatar={<span style={{ fontSize: 24 }}>👤</span>}
                      title={member.username}
                      description={<Tag>{memberRoleLabel(member.role)}</Tag>}
                    />
                  </List.Item>
                )}
              />
            ),
          },
          {
            key: 'invite',
            label: '邀请成员',
            children: (
              <Space direction="vertical" size={14} style={{ width: '100%' }}>
                <Typography.Paragraph type="secondary">
                  生成链接后，已登录教师可以通过链接加入这个团队知识库。
                </Typography.Paragraph>
                {link ? (
                  <Input value={link} readOnly />
                ) : (
                  <Alert type="info" message="尚未生成邀请链接" />
                )}
                <Space>
                  <Button
                    type="primary"
                    loading={generatingInvite}
                    onClick={() => void onGenerateInvite().then(setToken)}
                  >
                    {link ? '重新生成' : '生成邀请链接'}
                  </Button>
                  <Button disabled={!link} onClick={() => void copyLink()}>
                    复制链接
                  </Button>
                </Space>
              </Space>
            ),
          },
        ]}
      />
    </Modal>
  )
}
