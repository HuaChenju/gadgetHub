import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createMemoryHistory, createRouter } from 'vue-router'
import { api, setUnauthorizedHandler } from '../src/api/client.ts'
import { auth, createAuthStore } from '../src/state/auth.ts'
import { installSessionHandling } from '../src/router/session.ts'

test('session expiry clears authentication and redirects, while guest checks, invalid credentials and other failures do not', async () => {
  const originalFetch = globalThis.fetch
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: {} },
      { path: '/login', name: 'login', component: {} },
      { path: '/catalog', name: 'catalog', component: {} },
    ],
  })
  installSessionHandling(router)
  const unauthorized = () => Response.json({ code: 'UNAUTHORIZED', message: 'Войдите в систему', fieldErrors: {} }, { status: 401 })
  try {
    await router.push('/')
    globalThis.fetch = async () => unauthorized()
    await auth.restoreSession()
    assert.equal(router.currentRoute.value.name, 'home')
    assert.equal(auth.isAuthenticated.value, false)

    globalThis.fetch = async () => Response.json({ id: 1, login: 'student' })
    await auth.login('student', 'student')
    await router.push('/catalog')
    globalThis.fetch = async () => Response.json({ code: 'INVALID_CREDENTIALS', message: 'Неверный пароль', fieldErrors: {} }, { status: 401 })
    await assert.rejects(auth.login('student', 'wrong'))
    assert.equal(auth.isAuthenticated.value, true)

    globalThis.fetch = async () => new Response('Unavailable', { status: 500 })
    await assert.rejects(api.get('/goods'))
    globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
    await assert.rejects(api.get('/goods'))
    assert.equal(auth.isAuthenticated.value, true)
    assert.equal(router.currentRoute.value.name, 'catalog')

    const redirected = new Promise(resolve => {
      const stop = router.afterEach(to => {
        if (to.name === 'login') {
          stop()
          resolve()
        }
      })
    })
    globalThis.fetch = async () => unauthorized()
    await assert.rejects(api.post('/cart/calculate', { items: [] }))
    await redirected
    assert.equal(auth.isAuthenticated.value, false)
    assert.equal(router.currentRoute.value.name, 'login')
    assert.equal(auth.state.error, null)
  } finally {
    setUnauthorizedHandler(null)
    auth.clearSession()
    globalThis.fetch = originalFetch
  }
})

test('a pending session check cannot restore the user after session expiry', async () => {
  const originalFetch = globalThis.fetch
  const store = createAuthStore()
  let complete
  globalThis.fetch = async () => new Promise(resolve => { complete = resolve })
  try {
    const pending = store.restoreSession()
    store.clearSession()
    complete(Response.json({ id: 1, login: 'student' }))
    await pending
    assert.equal(store.isAuthenticated.value, false)
    assert.equal(store.state.isInitialized, true)
    assert.equal(store.state.isLoading, false)
  } finally {
    globalThis.fetch = originalFetch
  }
})
