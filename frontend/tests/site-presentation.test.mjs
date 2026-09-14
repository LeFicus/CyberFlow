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
