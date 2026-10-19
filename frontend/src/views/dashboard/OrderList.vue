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
      <el-form-item label="建站类型">
        <el-select v-model="filters.siteTag" placeholder="全部类型" clearable @change="handleSearch">
          <el-option v-for="type in [0, 1, 2]" :key="type" :label="siteTagLabel(type)" :value="type" />
        </el-select>
      </el-form-item>
      <el-form-item label="支付状态">
        <el-select v-model="filters.payStatus" placeholder="全部状态" clearable>
          <el-option v-for="status in payStatusOptions" :key="status" :label="status" :value="status" />
        </el-select>
      </el-form-item>
      <el-form-item label="刷单审核">
        <el-select v-model="filters.fraudStatus" placeholder="全部订单" clearable @change="handleSearch">
          <el-option v-for="(label, status) in fraudLabels" :key="status" :label="label" :value="status" />
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
        <el-button
          v-if="isAdmin"
          type="danger"
          plain
          :disabled="total === 0"
          :loading="clearingOrders"
          @click="handleClearAllOrders"
        >清空全部订单</el-button>
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
    <el-alert class="fraud-notice" type="warning" :closable="false" show-icon
      title="命中“英文名.英文名.5位数字@gmail.com”的订单默认待审核，不计入去重订单和有效去重订单；管理员确认无异常并审核通过后恢复参与统计，有效去重仍须满足平台有效条件。" />
    <!-- 订单表格：每条订单可展开查看订单爬取结果中的商品详情 -->
    <el-table :data="tableData" v-loading="loading" class="order-table" stripe row-key="orderKey">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="order-products">
            <div class="expand-title">
              <span>订单商品</span>
              <el-tag size="small" type="info">{{ row.productInfo.length }} 项商品</el-tag>
            </div>
            <div v-if="row.productInfo.length" class="product-grid">
              <article v-for="(product, index) in row.productInfo" :key="product.key" class="product-card">
                <div v-if="product.images.length" class="product-card-images">
                  <el-image
                    v-for="(src, imageIndex) in product.images"
                    :key="src"
                    :src="src"
                    :alt="`${product.name} · 图片 ${imageIndex + 1}`"
                    :preview-src-list="product.images"
                    :initial-index="imageIndex"
                    fit="contain"
                    preview-teleported
                  >
                    <template #error><span class="image-placeholder">图片加载失败</span></template>
                  </el-image>
                </div>
                <div v-else class="product-no-image">暂无图片</div>
                <div class="product-card-content">
                  <div class="product-heading">
                    <el-tag size="small" type="info">{{ index + 1 }}</el-tag>
                    <strong>{{ product.name }}</strong>
                  </div>
                  <dl v-if="product.details.length" class="product-details">
                    <div v-for="detail in product.details" :key="detail.key" class="product-detail">
                      <dt>{{ detail.label }}</dt>
                      <dd>{{ detail.value }}</dd>
                    </div>
                  </dl>
                  <span v-else class="no-image">暂无其他商品信息</span>
                </div>
              </article>
            </div>
            <el-empty v-else description="该订单暂无商品详情" :image-size="64" />
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="订单ID" width="80" />
      <el-table-column label="商品图片" width="190" align="center">
        <template #default="{ row }">
          <div v-if="row.productImages.length" class="order-product-images">
            <el-image
              v-for="(src, index) in row.productImages"
              :key="`${index}-${src}`"
              class="order-product-image"
              :src="src"
              :alt="`订单商品图片 ${index + 1}`"
              :preview-src-list="row.productImages"
              :initial-index="index"
              fit="contain"
              preview-teleported
            >
              <template #error><span class="image-placeholder">加载失败</span></template>
            </el-image>
          </div>
          <span v-else class="no-image">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="amount" label="金额" width="100" />
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column prop="product_host" label="订单站点" min-width="160" />
      <el-table-column label="分类明细" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ formatSiteCategories(row.cat_names, row.product_category) }}</template>
      </el-table-column>
      <el-table-column label="建站类型" width="100" align="center">
        <template #default="{ row }"><el-tag v-if="row.site_tag !== undefined && row.site_tag !== null" :type="siteTagType(row.site_tag)">{{ siteTagLabel(row.site_tag) }}</el-tag><span v-else>—</span></template>
      </el-table-column>
      <el-table-column prop="pay_status_text" label="支付状态" width="90" />
      <el-table-column prop="customer_ip_country" label="国家" width="70" />
      <el-table-column prop="shipping_email" label="收货邮箱" width="180" />
      <el-table-column label="刷单审核" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.fraud_status" :type="fraudTagType(row.fraud_status)">{{ fraudLabels[row.fraud_status] }}</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="有效统计" width="100">
        <template #default="{ row }">{{ row.effective_valid === undefined ? '—' : Number(row.effective_valid) === 1 ? '计入' : '不计入' }}</template>
      </el-table-column>
      <el-table-column v-if="isAdmin" label="审核操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.fraud_status && row.fraud_status !== 'NORMAL'" link type="primary" @click="openFraudReview(row)">审核 / 记录</el-button>
        </template>
      </el-table-column>
      <el-table-column prop="admin_name" label="管理员" width="90" />
      <el-table-column label="用户组" width="86" align="center">
        <template #default="{ row }"><el-tag v-if="row.user_group" effect="light" :style="groupTagStyle(row.user_group)">{{ row.user_group }}组</el-tag><span v-else>—</span></template>
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
    <el-dialog v-model="fraudDialog" title="疑似刷单审核" width="680px" :close-on-click-modal="false" :before-close="closeFraudReview">
      <template v-if="reviewOrder">
        <p>订单 {{ reviewOrder.id }} · {{ reviewOrder.product_host }}</p>
        <p>{{ reviewOrder.shipping_email }}</p>
        <el-tag :type="fraudTagType(reviewOrder.fraud_status)">{{ fraudLabels[reviewOrder.fraud_status] }}</el-tag>
        <el-alert type="info" :closable="false" title="审核通过后恢复参与去重订单统计；有效去重仍须满足平台有效条件。重新同步不会覆盖审核记录。" />
        <el-form label-width="80px" style="margin-top: 18px">
          <el-form-item label="审核结果">
            <el-radio-group v-model="reviewForm.decision" :disabled="reviewSaving">
              <el-radio value="APPROVED">确认无异常</el-radio>
              <el-radio value="REJECTED">确认刷单</el-radio>
              <el-radio value="PENDING">重新待审核</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审核意见">
            <el-input v-model="reviewForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit :disabled="reviewSaving" placeholder="请填写核查依据或审核意见" />
          </el-form-item>
        </el-form>
        <el-table :data="reviewHistory" v-loading="reviewLoading" size="small" max-height="260">
          <el-table-column label="结果" width="120"><template #default="{ row }">{{ fraudLabels[row.decision] }}</template></el-table-column>
          <el-table-column prop="email" label="审核邮箱" min-width="180" show-overflow-tooltip />
          <el-table-column prop="reviewer" label="审核人" width="90" />
          <el-table-column prop="note" label="意见" min-width="130" />
          <el-table-column prop="reviewed_at" label="时间" width="165" />
        </el-table>
      </template>
      <template #footer>
        <el-button :disabled="reviewSaving" @click="fraudDialog = false">取消</el-button>
        <el-button type="primary" :loading="reviewSaving" :disabled="reviewLoading || !reviewForm.decision || !reviewForm.note.trim()" @click="submitFraudReview">提交审核</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrders, clearAllOrders, getOrderFraudReviews, reviewOrderFraud } from '@/api/dashboard'
