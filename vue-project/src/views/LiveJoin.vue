<template>
  <div class="live-join-page">
    <div class="background-orb orb-left"></div>
    <div class="background-orb orb-right"></div>

    <header class="page-header">
      <router-link to="/" class="brand-link" aria-label="返回 EduMind 首页">
        <span class="brand-mark">E</span>
        <div><strong>EduMind</strong><small>学生课堂</small></div>
      </router-link>
      <router-link to="/" class="teacher-link">
        <span>学生端</span>
        <b>返回作业提交 →</b>
      </router-link>
    </header>

    <main class="join-layout">
      <section class="join-card">
        <div class="card-heading">
          <span class="heading-icon">课</span>
          <div>
            <span class="eyebrow">JOIN LIVE CLASS</span>
            <h1>加入实时课堂</h1>
            <p>输入老师大屏或课堂邀请中的 6 位加入码</p>
          </div>
        </div>

        <div class="code-field">
          <div class="field-label-row">
            <label for="session-code">课堂加入码</label>
            <button type="button" class="paste-button" @click="pasteFromClipboard">
              <span aria-hidden="true">▣</span> 粘贴
            </button>
          </div>

          <div
            class="code-input-shell"
            :class="{
              focused: inputFocused,
              invalid: showCodeError,
              complete: isComplete,
            }"
            role="group"
            aria-label="六位课堂加入码"
            @click="focusInput"
          >
            <span
              v-for="index in 6"
              :key="index"
              class="code-slot"
              :class="{
                filled: !!codeCharacters[index - 1],
                current: inputFocused && currentSlot === index - 1,
              }"
            >
              {{ codeCharacters[index - 1] || '' }}
              <i v-if="inputFocused && currentSlot === index - 1"></i>
            </span>
            <input
              id="session-code"
              ref="codeInput"
              :value="code"
              class="native-code-input"
              type="text"
              inputmode="text"
              enterkeyhint="go"
              autocomplete="one-time-code"
              autocapitalize="characters"
              spellcheck="false"
              maxlength="6"
              aria-describedby="code-help"
              @input="handleInput"
              @paste="handlePaste"
              @focus="inputFocused = true"
              @blur="inputFocused = false"
              @keyup.enter="go"
            />
          </div>

          <div id="code-help" class="field-message" :class="{ error: showCodeError }">
            <template v-if="showCodeError">
              <span>!</span>{{ inputMessage || '请输入完整的 6 位课堂码' }}
            </template>
            <template v-else-if="isComplete"> <span>✓</span>课堂码格式正确，可以进入 </template>
            <template v-else> <span>i</span>不区分大小写，不包含 I、L、O、0、1 </template>
            <b>{{ code.length }}/6</b>
          </div>
        </div>

        <button
          type="button"
          class="join-button"
          :class="{ ready: isComplete }"
          :disabled="!isComplete"
          @click="go"
        >
          <span>{{ isComplete ? '进入课堂' : '请输入完整课堂码' }}</span>
          <i aria-hidden="true">→</i>
        </button>

        <div class="safe-tip">
          <span>✓</span>
          <p>
            <strong>无需注册学生账号</strong>进入后使用学号确认身份，个人信息不会展示给其他同学。
          </p>
        </div>

        <div class="divider"><span>其他加入方式</span></div>
        <div class="qr-tip">
          <span class="qr-icon"> <i></i><i></i><i></i><i></i> </span>
          <div>
            <strong>老师分享了二维码？</strong>
            <p>直接使用微信或系统相机扫码，即可跳过课堂码输入。</p>
          </div>
        </div>
      </section>

      <section class="join-guide">
        <span class="guide-kicker">STUDENT EXPERIENCE</span>
        <h2>从加入到互动，<br />每一步都清楚。</h2>
        <p>课堂题目、剩余时间和提交状态实时同步，专注作答，不需要反复刷新页面。</p>

        <div class="classroom-visual" aria-hidden="true">
          <div class="visual-window">
            <div class="visual-topbar">
              <span><i></i><i></i><i></i></span>
              <b>实时课堂</b>
              <em>LIVE</em>
            </div>
            <div class="visual-body">
              <div class="visual-question">
                <span>随堂选择题</span>
                <strong>老师发布题目后<br />会自动出现在这里</strong>
                <div><i></i><i></i><i></i></div>
              </div>
              <div class="visual-side">
                <b>课堂状态</b>
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
          <div class="floating-chip chip-answer"><span>✓</span>答案已提交</div>
          <div class="floating-chip chip-live"><i></i>实时连接</div>
        </div>

        <div class="join-steps">
          <div>
            <span>1</span>
            <p><strong>输入课堂码</strong><small>来自教师大屏或邀请</small></p>
          </div>
          <i></i>
          <div>
            <span>2</span>
            <p><strong>确认身份</strong><small>匹配班级学号与姓名</small></p>
          </div>
          <i></i>
          <div>
            <span>3</span>
            <p><strong>开始互动</strong><small>接题、作答与提问</small></p>
          </div>
        </div>
      </section>
    </main>

    <footer class="page-footer">
      <span>© 2026 EduMind 智能教学平台</span>
      <div>
        <span><i></i>服务正常</span><span>安全连接</span><span>隐私保护</span>
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const codeInput = ref<HTMLInputElement | null>(null)
const code = ref('')
const inputFocused = ref(false)
const touched = ref(false)
const inputMessage = ref('')

