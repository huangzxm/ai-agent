<template>
  <div class="container">
    <h1 class="page-title">AI 应用中心</h1>
    <div class="apps-container">
      <div class="app-card">
        <h2>AI 恋爱大师</h2>
        <p>与AI恋爱大师聊天，获取情感建议</p>
        <router-link to="/love-master" class="btn">开始使用</router-link>
      </div>
      <div class="app-card">
        <h2>AI 超级智能体</h2>
        <p>体验新一代AI超级智能体，解决您的各种问题</p>
        <router-link to="/super-agent" class="btn">开始使用</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
// Home page component
</script>

<style scoped>
.apps-container {
  display: flex;
  justify-content: center;
  gap: 30px;
  margin-top: 50px;
}

.app-card {
  width: 300px;
  height: 250px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: center;
  padding: 30px;
}

.app-card h2 {
  margin-bottom: 15px;
  color: #4CAF50;
}

.app-card p {
  margin-bottom: 20px;
  color: #666;
}
</style> 