import { useUserStore } from '@/store/user'
import { normalizeOrder } from '@/utils/orderProducts'
import { formatSiteCategories, groupTagStyle, siteTagLabel, siteTagType } from '@/utils/sitePresentation'
import { useSiteGroups } from '@/composables/useSiteGroups'

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
const clearingOrders = ref(false)
const userStore = useUserStore()
/** 筛选条件 */
const summary = ref({})
const { groupOptions, loadSiteGroups } = useSiteGroups()
const isAdmin = computed(() => (userStore.userInfo?.roles || []).some(role => String(role).toUpperCase() === 'ROLE_ADMIN'))
const filters = reactive({ userGroup: '', orderId: '', domain: '', adminName: '', siteTag: '', fraudStatus: '', payStatus: '', currency: '', country: '', dateRange: [] })
const fraudLabels = { NORMAL: '未命中规则', PENDING: '疑似刷单 · 待审核', APPROVED: '审核通过', REJECTED: '确认刷单' }
const fraudTagType = status => ({ NORMAL: 'info', PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' })[status] || 'info'
const fraudDialog = ref(false)
const reviewOrder = ref(null)
const reviewForm = reactive({ decision: '', note: '' })
const reviewHistory = ref([])
const reviewLoading = ref(false)
const reviewSaving = ref(false)
let reviewSequence = 0

function closeFraudReview(done) {
  if (!reviewSaving.value) done()
}

async function openFraudReview(row) {
  if (!isAdmin.value || reviewSaving.value) return
  const sequence = ++reviewSequence
  reviewOrder.value = { ...row }
  reviewForm.decision = ''
  reviewForm.note = ''
  reviewHistory.value = []
  fraudDialog.value = true
  reviewLoading.value = true
  try {
    const response = await getOrderFraudReviews(row.user_group, row.id)
    if (sequence === reviewSequence) reviewHistory.value = response.data || []
  } finally {
    if (sequence === reviewSequence) reviewLoading.value = false
  }
}

async function submitFraudReview() {
  if (!isAdmin.value || reviewSaving.value || reviewLoading.value || !reviewOrder.value || !reviewForm.decision || !reviewForm.note.trim()) return
  reviewSaving.value = true
  const order = reviewOrder.value
  try {
    await reviewOrderFraud(order.user_group, order.id, {
      email: order.shipping_email, decision: reviewForm.decision, note: reviewForm.note.trim(),
    })
    ElMessage.success('审核结果已保存，相关统计已更新')
    fraudDialog.value = false
    await fetchData()
  } finally {
    reviewSaving.value = false
  }
}
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
      siteTag: [0, 1, 2].includes(filters.siteTag) ? filters.siteTag : undefined,
      fraudStatus: filters.fraudStatus || undefined,
      payStatus: filters.payStatus || undefined,
      currency: filters.currency || undefined,
      country: filters.country.trim() || undefined,
      startDate: filters.dateRange?.[0] || undefined,
      endDate: filters.dateRange?.[1] || undefined,
    }
    const res = await getOrders(params)
    tableData.value = (res.data.list || []).map(normalizeOrder)
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
  Object.assign(filters, { userGroup: '', orderId: '', domain: '', adminName: '', siteTag: '', fraudStatus: '', payStatus: '', currency: '', country: '', dateRange: [] })
  page.value = 1
  fetchData()
}

