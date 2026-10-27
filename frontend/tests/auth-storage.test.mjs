import assert from 'node:assert/strict'
import { test, beforeEach } from 'node:test'

function makeStorage() {
  const data = new Map()
  return {
    getItem: key => data.has(key) ? data.get(key) : null,
    setItem: (key, value) => data.set(key, String(value)),
    removeItem: key => data.delete(key),
    clear: () => data.clear(),
  }
}

globalThis.localStorage = makeStorage()
globalThis.sessionStorage = makeStorage()

const { clearAuth, readToken, readUserInfo, writeAuth } = await import('../src/utils/authStorage.js')

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})

test('remember me stores the session in localStorage', () => {
  writeAuth({ token: 'persistent-token', userInfo: { username: 'admin' }, persist: true })
  assert.equal(localStorage.getItem('token'), 'persistent-token')
  assert.equal(sessionStorage.getItem('token'), null)
  assert.equal(readToken(), 'persistent-token')
  assert.deepEqual(JSON.parse(readUserInfo()), { username: 'admin' })
})

test('unchecked remember me stores the session in sessionStorage only', () => {
  writeAuth({ token: 'tab-token', userInfo: { username: 'admin' }, persist: false })
  assert.equal(localStorage.getItem('token'), null)
  assert.equal(sessionStorage.getItem('token'), 'tab-token')
  assert.equal(readToken(), 'tab-token')
})

test('writing a new token drops the previous user info from both stores', () => {
  writeAuth({ token: 'old', userInfo: { username: 'old' }, persist: true })
  writeAuth({ token: 'new', persist: true })
  assert.equal(localStorage.getItem('token'), 'new')
  assert.equal(localStorage.getItem('userInfo'), null)
  assert.equal(sessionStorage.getItem('userInfo'), null)
})

test('clearAuth removes both storages', () => {
  writeAuth({ token: 'persistent', userInfo: { username: 'a' }, persist: true })
  writeAuth({ token: 'session', userInfo: { username: 'b' }, persist: false })
  clearAuth()
  assert.equal(readToken(), '')
  assert.equal(readUserInfo(), '{}')
  assert.equal(localStorage.getItem('userInfo'), null)
  assert.equal(sessionStorage.getItem('userInfo'), null)
})
