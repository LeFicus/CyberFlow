<template>
  <div class="site-workspace">
    <section class="workspace-heading">
      <div>
        <p class="eyebrow">SITE INTELLIGENCE</p>
        <h1>站点与收录数据</h1>
        <p>用一个页面查看站点归属、最新收录状态和汇总表现，低频信息按需展开。</p>
      </div>
      <div class="heading-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Download" :loading="exporting" :disabled="loading || !total" @click="exportData">导出当前视图</el-button>
      </div>
    </section>

    <section class="summary-grid">
      <article v-for="item in metrics" :key="item.label" class="summary-card" :class="item.tone">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </article>
    </section>

    <el-card shadow="never" class="control-card">
      <div class="view-switch">
        <el-segmented :model-value="dimension" :options="viewOptions" @change="changeDimension" />
        <span>{{ viewDescription }}</span>
      </div>

      <el-alert v-if="drilled" :title="`当前仅查看：${route.query.label || '指定范围'}`" type="info" show-icon :closable="false" class="scope-alert">
        <template #default><el-button link type="primary" @click="clearScope">清除范围</el-button></template>
      </el-alert>

      <el-form label-position="top" @submit.prevent="search">
        <div class="filter-grid">
          <el-form-item label="站点域名"><el-input v-model="filters.domain" clearable placeholder="输入域名关键词" @keyup.enter="search" /></el-form-item>
          <el-form-item label="建站者"><el-input v-model="filters.adminName" clearable placeholder="姓名或账号" @keyup.enter="search" /></el-form-item>
          <el-form-item label="服务器"><el-input v-model="filters.serverName" clearable placeholder="名称或 IP" @keyup.enter="search" /></el-form-item>
          <el-form-item v-if="isAdmin" label="分组"><el-select v-model="filters.userGroup" clearable placeholder="全部分组"><el-option v-for="option in groupOptions.slice(1)" :key="option.value" :value="option.value" :label="option.label" /></el-select></el-form-item>
        </div>
        <div v-if="advanced" class="filter-grid advanced-grid">
          <el-form-item label="主题"><el-input v-model="filters.themeName" clearable placeholder="站点主题" /></el-form-item>
          <el-form-item label="商品分类"><el-input v-model="filters.productCategory" clearable placeholder="商品分类" /></el-form-item>
          <el-form-item label="建站日期"><el-date-picker v-model="filters.siteDateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" /></el-form-item>
          <el-form-item label="收录更新日期"><el-date-picker v-model="filters.updatedDateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" /></el-form-item>
          <el-form-item label="Sitemap 提交日期"><el-date-picker v-model="filters.submittedDateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" /></el-form-item>
          <el-form-item label="收录变化"><el-select v-model="filters.changeDirection" clearable placeholder="全部"><el-option label="增长" value="up" /><el-option label="下降" value="down" /><el-option label="持平" value="flat" /></el-select></el-form-item>
          <el-form-item label="最小收录数"><el-input-number v-model="filters.minIndexCount" :min="0" :precision="0" :controls="false" /></el-form-item>
          <el-form-item label="最大收录数"><el-input-number v-model="filters.maxIndexCount" :min="0" :precision="0" :controls="false" /></el-form-item>
        </div>
        <div class="filter-actions">
          <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
          <el-button @click="reset">重置</el-button>
          <el-button text @click="advanced = !advanced">{{ advanced ? '收起高级筛选' : '高级筛选' }}</el-button>
          <span v-if="activeFilterCount">已启用 {{ activeFilterCount }} 项筛选</span>
        </div>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <div class="table-heading"><strong>{{ viewTitle }}</strong><span>共 {{ number(total) }} 条</span></div>
      <el-table :data="rows" v-loading="loading" :max-height="640" stripe empty-text="暂无匹配的站点数据">
        <template v-if="dimension === 'site'">
          <el-table-column prop="site_domain" label="站点域名" min-width="220" fixed show-overflow-tooltip />
          <el-table-column label="收录状态" width="160" align="right">
            <template #default="{ row }"><div class="index-value"><strong>{{ row.index_updated_at ? number(row.index_count) : '未采集' }}</strong><el-tag v-if="row.index_updated_at" :type="changeTone(row.index_change)" effect="plain" size="small">{{ signed(row.index_change) }}</el-tag></div></template>
          </el-table-column>
          <el-table-column label="商品数" width="105" align="right"><template #default="{ row }">{{ number(row.product_count) }}</template></el-table-column>
          <el-table-column label="归属" min-width="160"><template #default="{ row }"><div class="stack"><strong>{{ row.admin_name || row.builder_username || '未分配' }}</strong><small>{{ row.user_group ? `${row.user_group}组` : '未分组' }}</small></div></template></el-table-column>
          <el-table-column label="服务器" min-width="180"><template #default="{ row }"><div class="stack"><span>{{ row.server_name || '未分配' }}</span><small>{{ row.server_ip || '—' }}</small></div></template></el-table-column>
          <el-table-column label="收录日期" width="150"><template #default="{ row }"><div class="stack"><span>{{ dateOnly(row.index_updated_at) }}</span><small>Sitemap {{ date(row.last_submitted_at) }}</small></div></template></el-table-column>
          <el-table-column label="操作" width="95" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">查看详情</el-button></template></el-table-column>
        </template>
        <template v-else>
          <el-table-column :label="dimension === 'builder' ? '建站者' : '服务器'" min-width="230" fixed><template #default="{ row }"><div class="stack"><strong>{{ row.dimension_name }}</strong><small>{{ dimension === 'builder' ? row.builder_username : row.server_ip || '未分配 IP' }}</small></div></template></el-table-column>
          <el-table-column prop="site_count" label="站点数" width="110" align="right" />
          <el-table-column label="收录表现" width="190" align="right"><template #default="{ row }"><div class="aggregate-index"><strong>{{ number(row.index_count) }}</strong><span>平均 {{ number(row.average_index_count) }}</span><el-tag :type="changeTone(row.index_change)" effect="plain" size="small">{{ signed(row.index_change) }}</el-tag></div></template></el-table-column>
          <el-table-column label="商品数" width="120" align="right"><template #default="{ row }">{{ number(row.product_count) }}</template></el-table-column>
          <el-table-column label="最近收录日期" min-width="160"><template #default="{ row }">{{ dateOnly(row.index_updated_at) }}</template></el-table-column>
          <el-table-column label="操作" width="105" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="drill(row)">查看站点</el-button></template></el-table-column>
        </template>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[20, 50, 100, 200]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
    </el-card>

    <el-drawer v-model="drawerVisible" :title="drawerSite?.site_domain || '站点详情'" size="min(760px, 92%)">
      <el-tabs v-if="drawerSite" v-model="detailTab" @tab-change="handleDetailTab">
        <el-tab-pane label="基本信息" name="overview">
          <el-descriptions :column="2" border class="detail-descriptions">
            <el-descriptions-item label="建站者">{{ drawerSite.admin_name || drawerSite.builder_username || '—' }}</el-descriptions-item>
            <el-descriptions-item label="用户组">{{ drawerSite.user_group ? `${drawerSite.user_group}组` : '—' }}</el-descriptions-item>
            <el-descriptions-item label="服务器">{{ drawerSite.server_name || '—' }} / {{ drawerSite.server_ip || '—' }}</el-descriptions-item>
            <el-descriptions-item label="最新收录">{{ drawerSite.index_updated_at ? number(drawerSite.index_count) : '未采集' }}</el-descriptions-item>
            <el-descriptions-item label="主题">{{ drawerSite.theme_name || '—' }}</el-descriptions-item>
            <el-descriptions-item label="商品分类">{{ formatSiteCategories(null, drawerSite.product_category) }}</el-descriptions-item>
            <el-descriptions-item label="域名申请">{{ date(drawerSite.domain_applied_at) }}</el-descriptions-item>
            <el-descriptions-item label="建站时间">{{ date(drawerSite.created_at) }}</el-descriptions-item>
            <el-descriptions-item label="Sitemap 提交">{{ date(drawerSite.last_submitted_at) }}</el-descriptions-item>
            <el-descriptions-item label="收录日期">{{ dateOnly(drawerSite.index_updated_at) }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>
        <el-tab-pane label="收录趋势" name="trend">
          <div v-loading="historyLoading" class="trend-wrap"><v-chart v-if="indexHistory.length" :option="indexChartOption" autoresize class="trend-chart" /><el-empty v-else description="暂无收录历史" /></div>
        </el-tab-pane>
        <el-tab-pane v-if="canViewOrders" label="关联订单" name="orders">
          <div class="order-toolbar"><el-date-picker v-model="orderRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" /><el-button type="primary" @click="loadOrders(1)">查询</el-button></div>
          <el-table :data="orderRows" v-loading="orderLoading" stripe size="small" empty-text="该站点暂无订单">
            <el-table-column prop="id" label="订单 ID" width="100" />
            <el-table-column prop="amount" label="金额" width="100" />
            <el-table-column prop="currency" label="币种" width="75" />
            <el-table-column prop="pay_status_text" label="状态" width="90" />
            <el-table-column prop="customer_ip_country" label="国家" width="75" />
            <el-table-column prop="create_time" label="创建时间" min-width="170" />
          </el-table>
          <el-pagination v-if="orderTotal > 10" v-model:current-page="orderPage" :page-size="10" :total="orderTotal" layout="total, prev, pager, next" @current-change="loadOrders" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { exportSiteIndexes, getOrdersByDomain, getSiteIndexes, getSiteIndexHistory } from '@/api/dashboard'
import { useUserStore } from '@/store/user'
import { useSiteGroups } from '@/composables/useSiteGroups'
import { formatSiteCategories } from '@/utils/sitePresentation'

use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent])
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const isAdmin = computed(() => user.userInfo?.roles?.includes('ROLE_ADMIN'))
const canViewOrders = computed(() => user.hasPermission('dashboard:order:view'))
const { groupOptions, loadSiteGroups } = useSiteGroups()
loadSiteGroups()

