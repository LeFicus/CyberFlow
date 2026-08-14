/**
 * @fileoverview 选择器模板 & 站点配置 API 接口
 * @description 封装选择器模板的 CRUD 及克隆操作，以及站点配置（商品爬取）的注册、
 *              爬取触发、删除等接口。
 */

import request from '@/utils/request'

// ==================== 选择器模板 ====================

/**
 * 获取选择器模板列表，可按平台过滤
 * @param {string} [platform] - 可选，模板类型（shopify 或 woocommerce）
 * @returns {Promise<Object>} 返回模板数组
 */
export function listTemplates(params) {
  const query = typeof params === 'string' ? { platform: params } : (params || {})
  return request.get('/admin/selector/template', { params: query })
}

/**
 * 创建选择器模板
 * @param {Object} data - 模板数据 { name, platform, titleSelector, priceSelector, ... }
 * @returns {Promise<Object>} 返回创建后的模板对象
 */
export function createTemplate(data) {
  return request.post('/admin/selector/template', data)
}

/**
 * 更新选择器模板
 * @param {number} id - 模板 ID
 * @param {Object} data - 更新的模板字段
 * @returns {Promise<Object>} 返回更新后的模板对象
 */
export function updateTemplate(id, data) {
  return request.put(`/admin/selector/template/${id}`, data)
}

/**
 * 删除选择器模板
 * @param {number} id - 模板 ID
 * @returns {Promise<Object>} 返回操作结果
 */
export function deleteTemplate(id) {
  return request.delete(`/admin/selector/template/${id}`)
}

/**
 * 克隆选择器模板
 * @param {number} id - 源模板 ID
 * @returns {Promise<Object>} 返回克隆后的新模板对象
 */
export function cloneTemplate(id) {
  return request.post(`/admin/selector/template/${id}/clone`)
}

// ==================== 站点配置（商品爬取） ====================

/**
 * 获取所有站点配置列表
 * @returns {Promise<Object>} 返回站点配置数组
 */
export function listSiteConfigs(params) {
  return request.get('/admin/crawler/site-config', { params })
}

/**
 * 获取单个站点配置详情
 * @param {number} id - 站点配置 ID
 * @returns {Promise<Object>} 返回站点配置数据及关联的模板映射
 */
export function getSiteConfig(id) {
  return request.get(`/admin/crawler/site-config/${id}`)
}

/**
 * 创建站点配置
 * @param {Object} data - 站点配置数据 { config: { domain, type, category }, mappings: [...] }
 * @returns {Promise<Object>} 返回创建后的站点配置
 */
export function createSiteConfig(data) {
  return request.post('/admin/crawler/site-config', data)
}

/**
 * 更新站点配置
 * @param {number} id - 站点配置 ID
 * @param {Object} data - 站点配置及模板映射
 * @returns {Promise<Object>} 返回更新后的站点配置
 */
export function updateSiteConfig(id, data) {
  return request.put(`/admin/crawler/site-config/${id}`, data)
}

/**
 * 触发站点的商品爬取任务
 * @param {number} id - 站点配置 ID
 * @param {number} userId - 触发用户 ID
 * @returns {Promise<Object>} 返回任务 ID 及下发状态
 */
export function triggerSiteCrawl(id, userId) {
  return request.post(`/admin/crawler/site-config/${id}/crawl`, { user_id: String(userId) })
}

/**
 * 删除站点配置
 * @param {number} id - 站点配置 ID
 * @returns {Promise<Object>} 返回操作结果
 */
export function deleteSiteConfig(id) {
  return request.delete(`/admin/crawler/site-config/${id}`)
}
