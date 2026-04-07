<template>
  <div class="page1">
    <!-- 顶部信息栏 -->
    <div class="top-info">
      <div class="info-left">
        <p>本网站由 <span class="name">Liusy</span> 创建及维护</p>
        <p>本网站由 <span class="name">是爱你的白毛</span> 提供CDN防护</p>
        <p>购买高防服务器、CDN、游戏盾找 <span class="name">快快网络</span></p>
        <p>本网站由 <span class="name">造梦云服务</span> 提供高防服务器</p>
        <p>点击加入 👉 鼠鼠小窝</p>
      </div>
      <div class="info-right">
        <button class="btn btn-primary">加入鼠鼠小窝</button>
        <button class="btn btn-secondary">78鼠鼠聚集地</button>
      </div>
    </div>

    <!-- 每日密码 -->
    <div class="card">
      <div class="card-header">
        <h2>每日密码</h2>
        <span>2026-03-21</span>
      </div>
      <!-- 加载状态 -->
      <div v-if="passwordsLoading" class="loading">加载密码数据中...</div>
      <!-- 错误状态 -->
      <div v-else-if="passwordsError" class="error">密码数据加载失败：{{ passwordsError }}</div>
      <!-- 数据展示 -->
      <div v-else class="password-grid">
        <div class="password-item" v-for="item in passwords" :key="item.name">
          <div class="map-name">{{ item.name }}</div>
          <div class="code">{{ item.code }}</div>
        </div>
      </div>
      <p class="tip">数据来自后端接口（每日更新）</p>
    </div>

    <!-- 本周一个亿 -->
    <div class="card">
      <div class="card-header">
        <h2>本周一个亿</h2>
        <span>03月17日 - 03月24日</span>
      </div>
      <div class="billion-box">
        <div class="billion-item blue">
          <div class="text">
            <div class="title">军情录音</div>
            <div class="desc">资料情报 · 28,214</div>
          </div>
        </div>
        <div class="billion-item teal">
          <div class="text">
            <div class="title">情报文件</div>
            <div class="desc">资料情报 · 15,910</div>
          </div>
        </div>
        <div class="billion-item red">
          <div class="text">
            <div class="title">Liusy</div>
            <div class="desc">我也一个亿 😊</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 制造台利润 -->
    <div class="card">
      <div class="card-header">
        <h2>制造台利润</h2>
        <span>2026-03-21 16:30:00</span>
      </div>
      <!-- 加载状态 -->
      <div v-if="profitsLoading" class="loading">加载利润数据中...</div>
      <!-- 错误状态 -->
      <div v-else-if="profitsError" class="error">利润数据加载失败：{{ profitsError }}</div>
      <!-- 数据展示 -->
      <div v-else class="profit-grid">
        <div class="profit-item" v-for="item in profits" :key="item.name">
          <div class="info">
            <div class="station">{{ item.station }}</div>
            <div class="name">{{ item.name }}</div>
            <div class="profit">总利润：{{ item.total }}</div>
            <div class="profit">小时利润：{{ item.hour }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../api/request'

// 定义数据类型
interface Password {
  name: string
  code: string
}

interface Profit {
  station: string
  name: string
  total: string
  hour: string
}

// 响应式数据
const passwords = ref<Password[]>([])
const passwordsLoading = ref(true)
const passwordsError = ref<string | null>(null)

const profits = ref<Profit[]>([])
const profitsLoading = ref(true)
const profitsError = ref<string | null>(null)

// 请求每日密码数据
const fetchPasswords = async () => {
  passwordsLoading.value = true
  passwordsError.value = null
  try {
    console.log('开始请求密码接口')
    const response = await request.get<Password[]>('/daily-passwords')
    console.log('请求成功，响应数据：', response.data)
    passwords.value = response.data
  } catch (error) {
    console.error('获取每日密码失败:', error)
    passwordsError.value = error instanceof Error ? error.message : '请求失败'
    // 可选：使用默认数据作为降级方案
    passwords.value = [
      { name: '零号大坝', code: '0449' },
      { name: '长弓溪谷', code: '0684' },
      { name: '巴克什', code: '0001' },
      { name: '航天基地', code: '0605' },
      { name: '潮汐监狱', code: '0811' },
    ]
    passwordsError.value = null
    console.log('降级数据已赋值', passwords.value)
  } finally {
    passwordsLoading.value = false
  }
}

// 请求制造台利润数据
const fetchProfits = async () => {
  profitsLoading.value = true
  profitsError.value = null
  try {
    const response = await request.get<Profit[]>('/profits')
    profits.value = response.data
  } catch (error) {
    console.error('获取制造台利润失败:', error)
    profitsError.value = error instanceof Error ? error.message : '请求失败'
    // 可选：使用默认数据作为降级方案
    profits.value = [
      { station: '技术中心', name: 'OLIGHT Baldr Pro R多功能手电', total: '50,131', hour: '11,140' },
      { station: '工作台', name: '7.62*39mm AP SUB', total: '362,692', hour: '45,336' },
      { station: '制药台', name: '精密头盔维修包', total: '41,541', hour: '5,193' },
      { station: '防具台', name: '精英防弹背心', total: '193,860', hour: '24,232' },
    ]
    profitsError.value = null
    console.log('降级数据已赋值', profits.value)
  } finally {
    profitsLoading.value = false
  }
}

// 组件挂载时请求数据
onMounted(() => {
  fetchPasswords()
  fetchProfits()
})
</script>

<style scoped>
.page1 {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 顶部信息 */
.top-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #2a2a2a;
  padding: 16px 20px;
  border-radius: 10px;
  flex-wrap: wrap;
  gap: 16px;
}
.info-left p {
  margin: 4px 0;
  color: #ccc;
  font-size: 14px;
}
.name {
  color: #ff9500;
  font-weight: 500;
}
.info-right {
  display: flex;
  gap: 10px;
}
.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}
.btn-primary {
  background: #1677ff;
  color: #fff;
}
.btn-secondary {
  background: #555;
  color: #fff;
}

/* 卡片通用 */
.card {
  background: #2a2a2a;
  border-radius: 10px;
  padding: 16px 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.card-header h2 {
  font-size: 18px;
  margin: 0;
  color: #fff;
}
.card-header span {
  font-size: 13px;
  color: #999;
}

/* 每日密码 */
.password-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.password-item {
  background: #333;
  padding: 16px;
  border-radius: 8px;
  text-align: center;
}
.map-name {
  font-size: 14px;
  color: #ccc;
  margin-bottom: 8px;
}
.code {
  font-size: 24px;
  font-weight: bold;
  color: #ff9500;
}
.tip {
  text-align: center;
  font-size: 12px;
  color: #777;
  margin-top: 12px;
}

/* 本周一个亿 */
.billion-box {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.billion-item {
  padding: 16px;
  border-radius: 8px;
  color: #fff;
}
.blue {
  background: #165abb;
}
.teal {
  background: #0e8c8c;
}
.red {
  background: #c92f2f;
}
.title {
  font-size: 16px;
  font-weight: bold;
  margin-bottom: 4px;
}
.desc {
  font-size: 13px;
  opacity: 0.9;
}

/* 制造台利润 */
.profit-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.profit-item {
  background: #333;
  padding: 14px;
  border-radius: 8px;
}
.station {
  font-size: 13px;
  color: #aaa;
}
.name {
  font-size: 14px;
  color: #fff;
  margin: 4px 0;
}
.profit {
  font-size: 13px;
  color: #ff9500;
  margin-top: 4px;
}
</style>
