import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { SourceTextModule, SyntheticModule } from 'node:vm'
import { parse, compileScript } from '@vue/compiler-sfc'
import * as vue from 'vue'
import { normalizeOrder } from '../src/utils/orderProducts.js'

const source = await readFile(new URL('../src/views/dashboard/OrderList.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const { content } = compileScript(descriptor, { id: 'order-platform-status-test' })
async function setup(overrides = {}, admin = true) {
  const calls = { lists: [] }
  const api = {
    async getOrders(params) { calls.lists.push(params); return { data: { list: [], total: 0 } } },
    async clearAllOrders() {},
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
test('platform flags use the API conventions and distinguish missing data', async () => {
  const { state } = await setup()
  assert.equal(state.platformFlagLabel(1), '是')
  assert.equal(state.platformFlagLabel(0), '否')
  assert.equal(state.platformFlagLabel('0', 0), '是')
  assert.equal(state.platformFlagLabel(1, 0), '否')
  assert.equal(state.platformFlagLabel(null), '未同步')
  assert.equal(state.platformFlagLabel(undefined), '—')
  assert.equal(state.platformFlagLabel(2), '未同步')
  assert.doesNotMatch(source, /英文名|审核操作|submitFraudReview|reviewOrderFraud/)
})

test('platform risk filter is sent to the backend and reset clears it', async () => {
  const { state, calls } = await setup()
  state.filters.riskStatus = 'RISK'
  await state.fetchData()
  assert.equal(calls.lists[0].riskStatus, 'RISK')
  assert.equal(calls.lists[0].fraudStatus, undefined)
  state.resetFilters()
  assert.equal(state.filters.riskStatus, '')
})
