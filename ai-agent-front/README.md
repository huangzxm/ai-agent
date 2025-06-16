# AI 智能体应用前端

这是一个基于Vue3的AI智能体应用前端项目，包含两个主要功能：AI恋爱大师和AI超级智能体。

## 功能特点

- 主页：用于切换不同的AI应用
- AI恋爱大师：通过SSE实时聊天，提供情感咨询服务
- AI超级智能体：通过SSE实时聊天，解决各种问题

## 技术栈

- Vue3
- Vue Router
- Axios
- Server-Sent Events (SSE)

## 安装与运行

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 预览生产版本
npm run preview
```

## 后端接口

项目需要连接到后端服务，后端服务地址为：`http://localhost:8123/api`

## 文件结构

```
ai-agent-front/
├── public/            # 静态资源
├── src/
│   ├── api/           # API接口服务
│   ├── assets/        # 静态资源
│   ├── components/    # 组件
│   ├── router/        # 路由配置
│   ├── views/         # 页面视图
│   ├── App.vue        # 根组件
│   └── main.js        # 入口文件
├── index.html         # HTML模板
└── vite.config.js     # Vite配置
```

## Recommended IDE Setup

[VSCode](https://code.visualstudio.com/) + [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).
