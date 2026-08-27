import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { SourceTextModule, SyntheticModule } from 'node:vm'
import * as vue from 'vue'
const source = await readFile(new URL('../src/composables/useProductWorkspace.js', import.meta.url), 'utf8')
async function setup(api = {}) {
  const calls = [], counts = []
  const modules = { vue, '@/api/dashboard': {
    searchProducts: async data => { calls.push(structuredClone(vue.toRaw(data.filters))); return { data: { list: [], hasMore: false, snapshotId: '100' } } },
    countFilteredProducts: async data => { counts.push(data); return { data: { total: 50 } } }, ...api,
  } }
  const script = new SourceTextModule(source)
  await script.link(name => new SyntheticModule(Object.keys(modules[name]), function () {
    for (const [key, value] of Object.entries(modules[name])) this.setExport(key, value)
  }))
  await script.evaluate()
  return { state: script.namespace.useProductWorkspace(), module: script.namespace, calls, counts }
}
test('draft filters never change the applied query/export scope until query is pressed', async () => {
  const { state, calls, counts } = await setup()
  state.draft.name = ' hammer '; await state.apply()
  assert.equal(state.applied.value.name, 'hammer')
  state.draft.name = 'saw'
  assert.equal(state.applied.value.name, 'hammer'); assert.equal(state.dirty.value, true)
  await state.refresh(); assert.equal(calls[1].name, 'hammer'); assert.equal(counts.length, 0)
})
test('next and previous use cursor history with a fixed ID upper bound, never offset', async () => {
  const calls = []
  const { state } = await setup({ searchProducts: async data => {
    calls.push(data); return { data: { list: [{ id: 9 }], hasMore: true, nextCursor: '9', snapshotId: '10' } }
  } })
  await state.refresh(); await state.next(); await state.previous()
  assert.deepEqual(calls.map(c => c.beforeId), [null, '9', null]); assert.equal(calls[1].snapshotId, '10')
  assert.equal(calls[1].offset, undefined); assert.equal(state.pageIndex.value, 0)
})
test('late search responses cannot replace newer filters', async () => {
  const pending = []
  const { state } = await setup({ searchProducts: data => new Promise(resolve => pending.push(resolve)) })
  const first = state.refresh(); state.draft.name = 'new'; const second = state.apply()
  pending[1]({ data: { list: [{ id: 2 }], snapshotId: '2' } }); await second
  pending[0]({ data: { list: [{ id: 1 }], snapshotId: '1' } }); await first
  assert.equal(state.rows.value[0].id, 2)
})
test('count is optional and invalidated when a new query is applied', async () => {
  let finish
  const { state } = await setup({ countFilteredProducts: () => new Promise(resolve => { finish = resolve }) })
  await state.refresh(); const counting = state.count(); state.draft.name = 'new'; await state.apply()
  finish({ data: { total: 999 } }); await counting
  assert.equal(state.total.value, null); assert.equal(state.counting.value, false)
})
test('invalid price/date filters do not trigger queries; zero remains a valid price', async () => {
  const { state, calls, module } = await setup()
  state.draft.minPrice = 10; state.draft.maxPrice = 5
  await assert.rejects(state.apply(), /价格/); assert.equal(calls.length, 0)
  assert.equal(module.normalizeProductFilters({ minPrice: 0 }).minPrice, 0)
  assert.throws(() => module.normalizeProductFilters({ startDate: '2026-08-28', endDate: '2026-08-27' }), /日期/)
})
test('failed queries clear stale results and disable next page', async () => {
  const { state } = await setup({ searchProducts: async () => { throw new Error('timeout') } })
  state.rows.value = [{ id: 1 }]; state.hasMore.value = true
  await state.fetchPage(); assert.equal(state.rows.value.length, 0); assert.equal(state.hasMore.value, false); assert.equal(state.loading.value, false)
})
