<template>
  <el-card>
    <el-form :inline="true" :model="filters" style="margin-bottom: 16px;">
      <el-form-item label="管理员">
        <el-input v-model="filters.adminName" placeholder="按管理员筛选" clearable />
      </el-form-item>
      <el-form-item label="主题">
        <el-select v-model="filters.themeName" placeholder="按主题筛选" clearable>
          <el-option label="Default" value="Default" />
          <el-option label="Electro" value="Electro" />
          <el-option label="Fashion" value="Fashion" />
          <el-option label="Minimal" value="Minimal" />
          <el-option label="Organic" value="Organic" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetchData">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData" v-loading="loading" stripe @expand-change="handleExpandChange" row-key="id">
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
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="site_domain" label="域名" min-width="200" />
      <el-table-column prop="admin_name" label="管理员" width="100" />
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

    <!-- 收录详情抽屉 -->
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

use([CanvasRenderer, LineChart, BarChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, DataZoomComponent])

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filters = reactive({ adminName: '', themeName: '' })

// 展开行的订单数据缓存 (key: row.id)
const ordersCache = ref({})    // { list: [], total: 0 }
const ordersLoading = ref({})  // boolean
const ordersFilters = ref({})  // { dateRange: [start, end], page: 1, size: 10 }

function getOrderFilters(rowId) {
  if (!ordersFilters.value[rowId]) {
    ordersFilters.value[rowId] = reactive({ dateRange: null, page: 1, size: 10 })
  }
  return ordersFilters.value[rowId]
}

// 展开行 — 初始化筛选条件并加载订单
function handleExpandChange(row, expandedRows) {
  if (!row) return
  const isExpanded = expandedRows.some(r => r.id === row.id)
  if (!isExpanded) return
  getOrderFilters(row.id)
  if (ordersCache.value[row.id]) return
  fetchOrdersForSite(row, 1)
}

async function fetchOrdersForSite(row, page) {
  ordersLoading.value[row.id] = true
  const f = getOrderFilters(row.id)
  f.page = page
  const baseDomain = row.site_domain.replace(/\/.*$/, '')
  const params = { domain: baseDomain, page, size: f.size }
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

// 收录详情抽屉
const drawerVisible = ref(false)
const drawerSite = ref(null)
const chartLoading = ref(false)
const chartRange = ref('30')
const indexData = ref([])

const latestIndex = computed(() => {
  if (!indexData.value.length) return null
  return indexData.value[indexData.value.length - 1]
})

async function openIndexDrawer(row) {
  drawerSite.value = row
  drawerVisible.value = true
  chartRange.value = '30'
  await loadIndexHistory(row)
}

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

function onChartRangeChange() {
  // data changed reactively through computed
}

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

async function fetchData() {
  loading.value = true
  try {
    const res = await getSites({ page: page.value, size: size.value, ...filters })
    tableData.value = res.data.list || []
    total.value = res.data.total
    ordersCache.value = {}
    ordersFilters.value = {}
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.adminName = ''
  filters.themeName = ''
  page.value = 1
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
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
</style>
