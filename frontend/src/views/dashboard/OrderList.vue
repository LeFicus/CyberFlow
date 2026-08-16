<!--
  OrderListPage - 订单列表页面
  展示所有电商订单的分页列表，支持按日期范围和管理员筛选。
  顶部标签栏汇总总订单数、成功订单数、订单总金额和成功金额。
-->
<template>
  <el-card>
    <!-- 筛选栏 -->
    <el-form :inline="true" :model="filters" class="order-filter" @submit.prevent="handleSearch">
      <el-form-item v-if="isAdmin" label="用户组">
        <el-segmented v-model="filters.userGroup" :options="groupOptions" @change="handleSearch" />
      </el-form-item>
      <el-form-item label="订单号">
        <el-input v-model="filters.orderId" placeholder="输入订单号" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="站点域名">
        <el-input v-model="filters.domain" placeholder="输入订单站点" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="管理员">
        <el-input v-model="filters.adminName" placeholder="输入管理员" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="支付状态">
        <el-select v-model="filters.payStatus" placeholder="全部状态" clearable>
          <el-option v-for="status in payStatusOptions" :key="status" :label="status" :value="status" />
        </el-select>
      </el-form-item>
      <el-form-item label="币种">
        <el-select v-model="filters.currency" placeholder="全部币种" clearable filterable>
          <el-option v-for="currency in currencyOptions" :key="currency" :label="currency" :value="currency" />
        </el-select>
      </el-form-item>
      <el-form-item label="国家/地区">
        <el-input v-model="filters.country" placeholder="例如 US" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="下单日期" class="date-filter">
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
    <!-- 统计摘要 -->
    <div class="order-summary">
      <article v-for="item in summaryItems" :key="item.label" class="summary-item" :class="item.tone">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <small>{{ item.note }}</small>
      </article>
    </div>
    <!-- 订单表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="订单ID" width="80" />
      <el-table-column prop="amount" label="金额" width="100" />
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column prop="product_host" label="订单站点" min-width="160" />
      <el-table-column prop="pay_status_text" label="支付状态" width="90" />
      <el-table-column prop="customer_ip_country" label="国家" width="70" />
      <el-table-column prop="shipping_email" label="收货邮箱" width="180" />
      <el-table-column prop="admin_name" label="管理员" width="90" />
      <el-table-column label="用户组" width="86" align="center">
        <template #default="{ row }"><el-tag v-if="row.user_group" :type="row.user_group === 'A' ? 'primary' : 'success'">{{ row.user_group }}组</el-tag><span v-else>—</span></template>
      </el-table-column>
      <el-table-column prop="create_time" label="创建时间" width="180" />
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end;"
      v-model:current-page="page" :page-size="size"
      :page-sizes="[10, 20, 50, 100]"
      :total="total" layout="total, sizes, prev, pager, next"
      @current-change="fetchData"
      @size-change="handleSizeChange"
    />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getOrders } from '@/api/dashboard'
import { useUserStore } from '@/store/user'

