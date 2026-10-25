import assert from 'node:assert/strict'
import { test } from 'node:test'
import { CART_STORAGE_KEY, createCartStore } from '../src/state/cart.ts'

function memoryStorage(initial) {
  const data = new Map(initial ? [[CART_STORAGE_KEY, initial]] : [])
  return {
    getItem: key => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value),
    removeItem: key => data.delete(key),
  }
}

test('cart restores its contents, uses the server count, persists changes and preserves storage on logout or errors', async () => {
  const originalFetch = globalThis.fetch
  const initial = JSON.stringify([{ productId: 1, quantity: 2 }, { productId: 13, quantity: 1 }])
  const storage = memoryStorage(initial)
  const cart = createCartStore(() => storage)
  try {
    globalThis.fetch = async (url, options) => {
      assert.equal(new URL(url).pathname, '/api/cart/calculate')
      assert.equal(options.credentials, 'include')
      assert.deepEqual(JSON.parse(options.body), { items: JSON.parse(initial) })
      return Response.json({ totalQuantity: 3 })
    }
    await cart.restore()
    assert.equal(cart.state.totalQuantity, 3)
    cart.reset()
    assert.equal(storage.getItem(CART_STORAGE_KEY), initial)
    assert.equal(cart.state.totalQuantity, null)

    globalThis.fetch = async () => Response.json({ totalQuantity: 7 })
    await cart.setItems([{ productId: 1, quantity: 4 }])
    assert.equal(cart.state.totalQuantity, 7)
    assert.equal(storage.getItem(CART_STORAGE_KEY), '[{"productId":1,"quantity":4}]')

    globalThis.fetch = async () => { throw new TypeError('Failed to fetch') }
    await cart.setItems([{ productId: 1, quantity: 5 }])
    assert.equal(cart.state.totalQuantity, null)
    assert.ok(cart.state.error)
    assert.equal(storage.getItem(CART_STORAGE_KEY), '[{"productId":1,"quantity":5}]')

    storage.setItem(CART_STORAGE_KEY, 'broken JSON')
    await cart.restore()
    assert.ok(cart.state.error)
    assert.equal(storage.getItem(CART_STORAGE_KEY), 'broken JSON')

    globalThis.fetch = async () => Response.json({ totalQuantity: 0 })
    await cart.clear()
    assert.equal(storage.getItem(CART_STORAGE_KEY), null)
    assert.equal(cart.state.totalQuantity, 0)
    assert.equal(cart.state.error, null)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('older calculations cannot overwrite a newer cart count or restore it after logout', async () => {
  const originalFetch = globalThis.fetch
  const cart = createCartStore(() => memoryStorage())
  const responses = []
  globalThis.fetch = async () => new Promise(resolve => responses.push(resolve))
  try {
    const first = cart.setItems([{ productId: 1, quantity: 1 }])
    const second = cart.setItems([{ productId: 1, quantity: 2 }])
    responses[1](Response.json({ totalQuantity: 2 }))
    await second
    responses[0](Response.json({ totalQuantity: 1 }))
    await first
    assert.equal(cart.state.totalQuantity, 2)

    const pending = cart.refresh()
    cart.reset()
    responses[2](Response.json({ totalQuantity: 2 }))
    await pending
    assert.equal(cart.state.totalQuantity, null)
    assert.equal(cart.state.isLoading, false)
  } finally {
    globalThis.fetch = originalFetch
  }
})
