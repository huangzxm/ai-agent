<template>
  <div class="chat-page">
    <div class="sidebar">
      <div class="logo">AI 客服中心</div>
      <button class="new-chat-btn" @click="newChat">
        <span class="btn-icon">+</span>
        开启新会话
      </button>

      <div class="identity-section">
        <div class="section-title">当前身份（演示鉴权）</div>
        <select v-model="identity" class="identity-select" @change="onIdentityChange">
          <option v-for="item in identityOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </option>
        </select>
      </div>

      <div class="history-section">
        <div class="section-title">试试这些问题</div>
        <div v-for="question in sampleQuestions" :key="question"
             class="chat-history-item" @click="askSample(question)">
          <span>{{ question }}</span>
        </div>
      </div>

      <div class="sidebar-footer">
        路由：规则 + LLM 语义识别<br />
        CHAT / AGENT / HUMAN 三层分流
      </div>
    </div>

    <div class="main-content">
      <div class="header">
        <h1 class="page-title">
          AI 智能客服
          <span class="chat-id">会话ID: {{ chatId }}</span>
        </h1>
        <div class="header-tags">
          <span v-if="routeInfo.route" :class="['route-tag', routeClass(routeInfo.route)]">
            {{ routeLabel(routeInfo.route) }}
          </span>
          <span v-if="routeInfo.route" class="confidence">
            置信度 {{ (routeInfo.confidence * 100).toFixed(0) }}%
          </span>
        </div>
      </div>

      <div class="chat-container">
        <div class="chat-messages" ref="messagesContainer">
          <div v-for="(msg, index) in messages" :key="index"
               :class="['message-wrapper', msg.type === 'user' ? 'user-wrapper' : 'ai-wrapper']">
            <div class="avatar-container">
              <div :class="['avatar', msg.type === 'user' ? 'user-avatar' : 'ai-avatar']">
                <img v-if="msg.type === 'user'" src="../assets/images/user-logo.png" alt="用户">
                <img v-if="msg.type === 'ai'" src="../assets/images/robit-logo.png" alt="AI">
              </div>
            </div>
            <div :class="['message', msg.type === 'user' ? 'user-message' : 'ai-message']">
              <div v-if="msg.type === 'ai' && msg.meta" class="message-meta">
                <span :class="['route-tag', routeClass(msg.meta.route)]">{{ routeLabel(msg.meta.route) }}</span>
                <span v-if="msg.meta.cacheHit" class="meta-chip">FAQ 缓存命中</span>
                <span v-if="msg.meta.traces && msg.meta.traces.length" class="meta-chip">
                  {{ msg.meta.traces.join(' / ') }}
                </span>
                <span v-if="msg.meta.costMillis" class="meta-chip">{{ msg.meta.costMillis }}ms</span>
              </div>
              <div class="message-content">{{ msg.content }}<span v-if="msg.streaming" class="cursor">▍</span></div>
              <div v-if="msg.transfer" class="transfer-card">
                <div class="transfer-title">已转接人工客服</div>
                <div class="transfer-line">坐席：{{ msg.transfer.agent }}</div>
                <div class="transfer-line">转接单号：{{ msg.transfer.transferId }}</div>
                <div class="transfer-line">原因：{{ msg.transfer.reason }}</div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="stage" class="stage-hint">
          <span class="dot"></span>{{ stage }}
        </div>

        <div class="chat-input-container">
          <div class="chat-input">
            <input
              type="text"
              v-model="inputMessage"
              placeholder="请描述您的问题，例如：我要退款，订单 1001"
              @keyup.enter="sendMessage"
              :disabled="isStreaming"
            />
            <div class="input-actions">
              <button @click="sendMessage" :disabled="isStreaming" class="send-btn">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
              </button>
            </div>
          </div>
          <div class="powered-by">AI 客服系统 · 会话管理 / 意图路由 / RAG / Agent / 人工兜底</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { apiService } from '../api'

const generateChatId = () => 'cs_' + Math.random().toString(36).slice(2, 11)

const chatId = ref(generateChatId())
const identity = ref('u1')
const inputMessage = ref('')
const messages = ref([])
const isStreaming = ref(false)
const stage = ref('')
const routeInfo = ref({})
const messagesContainer = ref(null)

const identityOptions = [
  { value: 'u1', label: '用户 u1（订单 1001/1002）' },
  { value: 'u2', label: '用户 u2（订单 2001）' },
  { value: 'admin', label: '管理员 admin' },
  { value: '', label: '未登录用户' }
]

const sampleQuestions = [
  '你好',
  '退货政策是什么？',
  '我的订单 1001 到哪了？',
  '我要退款，订单 1001',
  '采购单 PR-202303 现在走到哪一步了？',
  '我要转人工'
]

const routeLabel = (route) => ({ CHAT: '知识库', AGENT: '任务执行', HUMAN: '人工客服' }[route] || route)

const routeClass = (route) => ({
  CHAT: 'route-chat',
  AGENT: 'route-agent',
  HUMAN: 'route-human'
}[route] || 'route-chat')

