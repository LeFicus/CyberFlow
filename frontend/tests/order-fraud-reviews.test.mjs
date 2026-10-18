import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { SourceTextModule, SyntheticModule } from 'node:vm'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
import { normalizeOrder } from '../src/utils/orderProducts.js'

const source = await readFile(new URL('../src/views/dashboard/OrderList.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const { content } = compileScript(descriptor, { id: 'order-fraud-review-test' })
async function setup(overrides = {}, admin = true) {
  const calls = { reviews: [], lists: [], history: [] }
  const api = {
    async getOrders(params) { calls.lists.push(params); return { data: { list: [], total: 0 } } },
    async clearAllOrders() {},
    async getOrderFraudReviews(...args) { calls.history.push(args); return { data: [] } },
    async reviewOrderFraud(...args) { calls.reviews.push(args) },
    ...overrides,
  }
  const modules = {
    vue: { ...vue, onMounted() {} },
    'element-plus': { ElMessage: { success() {} }, ElMessageBox: {} },
    '@/api/dashboard': api,
    '@/store/user': { useUserStore: () => ({ userInfo: { roles: admin ? ['ROLE_ADMIN'] : ['ROLE_USER'] } }) },
    '@/utils/orderProducts': { normalizeOrder },
    '@/utils/sitePresentation': { formatSiteCategories() {}, groupTagStyle() {}, siteTagLabel() {}, siteTagType() {} },
    '@/composables/useSiteGroups': { useSiteGroups: () => ({ groupOptions: [], loadSiteGroups() {} }) },
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
const order = { id: 42, user_group: 'A', shipping_email: 'alice.smith.12345@gmail.com', fraud_status: 'PENDING' }

test('review requires an explicit decision and evidence and submits the displayed email', async () => {
  const { state, calls } = await setup()
  await state.openFraudReview(order)
  await state.submitFraudReview()
  assert.equal(calls.reviews.length, 0)
  state.reviewForm.decision = 'APPROVED'
  state.reviewForm.note = ' 核实无异常 '
  await state.submitFraudReview()
  assert.deepEqual(calls.reviews, [['A', 42, { email: order.shipping_email, decision: 'APPROVED', note: '核实无异常' }]])
  assert.equal(state.fraudDialog.value, false)
  assert.equal(calls.lists.length, 1)
})

test('failed review keeps the dialog and evidence for retry without changing statistics', async () => {
  const { state, calls } = await setup({ reviewOrderFraud: async () => { throw new Error('stale email') } })
  await state.openFraudReview(order)
  state.reviewForm.decision = 'REJECTED'
  state.reviewForm.note = '异常'
  await assert.rejects(() => state.submitFraudReview(), /stale email/)
  assert.equal(state.fraudDialog.value, true)
  assert.equal(state.reviewForm.note, '异常')
  assert.equal(state.reviewSaving.value, false)
  assert.equal(calls.lists.length, 0)
})

test('nonadministrators cannot open or submit reviews and requests cannot be duplicated', async () => {
  const { state, calls } = await setup({}, false)
  await state.openFraudReview(order)
  state.reviewOrder.value = order
  state.reviewForm.decision = 'APPROVED'
  state.reviewForm.note = 'test'
  await state.submitFraudReview()
  assert.equal(calls.history.length + calls.reviews.length, 0)
  const pending = await setup()
  await pending.state.openFraudReview(order)
  pending.state.reviewForm.decision = 'APPROVED'
  pending.state.reviewForm.note = 'test'
  pending.state.reviewSaving.value = true
  await pending.state.submitFraudReview()
  let closed = false
  pending.state.closeFraudReview(() => { closed = true })
  assert.equal(closed, false)
  assert.equal(pending.calls.reviews.length, 0)
})

test('review state is included in the server filter and reset clears it', async () => {
  const { state, calls } = await setup()
  state.filters.fraudStatus = 'PENDING'
  await state.fetchData()
  assert.equal(calls.lists[0].fraudStatus, 'PENDING')
  state.resetFilters()
  assert.equal(state.filters.fraudStatus, '')
})
