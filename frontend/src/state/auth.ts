import { computed, reactive, readonly } from 'vue'
import { api, ApiError, NetworkError } from '../api/client.ts'

export type User = {
  id: number
  login: string
}

type AuthState = {
  user: User | null
  isLoading: boolean
  isInitialized: boolean
  isLoggingOut: boolean
  error: string | null
}

export function createAuthStore() {
  const state = reactive<AuthState>({
    user: null,
    isLoading: false,
    isInitialized: false,
    isLoggingOut: false,
    error: null,
  })
  let pending: Promise<void> | null = null
  let revision = 0

  function clearSession() {
    revision++
    state.user = null
    state.error = null
    state.isInitialized = true
  }

  async function loadSession(): Promise<void> {
    const current = revision
    try {
      const user = await api.get<User>('/auth/me')
      if (current !== revision) return
      state.user = user
      state.isInitialized = true
    } catch (error) {
      if (current !== revision) return
      if (error instanceof ApiError && error.status === 401) {
        state.user = null
        state.isInitialized = true
      } else {
        state.error = error instanceof ApiError || error instanceof NetworkError
          ? error.message
          : 'Не удалось проверить вход. Попробуйте позже.'
      }
    }
  }

  function restoreSession(): Promise<void> {
    if (pending) return pending
    state.isLoading = true
    state.error = null
    pending = loadSession().finally(() => {
      state.isLoading = false
      pending = null
    })
    return pending
  }

  async function login(login: string, password: string): Promise<void> {
    if (pending) await pending
    state.error = null
    const user = await api.post<User>('/auth/login', { login, password })
    revision++
    state.user = user
    state.isInitialized = true
  }

  async function logout(): Promise<void> {
    if (state.isLoggingOut) return
    state.isLoggingOut = true
    try {
      if (pending) await pending
      await api.post('/auth/logout')
      clearSession()
    } finally {
      state.isLoggingOut = false
    }
  }

  return {
    state: readonly(state),
    isAuthenticated: computed(() => state.user !== null),
    restoreSession,
    login,
    logout,
    clearSession,
  }
}

export const auth = createAuthStore()
