import { useEffect } from 'react'
import { App, Button, Form, Input, Modal, Select, Space } from 'antd'
import type { Course, CoursePayload, PresetTemplate } from '@/features/classroom/model/types'

type CourseFormValues = {
  name: string
  presetKey?: string
  systemPrompt?: string
  knowledgeScope?: string
}

type CourseEditorModalProps = {
  open: boolean
  course: Course | null
  presets: Record<string, PresetTemplate>
  submitting: boolean
  deleting: boolean
  onCancel: () => void
  onSubmit: (payload: CoursePayload) => Promise<void>
  onDelete: () => Promise<void>
}

export function CourseEditorModal({
  open,
  course,
  presets,
  submitting,
  deleting,
  onCancel,
  onSubmit,
  onDelete,
}: CourseEditorModalProps) {
  const [form] = Form.useForm<CourseFormValues>()
  const { modal } = App.useApp()
  const editing = course !== null

  useEffect(() => {
    if (!open) return
    const defaultPreset = presets.generic ? 'generic' : Object.keys(presets)[0]
    const preset = defaultPreset ? presets[defaultPreset] : undefined
    form.setFieldsValue({
      name: course?.name || '',
      presetKey: editing ? undefined : defaultPreset,
      systemPrompt:
        course?.systemPrompt || preset?.prompt.replace('{{courseName}}', '新课程') || '',
      knowledgeScope: course?.knowledgeScope || '',
    })
  }, [course, editing, form, open, presets])

  function handlePresetChange(key: string) {
    const preset = presets[key]
    if (!preset) return
    const courseNameValue: unknown = form.getFieldValue('name')
    const courseName =
      typeof courseNameValue === 'string' && courseNameValue.trim()
        ? courseNameValue.trim()
        : '新课程'
    form.setFieldValue('systemPrompt', preset.prompt.replace('{{courseName}}', courseName))
  }

  function confirmDelete() {
    if (!course) return
    modal.confirm({
      title: `删除课程「${course.name}」？`,
      content: '课程下的班级不会删除，但会变成未分组班级。',
      okText: '删除课程',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: onDelete,
    })
  }

  return (
    <Modal
      open={open}
      title={editing ? '课程设置' : '创建课程'}
      okText={editing ? '保存课程' : '创建课程'}
      cancelText="取消"
      confirmLoading={submitting}
      destroyOnHidden
      onCancel={onCancel}
      onOk={() => form.submit()}
      footer={(_, { OkBtn, CancelBtn }) => (
        <Space style={{ width: '100%', justifyContent: editing ? 'space-between' : 'flex-end' }}>
          {editing && (
            <Button danger loading={deleting} onClick={confirmDelete}>
              删除课程
            </Button>
          )}
          <Space>
            <CancelBtn />
            <OkBtn />
          </Space>
        </Space>
      )}
    >
      <Form<CourseFormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        onFinish={(values) =>
          void onSubmit({
            name: values.name.trim(),
            presetKey: values.presetKey,
            systemPrompt: values.systemPrompt?.trim(),
            knowledgeScope: values.knowledgeScope?.trim(),
          })
        }
      >
        <Form.Item
          label="课程名称"
          name="name"
          rules={[{ required: true, whitespace: true, message: '请输入课程名称' }]}
        >
          <Input placeholder="例如：高中数学" />
        </Form.Item>

        {!editing && (
          <Form.Item label="课程预设" name="presetKey">
            <Select
              placeholder="选择 AI 助手预设"
              options={Object.values(presets).map((preset) => ({
                label: preset.name,
                value: preset.key,
              }))}
              onChange={handlePresetChange}
            />
          </Form.Item>
        )}

        <Form.Item label="AI 系统提示词" name="systemPrompt">
          <Input.TextArea rows={5} placeholder="描述 AI 在这门课程中的角色和边界" />
        </Form.Item>

        <Form.Item label="知识范围" name="knowledgeScope">
          <Input.TextArea rows={3} placeholder="例如：必修一、函数、集合与逻辑" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
