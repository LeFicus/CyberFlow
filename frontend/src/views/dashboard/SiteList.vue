<!--
  SiteListPage - 站点列表页面
  展示所有电商站点的分页列表，支持按管理员/建站日期筛选。
  每行可展开查看该站点关联的订单详情（支持分页和时间范围筛选）。
  点击"收录详情"打开抽屉面板，通过 ECharts 展示 Google 收录指数和产品总数的历史趋势图。
-->
<template>
  <el-card>
    <!-- 筛选栏 -->
    <el-form :inline="true" :model="filters" class="site-filter" @submit.prevent="handleSearch">
      <el-form-item label="用户组">
        <el-segmented v-model="filters.userGroup" :options="groupOptions" @change="handleSearch" />
      </el-form-item>
      <el-form-item label="管理员">
        <el-input v-model="filters.adminName" placeholder="输入管理员名称" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="站点域名">
        <el-input v-model="filters.domain" placeholder="例如 example.com" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="建站日期">
        <el-date-picker
          v-model="filters.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>
      <el-form-item class="filter-actions">
        <el-button native-type="submit" type="primary">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="result-meta">
      <span>共找到 <strong>{{ total.toLocaleString('en-US') }}</strong> 个站点</span>
      <span v-if="activeFilterCount" class="filter-count">已启用 {{ activeFilterCount }} 项筛选</span>
    </div>

    <!-- 站点表格 -->
    <el-table :data="tableData" v-loading="loading" stripe @expand-change="handleExpandChange" row-key="id">
      <!-- 展开行：关联订单详情 -->
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand-orders">
            <h4>「{{ row.site_domain }}」— 关联订单</h4>
            <el-form :inline="true" size="small" style="margin-bottom: 12px;">
              <el-form-item label="时间范围">
                <el-date-picker
                  v-model="ordersFilters[row.id].dateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="small" @click="fetchOrdersForSite(row, 1)">查询</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="(ordersCache[row.id] || {}).list || []" v-loading="ordersLoading[row.id]" size="small" stripe>
              <el-table-column prop="id" label="订单ID" width="90" />
              <el-table-column prop="amount" label="金额" width="100" />
              <el-table-column prop="currency" label="币种" width="80" />
              <el-table-column prop="pay_status_text" label="支付状态" width="90" />
              <el-table-column prop="customer_ip_country" label="国家" width="70" />
              <el-table-column prop="shipping_email" label="收货邮箱" min-width="180" />
              <el-table-column prop="create_time" label="创建时间" width="180" />
            </el-table>
            <!-- 无数据提示 -->
            <el-empty v-if="!ordersLoading[row.id] && (!ordersCache[row.id] || !ordersCache[row.id].list || ordersCache[row.id].list.length === 0)" description="该站点暂无订单" />
            <el-pagination
              v-if="ordersCache[row.id] && ordersCache[row.id].total > 10"
              style="margin-top: 12px; justify-content: flex-end;"
              :current-page="ordersFilters[row.id].page"
              :page-size="10"
              :total="ordersCache[row.id].total"
              layout="total, prev, pager, next"
              size="small"
              @current-change="(p) => fetchOrdersForSite(row, p)"
            />
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="84" />
      <el-table-column prop="site_domain" label="域名" min-width="200" />
      <el-table-column prop="admin_name" label="管理员" width="100" />
      <el-table-column label="用户组" width="86" align="center">
        <template #default="{ row }"><el-tag v-if="row.user_group" :type="row.user_group === 'A' ? 'primary' : 'success'">{{ row.user_group }}组</el-tag><span v-else>—</span></template>
      </el-table-column>
      <el-table-column prop="theme_name" label="主题" width="100" />
      <el-table-column prop="product_category" label="产品分类" width="100" />
      <el-table-column prop="created_at" label="创建时间" width="180" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" link @click.stop="openIndexDrawer(row)">收录详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end;"
      v-model:current-page="page" :page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="fetchData"
    />

    <!-- 收录详情抽屉 — ECharts 趋势图 -->
    <el-drawer
      v-model="drawerVisible"
      title="站点收录详情"
      size="60%"
      direction="rtl"
    >
      <template v-if="drawerSite">
        <div class="drawer-site-info">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="域名">{{ drawerSite.site_domain }}</el-descriptions-item>
            <el-descriptions-item label="管理员">{{ drawerSite.admin_name }}</el-descriptions-item>
            <el-descriptions-item label="主题">{{ drawerSite.theme_name }}</el-descriptions-item>
            <el-descriptions-item label="产品分类">{{ drawerSite.product_category }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ drawerSite.created_at }}</el-descriptions-item>
            <el-descriptions-item label="Google 收录数">
              {{ latestIndex?.index_count ?? '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-loading="chartLoading" style="margin-top: 20px;">
          <el-tabs v-model="chartRange" @tab-change="onChartRangeChange">
            <el-tab-pane label="近 30 天" name="30" />
            <el-tab-pane label="近 60 天" name="60" />
            <el-tab-pane label="近 90 天" name="90" />
          </el-tabs>
          <v-chart :option="indexChartOption" style="height: 400px;" autoresize />
        </div>
      </template>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, DataZoomComponent } from 'echarts/components'
import { getSites, getSiteIndexHistory, getOrdersByDomain } from '@/api/dashboard'

// 注册 ECharts 所需模块
use([CanvasRenderer, LineChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, DataZoomComponent])

/** 表格 loading 状态 */
const loading = ref(false)
/** 站点列表数据 */
const tableData = ref([])
/** 当前页码 */
const page = ref(1)
/** 每页条数 */
const size = ref(10)
/** 总条数 */
const total = ref(0)
/** 筛选条件 */
const groupOptions = [{ label: '全部', value: '' }, { label: 'A组', value: 'A' }, { label: 'B组', value: 'B' }]
const filters = reactive({ userGroup: '', adminName: '', domain: '', dateRange: [] })
const activeFilterCount = computed(() => [filters.userGroup, filters.adminName, filters.domain, filters.dateRange?.length].filter(Boolean).length)

// 展开行的订单数据缓存 (key: row.id)
/** @type {import('vue').Ref<Object>} 订单数据缓存 { [rowId]: { list, total } } */
const ordersCache = ref({})
/** @type {import('vue').Ref<Object>} 订单加载状态 { [rowId]: boolean } */
const ordersLoading = ref({})
/** @type {import('vue').Ref<Object>} 订单筛选条件 { [rowId]: { dateRange, page, size } } */
const ordersFilters = ref({})

/**
 * 获取指定行 ID 的订单筛选条件（懒初始化）
 * @param {number} rowId - 行 ID
 * @returns {Object} 该行对应的筛选条件 reactive 对象
 */
function getOrderFilters(rowId) {
  if (!ordersFilters.value[rowId]) {
    ordersFilters.value[rowId] = reactive({ dateRange: null, page: 1, size: 10 })
  }
  return ordersFilters.value[rowId]
}

/**
 * 展开行事件处理 — 初始化筛选条件并首次加载订单
 * @param {Object} row - 展开的行数据
 * @param {Array} expandedRows - 当前所有展开的行
 */
function handleExpandChange(row, expandedRows) {
  if (!row) return
  const isExpanded = expandedRows.some(r => r.id === row.id)
  if (!isExpanded) return
  getOrderFilters(row.id)
  if (ordersCache.value[row.id]) return
  fetchOrdersForSite(row, 1)
}

/**
 * 为指定站点加载关联订单数据
 * @param {Object} row - 站点行数据
 * @param {number} pageNum - 页码
 */
async function fetchOrdersForSite(row, pageNum) {
  ordersLoading.value[row.id] = true
  const f = getOrderFilters(row.id)
  f.page = pageNum
  // 从域名中移除路径部分，仅保留基础域名
  const baseDomain = row.site_domain.replace(/\/.*$/, '')
  const params = { domain: baseDomain, page: pageNum, size: f.size }
  if (f.dateRange && f.dateRange.length === 2) {
    params.startDate = f.dateRange[0]
    params.endDate = f.dateRange[1]
  }
  try {
    const res = await getOrdersByDomain(params)
    ordersCache.value[row.id] = {
      list: res.data.list || [],
      total: res.data.total || 0,
    }
  } finally {
    ordersLoading.value[row.id] = false
  }
}

// ==================== 收录详情抽屉 ====================

/** 抽屉是否可见 */
const drawerVisible = ref(false)
/** 当前查看的站点数据 */
const drawerSite = ref(null)
/** 图表加载状态 */
const chartLoading = ref(false)
/** 当前选择的时间范围天数 */
const chartRange = ref('30')
/** 收录历史数据数组 */
const indexData = ref([])

/** 最新一条收录数据 */
const latestIndex = computed(() => {
  if (!indexData.value.length) return null
  return indexData.value[indexData.value.length - 1]
})

/**
 * 打开站点收录详情抽屉
 * @param {Object} row - 站点行数据
 */
async function openIndexDrawer(row) {
  drawerSite.value = row
  drawerVisible.value = true
  chartRange.value = '30'
  await loadIndexHistory(row)
}

/**
 * 加载站点收录历史数据
 * @param {Object} site - 站点数据（需含 site_domain）
 */
async function loadIndexHistory(site) {
  chartLoading.value = true
  try {
    const baseDomain = site.site_domain.replace(/\/.*$/, '')
    const res = await getSiteIndexHistory({ domain: baseDomain })
    indexData.value = res.data || []
  } finally {
    chartLoading.value = false
  }
}

/** 切换时间范围时的回调（数据响应式更新由 computed 自动处理） */
function onChartRangeChange() {
  // data changed reactively through computed
}

/**
 * 收录趋势图 ECharts 配置（双 Y 轴：收录数 + 产品数，可 Zoom 缩放）
 * @type {import('vue').ComputedRef<Object>}
 */
const indexChartOption = computed(() => {
  const days = parseInt(chartRange.value)
  const sliced = indexData.value.slice(-days)
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['Google 收录数', '产品总数'] },
    grid: { left: 60, right: 60, top: 40, bottom: 60 },
    dataZoom: [
      { type: 'slider', start: 0, end: 100, height: 24, bottom: 8 },
      { type: 'inside' },
    ],
    xAxis: {
      type: 'category',
      data: sliced.map(i => i.date.slice(5)),
      axisLabel: { rotate: 45 },
    },
    yAxis: [
      { type: 'value', name: '收录数' },
      { type: 'value', name: '产品数' },
    ],
    series: [
      {
        name: 'Google 收录数',
        type: 'line',
        data: sliced.map(i => i.index_count),
        smooth: true,
        itemStyle: { color: '#409EFF' },
        areaStyle: { color: 'rgba(64,158,255,0.1)' },
      },
      {
        name: '产品总数',
        type: 'line',
        yAxisIndex: 1,
        data: sliced.map(i => i.product_count),
        smooth: true,
        itemStyle: { color: '#67C23A' },
        areaStyle: { color: 'rgba(103,194,58,0.1)' },
      },
    ],
  }
})

