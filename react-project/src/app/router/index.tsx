import { createBrowserRouter, type RouteObject } from 'react-router'
import { RouteErrorPage } from '@/app/pages/RouteErrorPage'
import { PublicLayout } from '@/app/layouts/PublicLayout'
import { TeacherLayout } from '@/app/layouts/TeacherLayout'
import {
  guestOnlyLoader,
  redirectToPublicHome,
  redirectToTeacherHome,
  requireAuthLoader,
} from './guards'

export const routeObjects: RouteObject[] = [
  {
    Component: PublicLayout,
    ErrorBoundary: RouteErrorPage,
    children: [
      {
        index: true,
        lazy: async () => ({
          Component: (await import('@/features/homework/pages/StudentSubmitPage'))
            .StudentSubmitPage,
        }),
      },
      {
        path: 'login',
        loader: guestOnlyLoader,
        lazy: async () => ({
          Component: (await import('@/features/auth/pages/LoginPage')).LoginPage,
        }),
      },
      {
        path: 'register',
        loader: guestOnlyLoader,
        lazy: async () => ({
          Component: (await import('@/features/auth/pages/RegisterPage')).RegisterPage,
        }),
      },
      {
        path: 'view/submission/:id',
        loader: requireAuthLoader,
        lazy: async () => ({
          Component: (await import('@/features/homework/pages/SubmissionViewPage'))
            .SubmissionViewPage,
        }),
      },
      {
        path: 'live/join',
        lazy: async () => ({
          Component: (await import('@/features/live/pages/LiveJoinPage')).LiveJoinPage,
        }),
      },
      {
        path: 'live/:sessionCode',
        lazy: async () => ({
          Component: (await import('@/features/live/pages/StudentLivePage')).StudentLivePage,
        }),
      },
      {
        path: 'preview/:taskId',
        lazy: async () => ({
          Component: (await import('@/features/teaching/pages/PreviewTaskPage')).PreviewTaskPage,
        }),
      },
    ],
  },
  {
    id: 'teacher',
    path: 'teacher',
    loader: requireAuthLoader,
    Component: TeacherLayout,
    ErrorBoundary: RouteErrorPage,
    children: [
      { index: true, loader: redirectToTeacherHome },
      {
        path: 'chat',
        lazy: async () => ({
          Component: (await import('@/features/assistant/pages/ChatPage')).ChatPage,
        }),
      },
      {
        path: 'docs',
        lazy: async () => ({
          Component: (await import('@/features/knowledge/pages/KnowledgePage')).KnowledgePage,
        }),
      },
      {
        path: 'classes',
        lazy: async () => ({
          Component: (await import('@/features/classroom/pages/ClassListPage')).ClassListPage,
        }),
      },
      {
        path: 'classes/:id',
        lazy: async () => ({
          Component: (await import('@/features/classroom/pages/ClassDetailPage')).ClassDetailPage,
        }),
      },
      {
        path: 'tasks',
        lazy: async () => ({
          Component: (await import('@/features/homework/pages/TaskManagePage')).TaskManagePage,
        }),
      },
      {
        path: 'tasks/:id',
        lazy: async () => ({
          Component: (await import('@/features/homework/pages/TaskDetailPage')).TaskDetailPage,
        }),
      },
      {
        path: 'data',
        lazy: async () => ({
          Component: (await import('@/features/teaching/pages/DashboardPage')).DashboardPage,
        }),
      },
      {
        path: 'live/:classId',
        lazy: async () => ({
          Component: (await import('@/features/live/pages/TeacherLivePage')).TeacherLivePage,
        }),
      },
      {
        path: 'pre-lesson',
        lazy: async () => ({
          Component: (await import('@/features/teaching/pages/PreLessonPage')).PreLessonPage,
        }),
      },
      {
        path: 'preview/create',
        lazy: async () => ({
          Component: (await import('@/features/teaching/pages/PreviewCreatePage'))
            .PreviewCreatePage,
        }),
      },
    ],
  },
  { path: '*', loader: redirectToPublicHome },
]

export const router = createBrowserRouter(routeObjects)
