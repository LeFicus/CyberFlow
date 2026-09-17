import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

test('indexing views export all rows from the last applied filter scope', async () => {
  const view = await readFile(new URL('../src/views/dashboard/IndexingList.vue', import.meta.url), 'utf8')
  const api = await readFile(new URL('../src/api/dashboard.js', import.meta.url), 'utf8')

  assert.match(view, />导出当前视图<\/el-button>/)
  assert.match(view, /appliedParams\.value\s*=\s*query/)
  assert.match(view, /appliedParams\.value \|\| params\(\)/)
  assert.match(view, /delete exportParams\.page[\s\S]*delete exportParams\.size/)
  assert.match(api, /site-indexes\/export/)
  assert.match(api, /responseType: 'blob'/)
})
