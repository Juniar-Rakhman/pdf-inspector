import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import 'primeflex/primeflex.css'
import 'primeicons/primeicons.css'
import './style.css'
import App from './App.vue'
import router from './router'
import './lib/pdfjs'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(PrimeVue, { theme: 'none' })
app.mount('#app')
