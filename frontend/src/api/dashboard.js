import request from '@/utils/request'

export function getOverview() {
  return request.get('/admin/dashboard/overview')
}

export function getCharts() {
  return request.get('/admin/dashboard/charts')
}

export function getSites(params) {
  return request.get('/admin/dashboard/sites', { params })
}

export function getOrders(params) {
  return request.get('/admin/dashboard/orders', { params })
}

export function getProducts(params) {
  return request.get('/admin/dashboard/products', { params })
}

export function getSiteIndexHistory(params) {
  return request.get('/admin/dashboard/site-index-history', { params })
}

export function getOrdersByDomain(params) {
  return request.get('/admin/dashboard/orders-by-domain', { params })
}
