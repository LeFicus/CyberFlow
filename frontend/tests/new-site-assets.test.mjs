import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

test('brand asset dialog covers generation review selection and authenticated downloads', async () => {
  const view = await readFile(new URL('../src/views/newsite/NewSiteAssetsDialog.vue', import.meta.url), 'utf8')
  const api = await readFile(new URL('../src/api/newSite.js', import.meta.url), 'utf8')

  assert.match(view, /生成品牌素材/)
  assert.match(view, /重新生成一套/)
  assert.match(view, /Logo 与配套 Icon 已选用/)
  assert.match(view, /下载已选素材包/)
  assert.match(api, /assets\/generate/)
  assert.match(api, /assets\/\$\{assetId\}\/select/)
  assert.match(api, /responseType: 'blob'/)
})
