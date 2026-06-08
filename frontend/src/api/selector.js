import request from '@/utils/request'

// 选择器模板
export function listTemplates(platform) {
  return request.get('/admin/selector/template', { params: { platform } })
}

export function createTemplate(data) {
  return request.post('/admin/selector/template', data)
}

export function updateTemplate(id, data) {
  return request.put(`/admin/selector/template/${id}`, data)
}

export function deleteTemplate(id) {
  return request.delete(`/admin/selector/template/${id}`)
}

export function cloneTemplate(id) {
  return request.post(`/admin/selector/template/${id}/clone`)
}

// 站点配置 (商品爬取)
export function listSiteConfigs() {
  return request.get('/admin/crawler/site-config')
}

export function getSiteConfig(id) {
  return request.get(`/admin/crawler/site-config/${id}`)
}

export function createSiteConfig(data) {
  return request.post('/admin/crawler/site-config', data)
}

export function triggerSiteCrawl(id, userId) {
  return request.post(`/admin/crawler/site-config/${id}/crawl`, { user_id: String(userId) })
}

export function deleteSiteConfig(id) {
  return request.delete(`/admin/crawler/site-config/${id}`)
}
