<template>
  <div class="chat" :class="themeClass">
    <!-- 动态背景 -->
    <div class="bg" aria-hidden="true">
      <span class="orb orb-1"></span>
      <span class="orb orb-2"></span>
      <span class="orb orb-3"></span>
    </div>

    <aside class="sidebar">
      <router-link to="/" class="back">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 12H5M11 18l-6-6 6-6"/>
        </svg>
        返回首页
      </router-link>

      <div class="brand">
        <span class="icon-box">
          <svg v-if="mode === 'RAG'" viewBox="0 0 24 24" width="18" height="18" fill="none"
               stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H19v15H6.5A2.5 2.5 0 0 0 4 20.5V5.5z"/>
            <path d="M19 18v3H6.5A2.5 2.5 0 0 1 4 18.5"/>
            <path d="M8 7h7M8 10.5h5"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor"
               stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
            <rect x="4" y="7" width="16" height="12" rx="3"/>
            <path d="M12 3v4M8 12h.01M16 12h.01M9 16h6M2 12v2M22 12v2"/>
          </svg>
        </span>
        <div>
          <div class="brand-name">{{ theme.title }}</div>
          <div class="brand-sub">{{ theme.subtitle }}</div>
        </div>
      </div>

      <button class="new-chat" @click="newChat">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
        新会话
      </button>

      <div class="section">
        <div class="section-title">当前身份</div>
        <select v-model="identity" class="select" @change="onIdentityChange">
          <option v-for="item in identityOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </div>

      <div class="section samples">
        <div class="section-title">试试这样问</div>
        <button v-for="q in theme.samples" :key="q" class="sample" @click="askSample(q)">
          <span class="sample-arrow">›</span>{{ q }}
        </button>
      </div>

      <div class="side-foot">
        <span class="mode-pill">{{ theme.tag }}</span>
        <span>{{ theme.hint }}</span>
      </div>
    </aside>

    <main class="main">
      <header class="header">
        <div class="header-left">
          <h1>{{ theme.title }}</h1>
          <span class="chat-id">{{ chatId }}</span>
        </div>
        <div class="header-right">
          <span v-if="routeInfo.route" :class="['route-tag', routeClass(routeInfo.route)]">
            {{ routeLabel(routeInfo.route) }}
          </span>
          <span v-if="routeInfo.route" class="confidence">
            置信度 {{ ((routeInfo.confidence || 0) * 100).toFixed(0) }}%
          </span>
        </div>
      </header>

      <div class="messages" ref="messagesContainer">
        <div class="messages-inner">
          <div v-for="(msg, index) in messages" :key="index"
               :class="['row', msg.type === 'user' ? 'row-user' : 'row-ai']">
            <div :class="['avatar', msg.type === 'user' ? 'avatar-user' : 'avatar-ai']">
              <svg v-if="msg.type === 'user'" viewBox="0 0 24 24" width="14" height="14" fill="none"
                   stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="8" r="4"/>
                <path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"/>
              </svg>
              <svg v-else-if="mode === 'RAG'" viewBox="0 0 24 24" width="14" height="14" fill="none"
                   stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H19v15H6.5A2.5 2.5 0 0 0 4 20.5V5.5z"/>
                <path d="M19 18v3H6.5A2.5 2.5 0 0 1 4 18.5M8 7h7"/>
              </svg>
              <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
                   stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="4" y="7" width="16" height="12" rx="3"/>
                <path d="M12 3v4M8 12h.01M16 12h.01M9 16h6"/>
              </svg>
            </div>

            <div :class="['bubble', msg.type === 'user' ? 'bubble-user' : 'bubble-ai']">
              <div v-if="msg.type === 'ai' && msg.meta" class="meta">
                <span :class="['route-tag', routeClass(msg.meta.route)]">{{ routeLabel(msg.meta.route) }}</span>
                <span v-if="msg.meta.cacheHit" class="chip">缓存命中</span>
                <span v-if="msg.meta.traces && msg.meta.traces.length" class="chip">
                  {{ msg.meta.traces.slice(0, 3).join(' · ') }}
                </span>
                <span v-if="msg.meta.costMillis" class="chip">{{ msg.meta.costMillis }}ms</span>
              </div>

              <div v-if="msg.streaming && !msg.content" class="typing">
                <span></span><span></span><span></span>
              </div>
              <div v-else class="content">{{ msg.content }}<span v-if="msg.streaming" class="cursor">▍</span></div>

              <div v-if="msg.transfer" class="transfer">
                <div class="transfer-title">已转接人工客服</div>
                <div class="transfer-line">坐席 {{ msg.transfer.agent }} · 单号 {{ msg.transfer.transferId }}</div>
                <div class="transfer-line">{{ msg.transfer.reason }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="composer-area">
        <transition name="fade">
          <div v-if="stage" class="stage"><span class="stage-dot"></span>{{ stage }}</div>
        </transition>
        <div class="composer" :class="{ focused: inputFocused }">
          <input
            v-model="inputMessage"
            :placeholder="theme.placeholder"
            @keyup.enter="sendMessage"
            @focus="inputFocused = true"
            @blur="inputFocused = false"
            :disabled="isStreaming"
          />
          <button class="send" :disabled="isStreaming || !inputMessage.trim()" @click="sendMessage">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 19V5M6 11l6-6 6 6"/>
            </svg>
          </button>
        </div>
        <div class="composer-foot">{{ theme.footer }}</div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { apiService } from '../api'

const props = defineProps({
  mode: { type: String, default: 'RAG' }
})

const MODES = {
  RAG: {
    title: '知识库问答',
    subtitle: 'RAG · 检索增强',
    tag: 'RAG',
    hint: '政策 / 物流 / 发票 / 账号',
    placeholder: '输入你的问题，例如：退货政策是什么？',
    footer: '双路召回 · 阈值拒答 · 低置信自动转人工',
    welcome: '你好，我是知识库助手。售后政策、物流时效、发票流程与账号问题，都可以直接问我，答案均来自官方知识库。',
    samples: ['退货政策是什么？', '物流一般几天能到？', '怎么开发票？', '账号密码忘了怎么办？']
  },
  AGENT: {
    title: '智能体助手',
    subtitle: 'Agent · 任务执行',
    tag: 'Agent',
    hint: '订单 / 退款 / 采购单',
    placeholder: '交给我一个任务，例如：我要退款，订单 1001',
    footer: '状态机约束 · 工具网关鉴权 · 多级降级兜底',
    welcome: '你好，我是任务执行助手。订单查询、退款办理、采购单进度都可以交给我，需要操作的步骤我会逐一执行并向你确认。',
    samples: ['我的订单 1001 到哪了？', '我要退款，订单 1001', '采购单 PR-202303 现在走到哪一步了？', '我要转人工']
  }
}

const theme = computed(() => MODES[props.mode] || MODES.RAG)
const themeClass = computed(() => (props.mode === 'AGENT' ? 'agent-theme' : 'rag-theme'))

const generateChatId = () => props.mode.toLowerCase() + '_' + Math.random().toString(36).slice(2, 10)

const chatId = ref(generateChatId())
const identity = ref('u1')
const inputMessage = ref('')
const messages = ref([])
const isStreaming = ref(false)
const stage = ref('')
const routeInfo = ref({})
const inputFocused = ref(false)
const messagesContainer = ref(null)

const identityOptions = [
  { value: 'u1', label: '用户 u1 · 订单 1001/1002' },
  { value: 'u2', label: '用户 u2 · 订单 2001' },
  { value: 'admin', label: '管理员 admin' },
  { value: '', label: '未登录' }
]

const routeLabel = (route) => ({ CHAT: '知识库', AGENT: '任务执行', HUMAN: '人工客服' }[route] || route)
const routeClass = (route) => ({ CHAT: 'route-chat', AGENT: 'route-agent', HUMAN: 'route-human' }[route] || 'route-chat')

const onIdentityChange = () => newChat()

const newChat = () => {
  chatId.value = generateChatId()
  messages.value = []
  routeInfo.value = {}
  stage.value = ''
  pushWelcome()
}

const pushWelcome = () => {
  messages.value.push({
    type: 'ai',
    content: theme.value.welcome,
    meta: null,
    transfer: null,
    streaming: false
  })
}

const askSample = (question) => {
  inputMessage.value = question
  sendMessage()
}

const sendMessage = () => {
  if (!inputMessage.value.trim() || isStreaming.value) return

  const text = inputMessage.value.trim()
  messages.value.push({ type: 'user', content: text })
  inputMessage.value = ''
  isStreaming.value = true
  stage.value = '正在接入…'
  routeInfo.value = {}

  messages.value.push({ type: 'ai', content: '', meta: null, transfer: null, streaming: true })
  const index = messages.value.length - 1
  const userId = identity.value || 'anonymous'
  const token = identity.value ? `demo-token-${identity.value}` : ''

  const eventSource = apiService.doChatWithCs(text, chatId.value, userId, token, props.mode)

  eventSource.addEventListener('route', (event) => {
    const data = JSON.parse(event.data)
    routeInfo.value = data
    stage.value = data.reason || ''
  })

  eventSource.addEventListener('stage', (event) => {
    stage.value = JSON.parse(event.data).text
  })

  eventSource.addEventListener('message', (event) => {
    const data = JSON.parse(event.data)
    messages.value[index].content += data.text
  })

  eventSource.addEventListener('transfer', (event) => {
    messages.value[index].transfer = JSON.parse(event.data)
  })

  eventSource.addEventListener('fail', (event) => {
    const data = JSON.parse(event.data)
    messages.value[index].content += `\n[系统提示] ${data.message}`
  })

  eventSource.addEventListener('done', (event) => {
    const data = JSON.parse(event.data)
    if (!messages.value[index].content && data.answer) {
      messages.value[index].content = data.answer
    }
    messages.value[index].meta = data
    messages.value[index].streaming = false
    if (data.needHuman) {
      routeInfo.value = { ...routeInfo.value, route: 'HUMAN' }
    }
    stage.value = ''
    isStreaming.value = false
    eventSource.close()
  })

  eventSource.onerror = () => {
    if (isStreaming.value) {
      messages.value[index].content = messages.value[index].content || '抱歉，连接中断了，请稍后重试。'
      messages.value[index].streaming = false
      stage.value = ''
      isStreaming.value = false
    }
    eventSource.close()
  }
}

const scrollToBottom = () => {
  if (messagesContainer.value) {
    nextTick(() => {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    })
  }
}

watch(() => messages.value.length, scrollToBottom)
watch(() => messages.value[messages.value.length - 1]?.content, scrollToBottom)
watch(() => props.mode, () => newChat())

onMounted(pushWelcome)
</script>

<style scoped>
.rag-theme { --accent: #6d5be8; --accent-rgb: 109, 91, 232; --bubble-user: #f1edfe; --bubble-border: #ddd4fb; }
.agent-theme { --accent: #3b82f6; --accent-rgb: 59, 130, 246; --bubble-user: #eaf1fe; --bubble-border: #cfe0fc; }

.chat {
  position: relative;
  height: 100vh;
  width: 100vw;
  display: flex;
  background-color: #fbfbfd;
  background-image:
    linear-gradient(rgba(20, 20, 40, 0.024) 1px, transparent 1px),
    linear-gradient(90deg, rgba(20, 20, 40, 0.024) 1px, transparent 1px);
  background-size: 48px 48px;
  color: #14141b;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
  overflow: hidden;
}

/* 动态光斑 */
.bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(72px);
  opacity: 0.85;
  will-change: transform;
}

.orb-1 {
  width: 640px;
  height: 640px;
  top: -210px;
  right: -60px;
  background: radial-gradient(circle, rgba(var(--accent-rgb), 0.42), transparent 68%);
  animation: drift 22s ease-in-out infinite;
}

.orb-2 {
  width: 560px;
  height: 560px;
  bottom: -220px;
  left: 6%;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.32), transparent 70%);
  animation: drift 28s ease-in-out infinite reverse;
}

