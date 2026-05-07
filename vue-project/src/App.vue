<script setup lang="ts">
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { computed, watch } from 'vue'

const router = useRouter()
const route = useRoute()

// 登录状态
const isLoggedIn = computed(() => !!localStorage.getItem('token'))

// 用户名
const username = computed(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      return user.username || '教师'
    } catch {
      return '教师'
    }
  }
  return '教师'
})

// 是否是老师端（显示导航栏）
const showNav = computed(() => {
  return route.path.startsWith('/teacher') || route.path === '/about'
})

// 登出
function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}

// 未登录时访问教师端 → 跳登录
watch(isLoggedIn, (loggedIn) => {
  const path = router.currentRoute.value.path
  if (!loggedIn && path.startsWith('/teacher')) {
    router.push('/login')
  }
}, { immediate: true })
</script>

<template>
  <div class="app-container">
    <!-- 学生端首页 / 登录注册页 → 不显示导航栏 -->
    <template v-if="showNav">
      <header class="app-header">
        <div class="wrapper">
          <div class="brand">
            <h2>作业批改系统 · 后台</h2>
          </div>
          <div class="header-actions">
            <span class="user">{{ username }}</span>
            <a href="#" @click.prevent="logout" class="logout">登出</a>
          </div>
        </div>
      </header>

      <div class="app-layout">
        <aside class="sidebar">
          <nav>
            <RouterLink to="/teacher/chat" class="nav-item">
              <span class="icon">🎲</span>
              AI对话
            </RouterLink>
            <RouterLink to="/teacher/docs" class="nav-item">
              <span class="icon">📝</span>
              知识库管理
            </RouterLink>
            <RouterLink to="/teacher/data" class="nav-item">
              <span class="icon">🎒</span>
              数据中心
            </RouterLink>
          </nav>
        </aside>

        <main class="content">
          <RouterView />
        </main>
      </div>
    </template>

    <!-- 学生端 / 登录页 / 注册页 → 没有导航栏 -->
    <template v-else>
      <main class="full-page">
        <RouterView />
      </main>
    </template>
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #1a1a1a;
  color: #fff;
  font-family: "Microsoft Yahei", sans-serif;
}

/* 学生端/登录注册页全屏 */
.full-page {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #f5f7ff;
  color: #333;
}

/* 顶部导航（老师端） */
.app-header {
  background-color: #2a2a2a;
  border-bottom: 1px solid #444;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 1000;
  height: 56px;
  display: flex;
  align-items: center;
}

.wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 16px;
  width: 100%;
}

.brand h2 {
  margin: 0;
  color: #ff7d00;
  font-size: 18px;
}

.header-actions .user {
  margin-right: 12px;
  color: #eee;
}

.header-actions .logout {
  color: #ff7d00;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  text-decoration: none;
}

.header-actions .logout:hover {
  background-color: #333;
}

/* 主布局（老师端） */
.app-layout {
  display: flex;
  flex: 1;
  min-height: calc(100vh - 56px);
}

/* 侧边栏 */
.sidebar {
  width: 200px;
  background-color: #2a2a2a;
  border-right: 1px solid #444;
  flex-shrink: 0;
  height: calc(100vh - 56px);
  overflow-y: auto;
  position: sticky;
  top: 56px;
}

.sidebar nav {
  padding: 12px 0;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  color: #ddd;
  text-decoration: none;
  transition: all 0.2s;
  border-left: 3px solid transparent;
}

.nav-item:hover {
  background-color: #333;
  color: #ff7d00;
}

.nav-item.router-link-exact-active {
  background-color: #333;
  color: #ff7d00;
  border-left-color: #ff7d00;
}

.nav-item .icon {
  margin-right: 10px;
  width: 18px;
  text-align: center;
}

/* 内容区域 */
.content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  background-color: #1a1a1a;
}

/* 移动端 */
@media (max-width: 768px) {
  .app-layout {
    flex-direction: column;
  }

  .sidebar {
    width: 100%;
    height: auto;
    max-height: 300px;
    position: static;
  }
}
</style>
