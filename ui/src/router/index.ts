import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../pages/HomePage.vue'
import ViewerPage from '../pages/ViewerPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomePage },
    { path: '/viewer/:documentId', component: ViewerPage, props: true },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

export default router