.orb-3 {
  width: 460px;
  height: 460px;
  top: 32%;
  left: 38%;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.18), transparent 70%);
  animation: drift 34s ease-in-out infinite;
}

@keyframes drift {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  33% { transform: translate3d(-56px, 44px, 0) scale(1.1); }
  66% { transform: translate3d(44px, -34px, 0) scale(0.94); }
}

/* ---------- 侧栏 ---------- */
.sidebar {
  position: relative;
  z-index: 2;
  width: 250px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 18px 14px;
  background: rgba(250, 250, 252, 0.94);
  backdrop-filter: blur(14px);
  border-right: 1px solid #ececf2;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8a8a9c;
  text-decoration: none;
  padding: 4px 6px;
  border-radius: 6px;
  margin-bottom: 22px;
  transition: color 0.18s ease, background 0.18s ease;
}

.back:hover {
  color: #14141b;
  background: rgba(20, 20, 40, 0.04);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 6px;
  margin-bottom: 20px;
}

.icon-box {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  color: var(--accent);
  background: rgba(var(--accent-rgb), 0.08);
  border: 1px solid rgba(var(--accent-rgb), 0.2);
}

.brand-name {
  font-size: 14px;
  font-weight: 600;
  color: #14141b;
}

.brand-sub {
  font-size: 11px;
  color: #8a8a9c;
  margin-top: 2px;
}

