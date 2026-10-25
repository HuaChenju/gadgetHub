import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createAuthStore } from '../src/state/auth.ts'
import { ApiError } from '../src/api/client.ts'

test('login updates shared user only after server success and preserves state on failure', async () => {
  const originalFetch = globalThis.fetch
  const auth = createAuthStore()
  try {
    globalThis.fetch = async (url, options) => {
      assert.equal(new URL(url).pathname, '/api/auth/login')
      assert.equal(options.method, 'POST')
      assert.equal(options.credentials, 'include')
      assert.deepEqual(JSON.parse(options.body), { login: 'student', password: 'student' })
      return Response.json({ id: 1, login: 'student' })
    }
    await auth.login('student', 'student')
    assert.deepEqual(auth.state.user, { id: 1, login: 'student' })
    assert.equal(auth.state.isInitialized, true)
    assert.equal(auth.isAuthenticated.value, true)

    globalThis.fetch = async () => Response.json({
      code: 'INVALID_CREDENTIALS', message: 'Неправильный логин или пароль', fieldErrors: {},
    }, { status: 401 })
    await assert.rejects(auth.login('student', 'wrong'), error => error instanceof ApiError && error.code === 'INVALID_CREDENTIALS')
    assert.deepEqual(auth.state.user, { id: 1, login: 'student' })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('session restoration shares one request, restores user and recognizes guest on 401', async () => {
  const originalFetch = globalThis.fetch
  const auth = createAuthStore()
  let complete
  let calls = 0
  globalThis.fetch = async (url, options) => {
    calls++
    assert.equal(new URL(url).pathname, '/api/auth/me')
    assert.equal(options.credentials, 'include')
    return new Promise(resolve => { complete = resolve })
  }
  try {
    const first = auth.restoreSession()
    const second = auth.restoreSession()
    assert.equal(first, second)
    assert.equal(calls, 1)
    assert.equal(auth.state.isLoading, true)
    assert.equal(auth.state.isInitialized, false)

    complete(Response.json({ id: 1, login: 'student' }))
    await first
    assert.deepEqual(auth.state.user, { id: 1, login: 'student' })
    assert.equal(auth.isAuthenticated.value, true)
    assert.equal(auth.state.isInitialized, true)
    assert.equal(auth.state.isLoading, false)

    globalThis.fetch = async () => Response.json({
      code: 'UNAUTHORIZED', message: 'Для выполнения действия войдите в систему', fieldErrors: {},
    }, { status: 401 })
    await auth.restoreSession()
    assert.equal(auth.state.user, null)
    assert.equal(auth.state.error, null)
    assert.equal(auth.state.isInitialized, true)
    assert.equal(auth.isAuthenticated.value, false)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('connection failure leaves status unknown and failed recheck preserves restored user', async () => {
  const originalFetch = globalThis.fetch
  const auth = createAuthStore()
  try {
    globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
    await auth.restoreSession()
    assert.equal(auth.state.isInitialized, false)
    assert.equal(auth.state.isLoading, false)
    assert.ok(auth.state.error)

    globalThis.fetch = async () => Response.json({ id: 1, login: 'student' })
    await auth.restoreSession()
    assert.equal(auth.state.error, null)
    assert.equal(auth.state.isInitialized, true)
    assert.equal(auth.isAuthenticated.value, true)

    globalThis.fetch = async () => new Response('Server unavailable', { status: 500 })
    await auth.restoreSession()
    assert.deepEqual(auth.state.user, { id: 1, login: 'student' })
    assert.equal(auth.isAuthenticated.value, true)
    assert.ok(auth.state.error)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('logout keeps the user on failure and clears the session state after server success', async () => {
  const originalFetch = globalThis.fetch
  const auth = createAuthStore()
  try {
    globalThis.fetch = async () => Response.json({ id: 1, login: 'student' })
    await auth.login('student', 'student')
    globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
    await assert.rejects(auth.logout())
    assert.equal(auth.isAuthenticated.value, true)
    assert.equal(auth.state.isLoggingOut, false)

    let complete
    let calls = 0
    globalThis.fetch = async (url, options) => {
      calls++
      assert.equal(new URL(url).pathname, '/api/auth/logout')
      assert.equal(options.method, 'POST')
      assert.equal(options.credentials, 'include')
      return new Promise(resolve => { complete = resolve })
    }
    const pending = auth.logout()
    await auth.logout()
    assert.equal(calls, 1)
    assert.equal(auth.state.isLoggingOut, true)
    assert.equal(auth.isAuthenticated.value, true)
    complete(new Response(null, { status: 204 }))
    await pending
    assert.equal(auth.state.user, null)
    assert.equal(auth.isAuthenticated.value, false)
    assert.equal(auth.state.isLoggingOut, false)
  } finally {
    globalThis.fetch = originalFetch
  }
})
