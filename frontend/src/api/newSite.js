import request from '@/utils/request'

export function listNewSites(params) {
  return request.get('/admin/new-site', { params })
}

export function getNewSiteOptions() {
  return request.get('/admin/new-site/options')
}

export function getNewSiteAiConfig() {
  return request.get('/admin/new-site/ai-config')
}

export function updateNewSiteAiConfig(data) {
  return request.put('/admin/new-site/ai-config', data)
}

export function createNewSites(sites) {
  // Domain generation calls the AI provider and RDAP sequentially. Keep the
  // normal API timeout short while allowing this long-running operation to finish.
  return request.post('/admin/new-site/batch', { sites }, { timeout: 10 * 60 * 1000 })
}

export function updateNewSiteStatus(id, status) {
  return request.put(`/admin/new-site/${id}/status`, { status })
}

export function deleteNewSite(id) {
  return request.delete(`/admin/new-site/${id}`)
}
