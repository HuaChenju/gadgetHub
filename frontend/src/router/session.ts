import type { Router } from 'vue-router'
import { setUnauthorizedHandler } from '../api/client.ts'
import { auth } from '../state/auth.ts'

export function installSessionHandling(router: Router) {
  setUnauthorizedHandler(path => {
    const wasAuthenticated = auth.isAuthenticated.value
    auth.clearSession()
    if (path.endsWith('/auth/me') && !wasAuthenticated) return
    if (router.currentRoute.value.name !== 'login') {
      void router.replace({ name: 'login' })
    }
  })
}
