import assert from 'node:assert/strict'
import { test } from 'node:test'
import { api, ApiError, NetworkError } from '../src/api/client.ts'

test('requests include cookies, encode repeated filters, send JSON and accept 204', async () => {
  const originalFetch = globalThis.fetch
  const requests = []
  const responses = [
    Response.json({ items: [] }),
    Response.json({ id: 1, login: 'student' }),
    new Response(null, { status: 204 }),
  ]
  globalThis.fetch = async (url, options) => {
    requests.push({ url: new URL(url), ...options })
    return responses.shift()
  }
  try {
    assert.deepEqual(await api.get('/goods', { colors: ['Белый', 'Черный'], minPrice: 0, maxPrice: null }), { items: [] })
    assert.equal(requests[0].url.origin, 'http://localhost:8080')
    assert.equal(requests[0].url.pathname, '/api/goods')
    assert.deepEqual(requests[0].url.searchParams.getAll('colors'), ['Белый', 'Черный'])
    assert.equal(requests[0].url.searchParams.get('minPrice'), '0')
    assert.equal(requests[0].url.searchParams.has('maxPrice'), false)
    assert.equal(requests[0].credentials, 'include')
    assert.equal(requests[0].headers['Content-Type'], undefined)

    await api.post('/auth/login', { login: 'student', password: 'student' })
    assert.equal(requests[1].method, 'POST')
    assert.equal(requests[1].credentials, 'include')
    assert.equal(requests[1].headers['Content-Type'], 'application/json')
    assert.deepEqual(JSON.parse(requests[1].body), { login: 'student', password: 'student' })
    assert.equal(await api.post('/auth/logout'), undefined)
    assert.equal(requests[2].body, undefined)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('HTTP errors retain contract fields while network and malformed responses are distinguished', async () => {
  const originalFetch = globalThis.fetch
  try {
    globalThis.fetch = async () => Response.json({
      code: 'VALIDATION_ERROR', message: 'Проверьте заполненные поля', fieldErrors: { phone: 'Заполните обязательное поле' },
    }, { status: 400 })
    await assert.rejects(api.post('/orders', {}), error => {
      assert.ok(error instanceof ApiError)
      assert.equal(error.status, 400)
      assert.equal(error.code, 'VALIDATION_ERROR')
      assert.deepEqual(error.fieldErrors, { phone: 'Заполните обязательное поле' })
      return true
    })

    globalThis.fetch = async () => Response.json({
      code: 'UNAUTHORIZED', message: 'Для выполнения действия войдите в систему', fieldErrors: {},
    }, { status: 401 })
    await assert.rejects(api.get('/auth/me'), error => error instanceof ApiError && error.status === 401 && error.code === 'UNAUTHORIZED')

    globalThis.fetch = async () => new Response('<html>Internal server details</html>', { status: 500 })
    await assert.rejects(api.get('/orders'), error => error instanceof ApiError && error.status === 500 && !error.message.includes('Internal server details'))

    globalThis.fetch = async () => new Response('invalid JSON')
    await assert.rejects(api.get('/goods'), error => error instanceof ApiError && error.code === 'INVALID_RESPONSE')

    globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
    await assert.rejects(api.get('/auth/me'), NetworkError)
  } finally {
    globalThis.fetch = originalFetch
  }
})
