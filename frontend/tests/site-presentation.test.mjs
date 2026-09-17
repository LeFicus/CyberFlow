import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

import {
  formatSiteCategories,
  groupTagStyle,
  siteTagLabel,
} from '../src/utils/sitePresentation.js'

test('site groups receive stable distinct colors', () => {
  assert.deepEqual(groupTagStyle('A'), groupTagStyle('A'))
  assert.notDeepEqual(groupTagStyle('A'), groupTagStyle('B'))
})

test('category details are the displayed product-category field', () => {
  assert.equal(formatSiteCategories('["服装/女装", "配饰"]', '旧分类'), '服装/女装、配饰')
  assert.equal(formatSiteCategories(null, '旧分类'), '旧分类')
})

test('site build types use the platform site_tag values', () => {
  assert.equal(siteTagLabel(0), '单独建站')
  assert.equal(siteTagLabel(1), '批量建站')
  assert.equal(siteTagLabel(2), '复制站')
})

test('order list renders category details and build type from its site', async () => {
  const view = await readFile(new URL('../src/views/dashboard/OrderList.vue', import.meta.url), 'utf8')
  assert.match(view, /row\.cat_names/)
  assert.match(view, /siteTagLabel\(row\.site_tag\)/)
})

test('site and indexing analysis use one compact workspace', async () => {
  const [siteView, workspace, router] = await Promise.all([
    readFile(new URL('../src/views/dashboard/SiteList.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/views/dashboard/IndexingList.vue', import.meta.url), 'utf8'),
    readFile(new URL('../src/router/index.js', import.meta.url), 'utf8'),
  ])
  assert.match(siteView, /<IndexingList/)
  assert.match(workspace, /站点与收录数据/)
  assert.match(workspace, /viewOptions/)
  assert.match(workspace, /查看详情/)
  assert.match(workspace, /收录趋势/)
  assert.match(workspace, /dateOnly\(row\.index_updated_at\)/)
  assert.doesNotMatch(workspace, /收录更新时间/)
  assert.match(workspace, /高级筛选/)
  assert.match(router, /indexing\/builders.*dashboard\/sites/)
  assert.match(router, /crawler\/collect.*crawler\/site/)
  assert.match(router, /crawler\/order.*crawler\/site/)
})
