import { useState } from 'react'
import { Alert, Button, Form, Input } from 'antd'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { AuthPageShell } from '@/features/auth/components/AuthPageShell'
import { useLoginMutation } from '@/features/auth/hooks/useAuthMutations'
import type { LoginPayload } from '@/features/auth/model/types'
import { safeRedirectPath } from '@/features/auth/utils/safeRedirect'
import { getApiErrorMessage } from '@/shared/api/errors'

const steps = [
  { title: '确认教师身份', description: '通过服务端 Session 恢复你的工作台状态。' },
  { title: '选择教学上下文', description: '进入班级、知识库或已有 AI 会话。' },
  { title: '继续真实工作流', description: '课堂、作业和材料保持在同一套业务数据中。' },
]

export function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const loginMutation = useLoginMutation()
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const registered = searchParams.get('registered') === '1'

  async function handleSubmit(values: LoginPayload) {
    setErrorMessage(null)
    try {
      await loginMutation.mutateAsync({
        username: values.username.trim(),
        password: values.password,
      })
      const target = safeRedirectPath(searchParams.get('redirect'), '/teacher/chat')
      void navigate(target, { replace: true })
    } catch (error: unknown) {
      setErrorMessage(getApiErrorMessage(error, '登录失败'))
    }
  }

  return (
    <AuthPageShell
      eyebrow="Teacher Workspace"
      title="把课堂、作业和知识库收束到一张清晰的教学桌面。"
      summary="面向真实教学流程设计：先看班级状态，再处理材料、互动和批改结果。"
      steps={steps}
      cardTitle="欢迎回来"
      cardDescription="登录后继续管理课程、知识库和课堂互动。"
      footer={
        <>
          还没有账号？<Link to="/register">创建教师账号</Link>
        </>
      }
    >
      {registered && (
        <Alert type="success" showIcon message="注册成功，请使用新账号登录" closable />
      )}
      {errorMessage && <Alert type="error" showIcon message={errorMessage} role="alert" />}

      <Form<LoginPayload>
        layout="vertical"
        requiredMark={false}
        onFinish={(values) => void handleSubmit(values)}
        disabled={loginMutation.isPending}
        size="large"
      >
        <Form.Item
          label="用户名"
          name="username"
          rules={[{ required: true, whitespace: true, message: '请输入用户名' }]}
        >
          <Input autoComplete="username" placeholder="例如：li_teacher" />
        </Form.Item>

        <Form.Item
          label="密码"
          name="password"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码至少6位' },
          ]}
        >
          <Input.Password autoComplete="current-password" placeholder="输入登录密码" />
        </Form.Item>

        <Button type="primary" htmlType="submit" block loading={loginMutation.isPending}>
          进入教学工作台
        </Button>
      </Form>
    </AuthPageShell>
  )
}
