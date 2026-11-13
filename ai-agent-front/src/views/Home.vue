<template>
  <div class="home" :style="glow" @mousemove="onMove">
    <!-- 动态背景 -->
    <div class="bg" aria-hidden="true">
      <span class="orb orb-1"></span>
      <span class="orb orb-2"></span>
      <span class="orb orb-3"></span>
    </div>

    <nav class="nav">
      <div class="brand">
        <span class="brand-mark">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"
               stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 3l1.9 4.6L18.5 9.5 13.9 11.4 12 16l-1.9-4.6L5.5 9.5l4.6-1.9L12 3z"/>
            <path d="M18 15l.9 2.1L21 18l-2.1.9L18 21l-.9-2.1L15 18l2.1-.9L18 15z"/>
          </svg>
        </span>
        <span class="brand-name">小蜜</span>
        <span class="brand-divider"></span>
        <span class="brand-desc">AI 客服平台</span>
      </div>
      <div class="status">
        <span class="status-dot"></span>
        服务在线
      </div>
    </nav>

    <main class="main">
      <div class="hero">
        <div class="eyebrow">SUPPORT INTELLIGENCE</div>
        <h1 class="title">
          懂业务的<span class="title-accent">AI 客服</span><br />
          回答有依据，任务能落地
        </h1>
        <p class="subtitle">
          基于知识库的精准问答与可执行任务智能体，同一套会话、路由与人工兜底体系。
        </p>
      </div>

      <div class="entries">
        <!-- RAG -->
        <router-link to="/cs/rag" class="card card-rag">
          <div class="card-glow"></div>
          <div class="card-head">
            <span class="icon-box">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
                   stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H19v15H6.5A2.5 2.5 0 0 0 4 20.5V5.5z"/>
                <path d="M19 18v3H6.5A2.5 2.5 0 0 1 4 18.5"/>
                <path d="M8 7h7M8 10.5h5"/>
              </svg>
            </span>
            <span class="tag">RAG</span>
          </div>
          <h2>知识库问答</h2>
          <p class="card-desc">
            售后政策、物流时效、发票流程与账号安全，基于官方知识库检索作答，低置信自动转人工。
          </p>
          <ul class="card-points">
            <li>双路召回 + 相似度阈值</li>
            <li>业务元数据精准过滤</li>
            <li>答案带出处与置信度</li>
          </ul>
          <div class="card-foot">
            <span class="path">/cs/rag</span>
            <span class="go">
              开始提问
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14M13 6l6 6-6 6"/>
              </svg>
            </span>
          </div>
        </router-link>

        <!-- Agent -->
        <router-link to="/cs/agent" class="card card-agent">
          <div class="card-glow"></div>
          <div class="card-head">
            <span class="icon-box">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
                   stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                <rect x="4" y="7" width="16" height="12" rx="3"/>
                <path d="M12 3v4M8 12h.01M16 12h.01M9 16h6"/>
                <path d="M2 12v2M22 12v2"/>
              </svg>
            </span>
            <span class="tag">Agent</span>
          </div>
          <h2>智能体助手</h2>
          <p class="card-desc">
            查订单、办退款、追踪采购进度。多步骤任务由状态机约束执行，工具调用全程鉴权。
          </p>
          <ul class="card-points">
            <li>FSM 约束 + ReAct 循环</li>
            <li>越权调用代码级拦截</li>
            <li>失败自动降级人工</li>
          </ul>
          <div class="card-foot">
            <span class="path">/cs/agent</span>
            <span class="go">
              进入工作台
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
                   stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M5 12h14M13 6l6 6-6 6"/>
              </svg>
            </span>
          </div>
        </router-link>
      </div>

      <div class="features">
        <span><i></i>混合意图路由</span>
        <span><i></i>工具网关鉴权</span>
        <span><i></i>关键服务熔断</span>
        <span><i></i>统一人工交接</span>
      </div>
    </main>

    <footer class="footer">Spring AI · RAG · ReAct · 事件驱动降级</footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const glow = ref({ '--mx': '50%', '--my': '20%' })
let last = 0

const onMove = (e) => {
  const now = performance.now()
  if (now - last < 24) return
  last = now
  const rect = e.currentTarget.getBoundingClientRect()
  glow.value = {
    '--mx': `${e.clientX - rect.left}px`,
    '--my': `${e.clientY - rect.top}px`
  }
}
</script>

<style scoped>
.home {
  position: relative;
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  background-color: #fafafc;
  background-image:
    linear-gradient(rgba(20, 20, 40, 0.028) 1px, transparent 1px),
    linear-gradient(90deg, rgba(20, 20, 40, 0.028) 1px, transparent 1px);
  background-size: 48px 48px;
  color: #14141b;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
}

/* 动态光斑 */
.bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(78px);
  opacity: 0.9;
  will-change: transform;
}

.orb-1 {
  width: 660px;
  height: 660px;
  top: -200px;
  left: -160px;
  background: radial-gradient(circle, rgba(109, 91, 232, 0.5), transparent 68%);
  animation: drift-a 22s ease-in-out infinite;
}

.orb-2 {
  width: 620px;
  height: 620px;
  top: 2%;
  right: -200px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.44), transparent 68%);
  animation: drift-b 26s ease-in-out infinite;
}

.orb-3 {
  width: 560px;
  height: 560px;
  bottom: -220px;
  left: 28%;
  background: radial-gradient(circle, rgba(236, 72, 153, 0.32), transparent 70%);
  animation: drift-c 30s ease-in-out infinite;
}

@keyframes drift-a {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  33% { transform: translate3d(76px, -54px, 0) scale(1.1); }
  66% { transform: translate3d(-44px, 42px, 0) scale(0.94); }
}

