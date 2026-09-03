import { useMemo, useState } from 'react'
import { Button, Layout, Menu, Space, Spin, Typography } from 'antd'
import { Outlet, useLocation, useNavigate, useNavigation, useRouteLoaderData } from 'react-router'
import type { MenuProps } from 'antd'
import type { AuthenticatedUser } from '@/features/auth/model/types'
import { useLogoutMutation } from '@/features/auth/hooks/useAuthMutations'
import styles from './TeacherLayout.module.css'

const navigationItems = [
  { key: '/teacher/chat', label: 'AI 对话' },
  { key: '/teacher/classes', label: '班级管理' },
  { key: '/teacher/docs', label: '知识库管理' },
  { key: '/teacher/tasks', label: '作业管理' },
  { key: '/teacher/data', label: '数据中心' },
  { key: '/teacher/pre-lesson', label: '备课工作台' },
] satisfies MenuProps['items']

function selectedNavigationKey(pathname: string) {
  if (pathname.startsWith('/teacher/classes')) return '/teacher/classes'
  if (pathname.startsWith('/teacher/tasks')) return '/teacher/tasks'
  if (pathname.startsWith('/teacher/live')) return '/teacher/classes'
  if (pathname.startsWith('/teacher/preview')) return '/teacher/pre-lesson'

  return navigationItems.find((item) => item && 'key' in item && item.key === pathname)?.key
}

export function TeacherLayout() {
  const user = useRouteLoaderData<AuthenticatedUser>('teacher')
  const location = useLocation()
  const navigate = useNavigate()
  const navigation = useNavigation()
  const logoutMutation = useLogoutMutation()
  const [collapsed, setCollapsed] = useState(false)

  const selectedKeys = useMemo(() => {
    const selected = selectedNavigationKey(location.pathname)
    return selected ? [String(selected)] : []
  }, [location.pathname])

  async function handleLogout() {
    try {
      await logoutMutation.mutateAsync()
    } finally {
      void navigate('/login', { replace: true })
    }
  }

  return (
    <Layout className={styles.shell}>
      <Layout.Header className={styles.header}>
        <div className={styles.brand}>
          <span className={styles.brandMark}>EM</span>
          <Typography.Text strong>EduMind · 教师工作台</Typography.Text>
        </div>
        <Space size={16}>
          {navigation.state !== 'idle' && <Spin size="small" aria-label="页面加载中" />}
          <Typography.Text type="secondary">{user?.username || '教师'}</Typography.Text>
          <Button
            type="text"
            loading={logoutMutation.isPending}
            onClick={() => void handleLogout()}
          >
            退出登录
          </Button>
        </Space>
      </Layout.Header>

      <Layout hasSider>
        <Layout.Sider
          className={styles.sider}
          width={208}
          collapsible
          collapsed={collapsed}
          breakpoint="lg"
          onCollapse={setCollapsed}
          theme="light"
        >
          <Menu
            mode="inline"
            items={navigationItems}
            selectedKeys={selectedKeys}
            onClick={({ key }) => void navigate(key)}
          />
        </Layout.Sider>

        <Layout.Content className={styles.content}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}