const VALID_CHARACTERS = /[^ABCDEFGHJKMNPQRSTUVWXYZ23456789]/g
const isComplete = computed(() => code.value.length === 6)
const codeCharacters = computed(() => code.value.split(''))
const currentSlot = computed(() => Math.min(code.value.length, 5))
const showCodeError = computed(() => !!inputMessage.value || (touched.value && !isComplete.value))

function normalizeCode(value: string) {
  return value.toUpperCase().replace(/\s/g, '').replace(VALID_CHARACTERS, '').slice(0, 6)
}

function applyCode(rawValue: string) {
  const compactValue = rawValue.toUpperCase().replace(/\s/g, '')
  const normalized = normalizeCode(rawValue)
  code.value = normalized
  touched.value = false
  inputMessage.value =
    compactValue.length !== normalized.length && normalized.length < 6
      ? '课堂码中包含无法识别的字符'
      : ''
  if (normalized.length === 6) inputMessage.value = ''
}

function handleInput(event: Event) {
  const target = event.target as HTMLInputElement
  applyCode(target.value)
  target.value = code.value
}

function handlePaste(event: ClipboardEvent) {
  event.preventDefault()
  applyCode(event.clipboardData?.getData('text') || '')
  void nextTick(focusInput)
}

async function pasteFromClipboard() {
  try {
    const text = await navigator.clipboard.readText()
    if (!text.trim()) {
      inputMessage.value = '剪贴板中没有课堂码'
      return
    }
    applyCode(text)
    await nextTick()
    focusInput()
  } catch {
    inputMessage.value = '无法读取剪贴板，请长按输入框粘贴'
    focusInput()
  }
}

function focusInput() {
  codeInput.value?.focus()
}

function go() {
  touched.value = true
  if (!isComplete.value) {
    inputMessage.value ||= '请输入完整的 6 位课堂码'
    focusInput()
    return
  }
  void router.push({ name: 'liveStudent', params: { sessionCode: code.value } })
}

onMounted(focusInput)
</script>

<style scoped>
:global(body) {
  margin: 0;
  background: #f5f7fc;
}

