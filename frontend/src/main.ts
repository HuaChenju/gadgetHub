import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { auth } from './state/auth'
import { installSessionHandling } from './router/session'
import './style.css'

installSessionHandling(router)
void auth.restoreSession()
createApp(App).use(router).mount('#app')