const legacyDimension = () => route.path.endsWith('/builders') ? 'builder' : route.path.endsWith('/servers') ? 'server' : 'site'
const dimension = computed(() => ['site', 'builder', 'server'].includes(String(route.query.view)) ? String(route.query.view) : legacyDimension())
const viewOptions = [{ label: '站点', value: 'site' }, { label: '建站者', value: 'builder' }, { label: '服务器', value: 'server' }]
const viewTitle = computed(() => ({ site: '站点明细', builder: '建站者汇总', server: '服务器汇总' }[dimension.value]))
const viewDescription = computed(() => ({ site: '查看每个站点的最新状态', builder: '比较不同建站者的整体表现', server: '按服务器观察站点和收录规模' }[dimension.value]))
const drilled = computed(() => !!(route.query.builderUsername || route.query.serverIp || route.query.serverNameExact))
const defaults = () => ({ domain: '', adminName: '', serverName: '', userGroup: '', themeName: '', productCategory: '', siteDateRange: [], submittedDateRange: [], updatedDateRange: [], minIndexCount: null, maxIndexCount: null, changeDirection: '' })
const filters = reactive(defaults())
const rows = ref([]), summary = ref({}), page = ref(1), size = ref(20), total = ref(0), loading = ref(false), exporting = ref(false), advanced = ref(false), appliedParams = ref(null)
const activeFilterCount = computed(() => Object.values(filters).filter(value => Array.isArray(value) ? value.length : value !== '' && value !== null).length)
const number = value => Number(value || 0).toLocaleString('zh-CN')
const signed = value => Number(value) > 0 ? `+${number(value)}` : number(value)
const date = value => value ? String(value).replace('T', ' ').slice(0, 16) : '—'
const dateOnly = value => value ? String(value).slice(0, 10) : '—'
const changeTone = value => Number(value) > 0 ? 'success' : Number(value) < 0 ? 'danger' : 'info'
const metrics = computed(() => [
  { label: '站点数', value: number(summary.value.site_count), note: '当前筛选范围', tone: 'blue' },
  { label: 'Google 收录', value: number(summary.value.index_count), note: `本次变化 ${signed(summary.value.index_change)}`, tone: Number(summary.value.index_change) < 0 ? 'red' : 'green' },
  { label: '商品数', value: number(summary.value.product_count), note: '最新快照合计', tone: 'violet' },
  { label: '平均收录', value: number(summary.value.average_index_count), note: '每个站点平均值', tone: 'amber' },
])

