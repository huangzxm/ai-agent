import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/cs/rag',
    name: 'CsRag',
    component: () => import('../views/CsChat.vue'),
    props: { mode: 'RAG' }
  },
  {
    path: '/cs/agent',
    name: 'CsAgent',
    component: () => import('../views/CsChat.vue'),
    props: { mode: 'AGENT' }
  },
  {
    path: '/cs',
    redirect: '/cs/rag'
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL || '/'),
  routes
})

export default router
