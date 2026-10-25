import type { RouteLocationNormalized } from 'vue-router'
import { auth } from '../state/auth.ts'

export async function requireSession(to: RouteLocationNormalized) {
  if (!to.meta.requiresAuth) return

  await auth.restoreSession()
  if (auth.state.error || !auth.isAuthenticated.value) {
    return { name: 'login' }
  }
}
