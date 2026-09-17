import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { monthOrderComparison } from '../src/utils/overviewComparison.js'

test('month-to-date orders compare against the same period in the previous month', () => {
  assert.deepEqual(monthOrderComparison(120, 100), {
    current: 120,
    previous: 100,
    change: 20,
    growth: 20,
  })
  assert.deepEqual(monthOrderComparison(75, 100), {
    current: 75,
    previous: 100,
    change: -25,
    growth: -25,
  })
})

test('a zero previous period is reported without a misleading percentage', () => {
  assert.deepEqual(monthOrderComparison(8, 0), {
    current: 8,
    previous: 0,
    change: 8,
    growth: null,
  })
})

test('overview shows today, yesterday and monthly forecasts with card-free deduplication', async () => {
  const overview = await readFile(new URL('../src/views/dashboard/Overview.vue', import.meta.url), 'utf8')
  assert.doesNotMatch(overview, /站点收录趋势|快捷任务/)
  assert.match(overview, /今日去重订单/)
  assert.match(overview, /今日有效去重/)
  assert.match(overview, /昨日去重订单/)
  assert.match(overview, /昨日有效去重/)
  assert.match(overview, /本月去重订单预测/)
  assert.match(overview, /本月成交金额预测/)
  assert.match(overview, /收货邮箱或地址相同即合并/)
  assert.doesNotMatch(overview, /来源、支付卡、订单站点/)
})

test('overview surfaces commission totals and explains their composition', async () => {
  const overview = await readFile(new URL('../src/views/dashboard/Overview.vue', import.meta.url), 'utf8')
  const details = await readFile(new URL('../src/components/RevenueRowDetails.vue', import.meta.url), 'utf8')
  assert.match(overview, /组员总提成/)
  assert.match(overview, /组长总提成/)
  assert.match(overview, /leader_personal_commission_rmb/)
  assert.doesNotMatch(overview, /revenue-summary-grid|revenueSummaryCards/)
  assert.match(overview, /RevenueRowDetails/)
  assert.match(overview, /classification_breakdown/)
  assert.match(overview, /category_breakdown/)
  assert.match(overview, /普通 \{\{ formatCommission\(row\.regular_commission_rmb\) \}\}/)
  assert.match(overview, /批量 \{\{ formatCommission\(row\.batch_site_commission_rmb\) \}\}/)
  assert.match(overview, /小组 \{\{ formatCommission\(row\.leader_commission_rmb\) \}\}/)
  assert.match(overview, /个人 \{\{ formatCommission\(row\.leader_personal_commission_rmb\) \}\}/)
  assert.match(overview, /personalDetails/)
  assert.match(overview, /leaderDetails/)
  assert.match(overview, /monthlyDetails/)
  assert.match(details, /订单站点归属/)
  assert.match(details, /按去重订单分类占比/)
  assert.match(details, /站点商品分类明细/)
  assert.match(details, /PieChart/)
  assert.match(details, /categoryChartOption/)
})
