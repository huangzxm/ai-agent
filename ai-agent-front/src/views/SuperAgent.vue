<template>
  <div class="chat-page">
    <div class="sidebar">
      <div class="logo">H Manus</div>
      <button class="new-chat-btn">
        <span class="btn-icon">+</span>
        开启新对话
      </button>
      
      <div class="history-section">
        <div class="section-title">今天</div>
        <div class="chat-history-item active">
          <span>用户与助理初次沟通交流</span>
          <span class="history-actions">···</span>
        </div>
      </div>
    </div>
    
    <div class="main-content">
      <div class="header">
        <h1 class="page-title">用户与助理初次沟通交流</h1>
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
              <div class="message-content">{{ msg.content }}</div>
              <div class="message-actions" v-if="msg.type === 'ai'">
                <button class="action-btn" title="复制">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                </button>
                <button class="action-btn" title="重新生成">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"></polyline><polyline points="23 20 23 14 17 14"></polyline><path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"></path></svg>
                </button>
                <button class="action-btn" title="点赞">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3"></path></svg>
                </button>
                <button class="action-btn" title="踩">
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zm7-13h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17"></path></svg>
                </button>
              </div>
            </div>
          </div>
        </div>
        
        <div class="chat-input-container">
          <div class="chat-input">
            <input 
              type="text" 
              v-model="inputMessage" 
              placeholder="给 H Manus 发送消息" 
              @keyup.enter="sendMessage"
              :disabled="isStreaming"
            />
            <div class="input-actions">
              <button class="action-btn">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
              </button>
              <button @click="sendMessage" :disabled="isStreaming" class="send-btn">
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"></line><polygon points="22 2 15 22 11 13 2 9 22 2"></polygon></svg>
              </button>
            </div>
          </div>
          <div class="powered-by">Powered by AI 引擎，由行为监测</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { apiService } from '../api'

const inputMessage = ref('')
const messages = ref([])
const isStreaming = ref(false)
const messagesContainer = ref(null)

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim() || isStreaming.value) return
  
  // 添加用户消息到列表
  messages.value.push({
    content: inputMessage.value,
    type: 'user'
  })
  
  // 获取用户输入并清空输入框
  const message = inputMessage.value
  inputMessage.value = ''
  isStreaming.value = true
  
  // 使用SSE接收流式响应
  const eventSource = apiService.doChatWithManus(message)
  
  eventSource.onmessage = (event) => {
    const data = event.data
    try {
      // 检查是否是JSON格式的消息
      const jsonData = JSON.parse(data)
      if (jsonData.done) {
        // 流结束标志
        eventSource.close()
        isStreaming.value = false
        return
      }
      
      // 如果有content字段，创建新的消息气泡
      if (jsonData.content) {
        messages.value.push({
          content: jsonData.content,
          type: 'ai'
        })
      }
    } catch (e) {
      // 如果不是JSON格式，按原方式处理
      if (data !== '[DONE]') {
        // 创建新的消息气泡
        messages.value.push({
          content: data,
          type: 'ai'
        })
      } else {
        // 流结束
        eventSource.close()
        isStreaming.value = false
      }
    }
  }
  
  eventSource.onerror = (error) => {
    console.error('SSE错误:', error)
    eventSource.close()
    isStreaming.value = false
    
    // 添加错误提示
    messages.value.push({
      content: '抱歉，发生了错误，请重试。',
      type: 'ai'
    })
  }
}

// 自动滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    nextTick(() => {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    })
  }
}

// 监听消息列表变化，自动滚动
watch(() => messages.value.length, scrollToBottom)
watch(() => messages.value[messages.value.length - 1]?.content, scrollToBottom)

// 组件挂载时添加欢迎消息
onMounted(() => {
  messages.value.push({
    content: '你好！😊 很高兴见到你～\n有什么我可以帮你的吗？或者只是想聊聊天？我都在这里！',
    type: 'ai'
  })
})
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
  width: 240px;
  background-color: #1a1a1a;
  border-right: 1px solid #333;
  display: flex;
  flex-direction: column;
  padding: 16px;
  flex-shrink: 0;
}

.logo {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 20px;
  padding-left: 8px;
  color: #e0e0e0;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  background-color: #4a61dd;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 10px 16px;
  font-size: 14px;
  cursor: pointer;
  margin-bottom: 24px;
}

.btn-icon {
  margin-right: 8px;
  font-size: 18px;
  line-height: 1;
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
  display: flex;
  justify-content: space-between;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  font-size: 14px;
}

.chat-history-item.active {
  background-color: #333;
}

.history-actions {
  color: #888;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.header {
  padding: 16px;
  border-bottom: 1px solid #333;
  display: flex;
  align-items: center;
  height: 60px;
  flex-shrink: 0;
}

.page-title {
  margin: 0;
  font-size: 16px;
  font-weight: normal;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 40px;
  scroll-behavior: smooth;
  background-color: #000000;
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
  width: 40px;
  height: 40px;
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
  max-width: 70%;
  word-break: break-word;
}

.ai-message {
  background-color: #1a1a1a;
  padding: 16px 20px;
  border-radius: 16px;
  position: relative;
}

.user-message {
  background-color: #2b5ebd;
  color: white;
  padding: 16px 20px;
  border-radius: 16px;
}

.message-content {
  white-space: pre-line;
  line-height: 1.5;
}

.message-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.action-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: #888;
  padding: 4px;
  border-radius: 4px;
}

.action-btn:hover {
  background-color: #3a3a3a;
  color: #e0e0e0;
}

.chat-input-container {
  padding: 16px 24px;
  border-top: 1px solid #333;
  flex-shrink: 0;
  position: sticky;
  bottom: 0;
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
  font-size: 12px;
  color: #888;
  text-align: center;
  margin-top: 8px;
}

.chat-input input::placeholder {
  color: #666;
}
</style> 