let sequence = 0
function params() {
  const query = { ...filters, page: page.value, size: size.value, dimension: dimension.value }
  for (const [key, prefix] of [['siteDateRange', 'site'], ['submittedDateRange', 'submitted'], ['updatedDateRange', 'updated']]) {
    query[`${prefix}StartDate`] = filters[key]?.[0]
    query[`${prefix}EndDate`] = filters[key]?.[1]
    delete query[key]
  }
  if (dimension.value === 'site') for (const key of ['builderUsername', 'builderNameExact', 'serverIp', 'serverNameExact', 'serverIpEmpty']) if (route.query[key]) query[key] = route.query[key]
  return query
}
async function load() {
  const request = ++sequence
  const query = params()
  loading.value = true
  try {
    const res = await getSiteIndexes(query)
    if (request !== sequence) return
    rows.value = res.data?.list || []
    summary.value = res.data?.summary || {}
    total.value = Number(res.data?.total || 0)
    appliedParams.value = query
  } catch {
    if (request === sequence) { rows.value = []; summary.value = {}; total.value = 0; appliedParams.value = null }
  } finally {
    if (request === sequence) loading.value = false
  }
}
function validateRange() {
  if (filters.minIndexCount != null && filters.maxIndexCount != null && filters.minIndexCount > filters.maxIndexCount) {
    ElMessage.warning('最小收录数不能大于最大收录数')
    return false
  }
  return true
}
function search() { if (!validateRange()) return; page.value = 1; load() }
function reset() {
  Object.assign(filters, defaults())
  page.value = 1
  if (drilled.value) clearScope()
  else load()
}
function changeDimension(value) { router.replace({ path: '/dashboard/sites', query: value === 'site' ? {} : { view: value } }) }
function clearScope(navigate = true) {
  const query = { ...route.query }
  for (const key of ['builderUsername', 'builderNameExact', 'serverIp', 'serverNameExact', 'serverIpEmpty', 'label']) delete query[key]
  if (navigate) router.replace({ path: '/dashboard/sites', query })
}
function drill(row) {
  const query = dimension.value === 'builder'
    ? { view: 'site', builderUsername: row.builder_username, builderNameExact: row.admin_name, label: row.dimension_name }
    : { view: 'site', serverNameExact: row.dimension_name, serverIp: row.server_ip || undefined, serverIpEmpty: row.server_ip ? undefined : 'true', label: `${row.dimension_name} ${row.server_ip || ''}` }
  router.push({ path: '/dashboard/sites', query })
}
async function exportData() {
  if (!validateRange()) return
  exporting.value = true
  try {
    const exportParams = { ...(appliedParams.value || params()) }
    delete exportParams.page
    delete exportParams.size
    const res = await exportSiteIndexes(exportParams)
    const disposition = res.headers?.['content-disposition'] || ''
    const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
    const fileName = encodedName ? decodeURIComponent(encodedName) : `${viewTitle.value}-${new Date().toISOString().slice(0, 10)}.xlsx`
    const url = URL.createObjectURL(res.data), link = document.createElement('a')
    link.href = url; link.download = fileName; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000)
    ElMessage.success('导出完成')
  } finally { exporting.value = false }
}

