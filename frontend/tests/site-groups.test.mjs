import assert from 'node:assert/strict'
import fs from 'node:fs'
import test from 'node:test'

const read = path => fs.readFileSync(new URL(`../${path}`, import.meta.url), 'utf8')

test('site platform is configured once and UI groups come from site_info', () => {
  const siteCrawler = read('src/views/crawler/SiteCrawler.vue')
  const orderCrawler = read('src/views/crawler/OrderCrawler.vue')
  const groupComposable = read('src/composables/useSiteGroups.js')
  const dashboardApi = read('src/api/dashboard.js')

  assert.match(siteCrawler, /form\.adminApi/)
  assert.doesNotMatch(siteCrawler, /adminApiAB|adminApiCD|采集 AB 平台|采集 CD 平台/)
  assert.match(groupComposable, /getSiteGroups/)
  assert.match(dashboardApi, /\/admin\/dashboard\/site-groups/)
  assert.match(orderCrawler, /v-for="item in siteGroups"/)
  assert.doesNotMatch(orderCrawler, /\['A',\s*'B',\s*'C',\s*'D'\]/)
})
