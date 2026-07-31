import { createRouter, createWebHistory } from 'vue-router'
import type { RouteLocationNormalized } from 'vue-router'
import { useChatStore } from '@/features/assistant/store'
import { useAuthStore } from '@/features/auth/store'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ============= 学生端（公开，免登录）=============
    {
      path: '/',
      name: 'home',
      component: () => import('@/features/homework/views/StudentSubmit.vue'),
    },

    // ============= 老师端（需要登录）=============
    {
      path: '/login',
      name: 'login',
      component: () => import('@/features/auth/views/LoginView.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/features/auth/views/RegisterView.vue'),
      meta: { requiresGuest: true },
    },
    {
      path: '/teacher/chat',
      name: 'chat',
      component: () => import('@/features/assistant/views/AIChat.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teacher/docs',
      name: 'docs',
      component: () => import('@/features/knowledge/views/KnowledgeBase.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teacher/classes',
      name: 'classes',
      component: () => import('@/features/classroom/views/ClassList.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teacher/classes/:id',
      name: 'classManage',
      component: () => import('@/features/classroom/views/ClassManage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teacher/tasks',
      name: 'tasks',
      component: () => import('@/features/homework/views/TaskManage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teacher/tasks/:id',
      name: 'taskDetail',
      component: () => import('@/features/homework/views/TaskDetail.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/view/submission/:id',
      name: 'submissionView',
      component: () => import('@/features/homework/views/SubmissionView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/teacher/data',
      name: 'data',
      component: () => import('@/features/teaching/views/Dashboard.vue'),
      meta: { requiresAuth: true },
    },
    // 课堂实时互动
    {
      path: '/teacher/live/:classId',
      name: 'liveTeacher',
      component: () => import('@/features/live/views/LiveDashboard.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/live/join',
      name: 'liveJoin',
      component: () => import('@/features/live/views/LiveJoin.vue'),
    },
    {
      path: '/live/:sessionCode',
      name: 'liveStudent',
      component: () => import('@/features/live/views/StudentLive.vue'),
    },
    // 备课学情仪表盘
    {
      path: '/teacher/pre-lesson',
      name: 'preLesson',
      component: () => import('@/features/teaching/views/PreLessonDashboard.vue'),
      meta: { requiresAuth: true },
    },
    // 预习任务
    {
      path: '/teacher/preview/create',
      name: 'previewCreate',
      component: () => import('@/features/teaching/views/PreviewTaskCreate.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/preview/:taskId',
      name: 'previewView',
      component: () => import('@/features/teaching/views/PreviewTaskView.vue'),
    },
    {
      path: '/:catchAll(.*)*',
      name: 'not-found',
      redirect: '/',
    },
  ],
})

// 全局路由守卫
router.beforeEach(async (to: RouteLocationNormalized) => {
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)
  const requiresGuest = to.matched.some((record) => record.meta.requiresGuest)

  const authStore = useAuthStore()
  await authStore.ensureInitialized()
  const isLoggedIn = authStore.isAuthenticated

  // 老师端页面 → 必须登录
  if (requiresAuth && !isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 登录/注册页 → 已登录就跳到老师后台
  if (requiresGuest && isLoggedIn) {
    return { name: 'chat' }
  }

  // 学生端页面（/）→ 直接放行，无需教师 Session
  return true
})

// AI 回复中切换页面 → 确认弹窗
router.beforeEach((_to) => {
  // 在 Pinia store 可用时才检查（确保组件已初始化）
  try {
    const chatStore = useChatStore()
    if (chatStore.aiResponding) {
      const leave = confirm('AI 正在回复中，切换页面将中断回复。确定离开吗？')
      if (!leave) return false
    }
  } catch {
    // Pinia 还没初始化，跳过
  }
  return true
})

router.afterEach((to: RouteLocationNormalized) => {
  console.log(`Navigated to ${String(to.name)}`)
})

export default router