const drawerVisible = ref(false), drawerSite = ref(null), detailTab = ref('overview')
const historyLoading = ref(false), indexHistory = ref([])
const orderLoading = ref(false), orderRows = ref([]), orderTotal = ref(0), orderPage = ref(1), orderRange = ref([])
function openDetail(row) {
  drawerSite.value = row
  detailTab.value = 'overview'
  drawerVisible.value = true
  indexHistory.value = []
  orderRows.value = []
  orderTotal.value = 0
}
async function handleDetailTab(name) {
  if (name === 'trend' && !indexHistory.value.length) await loadHistory()
  if (name === 'orders' && !orderRows.value.length) await loadOrders(1)
}
async function loadHistory() {
  if (!drawerSite.value) return
  historyLoading.value = true
  try { const res = await getSiteIndexHistory({ domain: normalizeDomain(drawerSite.value.site_domain) }); indexHistory.value = res.data || [] }
  finally { historyLoading.value = false }
}
async function loadOrders(nextPage = orderPage.value) {
  if (!drawerSite.value) return
  orderLoading.value = true
  orderPage.value = nextPage
  const query = { domain: normalizeDomain(drawerSite.value.site_domain), page: nextPage, size: 10 }
  if (orderRange.value?.length === 2) { query.startDate = orderRange.value[0]; query.endDate = orderRange.value[1] }
  try { const res = await getOrdersByDomain(query); orderRows.value = res.data?.list || []; orderTotal.value = Number(res.data?.total || 0) }
  finally { orderLoading.value = false }
}
function normalizeDomain(value) {
  return String(value || '').replace(/^https?:\/\//i, '').replace(/\/.*$/, '')
}
const indexChartOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['Google 收录', '商品数'] },
  grid: { left: 16, right: 18, top: 42, bottom: 12, containLabel: true },
  xAxis: { type: 'category', data: indexHistory.value.map(item => String(item.date || item.recorded_at || '').slice(5, 10)), axisLabel: { color: '#8d99ab' } },
  yAxis: [{ type: 'value', splitLine: { lineStyle: { color: '#edf0f5' } } }, { type: 'value', splitLine: { show: false } }],
  series: [
    { name: 'Google 收录', type: 'line', smooth: true, data: indexHistory.value.map(item => item.index_count), lineStyle: { width: 3, color: '#536ff1' }, areaStyle: { color: '#536ff11a' } },
    { name: '商品数', type: 'line', yAxisIndex: 1, smooth: true, data: indexHistory.value.map(item => item.product_count), lineStyle: { width: 2, color: '#45bc8d' } },
  ],
}))

