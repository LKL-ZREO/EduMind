<script setup lang="ts">
import { RouterLink, RouterView, useRouter } from 'vue-router'
import {computed, watch } from 'vue'

const router = useRouter()

// 🔴 直接用 localStorage 判断登录状态
const isLoggedIn = computed(() => {
  return !!localStorage.getItem('token')
})

// 获取用户名（从 localStorage）
const username = computed(() => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const user = JSON.parse(userStr)
      return user.username || '用户'
    } catch {
      return '用户'
    }
  }
  return '用户'
})

// 登出
function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}

// 未登录时强制跳转（排除登录和注册页）
watch(isLoggedIn, (loggedIn) => {
  const currentPath = router.currentRoute.value.path
  if (!loggedIn && currentPath !== '/login' && currentPath !== '/register') {
    router.push('/login')
  }
}, { immediate: true })
</script>

<template>
  <div class="app-container">
    <template v-if="isLoggedIn">
      <header class="app-header">
        <div class="wrapper">
          <div class="brand">
            <h2>个人博客系统</h2>
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
            <RouterLink to="/page1" class="nav-item">
              <span class="icon">🏠</span>
              首页
            </RouterLink>
            <RouterLink to="/page2" class="nav-item">
              <span class="icon">🎲</span>
              AI对话
            </RouterLink>
            <RouterLink to="/page3" class="nav-item">
              <span class="icon">📝</span>
              动态与评论区
            </RouterLink>
            <RouterLink to="/page4" class="nav-item">
              <span class="icon">🎒</span>
              个人中心
            </RouterLink>
            <RouterLink to="/page4" class="nav-item">
              <span class="icon">🛠️</span>
              小工具
            </RouterLink>
            <RouterLink to="/page4" class="nav-item">
              <span class="icon">🎮</span>
              休闲小游戏
            </RouterLink>
          </nav>
        </aside>

        <main class="content">
          <RouterView />
        </main>
      </div>
    </template>

    <!-- 未登录时只显示登录/注册页 -->
    <template v-else>
      <main class="login-only">
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

/* 未登录时的全屏居中布局 */
.login-only {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background-color: #1a1a1a;
}

/* 顶部导航 */
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

/* 主体布局 */
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

/* 移动端适配 */
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