/** 管理员清空全部订单，清空范围不受当前筛选条件影响。 */
async function handleClearAllOrders() {
  if (!isAdmin.value || total.value === 0) return
  try {
    await ElMessageBox.confirm(
      `将永久删除全部 ${total.value.toLocaleString('en-US')} 条订单（包含当前数据库全部站点分组），该操作不可恢复，确定继续吗？`,
      '清空全部订单',
      { type: 'warning', confirmButtonText: '确认清空', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  clearingOrders.value = true
  try {
    const res = await clearAllOrders()
    ElMessage.success(`已清空 ${res.data.deleted_count} 条订单`)
    page.value = 1
    await fetchData()
  } finally {
    clearingOrders.value = false
  }
}

onMounted(async () => {
  await loadSiteGroups()
  await fetchData()
})

</script>
<style scoped>
.order-filter { margin-bottom: 18px; }
.fraud-notice { margin-bottom: 16px; }
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
.order-table { container-type: inline-size; }
.order-products { box-sizing: border-box; width: 100cqw; max-width: 100%; padding: 4px 30px 12px; }
.expand-title { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; color: var(--cf-ink); font-size: 13px; font-weight: 600; }
.product-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: 12px; }
.product-card { display: flex; align-items: flex-start; gap: 18px; padding: 16px; border: 1px solid var(--cf-line-soft); border-radius: 10px; background: #fff; }
.product-card-images { display: grid; flex: 0 0 196px; grid-template-columns: repeat(2, 92px); gap: 10px; }
.product-card-images :deep(.el-image) { width: 92px; height: 92px; border: 1px solid var(--cf-line-soft); border-radius: 8px; background: #f5f7fa; }
.product-no-image { display: grid; flex: 0 0 196px; min-height: 92px; place-items: center; color: var(--cf-subtle); background: #f5f7fa; font-size: 12px; border-radius: 8px; }
.product-card-content { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 12px; color: var(--cf-muted); font-size: 12px; }
.product-heading { display: flex; align-items: flex-start; gap: 8px; }
.product-heading :deep(.el-tag) { flex-shrink: 0; }
.product-card-content strong { color: var(--cf-ink); font-size: 14px; line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; }
.product-details { display: grid; grid-template-columns: minmax(0, 1fr); margin: 0; }
.product-detail { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--cf-line-soft); }
.product-detail:last-child { border-bottom: 0; }
.product-detail dt { color: var(--cf-muted); overflow-wrap: anywhere; }
.product-detail dd { margin: 0; color: var(--cf-ink); white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.order-product-images { display: flex; flex-wrap: wrap; justify-content: center; gap: 6px; padding: 4px 0; }
.order-product-image { flex: 0 0 46px; width: 46px; height: 46px; border: 1px solid var(--cf-line-soft); border-radius: 6px; background: #f5f7fa; }
.image-placeholder { display: grid; width: 100%; height: 100%; place-items: center; color: var(--cf-subtle); font-size: 10px; line-height: 1.4; }
.no-image { color: var(--cf-subtle); }
@media (max-width: 980px) { .order-summary { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 540px) { .order-summary { gap: 8px; }.summary-item { padding: 13px; }.summary-item strong { font-size: 17px; } }
@media (max-width: 720px) { .order-filter :deep(.el-input), .order-filter :deep(.el-select), .order-filter .date-filter :deep(.el-date-editor) { width: 100%; } }
@media (max-width: 720px) {
  .order-products { padding: 4px 12px 12px; }
  .product-card { flex-direction: column; gap: 12px; padding: 12px; }
  .product-card-images { display: flex; flex: auto; flex-wrap: wrap; }
  .product-no-image { flex: auto; width: 92px; }
  .product-card-content { width: 100%; }
  .product-detail { grid-template-columns: 90px minmax(0, 1fr); gap: 8px; }
}
</style>