.live-join-page {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: #172039;
  background:
    radial-gradient(circle at 14% 16%, rgba(103, 92, 231, 0.12), transparent 31%),
    radial-gradient(circle at 88% 78%, rgba(69, 130, 224, 0.1), transparent 28%),
    linear-gradient(135deg, #f9faff 0%, #f3f5fd 52%, #fafbff 100%);
  font-family:
    Inter,
    'PingFang SC',
    'Microsoft YaHei',
    -apple-system,
    BlinkMacSystemFont,
    sans-serif;
}

.background-orb {
  position: absolute;
  border: 1px solid rgba(94, 88, 215, 0.09);
  border-radius: 50%;
  pointer-events: none;
}

.orb-left {
  bottom: -290px;
  left: -260px;
  width: 560px;
  height: 560px;
  box-shadow: 0 0 0 86px rgba(94, 88, 215, 0.022);
}

.orb-right {
  top: -220px;
  right: -180px;
  width: 430px;
  height: 430px;
  box-shadow: 0 0 0 72px rgba(69, 130, 224, 0.02);
}

.page-header,
.page-footer,
.join-layout {
  position: relative;
  z-index: 2;
  width: min(1160px, calc(100% - 48px));
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 82px;
}

.brand-link,
.teacher-link {
  color: inherit;
  text-decoration: none;
}

.brand-link {
  display: flex;
  align-items: center;
  gap: 11px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  background: linear-gradient(145deg, #675ce7, #4b63d8);
  box-shadow: 0 8px 22px rgba(85, 81, 210, 0.23);
}

.brand-link > div,
.teacher-link,
.card-heading > div,
.safe-tip p,
.qr-tip > div,
.join-steps p {
  display: flex;
  flex-direction: column;
}

.brand-link strong {
  font-size: 16px;
}

.brand-link small {
  margin-top: 2px;
  color: #8f96aa;
  font-size: 10px;
  letter-spacing: 0.12em;
}

.teacher-link {
  align-items: flex-end;
  gap: 2px;
}

.teacher-link span {
  color: #a0a5b5;
  font-size: 9px;
  letter-spacing: 0.1em;
}

.teacher-link b {
  color: #5c58bf;
  font-size: 11px;
}

.join-layout {
  display: grid;
  grid-template-columns: minmax(380px, 0.76fr) minmax(0, 1.08fr);
  align-items: center;
  gap: clamp(60px, 8vw, 120px);
  min-height: calc(100vh - 150px);
  padding: 24px 0 60px;
}

.join-card {
  padding: 31px;
  border: 1px solid rgba(215, 219, 235, 0.88);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.93);
  box-shadow: 0 26px 70px rgba(42, 52, 96, 0.13);
  backdrop-filter: blur(18px);
}

.card-heading {
  display: flex;
  align-items: center;
  gap: 13px;
  margin-bottom: 28px;
}

.heading-icon {
  display: grid;
  flex: 0 0 48px;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  color: #5e58cc;
  font-size: 15px;
  font-weight: 750;
  background: #ecebfc;
}

.eyebrow,
.guide-kicker {
  color: #7069dc;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.card-heading h1 {
  margin: 5px 0 3px;
  font-size: 22px;
  letter-spacing: -0.02em;
}

.card-heading p {
  margin: 0;
  color: #8d94a7;
  font-size: 11px;
}

.field-label-row,
.field-message,
.safe-tip,
.qr-tip,
.page-footer,
.page-footer > div,
.page-footer > div span,
.join-steps,
.join-steps > div {
  display: flex;
  align-items: center;
}

.field-label-row {
  justify-content: space-between;
  margin-bottom: 10px;
}

.field-label-row label {
  color: #454d62;
  font-size: 12px;
  font-weight: 700;
}

.paste-button {
  padding: 3px 0;
  border: 0;
  color: #716bd3;
  font-size: 10px;
  background: transparent;
  cursor: pointer;
}

.paste-button span {
  margin-right: 3px;
  font-size: 9px;
}

.code-input-shell {
  position: relative;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  cursor: text;
}

.code-slot {
  position: relative;
  display: grid;
  place-items: center;
  height: 54px;
  border: 1px solid #dfe2ed;
  border-radius: 10px;
  color: #2a3147;
  font:
    750 21px/1 ui-monospace,
    SFMono-Regular,
    Menlo,
    monospace;
  background: #f9fafc;
  transition:
    border-color 0.16s,
    background 0.16s,
    box-shadow 0.16s,
    transform 0.16s;
}

.code-slot.filled {
  border-color: #c7c4ef;
  color: #5752bb;
  background: #f7f6ff;
}

.code-slot.current {
  border-color: #6a62dc;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(106, 98, 220, 0.09);
  transform: translateY(-1px);
}

.code-slot i {
  width: 2px;
  height: 22px;
  border-radius: 2px;
  background: #6962db;
  animation: cursor-blink 1s step-end infinite;
}

.code-slot.filled i {
  display: none;
}

@keyframes cursor-blink {
  50% {
    opacity: 0;
  }
}

.code-input-shell.invalid .code-slot {
  border-color: #e9b1ad;
  background: #fff8f7;
}

.code-input-shell.complete .code-slot {
  border-color: #9fd3c2;
}

.native-code-input {
  position: absolute;
  inset: 0;
  z-index: 2;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  opacity: 0;
  cursor: text;
}

.field-message {
  gap: 6px;
  min-height: 18px;
  margin-top: 9px;
  color: #8e95a8;
  font-size: 9px;
}

.field-message > span {
  display: grid;
  place-items: center;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  color: #fff;
  font-size: 8px;
  font-weight: 700;
  background: #9ba1b2;
}

.field-message b {
  margin-left: auto;
  color: #7c8498;
  font:
    600 9px/1 ui-monospace,
    monospace;
}

.field-message.error {
  color: #c25e57;
}

.field-message.error > span {
  background: #d86c64;
}

.join-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 48px;
  margin-top: 19px;
  padding: 0 17px;
  border: 0;
  border-radius: 10px;
  color: #a1a6b3;
  font-size: 12px;
  font-weight: 700;
  background: #eceef3;
  cursor: not-allowed;
  transition:
    transform 0.16s,
    box-shadow 0.16s,
    background 0.16s;
}

