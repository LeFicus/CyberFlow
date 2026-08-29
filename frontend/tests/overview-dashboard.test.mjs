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

test('overview removes indexing trend and quick tasks', async () => {
  const overview = await readFile(new URL('../src/views/dashboard/Overview.vue', import.meta.url), 'utf8')
  assert.doesNotMatch(overview, /站点收录趋势|快捷任务/)
  assert.match(overview, /上月同期去重订单/)
})
