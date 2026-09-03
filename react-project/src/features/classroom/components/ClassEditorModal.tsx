import { useEffect } from 'react'
import { Form, Input, Modal, Select } from 'antd'
import type { ClassPayload, Course } from '@/features/classroom/model/types'

export type ClassFormValues = {
  name: string
  description?: string
  courseId?: number
  courseGroup?: string
  qqGroupId?: string
}

type ClassEditorModalProps = {
  open: boolean
  mode: 'create' | 'edit'
  courses: Course[]
  initialValues?: Partial<ClassFormValues>
  submitting: boolean
  onCancel: () => void
  onSubmit: (payload: ClassPayload) => Promise<void>
}

function toPayload(values: ClassFormValues, mode: 'create' | 'edit'): ClassPayload {
  const payload: ClassPayload = { name: values.name.trim() }
  const description = values.description?.trim() || ''
  const courseGroup = values.courseGroup?.trim() || ''
  const qqGroupId = values.qqGroupId?.trim() || ''

  if (description || mode === 'edit') payload.description = description
  if (values.courseId) payload.courseId = values.courseId
  if (courseGroup || mode === 'edit') payload.courseGroup = courseGroup
  if (qqGroupId || mode === 'edit') payload.qqGroupId = qqGroupId
  return payload
}

export function ClassEditorModal({
  open,
  mode,
  courses,
  initialValues,
  submitting,
  onCancel,
  onSubmit,
}: ClassEditorModalProps) {
  const [form] = Form.useForm<ClassFormValues>()

  useEffect(() => {
    if (!open) return
    form.setFieldsValue({
      name: '',
      description: '',
      courseId: undefined,
      courseGroup: '',
      qqGroupId: '',
      ...initialValues,
    })
  }, [form, initialValues, open])

  function handleCourseChange(courseId?: number) {
    const course = courses.find((item) => item.id === courseId)
    if (course) form.setFieldValue('courseGroup', course.name)
  }

  return (
    <Modal
      open={open}
      title={mode === 'create' ? '创建班级' : '编辑班级'}
      okText={mode === 'create' ? '创建' : '保存'}
      cancelText="取消"
      confirmLoading={submitting}
      destroyOnHidden
      onCancel={onCancel}
      onOk={() => form.submit()}
    >
      <Form<ClassFormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        onFinish={(values) => void onSubmit(toPayload(values, mode))}
      >
        <Form.Item
          label="班级名称"
          name="name"
          rules={[
            { required: true, whitespace: true, message: '请输入班级名称' },
            { max: 30, message: '班级名称不能超过30个字符' },
          ]}
        >
          <Input placeholder="例如：高一（3）班" />
        </Form.Item>

        <Form.Item label="所属课程" name="courseId">
          <Select
            placeholder="可选：选择课程"
            allowClear={mode === 'create'}
            options={courses.map((course) => ({ label: course.name, value: course.id }))}
            onChange={handleCourseChange}
          />
        </Form.Item>

        <Form.Item
          label="课程分组"
          name="courseGroup"
          rules={[{ max: 64, message: '课程分组不能超过64个字符' }]}
        >
          <Input placeholder="选择课程时会自动填写，也可以自定义" />
        </Form.Item>

        <Form.Item label="QQ 群号" name="qqGroupId">
          <Input placeholder="用于 OneBot 自动识别班级" />
        </Form.Item>

        <Form.Item
          label="班级描述"
          name="description"
          rules={[{ max: 200, message: '班级描述不能超过200个字符' }]}
        >
          <Input.TextArea rows={3} placeholder="简要描述课程安排或班级特点" showCount />
        </Form.Item>
      </Form>
    </Modal>
  )
}
