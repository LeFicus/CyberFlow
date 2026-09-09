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

export function getNewSiteImageAiConfig() {
  return request.get('/admin/new-site/image-ai-config')
}

export function updateNewSiteImageAiConfig(data) {
  return request.put('/admin/new-site/image-ai-config', data)
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

export function listNewSiteAssets(siteId) {
  return request.get(`/admin/new-site/${siteId}/assets`)
}

export function generateNewSiteAssets(siteId) {
  return request.post(`/admin/new-site/${siteId}/assets/generate`)
}

export function selectNewSiteAsset(siteId, assetId) {
  return request.put(`/admin/new-site/${siteId}/assets/${assetId}/select`)
}

export function getNewSiteAssetContent(assetId) {
  return request.get(`/admin/new-site/assets/${assetId}/content`, { responseType: 'blob', timeout: 60_000 })
}

export function downloadNewSiteAssetPack(siteId) {
  return request.get(`/admin/new-site/${siteId}/assets/download`, { responseType: 'blob', timeout: 60_000 })
}