@keyframes drift-b {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  40% { transform: translate3d(-70px, 50px, 0) scale(1.08); }
  75% { transform: translate3d(36px, -30px, 0) scale(0.95); }
}

@keyframes drift-c {
  0%, 100% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(56px, -48px, 0) scale(1.12); }
}

/* 鼠标跟随微光 */
.home::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 1;
  background: radial-gradient(560px circle at var(--mx) var(--my), rgba(109, 91, 232, 0.08), transparent 65%);
  transition: background 0.15s ease-out;
}

.nav {
  position: relative;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 36px;
  border-bottom: 1px solid #ececf2;
  background: rgba(250, 250, 252, 0.75);
  backdrop-filter: blur(14px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-mark {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #6d5be8;
  background: rgba(109, 91, 232, 0.08);
  border: 1px solid rgba(109, 91, 232, 0.2);
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.brand-divider {
  width: 1px;
  height: 14px;
  background: #e2e2ec;
}

.brand-desc {
  font-size: 13px;
  color: #8a8a9c;
}

.status {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: #5c5c70;
  padding: 5px 11px;
  border: 1px solid #e6e6ee;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: 0 1px 2px rgba(20, 20, 40, 0.03);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.15);
  animation: breathe 2.4s ease-in-out infinite;
}

@keyframes breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

.main {
  position: relative;
  z-index: 2;
  max-width: 1100px;
  margin: 0 auto;
  padding: 76px 36px 40px;
}

.hero {
  max-width: 660px;
  animation: rise 0.6s ease both;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 2.6px;
  color: #6d5be8;
  font-weight: 600;
  margin-bottom: 18px;
}

.title {
  font-size: 44px;
  line-height: 1.24;
  font-weight: 650;
  letter-spacing: -0.5px;
  margin: 0 0 18px;
  color: #14141b;
}

.title-accent {
  background: linear-gradient(96deg, #6d5be8, #3b82f6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.subtitle {
  font-size: 15px;
  line-height: 1.75;
  color: #5c5c70;
  margin: 0;
}

.entries {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 52px;
}

.card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 22px 24px 18px;
  border: 1px solid #e8e8f0;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(20, 20, 40, 0.04), 0 10px 28px rgba(20, 20, 40, 0.045);
  text-decoration: none;
  color: inherit;
  overflow: hidden;
  transition: border-color 0.22s ease, transform 0.22s ease, box-shadow 0.22s ease;
  animation: rise 0.6s ease both;
}

.card-rag { animation-delay: 0.08s; }
.card-agent { animation-delay: 0.16s; }

.card:hover {
  transform: translateY(-3px);
  border-color: #d8d8e6;
  box-shadow: 0 2px 4px rgba(20, 20, 40, 0.05), 0 18px 44px rgba(20, 20, 40, 0.09);
}

.card-glow {
  position: absolute;
  inset: -1px;
  border-radius: inherit;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.25s ease;
}

.card-rag .card-glow {
  background: radial-gradient(420px circle at 20% 0%, rgba(109, 91, 232, 0.09), transparent 70%);
}

.card-agent .card-glow {
  background: radial-gradient(420px circle at 20% 0%, rgba(59, 130, 246, 0.09), transparent 70%);
}

.card:hover .card-glow {
  opacity: 1;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.icon-box {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid #e8e8f0;
  background: #fafafc;
  color: #6f6f82;
  transition: color 0.2s ease, border-color 0.2s ease, background 0.2s ease;
}

.card-rag:hover .icon-box {
  color: #6d5be8;
  border-color: rgba(109, 91, 232, 0.28);
  background: rgba(109, 91, 232, 0.06);
}

.card-agent:hover .icon-box {
  color: #3b82f6;
  border-color: rgba(59, 130, 246, 0.28);
  background: rgba(59, 130, 246, 0.06);
}

.tag {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 1px;
  padding: 3px 8px;
  border-radius: 5px;
  border: 1px solid #e6e6ee;
  color: #8a8a9c;
  background: #fafafc;
}

.card h2 {
  font-size: 19px;
  font-weight: 600;
  margin: 0 0 10px;
  letter-spacing: -0.2px;
  color: #14141b;
}

.card-desc {
  font-size: 13px;
  line-height: 1.75;
  color: #5c5c70;
  margin: 0 0 16px;
  min-height: 66px;
}

.card-points {
  list-style: none;
  padding: 0;
  margin: 0 0 20px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.card-points li {
  position: relative;
  padding-left: 16px;
  font-size: 12.5px;
  color: #6b6b80;
}

.card-points li::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 7px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #d4d4e0;
}

.card-rag:hover .card-points li::before { background: #6d5be8; }
.card-agent:hover .card-points li::before { background: #3b82f6; }

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
  border-top: 1px solid #eeeef4;
  margin-top: auto;
}

.path {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  color: #a0a0b2;
}

.go {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 500;
  color: #6b6b80;
  transition: color 0.2s ease;
}

.go svg {
  transition: transform 0.22s ease;
}

.card:hover .go {
  color: #14141b;
}

.card:hover .go svg {
  transform: translateX(3px);
}

.features {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 30px;
  margin-top: 38px;
  animation: rise 0.6s 0.24s ease both;
}

.features span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #7e7e92;
}

.features i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #cfcfdc;
}

.footer {
  position: relative;
  z-index: 2;
  text-align: center;
  padding: 28px 0 30px;
  font-size: 11px;
  letter-spacing: 0.6px;
  color: #a8a8ba;
}

@keyframes rise {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 900px) {
  .title { font-size: 32px; }
  .entries { grid-template-columns: 1fr; }
  .main { padding: 48px 22px 30px; }
  .nav { padding: 0 22px; }
}
</style>
