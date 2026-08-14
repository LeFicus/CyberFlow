/**
 * @fileoverview 爬虫相关 API 接口
 * @description 封装站点爬虫触发、收录统计、订单爬取、任务状态查询及任务历史等接口。
 *              所有接口均通过 @/utils/request 封装的 axios 实例发送请求。
 */

import request from '@/utils/request'

/**
 * 触发站点信息爬取
 * @param {string} username - 管理平台账号
 * @param {string} password - 管理平台密码
 * @returns {Promise<Object>} 返回任务 ID 及下发状态
 */
export function triggerSiteCrawler() {
  return request.post('/admin/crawler/site/start')
}

/**
 * 触发站点收录统计
 * @param {string} username - 管理平台账号
 * @param {string} password - 管理平台密码
 * @returns {Promise<Object>} 返回任务 ID 及下发状态
 */
export function triggerCollectCrawler() {
  return request.post('/admin/crawler/site/collect')
}

/**
 * 触发订单爬取
 * @param {string} startTime - 查询开始时间（格式: YYYY-MM-DD HH:mm:ss）
 * @param {string} endTime - 查询结束时间（格式: YYYY-MM-DD HH:mm:ss）
 * @returns {Promise<Object>} 返回任务 ID 及下发状态
 */
export function triggerOrderCrawler(userGroup) {
  return request.post('/admin/crawler/order/start', null, { params: { userGroup } })
}

export function getCrawlerConfig() {
  return request.get('/admin/crawler/config')
}

export function updateCrawlerConfig(data) {
  return request.put('/admin/crawler/config', data)
}

export function listCrawlerSchedules() {
  return request.get('/admin/crawler/config/schedules')
}

export function updateCrawlerSchedule(taskType, data) {
  return request.put(`/admin/crawler/config/schedules/${taskType}`, data)
}

export function triggerCrawlerSchedule(taskType) {
  return request.post(`/admin/crawler/config/schedules/${taskType}/trigger`)
}

/**
 * 查询单个任务执行状态
 * @param {string} taskId - 任务唯一标识
 * @returns {Promise<Object>} 返回任务当前状态和执行结果
 */
export function getTaskStatus(taskId) {
  return request.get(`/admin/crawler/status/${taskId}`)
}

/**
 * 获取最近的爬虫任务列表
 * @returns {Promise<Object>} 返回最近 20 条任务记录
 */
export function getRecentTasks(params) {
  return request.get('/admin/crawler/task-history/tasks', { params })
}

export function getTaskSummary() {
  return request.get('/admin/crawler/task-history/summary')
}

export function pauseTask(taskId) {
  return request.post(`/admin/crawler/task-history/tasks/${taskId}/pause`)
}

export function resumeTask(taskId) {
  return request.post(`/admin/crawler/task-history/tasks/${taskId}/resume`)
}

export function deleteTask(taskId) {
  return request.delete(`/admin/crawler/task-history/tasks/${taskId}`)
}

/** 增量获取指定任务的爬虫日志。 */
export function getTaskCrawlLog(taskId, params) {
  return request.get(`/admin/crawler/task-history/tasks/${taskId}/log`, { params })
}

/** 下载指定任务的完整日志。 */
export function downloadTaskCrawlLog(taskId) {
  return request.get(`/admin/crawler/task-history/tasks/${taskId}/log/download`, { responseType: 'blob' })
}
