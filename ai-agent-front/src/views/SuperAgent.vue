<template>
  <div class="chat-page">
    <div class="header">
      <router-link to="/" class="back-btn">返回首页</router-link>
      <h1 class="page-title">AI 超级智能体</h1>
    </div>
    
    <div class="chat-container">
      <div class="chat-messages" ref="messagesContainer">
        <div v-for="(msg, index) in messages" :key="index"
             :class="['message-wrapper', msg.type === 'user' ? 'user-wrapper' : 'ai-wrapper']">
          <div :class="['message', msg.type === 'user' ? 'user-message' : 'ai-message']">
            <div v-if="msg.content">{{ msg.content }}</div>
            <div v-else class="streaming">正在思考...</div>
          </div>
        </div>
      </div>
      
      <div class="chat-input">
        <input 
          type="text" 
          v-model="inputMessage" 
          placeholder="请输入您的消息..." 
          @keyup.enter="sendMessage"
          :disabled="isStreaming"
        />
        <button @click="sendMessage" :disabled="isStreaming">发送</button>
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
  
  // 添加AI回复占位
  const aiMessageIndex = messages.value.length
  messages.value.push({
    content: '',
    type: 'ai'
  })
  
  // 获取用户输入并清空输入框
  const message = inputMessage.value
  inputMessage.value = ''
  isStreaming.value = true
  
  // 使用SSE接收流式响应
  const eventSource = apiService.doChatWithManus(message)
  
  let aiResponse = ''
  
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
      
      // 如果有content字段，添加到响应中
      if (jsonData.content) {
        aiResponse += jsonData.content
        // 更新AI消息内容
        messages.value[aiMessageIndex].content = aiResponse
      }
    } catch (e) {
      // 如果不是JSON格式，按原方式处理
      if (data !== '[DONE]') {
        aiResponse += data
        // 更新AI消息内容
        messages.value[aiMessageIndex].content = aiResponse
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
    
    // 如果消息为空，添加错误提示
    if (!aiResponse) {
      messages.value[aiMessageIndex].content = '抱歉，发生了错误，请重试。'
    }
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
    content: '你好！我是AI超级智能体，我可以帮助您解决各种问题。请问有什么我可以帮助您的？',
    type: 'ai'
  })
})
</script>

<style scoped>
.chat-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 20px;
  background-color: #2196F3;
  color: white;
}

.back-btn {
  color: white;
  text-decoration: none;
  font-weight: bold;
}

.page-title {
  margin: 0;
  text-align: center;
  flex-grow: 1;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.message-wrapper {
  display: flex;
  margin-bottom: 15px;
}

.user-wrapper {
  justify-content: flex-end;
}

.ai-wrapper {
  justify-content: flex-start;
}

.message {
  padding: 10px 15px;
  border-radius: 18px;
  max-width: 70%;
  word-break: break-word;
}

.user-message {
  background-color: #E3F2FD;
  border-bottom-right-radius: 4px;
}

.ai-message {
  background-color: #E0E0E0;
  border-bottom-left-radius: 4px;
}

.chat-input button {
  background-color: #2196F3;
}

.chat-input button:hover {
  background-color: #1976D2;
}
</style> 