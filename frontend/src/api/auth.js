import request from '@/utils/request'

export function login(username, password) {
  return request.post('/admin/auth/login', { username, password })
}

export function getUserInfo() {
  return request.get('/admin/auth/userinfo')
}
