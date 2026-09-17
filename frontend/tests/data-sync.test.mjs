import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

const read = path => readFile(new URL(`../${path}`, import.meta.url), 'utf8')

test('schedule page acts as a lightweight data-sync console', async () => {
  const [view, api] = await Promise.all([
    read('src/views/crawler/ScheduleTask.vue'),
    read('src/api/crawler.js'),
  ])

  assert.match(view, /同步控制台/)
  assert.match(view, /今日成功/)
  assert.match(view, /今日失败/)
  assert.match(view, /latestTasks/)
  assert.match(view, /activeByType/)
  assert.match(view, /isActive\(row\.taskType\)/)
  assert.match(view, /window\.setTimeout\(loadOverview, 8000\)/)
  assert.match(api, /task-history\/overview/)
})

test('task history supports scoped search and demand-driven refresh', async () => {
  const view = await read('src/views/crawler/TaskHistory.vue')

  assert.match(view, /status: statusFilter\.value/)
  assert.match(view, /keyword: appliedKeyword\.value/)
  assert.match(view, /执行中自动刷新/)
  assert.match(view, /Number\(overview\.active \|\| 0\) > 0/)
  assert.match(view, /listRequestId/)
  assert.match(view, /耗时/)
})

test('order groups use independent activity scopes and revenue settings validate before saving', async () => {
  const order = await read('src/views/crawler/SiteCrawler.vue')
  const revenue = await read('src/views/crawler/RevenueConfig.vue')
  const overview = await read('src/views/dashboard/Overview.vue')
  assert.match(order, /activeByScope/)
  assert.match(order, /isGroupActive\(group\)/)
  assert.match(revenue, /hasChanges/)
  assert.match(revenue, /buildPayload\(\)/)
  assert.match(revenue, /提成比例采用固定业务规则/)
  assert.match(revenue, /3% \/ 5% \/ 8%/)
  assert.match(revenue, /2% \/ 4% \/ 6%/)
  assert.doesNotMatch(revenue, /leaderCommissionRate|commissionTiersText/)
  assert.doesNotMatch(revenue, /form\.batchSiteCommissionRate/)
  assert.match(overview, /Promise\.allSettled/)
  assert.match(overview, /dashboardRequestId/)
  assert.match(overview, /batch_site_commission_rate/)
})

test('site indexing and order sync share one page with independent task state', async () => {
  const site = await read('src/views/crawler/SiteCrawler.vue')
  const collect = await read('src/views/crawler/CollectCrawler.vue')
  const order = await read('src/views/crawler/OrderCrawler.vue')
  assert.match(site, /站点、收录与订单同步/)
  assert.match(site, /triggerSiteCrawler/)
  assert.match(site, /triggerCollectCrawler/)
  assert.match(site, /triggerOrderCrawler/)
  assert.match(site, /siteTaskActive/)
  assert.match(site, /indexTaskActive/)
  assert.match(site, /const siteProgress = useTaskProgress\(\)/)
  assert.match(site, /const indexProgress = useTaskProgress\(\)/)
  assert.match(site, /orderProgress = useTaskProgress\(\)/)
  assert.match(site, /activeByScope/)
  assert.match(site, /\['PENDING', 'RUNNING', 'PAUSED'\]/)
  assert.match(collect, /<SiteCrawler/)
  assert.match(order, /<SiteCrawler/)

  const progress = await read('src/composables/useTaskProgress.js')
  assert.match(progress, /consecutiveFailures < 3/)
  assert.match(progress, /task\.value\.state === 'PAUSED' \? 8000 : 2000/)
})
