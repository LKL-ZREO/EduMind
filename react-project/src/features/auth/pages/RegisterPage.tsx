import { useState } from 'react'
import { Alert, Button, Form, Input } from 'antd'
import { Link, useNavigate } from 'react-router'
import { AuthPageShell } from '@/features/auth/components/AuthPageShell'
import { useRegisterMutation } from '@/features/auth/hooks/useAuthMutations'
import { getApiErrorMessage } from '@/shared/api/errors'

type RegisterFormValues = {
  username: string
  email: string
  password: string
  confirmPassword: string
}

const steps = [
  { title: '建立教师身份', description: '用户名、邮箱和密码用于进入工作台。' },
  { title: '创建班级空间', description: '后续可以发放加入码和作业任务。' },
  { title: '接入课程知识库', description: '让课程资料成为 AI 可检索的上下文。' },
]

export function RegisterPage() {
  const navigate = useNavigate()
  const registerMutation = useRegisterMutation()
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  async function handleSubmit(values: RegisterFormValues) {
    setErrorMessage(null)
    try {
      await registerMutation.mutateAsync({
        username: values.username.trim(),
        email: values.email.trim(),
        password: values.password,
      })
      void navigate('/login?registered=1', { replace: true })
    } catch (error: unknown) {
      setErrorMessage(getApiErrorMessage(error, '注册失败，请稍后重试'))
    }
  }

  return (
    <AuthPageShell
      eyebrow="Teacher Onboarding"
      title="给新老师开一张干净的教学桌面。"
      summary="注册后可以管理班级、布置作业、接入知识库，并把 AI 助手放进清晰可控的教学流程。"
      steps={steps}
      cardTitle="创建教师账户"
      cardDescription="信息尽量少，但每一项都明确有用。"
      footer={
        <>
          已有账户？<Link to="/login">去登录</Link>
        </>
      }
    >
      {errorMessage && <Alert type="error" showIcon message={errorMessage} role="alert" />}

      <Form<RegisterFormValues>
        layout="vertical"
        requiredMark={false}
        onFinish={(values) => void handleSubmit(values)}
        disabled={registerMutation.isPending}
        size="large"
      >
        <Form.Item
          label="用户名"
          name="username"
          rules={[
            { required: true, whitespace: true, message: '请输入用户名' },
            { min: 3, message: '用户名至少需要3个字符' },
            { max: 50, message: '用户名不能超过50个字符' },
          ]}
        >
          <Input autoComplete="username" placeholder="例如：wang-laoshi" />
        </Form.Item>

        <Form.Item
          label="邮箱"
          name="email"
          rules={[
            { required: true, whitespace: true, message: '请输入邮箱地址' },
            { type: 'email', message: '请输入有效的邮箱地址' },
          ]}
        >
          <Input autoComplete="email" placeholder="teacher@example.com" />
        </Form.Item>

        <Form.Item
          label="密码"
          name="password"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 6, message: '密码至少需要6个字符' },
            { max: 128, message: '密码不能超过128个字符' },
          ]}
        >
          <Input.Password autoComplete="new-password" placeholder="至少 6 位" />
        </Form.Item>

        <Form.Item
          label="确认密码"
          name="confirmPassword"
          dependencies={['password']}
          rules={[
            { required: true, message: '请再次输入密码' },
            ({ getFieldValue }) => ({
              validator(_, value: string) {
                if (!value || getFieldValue('password') === value) return Promise.resolve()
                return Promise.reject(new Error('两次输入的密码不一致'))
              },
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" placeholder="再输入一次密码" />
        </Form.Item>

        <Button type="primary" htmlType="submit" block loading={registerMutation.isPending}>
          创建教师账户
        </Button>
      </Form>
    </AuthPageShell>
  )
}