watch(() => route.fullPath, () => {
  page.value = 1
  load()
}, { immediate: true })
</script>

<style scoped>
.site-workspace { display: grid; gap: 16px; }.workspace-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }.workspace-heading h1 { margin: 0; color: var(--cf-ink); font-size: 27px; letter-spacing: -.04em; }.workspace-heading p { margin: 7px 0 0; color: var(--cf-muted); font-size: 12px; }.workspace-heading .eyebrow { margin: 0 0 6px; color: var(--cf-blue); font-size: 9px; font-weight: 800; letter-spacing: .15em; }.heading-actions { display: flex; flex-shrink: 0; }.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }.summary-card { padding: 17px 18px; border: 1px solid var(--cf-line); border-radius: 13px; background: #fff; box-shadow: var(--cf-shadow-sm); }.summary-card span, .summary-card small { display: block; color: var(--cf-muted); font-size: 10px; }.summary-card strong { display: block; margin: 8px 0 5px; color: var(--cf-ink); font-size: 24px; }.summary-card.blue { border-top: 3px solid #536ff1; }.summary-card.green { border-top: 3px solid #36ad82; }.summary-card.red { border-top: 3px solid #df6577; }.summary-card.violet { border-top: 3px solid #8a64e8; }.summary-card.amber { border-top: 3px solid #d99a37; }.control-card, .table-card { min-width: 0; border-radius: 14px; }.view-switch { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }.view-switch > span { color: var(--cf-muted); font-size: 11px; }.filter-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0 14px; }.filter-grid :deep(.el-date-editor), .filter-grid :deep(.el-input-number), .filter-grid :deep(.el-select) { width: 100%; min-width: 0; }.advanced-grid { padding-top: 2px; border-top: 1px dashed var(--cf-line); }.filter-actions { display: flex; align-items: center; gap: 2px; }.filter-actions > span { margin-left: auto; color: var(--cf-muted); font-size: 10px; }.scope-alert { margin-bottom: 16px; }.table-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }.table-heading strong { color: var(--cf-ink); font-size: 14px; }.table-heading span { color: var(--cf-muted); font-size: 11px; }.stack { display: flex; min-width: 0; flex-direction: column; gap: 4px; }.stack strong, .stack span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.stack small { color: var(--cf-muted); font-size: 10px; }.index-value { display: flex; align-items: center; justify-content: flex-end; gap: 8px; }.index-value strong { color: #2d3c55; font-size: 14px; }.aggregate-index { display: flex; align-items: center; justify-content: flex-end; gap: 7px; }.aggregate-index strong { color: #2d3c55; font-size: 14px; }.aggregate-index span { color: var(--cf-muted); font-size: 9px; }.el-pagination { justify-content: flex-end; margin-top: 18px; flex-wrap: wrap; }.detail-descriptions { margin-top: 8px; }.trend-wrap { min-height: 380px; }.trend-chart { height: 400px; }.order-toolbar { display: flex; gap: 10px; margin-bottom: 14px; }
@media (max-width: 1050px) { .summary-grid, .filter-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) { .workspace-heading { align-items: flex-start; flex-direction: column; }.heading-actions { width: 100%; }.heading-actions .el-button { flex: 1; }.summary-grid, .filter-grid { grid-template-columns: 1fr 1fr; }.view-switch { align-items: flex-start; flex-direction: column; }.view-switch > span { display: none; }.order-toolbar { align-items: stretch; flex-direction: column; } }
@media (max-width: 480px) { .summary-grid, .filter-grid { grid-template-columns: 1fr; } }
</style>
