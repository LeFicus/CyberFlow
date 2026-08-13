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
export function getOverview() {
  return request.get('/admin/dashboard/overview')
}

/**
 * 获取图表趋势数据
 * @returns {Promise<Object>} 返回 order_trend, index_trend 等图表数据
 */
export function getCharts() {
  return request.get('/admin/dashboard/charts')
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

/**
 * 获取商品列表（分页）
 * @param {Object} params - 查询参数 { page, size, domain, ... }
 * @returns {Promise<Object>} 返回 { total, list } 分页结果
 */
export function getProducts(params) {
  return request.get('/admin/dashboard/products', { params })
}

/** Download products in a destination engine's native import CSV layout. */
export function exportProducts(params) {
  return request.get('/admin/dashboard/products/export', { params, responseType: 'blob' })
}

/**
 * 获取指定站点的 Google 收录指数历史
 * @param {Object} params - 查询参数 { domain }
 * @returns {Promise<Object>} 返回收录数据数组 [{ date, index_count, product_count }, ...]
 */
export function getSiteIndexHistory(params) {
  return request.get('/admin/dashboard/site-index-history', { params })
}

/**
 * 根据域名查询关联订单列表
 * @param {Object} params - 查询参数 { domain, page, size, startDate, endDate }
 * @returns {Promise<Object>} 返回 { total, list } 分页结果
 */
export function getOrdersByDomain(params) {
  return request.get('/admin/dashboard/orders-by-domain', { params })
}