const onIdentityChange = () => {
  // 切换身份后开启新会话，避免上下文串号
  newChat()
}

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
    content: '您好，我是 AI 智能客服。可以帮您查询订单物流、办理退换货、解答售后政策。请告诉我您的问题～',
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

  const eventSource = apiService.doChatWithCs(text, chatId.value, userId, token)

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
      messages.value[index].content = messages.value[index].content || '抱歉，连接中断，请稍后重试。'
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

onMounted(pushWelcome)
</script>

<style scoped>
.chat-page {
  height: 100vh;
  width: 100vw;
  display: flex;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  background-color: #121212;
  color: #e0e0e0;
  overflow: hidden;
}

.sidebar {
  width: 260px;
  background-color: #1a1a1a;
  border-right: 1px solid #333;
  display: flex;
  flex-direction: column;
  padding: 16px;
  flex-shrink: 0;
}

.logo {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 20px;
  padding-left: 8px;
  background: linear-gradient(90deg, #4a61dd, #7c3aed);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  background: linear-gradient(90deg, #4a61dd, #7c3aed);
  color: white;
  border: none;
  border-radius: 6px;
  padding: 10px 16px;
  font-size: 14px;
  cursor: pointer;
  margin-bottom: 20px;
}

.btn-icon {
  margin-right: 8px;
  font-size: 18px;
  line-height: 1;
}

.identity-section {
  margin-bottom: 18px;
}

.identity-select {
  width: 100%;
  background-color: #252525;
  color: #e0e0e0;
  border: 1px solid #3a3a3a;
  border-radius: 6px;
  padding: 8px;
  font-size: 13px;
  outline: none;
}

.history-section {
  flex: 1;
  overflow-y: auto;
}

.section-title {
  font-size: 12px;
  color: #888;
  margin-bottom: 8px;
  padding-left: 8px;
}

.chat-history-item {
  padding: 9px 8px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  font-size: 13px;
  color: #bbb;
  transition: background-color 0.2s;
}

.chat-history-item:hover {
  background-color: #2b2b2b;
  color: #e0e0e0;
}

.sidebar-footer {
  font-size: 11px;
  color: #666;
  line-height: 1.6;
  border-top: 1px solid #2c2c2c;
  padding-top: 12px;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1100px;
  margin: 0 auto;
  width: 100%;
}

.header {
  padding: 16px 24px;
  border-bottom: 1px solid #333;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  flex-shrink: 0;
}

.page-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
}

.chat-id {
  font-size: 12px;
  color: #777;
  margin-left: 10px;
}

.header-tags {
  display: flex;
  align-items: center;
  gap: 10px;
}

.route-tag {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.route-chat {
  background-color: rgba(76, 175, 80, 0.15);
  color: #66bb6a;
}

.route-agent {
  background-color: rgba(74, 97, 221, 0.18);
  color: #7c8ff0;
}

.route-human {
  background-color: rgba(255, 152, 0, 0.16);
  color: #ffa726;
}

.confidence {
  font-size: 12px;
  color: #888;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  scroll-behavior: smooth;
  background-color: #0d0d0d;
}

.message-wrapper {
  display: flex;
  margin-bottom: 20px;
  align-items: flex-start;
}

.user-wrapper {
  justify-content: flex-end;
  flex-direction: row-reverse;
}

.ai-wrapper {
  justify-content: flex-start;
}

.avatar-container {
  margin: 0 12px;
}

.avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-avatar {
  background-color: #4a61dd;
}

.ai-avatar {
  background-color: #1e1e1e;
}

.message {
  border-radius: 12px;
  max-width: 72%;
  word-break: break-word;
}

.ai-message {
  background-color: #1a1a1a;
  padding: 14px 18px;
  border-radius: 16px;
}

.user-message {
  background-color: #2b5ebd;
  color: white;
  padding: 14px 18px;
  border-radius: 16px;
}

.message-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.meta-chip {
  font-size: 11px;
  color: #888;
  background-color: #252525;
  padding: 2px 8px;
  border-radius: 8px;
}

.message-content {
  white-space: pre-line;
  line-height: 1.6;
  font-size: 14px;
}

.cursor {
  color: #7c8ff0;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

.transfer-card {
  margin-top: 12px;
  border: 1px solid rgba(255, 152, 0, 0.35);
  background-color: rgba(255, 152, 0, 0.08);
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 12px;
  color: #d8b17a;
}

.transfer-title {
  font-weight: 600;
  margin-bottom: 6px;
  color: #ffa726;
}

.transfer-line {
  line-height: 1.7;
}

.stage-hint {
  padding: 6px 40px 0;
  font-size: 12px;
  color: #7c8ff0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #7c8ff0;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

.chat-input-container {
  padding: 14px 24px 18px;
  border-top: 1px solid #333;
  flex-shrink: 0;
  background-color: #121212;
}

.chat-input {
  display: flex;
  background-color: #2d2d2d;
  border-radius: 8px;
  padding: 10px 16px;
  align-items: center;
}

.chat-input input {
  flex: 1;
  background: none;
  border: none;
  color: #e0e0e0;
  font-size: 14px;
  outline: none;
}

.chat-input input::placeholder {
  color: #666;
}

.input-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.send-btn {
  padding: 8px;
  border-radius: 6px;
  background-color: #4a61dd;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  color: white;
  cursor: pointer;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.powered-by {
  font-size: 11px;
  color: #666;
  text-align: center;
  margin-top: 8px;
}
</style>
