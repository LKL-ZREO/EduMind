<template>
  <div class="live-join-page">
    <div class="join-box">
      <h2>📱 加入课堂</h2>
      <p class="sub">输入教师大屏上的 6 位加入码</p>
      <el-input
        v-model="code"
        placeholder="如 A3F8K2"
        maxlength="6"
        size="large"
        class="code-inp"
        @keyup.enter="go"
      />
      <el-button type="primary" size="large" block :disabled="code.length < 4" @click="go"
        >加入课堂</el-button
      >
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
const router = useRouter()
const code = ref('')
function go() {
  if (code.value.trim().length >= 4)
    router.push({ name: 'liveStudent', params: { sessionCode: code.value.trim().toUpperCase() } })
}
</script>
<style scoped>
.live-join-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f0f2f5;
  padding: 20px;
}
.join-box {
  width: 100%;
  max-width: 360px;
  background: #fff;
  border-radius: 12px;
  padding: 30px 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  text-align: center;
}
.sub {
  font-size: 13px;
  color: #999;
  margin-bottom: 20px;
}
.code-inp {
  margin-bottom: 16px;
}
.code-inp :deep(input) {
  text-align: center;
  font-size: 24px;
  letter-spacing: 8px;
  font-family: monospace;
  font-weight: 700;
}
</style>
