/**
 * Auth storage helpers.
 * "Remember me" decides whether the session survives a browser restart:
 * localStorage for persistent sessions, sessionStorage for tab-only sessions.
 */

const TOKEN_KEY = 'token'
const USER_KEY = 'userInfo'

export function readToken() {
  return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || ''
}

export function readUserInfo() {
  return localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY) || '{}'
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
}

/**
 * Replace the stored session. A token write intentionally drops stale user info.
 * @param {{ token?: string, userInfo?: Object, persist?: boolean }} payload
 */
export function writeAuth({ token, userInfo, persist = true }) {
  clearAuth()
  const storage = persist ? localStorage : sessionStorage
  if (token) storage.setItem(TOKEN_KEY, token)
  if (userInfo !== undefined) storage.setItem(USER_KEY, JSON.stringify(userInfo))
}
