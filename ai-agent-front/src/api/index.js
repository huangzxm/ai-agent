import axios from 'axios'

const API_BASE_URL = 'http://localhost:8123/api'

// 创建axios实例
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000, // 设置超时时间为60秒
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  }
})

export const setupSSE = (endpoint, params) => {
  const url = new URL(`${API_BASE_URL}${endpoint}`)
  Object.keys(params).forEach(key => {
    url.searchParams.append(key, params[key])
  })
  return new EventSource(url)
}

export const apiService = {
  // AI恋爱大师应用聊天接口
  doChatWithLoveAppSse(message, chatId) {
    return setupSSE('/ai/love_app/chat/sse', { message, chatId })
  },

  // AI超级智能体应用聊天接口
  doChatWithManus(message) {
    return setupSSE('/ai/manus/chat', { message })
  }
}

export default apiService 