.join-button i {
  font-size: 17px;
  font-style: normal;
}

.join-button.ready {
  color: #fff;
  background: linear-gradient(135deg, #675ce7, #526fe1);
  box-shadow: 0 9px 22px rgba(91, 86, 211, 0.22);
  cursor: pointer;
}

.join-button.ready:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 27px rgba(91, 86, 211, 0.27);
}

.safe-tip {
  align-items: flex-start;
  gap: 8px;
  margin-top: 14px;
  padding: 10px 11px;
  border-radius: 9px;
  color: #66716e;
  background: #f2f8f6;
}

.safe-tip > span {
  display: grid;
  flex: 0 0 17px;
  place-items: center;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  color: #fff;
  font-size: 8px;
  background: #46a583;
}

.safe-tip p {
  gap: 2px;
  margin: 0;
  font-size: 9px;
  line-height: 1.55;
}

.safe-tip strong {
  color: #3a5f54;
  font-size: 10px;
}

.divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 20px 0 14px;
  color: #a2a7b7;
  font-size: 8px;
}

.divider::before,
.divider::after {
  flex: 1;
  height: 1px;
  content: '';
  background: #e7e9ef;
}

.qr-tip {
  gap: 10px;
}

.qr-icon {
  display: grid;
  grid-template-columns: repeat(2, 7px);
  flex: 0 0 33px;
  gap: 3px;
  place-content: center;
  width: 33px;
  height: 33px;
  border: 1px solid #e2e4ec;
  border-radius: 9px;
  background: #fafbfc;
}

.qr-icon i {
  width: 7px;
  height: 7px;
  border: 2px solid #686f84;
  box-sizing: border-box;
}

.qr-icon i:last-child {
  border: 0;
  background: #686f84;
}

.qr-tip strong {
  font-size: 10px;
}

.qr-tip p {
  margin: 3px 0 0;
  color: #9a9faf;
  font-size: 9px;
}

.join-guide {
  min-width: 0;
  padding-top: 10px;
}

.join-guide h2 {
  margin: 17px 0 13px;
  font-size: clamp(31px, 3.3vw, 45px);
  line-height: 1.22;
  letter-spacing: -0.04em;
}

.join-guide > p {
  max-width: 490px;
  margin: 0;
  color: #747d94;
  font-size: 13px;
  line-height: 1.8;
}

.classroom-visual {
  position: relative;
  margin: 38px 0 34px;
  padding: 16px 21px 22px;
}

.classroom-visual::before {
  position: absolute;
  inset: 0 8%;
  border-radius: 28px;
  content: '';
  background: rgba(98, 91, 220, 0.085);
  filter: blur(23px);
}

.visual-window {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(211, 215, 232, 0.9);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.93);
  box-shadow: 0 18px 42px rgba(46, 54, 97, 0.12);
}

.visual-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 36px;
  padding: 0 13px;
  border-bottom: 1px solid #e9ebf2;
}

.visual-topbar > span {
  display: flex;
  gap: 4px;
}

.visual-topbar > span i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #cfd3df;
}

.visual-topbar b {
  flex: 1;
  color: #798095;
  font-size: 8px;
  font-weight: 650;
}

.visual-topbar em {
  padding: 3px 5px;
  border-radius: 4px;
  color: #288166;
  font-size: 6px;
  font-style: normal;
  font-weight: 800;
  background: #e6f6f0;
}

.visual-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px;
  gap: 11px;
  padding: 15px;
  background: #f7f8fc;
}

.visual-question,
.visual-side {
  border: 1px solid #e7e9f0;
  border-radius: 10px;
  background: #fff;
}