/**
 * 获取站点分页列表
 */
async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      adminName: filters.adminName.trim() || undefined,
      userGroup: filters.userGroup || undefined,
      domain: filters.domain.trim() || undefined,
      startDate: filters.dateRange?.[0] || undefined,
      endDate: filters.dateRange?.[1] || undefined,
    }
    const res = await getSites(params)
    tableData.value = res.data.list || []
    total.value = res.data.total
    // 重置缓存的订单数据
    ordersCache.value = {}
    ordersFilters.value = {}
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

/**
 * 重置筛选条件并重新加载
 */
function resetFilters() {
  filters.userGroup = ''
  filters.adminName = ''
  filters.domain = ''
  filters.dateRange = []
  page.value = 1
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.site-filter { margin-bottom: 0; }
.site-filter :deep(.el-input) { width: 190px; }
.site-filter :deep(.el-date-editor) { width: 280px; }
.result-meta { display: flex; align-items: center; gap: 10px; margin: 16px 2px 12px; color: var(--cf-muted); font-size: 11px; }
.result-meta strong { color: var(--cf-ink); font-size: 13px; }
.filter-count { padding: 4px 8px; border-radius: 999px; color: var(--cf-blue); background: #f0f2ff; }
.expand-orders {
  padding: 12px 24px;
  background: #fafafa;
}
.expand-orders h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #606266;
}
.drawer-site-info {
  margin-bottom: 8px;
}
@media (max-width: 720px) {
  .site-filter :deep(.el-input), .site-filter :deep(.el-date-editor) { width: 100%; }
}
</style>