.new-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 34px;
  font-size: 12.5px;
  color: #3a3a4a;
  background: #ffffff;
  border: 1px solid #e6e6ee;
  border-radius: 8px;
  cursor: pointer;
  box-shadow: 0 1px 2px rgba(20, 20, 40, 0.04);
  transition: border-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
  margin-bottom: 22px;
}

.new-chat:hover {
  border-color: rgba(var(--accent-rgb), 0.4);
  color: var(--accent);
  box-shadow: 0 4px 14px rgba(var(--accent-rgb), 0.12);
  transform: translateY(-1px);
}

.section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 11px;
  color: #9a9aac;
  padding: 0 6px;
  margin-bottom: 8px;
}

.select {
  width: 100%;
  padding: 8px 10px;
  font-size: 12px;
  color: #3a3a4a;
  background: #ffffff;
  border: 1px solid #e6e6ee;
  border-radius: 8px;
  outline: none;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.select:focus {
  border-color: rgba(var(--accent-rgb), 0.5);
  box-shadow: 0 0 0 3px rgba(var(--accent-rgb), 0.08);
}

.samples {
  flex: 1;
  overflow-y: auto;
}

.sample {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  text-align: left;
  background: transparent;
  border: none;
  border-radius: 7px;
  padding: 8px 6px;
  font-size: 12.5px;
  color: #5c5c70;
  cursor: pointer;
  transition: color 0.16s ease, background 0.16s ease;
}

.sample:hover {
  color: #14141b;
  background: rgba(20, 20, 40, 0.04);
}

.sample-arrow {
  color: #c4c4d2;
  font-size: 13px;
  transition: color 0.16s ease, transform 0.16s ease;
}

.sample:hover .sample-arrow {
  color: var(--accent);
  transform: translateX(2px);
}

.side-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 6px 0;
  border-top: 1px solid #ececf2;
  font-size: 11px;
  color: #9a9aac;
}

