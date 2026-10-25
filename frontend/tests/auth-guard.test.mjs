import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createMemoryHistory, createRouter } from 'vue-router'
import { requireSession } from '../src/router/authGuard.ts'
import { auth } from '../src/state/auth.ts'

test('protected navigation checks the server, redirects guests and expired sessions, and blocks access on connection failure', async () => {
  const originalFetch = globalThis.fetch
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: {} },
      { path: '/login', name: 'login', component: {} },
      { path: '/catalog', name: 'catalog', component: {}, meta: { requiresAuth: true } },
      { path: '/cart', name: 'cart', component: {}, meta: { requiresAuth: true } },
    ],
  })
  router.beforeEach(requireSession)
  let signedIn = false
  let calls = 0
  globalThis.fetch = async (url, options) => {
    calls++
    assert.equal(new URL(url).pathname, '/api/auth/me')
    assert.equal(options.credentials, 'include')
    return signedIn
      ? Response.json({ id: 1, login: 'student' })
      : Response.json({ code: 'UNAUTHORIZED', message: 'Войдите в систему', fieldErrors: {} }, { status: 401 })
  }
  try {
    await router.push('/')
    assert.equal(calls, 0)
    await router.push('/catalog')
    assert.equal(router.currentRoute.value.name, 'login')
    await router.push('/cart')
    assert.equal(router.currentRoute.value.name, 'login')

    signedIn = true
    await router.push('/catalog')
    assert.equal(router.currentRoute.value.name, 'catalog')
    await router.push('/cart')
    assert.equal(router.currentRoute.value.name, 'cart')
    assert.equal(auth.isAuthenticated.value, true)

    signedIn = false
    await router.push('/catalog')
    assert.equal(router.currentRoute.value.name, 'login')
    assert.equal(auth.isAuthenticated.value, false)

    globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
    await router.push('/cart')
    assert.equal(router.currentRoute.value.name, 'login')
    assert.ok(auth.state.error)
  } finally {
    globalThis.fetch = originalFetch
  }
})