/** 表格 loading 状态 */
const loading = ref(false)
/** 订单列表数据 */
const tableData = ref([])
/** 当前页码 */
const page = ref(1)
/** 每页条数 */
const size = ref(10)
/** 总条数 */
const total = ref(0)
const userStore = useUserStore()
/** 筛选条件 */
const summary = ref({})
const groupOptions = [{ label: '全部', value: '' }, { label: 'A组', value: 'A' }, { label: 'B组', value: 'B' }]
const isAdmin = computed(() => (userStore.userInfo?.roles || []).some(role => String(role).toUpperCase() === 'ROLE_ADMIN'))
const filters = reactive({ userGroup: '', orderId: '', domain: '', adminName: '', payStatus: '', currency: '', country: '', dateRange: [] })
const payStatusOptions = ['已支付', '支付异常', '支付失败', '待支付', '退款', '已取消']
const currencyOptions = ['USD', 'EUR', 'GBP', 'JPY', 'CNY', 'AUD', 'CAD']
const formatAmount = value => value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const summaryAvailable = computed(() => Object.prototype.hasOwnProperty.call(summary.value, 'total_amount'))
const summaryNumber = key => summaryAvailable.value ? Number(summary.value[key] || 0) : null
const formatSummaryNumber = key => summaryNumber(key) === null ? '—' : summaryNumber(key).toLocaleString('en-US')
const formatSummaryAmount = key => summaryNumber(key) === null ? '—' : formatAmount(summaryNumber(key))
const summaryItems = computed(() => [
  { label: '订单总数', value: total.value.toLocaleString('en-US'), note: '当前筛选全部结果', tone: 'blue' },
  { label: '成功订单', value: formatSummaryNumber('paid_count'), note: '当前筛选全部结果', tone: 'green' },
  { label: '订单总金额', value: formatSummaryAmount('total_amount'), note: `当前筛选全部结果${filters.currency ? ` · ${filters.currency}` : ''}`, tone: 'violet' },
  { label: '成功金额', value: formatSummaryAmount('paid_amount'), note: `当前筛选全部结果${filters.currency ? ` · ${filters.currency}` : ''}`, tone: 'amber' },
])

/**
 * 获取订单分页列表
 * 根据筛选条件构造查询参数发起请求
 */
async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      orderId: filters.orderId.trim() || undefined,
      domain: filters.domain.trim() || undefined,
      adminName: filters.adminName.trim() || undefined,
      userGroup: filters.userGroup || undefined,
      payStatus: filters.payStatus || undefined,
      currency: filters.currency || undefined,
      country: filters.country.trim() || undefined,
      startDate: filters.dateRange?.[0] || undefined,
      endDate: filters.dateRange?.[1] || undefined,
    }
    const res = await getOrders(params)
    tableData.value = res.data.list || []
    total.value = res.data.total || 0
    summary.value = res.data.summary || {}
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleSizeChange(value) {
  size.value = value
  page.value = 1
  fetchData()
}

/**
 * 重置筛选条件并重新加载数据
 */
function resetFilters() {
  Object.assign(filters, { userGroup: '', orderId: '', domain: '', adminName: '', payStatus: '', currency: '', country: '', dateRange: [] })
  page.value = 1
  fetchData()
}

onMounted(fetchData)
</script>
<style scoped>
.order-filter { margin-bottom: 18px; }
.order-filter :deep(.el-input), .order-filter :deep(.el-select) { width: 170px; }
.order-filter .date-filter :deep(.el-date-editor) { width: 280px; }
.order-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 11px; margin: 18px 0; }
.summary-item { position: relative; overflow: hidden; padding: 15px 16px; border: 1px solid var(--cf-line-soft); border-radius: 12px; background: #fafbfc; }
.summary-item::after { position: absolute; top: -20px; right: -18px; width: 62px; height: 62px; border-radius: 50%; background: currentColor; opacity: .06; content: ''; }
.summary-item span, .summary-item small { display: block; }
.summary-item span { color: var(--cf-muted); font-size: 11px; }
.summary-item strong { display: block; margin: 8px 0 5px; color: var(--cf-ink); font-size: 20px; letter-spacing: -.035em; }
.summary-item small { color: var(--cf-subtle); font-size: 9px; }
.summary-item.blue { color: var(--cf-blue); }.summary-item.green { color: var(--cf-green); }.summary-item.violet { color: var(--cf-violet); }.summary-item.amber { color: #d79a36; }
@media (max-width: 980px) { .order-summary { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 540px) { .order-summary { gap: 8px; }.summary-item { padding: 13px; }.summary-item strong { font-size: 17px; } }
@media (max-width: 720px) { .order-filter :deep(.el-input), .order-filter :deep(.el-select), .order-filter .date-filter :deep(.el-date-editor) { width: 100%; } }
</style>
