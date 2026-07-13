<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import QRCode from 'qrcode'

const props = defineProps<{
  visible: boolean
  inviteCode: string
}>()

const emit = defineEmits<{
  close: []
}>()

const inviteLink = ref('')
const canvasRef = ref<HTMLCanvasElement | null>(null)
const copied = ref<'code' | 'link' | null>(null)

// 生成二维码
async function generateQR() {
  if (!props.visible || !canvasRef.value) return
  await nextTick()
  try {
    inviteLink.value = `https://your-site.com/join?code=${props.inviteCode}`
    await QRCode.toCanvas(canvasRef.value, inviteLink.value, {
      width: 180,
      margin: 1,
      color: {
        dark: '#303133',
        light: '#ffffff',
      },
    })
  } catch (e) {
    console.error('QR生成失败', e)
  }
}

watch(() => props.visible, (v) => {
  if (v) generateQR()
})

function copyCode() {
  navigator.clipboard.writeText(props.inviteCode).then(() => {
    copied.value = 'code'
    setTimeout(() => { copied.value = null }, 2000)
  })
}

function copyLink() {
  navigator.clipboard.writeText(inviteLink.value).then(() => {
    copied.value = 'link'
    setTimeout(() => { copied.value = null }, 2000)
  })
}

function handleOverlayClick(e: MouseEvent) {
  if ((e.target as HTMLElement).classList.contains('invite-overlay')) {
    emit('close')
  }
}
</script>

<template>
  <div v-if="visible" class="invite-overlay" @click="handleOverlayClick">
    <div class="invite-dialog">
      <div class="invite-header">
        <h3>🔗 邀请学生加入</h3>
        <p class="invite-sub">通过以下方式邀请学生加入「{{ '' }}」</p>
        <button class="invite-close" @click="emit('close')">×</button>
      </div>

      <div class="invite-body">
        <!-- 二维码 -->
        <div class="qr-section">
          <div class="qr-wrapper">
            <canvas ref="canvasRef" width="180" height="180"></canvas>
          </div>
          <p class="qr-tip">学生扫描二维码即可加入</p>
        </div>

        <!-- 邀请码 -->
        <div class="code-section">
          <div class="code-item">
            <span class="code-label">邀请码</span>
            <div class="code-action">
              <span class="code-value">{{ inviteCode }}</span>
              <button class="copy-btn" @click="copyCode">
                {{ copied === 'code' ? '✅ 已复制' : '📋 复制' }}
              </button>
            </div>
          </div>
          <div class="code-item">
            <span class="code-label">邀请链接</span>
            <div class="code-action">
              <span class="code-value link-value" :title="inviteLink">{{ inviteLink }}</span>
              <button class="copy-btn" @click="copyLink">
                {{ copied === 'link' ? '✅ 已复制' : '📋 复制' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="invite-footer">
        <div class="invite-steps">
          <div class="step">
            <span class="step-num">1</span>
            <span>老师分享邀请码/链接/二维码</span>
          </div>
          <div class="step">
            <span class="step-num">2</span>
            <span>学生打开作业提交页面</span>
          </div>
          <div class="step">
            <span class="step-num">3</span>
            <span>学生输入邀请码加入班级</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.invite-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.invite-dialog { background: #fff; border: 1px solid #e4e7ed; border-radius: 16px; width: 90%; max-width: 500px; box-shadow: 0 8px 32px rgba(0,0,0,.1); position: relative; }
.invite-header { text-align: center; padding: 28px 28px 0; position: relative; }
.invite-header h3 { margin: 0 0 6px 0; font-size: 1.2rem; color: #303133; }
.invite-sub { margin: 0; font-size: 0.85rem; color: #909399; }
.invite-close { position: absolute; top: 16px; right: 16px; background: none; border: none; color: #c0c4cc; font-size: 1.4rem; cursor: pointer; line-height: 1; }
.invite-close:hover { color: #606266; }
.invite-body { display: flex; gap: 28px; padding: 24px 28px; }
.qr-section { display: flex; flex-direction: column; align-items: center; flex-shrink: 0; }
.qr-wrapper { padding: 8px; background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 12px; line-height: 0; }
.qr-wrapper canvas { display: block; }
.qr-tip { margin: 10px 0 0 0; font-size: 0.75rem; color: #909399; }
.code-section { flex: 1; display: flex; flex-direction: column; gap: 16px; justify-content: center; }
.code-item { display: flex; flex-direction: column; gap: 6px; }
.code-label { font-size: 0.8rem; color: #909399; }
.code-action { display: flex; align-items: center; gap: 8px; }
.code-value { flex: 1; padding: 8px 12px; background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 8px; font-family: Consolas, monospace; font-size: 1.2rem; letter-spacing: 3px; color: #409EFF; text-align: center; }
.link-value { font-size: 0.75rem; letter-spacing: normal; color: #606266; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.copy-btn { padding: 8px 14px; background: transparent; border: 1px solid #dcdfe6; border-radius: 8px; color: #606266; font-size: 0.8rem; cursor: pointer; white-space: nowrap; transition: all 0.2s; }
.copy-btn:hover { background: #ecf5ff; border-color: #409EFF; color: #409EFF; }
.invite-footer { padding: 0 28px 24px; }
.invite-steps { display: flex; flex-direction: column; gap: 8px; padding: 14px 16px; background: #f5f7fa; border-radius: 10px; }
.step { display: flex; align-items: center; gap: 10px; font-size: 0.82rem; color: #606266; }
.step-num { width: 22px; height: 22px; border-radius: 50%; background: #ecf5ff; display: flex; align-items: center; justify-content: center; font-size: 0.7rem; font-weight: 700; color: #409EFF; flex-shrink: 0; }
@media (max-width: 480px) { .invite-body { flex-direction: column; align-items: center; } }
</style>
