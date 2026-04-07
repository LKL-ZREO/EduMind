import { createRouter, createWebHistory } from 'vue-router'
import type { RouteLocationNormalized } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/register'
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('../views/AboutView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { requiresGuest: true }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { requiresGuest: true }
    },
    {
      path: '/page1',
      name: 'page1',
      component: () => import('../views/PageOne.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/page2',
      name: 'page2',
      component: () => import('../views/PageTwo.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/page3',
      name: 'page3',
      component: () => import('../views/PageThree.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/page4',
      name: 'page4',
      component: () => import('../views/PageFour.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/:catchAll(.*)*',
      name: 'not-found',
      redirect: '/register'
    }
  ],
})

// 全局路由守卫 - 改成检查 token
router.beforeEach((to: RouteLocationNormalized, from: RouteLocationNormalized) => {
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
  const requiresGuest = to.matched.some(record => record.meta.requiresGuest)

  // 🔴 改成检查 token
  const token = localStorage.getItem('token')
  const isLoggedIn = !!token

  if (requiresAuth && !isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (requiresGuest && isLoggedIn) {
    return { name: 'page1' }
  }

  return true
})

router.afterEach((to: RouteLocationNormalized, from: RouteLocationNormalized) => {
  console.log(`Navigated from ${String(from.name)} to ${String(to.name)}`)
})

export default router