.mode-pill {
  font-size: 10px;
  font-weight: 600;
  color: var(--accent);
  border: 1px solid rgba(var(--accent-rgb), 0.25);
  background: rgba(var(--accent-rgb), 0.06);
  padding: 2px 6px;
  border-radius: 5px;
}

/* ---------- 主区 ---------- */
.main {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.header {
  height: 54px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #ececf2;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(10px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header h1 {
  font-size: 13.5px;
  font-weight: 600;
  color: #14141b;
  margin: 0;
}

.chat-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10.5px;
  color: #b0b0c0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.confidence {
  font-size: 11px;
  color: #8a8a9c;
}

.route-tag {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid #e6e6ee;
  color: #6b6b80;
  background: #ffffff;
}

.route-chat { color: #12805c; border-color: rgba(18, 128, 92, 0.2); background: rgba(18, 128, 92, 0.06); }
.route-agent { color: #2757c9; border-color: rgba(39, 87, 201, 0.2); background: rgba(39, 87, 201, 0.06); }
.route-human { color: #b45309; border-color: rgba(180, 83, 9, 0.22); background: rgba(245, 158, 11, 0.08); }

/* ---------- 消息 ---------- */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 26px 24px 12px;
  scroll-behavior: smooth;
}

.messages-inner {
  max-width: 780px;
  margin: 0 auto;
}

.row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 22px;
  animation: row-in 0.22s ease both;
}

@keyframes row-in {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

.row-user {
  flex-direction: row-reverse;
}

.avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: 1px solid #e6e6ee;
  background: #ffffff;
  color: #8a8a9c;
  margin-top: 2px;
}

.avatar-ai {
  color: var(--accent);
  border-color: rgba(var(--accent-rgb), 0.22);
  background: rgba(var(--accent-rgb), 0.06);
}

.bubble {
  max-width: 78%;
  font-size: 13.5px;
  line-height: 1.8;
  word-break: break-word;
}

.bubble-ai {
  background: #ffffff;
  border: 1px solid #ececf2;
  border-radius: 4px 12px 12px 12px;
  padding: 12px 16px;
  color: #2e2e3f;
  box-shadow: 0 1px 2px rgba(20, 20, 40, 0.03), 0 8px 22px rgba(20, 20, 40, 0.04);
}

.bubble-user {
  /* 不透明浅色底，避免背景光斑透出影响阅读 */
  background: var(--bubble-user);
  border: 1px solid var(--bubble-border);
  border-radius: 12px 4px 12px 12px;
  padding: 11px 15px;
  color: #2a2a3c;
  box-shadow: 0 1px 2px rgba(20, 20, 40, 0.03);
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: 9px;
}

.chip {
  font-size: 10.5px;
  color: #8a8a9c;
  border: 1px solid #ececf2;
  background: #fafafc;
  padding: 1px 7px;
  border-radius: 5px;
}

.content {
  white-space: pre-line;
}

.cursor {
  color: var(--accent);
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.typing {
  display: flex;
  gap: 5px;
  padding: 4px 0;
}

.typing span {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #a8a8ba;
  animation: typing 1.1s infinite;
}

.typing span:nth-child(2) { animation-delay: 0.15s; }
.typing span:nth-child(3) { animation-delay: 0.3s; }

@keyframes typing {
  0%, 60%, 100% { opacity: 0.25; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-3px); }
}

.transfer {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #fde3c4;
  background: #fffaf3;
  border-radius: 8px;
  font-size: 11.5px;
  color: #a16207;
}

.transfer-title {
  font-weight: 600;
  color: #b45309;
  margin-bottom: 4px;
}

.transfer-line {
  line-height: 1.7;
}

/* ---------- 输入区 ---------- */
.composer-area {
  flex-shrink: 0;
  padding: 6px 24px 18px;
  /* 底部渐白遮罩：光斑渐隐，输入区始终有干净底色 */
  background: linear-gradient(to bottom, rgba(251, 251, 253, 0) 0%, rgba(251, 251, 253, 0.9) 38%, #fbfbfd 100%);
}

.composer-area > * {
  max-width: 780px;
  margin-left: auto;
  margin-right: auto;
}

.stage {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 11.5px;
  color: #6b6b80;
  margin-bottom: 10px;
}

.stage-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  animation: breathe 1.4s infinite;
}

@keyframes breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

.composer {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #ffffff;
  border: 1px solid #e6e6ee;
  border-radius: 12px;
  padding: 8px 8px 8px 16px;
  box-shadow: 0 2px 6px rgba(20, 20, 40, 0.04), 0 10px 28px rgba(20, 20, 40, 0.05);
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.composer.focused {
  border-color: rgba(var(--accent-rgb), 0.55);
  box-shadow: 0 0 0 3px rgba(var(--accent-rgb), 0.1), 0 10px 28px rgba(20, 20, 40, 0.06);
}

.composer input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: #14141b;
  font-size: 13.5px;
  padding: 8px 0;
}

.composer input::placeholder {
  color: #a8a8ba;
}

.send {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: var(--accent);
  color: #ffffff;
  cursor: pointer;
  transition: filter 0.18s ease, transform 0.18s ease;
}

.send:hover:not(:disabled) {
  filter: brightness(1.08);
  transform: translateY(-1px);
}

.send:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.composer-foot {
  margin-top: 10px;
  text-align: center;
  font-size: 10.5px;
  color: #b0b0c0;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

@media (max-width: 820px) {
  .sidebar { display: none; }
  .bubble { max-width: 88%; }
}
</style>