.visual-question {
  padding: 17px;
}

.visual-question > span {
  color: #6862ce;
  font-size: 7px;
  font-weight: 750;
}

.visual-question > strong {
  display: block;
  margin: 10px 0 13px;
  font-size: 13px;
  line-height: 1.5;
}

.visual-question > div {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.visual-question > div i {
  width: 100%;
  height: 14px;
  border: 1px solid #eceef3;
  border-radius: 5px;
  background: #fafbfc;
}

.visual-side {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 11px;
}

.visual-side b {
  margin-bottom: 5px;
  font-size: 8px;
}

.visual-side span {
  width: 100%;
  height: 7px;
  border-radius: 3px;
  background: #eef0f5;
}

.visual-side span:nth-child(3) {
  width: 72%;
}

.visual-side span:last-child {
  width: 86%;
}

.floating-chip {
  position: absolute;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid #e1e4ed;
  border-radius: 9px;
  color: #596075;
  font-size: 8px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10px 24px rgba(46, 54, 92, 0.13);
}

.chip-answer {
  right: -7px;
  bottom: 3px;
}

.chip-answer span {
  display: grid;
  place-items: center;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  color: #fff;
  font-size: 7px;
  background: #43a581;
}

.chip-live {
  top: 3px;
  left: -3px;
}

.chip-live i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #3eaa84;
  box-shadow: 0 0 0 3px rgba(62, 170, 132, 0.12);
}

.join-steps {
  justify-content: space-between;
  gap: 12px;
}

.join-steps > div {
  gap: 9px;
  min-width: 0;
}

.join-steps > div > span {
  display: grid;
  flex: 0 0 26px;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid #d5d3ef;
  border-radius: 8px;
  color: #655fc8;
  font:
    750 9px/1 ui-monospace,
    monospace;
  background: rgba(255, 255, 255, 0.76);
}

.join-steps p {
  gap: 3px;
  margin: 0;
}

.join-steps strong {
  font-size: 9px;
  white-space: nowrap;
}

.join-steps small {
  color: #999fb0;
  font-size: 8px;
  white-space: nowrap;
}

.join-steps > i {
  flex: 1;
  max-width: 30px;
  height: 1px;
  background: #dfe2ed;
}

.page-footer {
  justify-content: space-between;
  min-height: 54px;
  border-top: 1px solid rgba(218, 221, 233, 0.7);
  color: #a0a5b4;
  font-size: 8px;
}

.page-footer > div {
  gap: 17px;
}

.page-footer > div span {
  gap: 5px;
}

.page-footer i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #46a481;
}

@media (max-width: 920px) {
  .join-layout {
    grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1fr);
    gap: 44px;
  }

  .join-guide h2 {
    font-size: 33px;
  }

  .visual-body {
    grid-template-columns: 1fr 90px;
  }

  .join-steps > i,
  .join-steps small {
    display: none;
  }
}

@media (max-width: 740px) {
  .page-header,
  .page-footer,
  .join-layout {
    width: calc(100% - 28px);
  }

  .page-header {
    min-height: 70px;
  }

  .teacher-link span {
    display: none;
  }

  .join-layout {
    display: flex;
    align-items: stretch;
    flex-direction: column;
    min-height: auto;
    padding: 32px 0 55px;
  }

  .join-guide {
    display: none;
  }

  .join-card {
    width: min(100%, 440px);
    margin: 0 auto;
    box-sizing: border-box;
  }

  .page-footer {
    justify-content: center;
  }

  .page-footer > div {
    display: none;
  }
}

@media (max-width: 420px) {
  .join-layout {
    padding-top: 20px;
  }

  .join-card {
    padding: 24px 18px;
    border-radius: 18px;
  }

  .card-heading {
    align-items: flex-start;
    margin-bottom: 25px;
  }

  .heading-icon {
    flex-basis: 42px;
    width: 42px;
    height: 42px;
    border-radius: 12px;
  }

  .card-heading h1 {
    font-size: 20px;
  }

  .card-heading p {
    max-width: 230px;
    line-height: 1.5;
  }

  .code-input-shell {
    gap: 5px;
  }

  .code-slot {
    height: 49px;
    border-radius: 8px;
    font-size: 19px;
  }

  .field-message {
    font-size: 8px;
  }

  .qr-tip p {
    line-height: 1.5;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
