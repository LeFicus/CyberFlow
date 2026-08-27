import { ref, reactive, computed } from 'vue'
import { searchProducts, countFilteredProducts } from '@/api/dashboard'

export function emptyProductFilters() {
  return { domains: [], customCategories: [], productCategories: [], productRoles: [], name: '', nameMatch: 'prefix',
    sku: '', language: '', minPrice: null, maxPrice: null, startDate: null, endDate: null }
}
export function normalizeProductFilters(raw) {
  const result = { ...emptyProductFilters(), ...raw }
  for (const key of ['domains', 'customCategories', 'productCategories', 'productRoles']) {
    result[key] = [...new Set((Array.isArray(result[key]) ? result[key] : []).map(v => String(v).trim()).filter(Boolean))]
    if (result[key].length > 50) throw new Error('每项筛选最多选择 50 个值')
  }
  for (const key of ['name', 'sku', 'language']) result[key] = String(result[key] || '').trim()
  for (const key of ['minPrice', 'maxPrice']) if (result[key] === '' || result[key] === undefined) result[key] = null
  if ((result.minPrice != null && result.minPrice < 0) || (result.maxPrice != null && result.maxPrice < 0)
    || (result.minPrice != null && result.maxPrice != null && result.minPrice > result.maxPrice)) throw new Error('请检查价格范围')
  if (result.startDate && result.endDate && result.startDate > result.endDate) throw new Error('开始日期不能晚于结束日期')
  return result
}
export function productFilterSummary(f) {
  const items = []
  const labels = { domains: '域名', customCategories: '自定义分类', productCategories: '商品分类', productRoles: '产品标签' }
  for (const [key, label] of Object.entries(labels)) if (f[key]?.length)
    items.push(`${label}：${f[key].map(v => v === 'main' ? '主产品' : v === 'supplement' ? '补充产品' : v).join('、')}`)
  if (f.name) items.push(`名称${f.nameMatch === 'contains' ? '包含' : '开头'}：${f.name}`)
  if (f.sku) items.push(`SKU 开头：${f.sku}`)
  if (f.language) items.push(`语言：${f.language}`)
  if (f.minPrice != null || f.maxPrice != null) items.push(`价格：${f.minPrice ?? '不限'} ~ ${f.maxPrice ?? '不限'}`)
  if (f.startDate || f.endDate) items.push(`采集时间：${f.startDate || '不限'} ~ ${f.endDate || '不限'}`)
  return items
}
export function useProductWorkspace() {
  const draft = reactive(emptyProductFilters()), applied = ref(emptyProductFilters())
  const rows = ref([]), size = ref(50), loading = ref(false), error = ref('')
  const snapshotId = ref(null), nextCursor = ref(null), hasMore = ref(false)
  const cursors = ref([null]), pageIndex = ref(0), total = ref(null), counting = ref(false)
  const dirty = computed(() => JSON.stringify(draft) !== JSON.stringify(applied.value))
  const summary = computed(() => productFilterSummary(applied.value))
  let sequence = 0, generation = 0
  async function fetchPage() {
    const request = ++sequence
    loading.value = true; error.value = ''
    try {
      const res = await searchProducts({ filters: applied.value, beforeId: cursors.value[pageIndex.value], snapshotId: snapshotId.value, size: size.value })
      if (request !== sequence) return
      rows.value = res.data.list || []; snapshotId.value = res.data.snapshotId
      nextCursor.value = res.data.nextCursor; hasMore.value = !!res.data.hasMore
    } catch (err) {
      if (request === sequence) { rows.value = []; hasMore.value = false; error.value = err.message || '查询失败，请缩小筛选范围后重试' }
    } finally { if (request === sequence) loading.value = false }
  }
  async function refresh() {
    generation++; counting.value = false; total.value = null
    snapshotId.value = null; cursors.value = [null]; pageIndex.value = 0; rows.value = []
    return fetchPage()
  }
  async function apply() {
    const filters = normalizeProductFilters(draft)
    Object.assign(draft, filters); applied.value = structuredClone(filters)
    return refresh()
  }
  async function reset() { Object.assign(draft, emptyProductFilters()); return apply() }
  async function next() {
    if (loading.value || !hasMore.value) return
    cursors.value[pageIndex.value + 1] = nextCursor.value; pageIndex.value++; return fetchPage()
  }
  async function previous() {
    if (loading.value || pageIndex.value === 0) return
    pageIndex.value--; return fetchPage()
  }
  async function count() {
    if (loading.value || counting.value || snapshotId.value == null) return
    const version = generation; counting.value = true
    try { const res = await countFilteredProducts({ filters: applied.value, snapshotId: snapshotId.value }); if (version === generation) total.value = res.data.total }
    finally { if (version === generation) counting.value = false }
  }
  return { draft, applied, rows, size, loading, error, snapshotId, hasMore, pageIndex, total, counting,
    dirty, summary, apply, reset, refresh, fetchPage, next, previous, count }
}
