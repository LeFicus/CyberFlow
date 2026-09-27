import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const page = readFileSync(new URL('../src/views/crawler/SiteHealth.vue', import.meta.url), 'utf8')
const routes = readFileSync(new URL('../src/router/index.js', import.meta.url), 'utf8')
const api = readFileSync(new URL('../src/api/crawler.js', import.meta.url), 'utf8')
const schedules = readFileSync(new URL('../src/views/crawler/ScheduleTask.vue', import.meta.url), 'utf8')
const login = readFileSync(new URL('../src/views/login/index.vue', import.meta.url), 'utf8')
const notificationConfig = readFileSync(new URL('../src/views/system/NotificationConfig.vue', import.meta.url), 'utf8')
const layout = readFileSync(new URL('../src/views/layout/index.vue', import.meta.url), 'utf8')

test('site health is visible, triggerable and integrated with schedules', () => {
  assert.match(routes, /path: 'dashboard\/site-health'.*section: '数据看板'.*perm: 'crawler:health:view'/)
  assert.match(routes, /path: 'crawler\/site-health', redirect: '\/dashboard\/site-health'/)
  assert.match(layout, /menuName: '站点健康检查', path: '\/dashboard\/site-health'/)
  assert.match(api, /\/admin\/crawler\/site-health\/trigger/)
  assert.match(page, /站点健康状态/)
  assert.match(page, /consecutiveFailures/)
  assert.match(page, /normalizeRow/)
  assert.match(page, /filters\.adminName/)
  assert.match(page, /filters\.reason/)
  assert.match(page, /filterOptions\.reasons/)
  assert.match(page, /getSiteHealthFilters/)
  assert.match(page, /getSiteHealthSummary\(\{ \.\.\.filters \}\)/)
  assert.match(page, /resetFilters/)
  assert.match(page, /crawler:health:trigger/)
  assert.match(page, /无异常也发汇总/)
  assert.match(schedules, /site_health: '站点健康'/)
  assert.match(login, /siteHealthAlert/)
  assert.match(login, /个人站点异常提醒/)
  assert.match(notificationConfig, /站点健康总体汇总只发送到已启用的飞书机器人/)
})
