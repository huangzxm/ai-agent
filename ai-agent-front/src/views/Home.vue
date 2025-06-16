<template>
  <div class="home-container">
    <div class="sidebar">
      <div class="logo">H Manus</div>
      <div class="menu-item active">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path><polyline points="9 22 9 12 15 12 15 22"></polyline></svg>
        首页
      </div>
      <router-link to="/love-master" class="menu-item">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
        聊天
      </router-link>
      <div class="menu-item">
        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>
        设置
      </div>
      
      <div class="footer-menu">
        <div class="menu-item">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"></path><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
          帮助
        </div>
        <div class="menu-item">
          <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
          个人信息
        </div>
      </div>
    </div>
    
    <div class="main-content">
      <h1 class="page-title">AI 应用中心</h1>
      <p class="subtitle">选择一个AI应用开始你的极客之旅</p>
      
      <div class="apps-grid">
        <div class="app-card">
          <div class="card-icon love-master-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path></svg>
          </div>
          <div class="card-content">
            <h2>AI 恋爱大师</h2>
            <p>与AI恋爱大师聊天，获取专业情感建议和恋爱技巧指导</p>
            <div class="card-tags">
              <span class="tag">情感咨询</span>
              <span class="tag">恋爱技巧</span>
            </div>
          </div>
          <router-link to="/love-master" class="card-btn">开始使用</router-link>
        </div>
        
        <div class="app-card">
          <div class="card-icon super-agent-icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>
          </div>
          <div class="card-content">
            <h2>AI 超级智能体</h2>
            <p>体验新一代AI超级智能体，解决各种复杂问题和技术挑战</p>
            <div class="card-tags">
              <span class="tag">多领域专家</span>
              <span class="tag">技术支持</span>
            </div>
          </div>
          <router-link to="/super-agent" class="card-btn">开始使用</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// Home page component
</script>

<style scoped>
.home-container {
  height: 100vh;
  width: 100vw;
  display: flex;
  background-color: #121212;
  color: #e0e0e0;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  overflow: hidden;
}

.sidebar {
  width: 240px;
  background-color: #1a1a1a;
  border-right: 1px solid #333;
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.logo {
  font-size: 24px;
  font-weight: bold;
  padding: 0 20px;
  margin-bottom: 40px;
  color: #e0e0e0;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 12px 20px;
  color: #999;
  cursor: pointer;
  margin-bottom: 5px;
  border-left: 3px solid transparent;
  transition: all 0.2s ease;
  text-decoration: none;
}

.menu-item svg {
  margin-right: 12px;
}

.menu-item:hover {
  background-color: #252525;
  color: #e0e0e0;
}

.menu-item.active {
  color: #4a61dd;
  background-color: #252525;
  border-left-color: #4a61dd;
}

.footer-menu {
  margin-top: auto;
  border-top: 1px solid #333;
  padding-top: 15px;
}

.main-content {
  flex: 1;
  padding: 40px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.page-title {
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 10px;
  background: linear-gradient(90deg, #4a61dd, #7C3AED);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.subtitle {
  color: #999;
  margin-bottom: 40px;
}

.apps-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(400px, 450px));
  gap: 25px;
  justify-content: center;
  max-width: 950px;
}

.app-card {
  background-color: #1e1e1e;
  border: 1px solid #333;
  border-radius: 10px;
  padding: 25px;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.app-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.3);
  border-color: #4a61dd;
}

.app-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, #4a61dd, #7C3AED);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.3s ease;
}

.app-card:hover::before {
  transform: scaleX(1);
}

.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  margin-bottom: 20px;
}

.love-master-icon {
  background-color: rgba(76, 175, 80, 0.1);
  color: #4CAF50;
}

.super-agent-icon {
  background-color: rgba(74, 97, 221, 0.1);
  color: #4a61dd;
}

.card-content {
  flex: 1;
}

.app-card h2 {
  font-size: 22px;
  font-weight: 600;
  margin-bottom: 12px;
}

.app-card p {
  color: #999;
  margin-bottom: 20px;
  line-height: 1.5;
}

.card-tags {
  display: flex;
  gap: 10px;
  margin-bottom: 25px;
}

.tag {
  background-color: #252525;
  color: #999;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
}

.card-btn {
  background: linear-gradient(90deg, #4a61dd, #7C3AED);
  color: white;
  border: none;
  padding: 12px 0;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  text-align: center;
  text-decoration: none;
  transition: all 0.2s ease;
}

.card-btn:hover {
  opacity: 0.9;
  transform: translateY(-2px);
}
</style> 