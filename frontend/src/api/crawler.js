import request from '@/utils/request'

export function triggerSiteCrawler(username, password) {
  return request.post('/admin/crawler/site/start', { username, password })
}

export function triggerCollectCrawler(username, password) {
  return request.post('/admin/crawler/site/collect', { username, password })
}

export function triggerOrderCrawler(startTime, endTime) {
  return request.post('/admin/crawler/order/start', { start_time: startTime, end_time: endTime })
}

export function getTaskStatus(taskId) {
  return request.get(`/admin/crawler/status/${taskId}`)
}

export function getRecentTasks() {
  return request.get('/admin/crawler/tasks')
}
