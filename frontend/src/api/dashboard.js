/**
 * @fileoverview 仪表盘数据 API 接口
 * @description 封装数据看板相关的接口，包括概览统计、图表数据、站点/订单/商品列表、
 *              站点收录历史、按域名查询订单等。
 */

import request from '@/utils/request'

/**
 * 获取仪表盘概览统计数据
 * @returns {Promise<Object>} 返回 total_sites, total_orders, total_products, today_orders 等概览指标
 */
export function getOverview(params) {
  return request.get('/admin/dashboard/overview', { params })
}

/**
 * 获取图表趋势数据
 * @returns {Promise<Object>} 返回 order_trend, index_trend 等图表数据
 */
export function getCharts(params) {
  return request.get('/admin/dashboard/charts', { params })
}

/** 获取个人绩效、组长提成和建站月度转化率。 */
export function getRevenueSummary(params) {
  return request.get('/admin/dashboard/revenue-summary', { params })
}

/**
 * 获取站点列表（分页）
 * @param {Object} params - 查询参数 { page, size, adminName, domain, startDate, endDate }
 * @returns {Promise<Object>} 返回 { total, list } 分页结果
 */
export function getSites(params) {
  return request.get('/admin/dashboard/sites', { params })
}

/**
 * 获取订单列表（分页）
 * @param {Object} params - 查询参数 { page, size, orderId, domain, adminName, payStatus, currency, country, startDate, endDate }
 * @returns {Promise<Object>} 返回 { total, list } 分页结果
 */
export function getOrders(params) {
  return request.get('/admin/dashboard/orders', { params })
}

/** 管理员清空全部订单（包含 A/B 两个用户组）。 */
export function clearAllOrders() {
  return request.delete('/admin/dashboard/orders/clear')
}

/**
 * 获取商品列表（分页）
 * @param {Object} params - 查询参数 { page, size, domain[], category[], productCategory[], name }
 * @returns {Promise<Object>} 返回 { total, list } 分页结果
 */
export function getProducts(params) {
  return request.get('/admin/dashboard/products', { params })
}

/** Cursor-based workspace APIs; list/count/export share one filter contract. */
export function searchProducts(data) {
  return request.post('/admin/dashboard/products/search', data, { timeout: 25000 })
}
export function countFilteredProducts(data) {
  return request.post('/admin/dashboard/products/count', data, { timeout: 12000 })
}
export function getProductDomainOptions(keyword) {
  return request.get('/admin/dashboard/products/domain-options', { params: { keyword }, timeout: 7000 })
}
export function createProductExport(data) {
  return request.post('/admin/dashboard/product-exports', data)
}
export function listProductExports() {
  return request.get('/admin/dashboard/product-exports')
}
export function cancelProductExport(id) {
  return request.post(`/admin/dashboard/product-exports/${id}/cancel`)
}
export function getProductExportDownload(id) {
  return request.post(`/admin/dashboard/product-exports/${id}/ticket`)
}

/** Delete selected products and their crawl fingerprints. */
export function deleteProducts(ids) {
  return request.delete('/admin/dashboard/products', { data: ids })
}

/** Delete up to 500 newest products in the applied workspace filter and snapshot. */
export function deleteFilteredProductBatch(data) {
  return request.post('/admin/dashboard/products/delete-batch', data, { timeout: 30000 })
}

/** Delete all products matching the current list filters. */
export function clearProducts(params) {
  return request.delete('/admin/dashboard/products/clear', { params })
}

/** Download products in a destination engine's native import CSV layout. */
export function exportProducts(params) {
  return request.get('/admin/dashboard/products/export', { params, responseType: 'blob' })
}

/** Download normalized crawler fields as an XLSX workbook. */
export function exportProductsExcel(params) {
  return request.get('/admin/dashboard/products/export/excel', { params, responseType: 'blob' })
}

/**
 * 获取指定站点的 Google 收录指数历史
 * @param {Object} params - 查询参数 { domain }
 * @returns {Promise<Object>} 返回收录数据数组 [{ date, index_count, product_count }, ...]
 */
export function getSiteIndexHistory(params) {
  return request.get('/admin/dashboard/site-index-history', { params })
}

/** 获取按站点、建站者或服务器聚合的最新收录数据。 */
export function getSiteIndexes(params) {
  return request.get('/admin/dashboard/site-indexes', { params })
}

/**
 * 根据域名查询关联订单列表
 * @param {Object} params - 查询参数 { domain, page, size, startDate, endDate }
 * @returns {Promise<Object>} 返回 { total, list } 分页结果
 */
export function getOrdersByDomain(params) {
  return request.get('/admin/dashboard/orders-by-domain', { params })
}
