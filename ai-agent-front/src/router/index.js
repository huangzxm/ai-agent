import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/cs',
    name: 'CustomerService',
    component: () => import('../views/CsChat.vue')
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/cs'
  }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL || '/'),
  routes
})

export default router
