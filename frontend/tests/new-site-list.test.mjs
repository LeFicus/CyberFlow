import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { SourceTextModule, SyntheticModule } from 'node:vm'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'

// Compile the real SFC setup with mocked network/UI boundaries, without a DOM or extra dependencies.
const source = await readFile(new URL('../src/views/newsite/NewSiteList.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const { content } = compileScript(descriptor, { id: 'new-site-list-test' })

async function setup(overrides = {}, permissions = ['newsite:list', 'newsite:status', 'newsite:delete']) {
  const calls = { deleted: [], updated: [], listed: [], confirmations: [] }
  const api = {
    createNewSites() {}, getNewSiteAiConfig() {}, getNewSiteOptions() {}, updateNewSiteAiConfig() {},
    async deleteNewSite(id) { calls.deleted.push(id) },
    async updateNewSiteStatus(id, status) { calls.updated.push([id, status]); return { data: { status } } },
    async listNewSites(params) { calls.listed.push(params); return { data: { records: [], total: 0 } } },
    ...overrides,
  }
  const modules = {
    vue: { ...vue, onMounted() {} },
    'element-plus': {
      ElMessage: { success() {}, warning() {} },
      ElMessageBox: { async confirm(...args) {
        calls.confirmations.push(args)
        if (overrides.confirm) return overrides.confirm(...args)
      } },
    },
    '@element-plus/icons-vue': { ArrowDown: {}, Check: {}, Delete: {}, Loading: {} },
    '@/store/user': { useUserStore: () => ({ hasPermission: perm => permissions.includes(perm) }) },
    '@/api/newSite': api,
  }
  const script = new SourceTextModule(content)
  await script.link(specifier => {
    const values = modules[specifier]
    assert.ok(values, `Unexpected import: ${specifier}`)
    return new SyntheticModule(Object.keys(values), function () {
      for (const [name, value] of Object.entries(values)) this.setExport(name, value)
    })
  })
  await script.evaluate()
  return { state: script.namespace.default.setup({}, { expose() {} }), calls }
}

test('delete confirms its scope, calls the API, and refreshes the list', async () => {
  const { state, calls } = await setup()
  await state.handleDelete({ id: 42, domain: 'example.test' })
  assert.match(calls.confirmations[0][0], /example.test/)
  assert.match(calls.confirmations[0][0], /不影响源站点/)
  assert.equal(calls.confirmations[0][2].confirmButtonType, 'danger')
  assert.deepEqual(calls.deleted, [42])
  assert.equal(calls.listed.length, 1)
  assert.equal(state.isRowBusy(42), false)
})

test('cancelled or failed deletion preserves the row and clears the busy state', async () => {
  for (const overrides of [
    { confirm: async () => { throw 'cancel' } },
    { deleteNewSite: async () => { throw new Error('request failed') } },
  ]) {
    const { state, calls } = await setup(overrides)
    state.rows.value = [{ id: 42 }]
    await state.handleDelete({ id: 42, domain: 'example.test' })
    assert.equal(state.rows.value.length, 1)
    assert.equal(calls.listed.length, 0)
    assert.equal(calls.deleted.length, 0)
    assert.equal(state.isRowBusy(42), false)
  }
})

test('read-only users cannot delete or change status', async () => {
  const { state, calls } = await setup({}, ['newsite:list'])
  await state.handleDelete({ id: 42 })
  await state.handleStatusChange({ id: 42, status: 'pending_review' }, 'enabled')
  assert.equal(state.canDelete.value, false)
  assert.equal(state.canUpdateStatus.value, false)
  assert.equal(calls.deleted.length + calls.updated.length + calls.confirmations.length, 0)
})

test('status options retain their labels and distinct visual tones', async () => {
  const { state } = await setup()
  assert.equal(state.statusMeta('pending_review').tone, 'pending')
  assert.equal(state.statusMeta('enabled').tone, 'enabled')
  assert.equal(state.statusMeta('disabled').tone, 'disabled')
  assert.equal(state.statusMeta('other').label, '未知状态')
})

test('status updates only after success; failures preserve its confirmed value', async () => {
  const { state, calls } = await setup()
  const row = { id: 42, status: 'pending_review' }
  await state.handleStatusChange(row, 'enabled')
  assert.equal(row.status, 'enabled')
  assert.deepEqual(calls.updated, [[42, 'enabled']])
  const failed = await setup({ updateNewSiteStatus: async () => { throw new Error('request failed') } })
  await failed.state.handleStatusChange(row, 'disabled')
  assert.equal(row.status, 'enabled')
  assert.equal(failed.state.isRowBusy(42), false)
})

test('unchanged and in-flight rows do not send duplicate mutations', async () => {
  const { state, calls } = await setup()
  const row = { id: 42, status: 'enabled' }
  await state.handleStatusChange(row, 'enabled')
  state.statusUpdating.add(42)
  await state.handleStatusChange(row, 'disabled')
  await state.handleDelete(row)
  assert.equal(calls.updated.length + calls.deleted.length + calls.confirmations.length, 0)
})

test('changing status under an active filter refreshes its results', async () => {
  const { state, calls } = await setup()
  state.status.value = 'pending_review'
  await state.handleStatusChange({ id: 42, status: 'pending_review' }, 'enabled')
  assert.equal(calls.listed.length, 1)
  assert.equal(calls.listed[0].status, 'pending_review')
})

test('an emptied last page falls back to the last valid page', async () => {
  const requestedPages = []
  const { state } = await setup({ listNewSites: async ({ page }) => {
    requestedPages.push(page)
    return { data: { total: 10, records: page === 1 ? [{ id: 1 }] : [] } }
  } })
  state.page.value = 2
  await state.fetchList()
  assert.deepEqual(requestedPages, [2, 1])
  assert.equal(state.page.value, 1)
  assert.equal(state.rows.value[0].id, 1)
  assert.equal(state.loading.value, false)
})

test('search resets pagination and stale responses cannot replace newer results', async () => {
  const pending = []
  const { state } = await setup({ listNewSites: params => new Promise(resolve => pending.push({ params, resolve })) })
  state.page.value = 3
  const oldRequest = state.fetchList()
  state.keyword.value = ' new '
  const newRequest = state.handleSearch()
  assert.equal(pending[1].params.page, 1)
  assert.equal(pending[1].params.keyword, 'new')
  pending[1].resolve({ data: { records: [{ id: 2 }], total: 1 } })
  await newRequest
  pending[0].resolve({ data: { records: [{ id: 1 }], total: 100 } })
  await oldRequest
  assert.equal(state.rows.value[0].id, 2)
  assert.equal(state.total.value, 1)
})
