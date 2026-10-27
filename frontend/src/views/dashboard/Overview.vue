<template>
  <div class="overview-page">
    <PageHeader
      eyebrow="OPERATIONS CENTER"
      title="运营总览"
      description="聚合站点、商品、订单与采集数据，快速判断今日经营状态"
    >
      <template #actions>
        <el-segmented v-if="isAdmin" v-model="userGroup" :options="groupOptions" @change="loadDashboard" />
        <div class="heading-date">
          <el-icon><Calendar /></el-icon>
          {{ todayLabel }}
        </div>
      </template>
    </PageHeader>

    <section class="daily-brief">
      <div class="brief-copy">
        <span class="brief-kicker"><i aria-hidden="true"></i>今日经营快照</span>

        <template v-if="overviewLoading">
          <div class="brief-skeleton" aria-label="正在加载今日经营数据">
            <span class="skeleton-line is-title"></span>
            <span class="skeleton-line is-copy"></span>
          </div>
        </template>
        <template v-else-if="hasOverviewData">
          <h2>
            今日有效去重 <strong>{{ formatNumber(overview.today_valid_deduplicated_orders) }}</strong> 笔订单，
            成交金额 <strong>{{ formatMoney(overview.today_amount ?? overview.successful_amount) }}</strong>
          </h2>
          <p>{{ growthCopy }}</p>
        </template>
        <template v-else>
          <h2>今日经营数据暂未同步</h2>
          <p>同步任务完成后，这里会自动展示有效订单与成交金额。</p>
        </template>
      </div>

      <router-link class="brief-action" to="/dashboard/orders">
        查看订单明细
        <el-icon><ArrowRight /></el-icon>
      </router-link>
    </section>

    <section class="stats-grid" aria-label="今日关键指标">
      <StatCard
        v-for="card in stats"
        :key="card.label"
        :label="card.label"
        :icon="card.icon"
        :tone="card.tone"
        :trend="card.trend"
        :trend-tone="card.trendTone"
        :note="card.note"
      >
        <template #value>
          <span v-if="overviewLoading" class="skeleton-line is-value" aria-hidden="true"></span>
          <template v-else>{{ card.value }}</template>
        </template>
      </StatCard>
    </section>

    <section class="compare-strip" aria-label="较昨日对比">
      <div class="compare-head">
        <span class="compare-title">较昨日</span>
        <span class="compare-note">同口径对比，统计全部去重订单</span>
      </div>
      <div class="compare-grid">
        <div v-for="item in comparisons" :key="item.label" class="compare-item">
          <span>{{ item.label }}</span>
          <strong class="cf-num">{{ item.value }}</strong>
          <small :class="`is-${item.tone}`">{{ item.note }}</small>
        </div>
      </div>
    </section>

    <DataPanel
      class="revenue-panel"
      title="收入转化与提成"
      :description="`按当前统计周期汇总${isAdmin ? '个人绩效、团队提成与月度转化' : '个人绩效与月度转化'}`"
    >
      <template #actions>
        <el-button text size="small" :loading="revenueLoading" @click="loadDashboard">刷新统计</el-button>

        <div class="revenue-date-control">
          <span>订单统计</span>
          <el-date-picker
            v-model="revenueDateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="revenue-date"
            @change="loadDashboard"
          />
        </div>

        <div class="revenue-date-control">
          <span>域名申请月份</span>
          <el-date-picker
            v-model="siteCreatedMonth"
            type="month"
            value-format="YYYY-MM"
            placeholder="选择申请月份"
            class="revenue-month"
            @change="loadDashboard"
          />
        </div>

        <el-tooltip placement="bottom-end" :content="revenueRuleText">
          <el-button text size="small" :icon="InfoFilled">核算规则</el-button>
        </el-tooltip>
      </template>

      <el-tabs v-model="revenueTab" class="revenue-tabs">
        <el-tab-pane label="个人绩效" name="personal">
          <el-table :data="personalPerformance" stripe max-height="390" empty-text="暂无绩效数据" class="compact-revenue-table">
            <el-table-column type="expand" label="详情" width="62">
              <template #default="{ row }"><RevenueRowDetails :details="personalDetails(row)" :breakdown="row.classification_breakdown" :category-breakdown="row.category_breakdown" show-category-sites /></template>
            </el-table-column>
            <el-table-column prop="user_group" label="组别" width="70" />
            <el-table-column prop="real_name" label="姓名" min-width="120" />
            <el-table-column prop="site_count" label="所有站点" width="95" align="right" />
            <el-table-column prop="valid_deduplicated_orders" label="有效去重" width="100" align="right" />
            <el-table-column label="订单转化率" width="105" align="right"><template #default="{ row }">{{ formatPercent(row.order_conversion_rate) }}</template></el-table-column>
            <el-table-column label="站点转化率" width="105" align="right"><template #default="{ row }">{{ formatPercent(row.site_conversion_rate) }}</template></el-table-column>
            <el-table-column label="百站转化率" width="105" align="right"><template #default="{ row }">{{ formatPercent(row.hundred_site_conversion_rate) }}</template></el-table-column>
            <el-table-column label="成功金额" min-width="130" align="right"><template #default="{ row }">{{ formatMoney(row.successful_amount) }}</template></el-table-column>
            <el-table-column label="组员总提成(RMB)" min-width="190" align="right" fixed="right"><template #default="{ row }"><div class="commission-breakdown"><strong>{{ formatCommission(row.total_member_commission_rmb) }}</strong><small><span>普通 {{ formatCommission(row.regular_commission_rmb) }}</span><span>批量 {{ formatCommission(row.batch_site_commission_rmb) }}</span></small></div></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane v-if="canViewLeaderSummary" label="组长汇总" name="leaders">
          <el-table :data="leaderSummary" stripe empty-text="暂无组长汇总" class="compact-revenue-table">
            <el-table-column type="expand" label="详情" width="62">
              <template #default="{ row }"><RevenueRowDetails :details="leaderDetails(row)" :breakdown="row.classification_breakdown" :category-breakdown="row.category_breakdown" show-category-sites category-scope="小组" /></template>
            </el-table-column>
            <el-table-column prop="user_group" label="组别" width="70" />
            <el-table-column prop="leader_name" label="组长" min-width="120" />
            <el-table-column prop="member_count" label="成员" width="80" align="right" />
            <el-table-column prop="site_count" label="所有站点" width="95" align="right" />
            <el-table-column prop="valid_deduplicated_orders" label="有效去重" width="100" align="right" />
            <el-table-column label="订单转化率" width="105" align="right"><template #default="{ row }">{{ formatPercent(row.order_conversion_rate) }}</template></el-table-column>
            <el-table-column label="站点转化率" width="105" align="right"><template #default="{ row }">{{ formatPercent(row.site_conversion_rate) }}</template></el-table-column>
            <el-table-column label="百站转化率" width="105" align="right"><template #default="{ row }">{{ formatPercent(row.hundred_site_conversion_rate) }}</template></el-table-column>
            <el-table-column label="小组成功金额" min-width="145" align="right"><template #default="{ row }">{{ formatMoney(row.original_amount) }}</template></el-table-column>
            <el-table-column label="组长总提成(RMB)" min-width="210" align="right" fixed="right"><template #default="{ row }"><div class="commission-breakdown"><strong>{{ formatCommission(row.leader_total_commission_rmb ?? row.leader_commission_rmb) }}</strong><small><span>小组 {{ formatCommission(row.leader_commission_rmb) }}</span><span>个人 {{ formatCommission(row.leader_personal_commission_rmb) }}</span></small></div></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="月度转化" name="monthly">
          <el-table :data="monthlyConversion" stripe max-height="390" empty-text="暂无月度数据" class="compact-revenue-table">
            <el-table-column type="expand" label="详情" width="62">
              <template #default="{ row }"><RevenueRowDetails :details="monthlyDetails(row)" :breakdown="row.classification_breakdown" :category-breakdown="row.category_breakdown" /></template>
            </el-table-column>
            <el-table-column prop="site_month" label="申请月份" width="105" />
            <el-table-column prop="user_group" label="组别" width="70" />
            <el-table-column prop="real_name" label="姓名" min-width="120" />
            <el-table-column prop="site_count" label="建站数" width="90" align="right" />
            <el-table-column prop="valid_deduplicated_orders" label="有效去重" width="100" align="right" />
            <el-table-column label="订单转化率" width="105" align="right"><template #default="{ row }">{{ formatPercent(row.order_conversion_rate ?? row.conversion_rate) }}</template></el-table-column>
            <el-table-column label="成功金额" min-width="120" align="right"><template #default="{ row }">{{ formatMoney(row.successful_amount) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </DataPanel>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/store/user'
import { Calendar, ArrowRight, Goods, InfoFilled, Money, ShoppingCart, WarningFilled } from '@element-plus/icons-vue'
import { getOverview, getRevenueSummary } from '@/api/dashboard'
import { useSiteGroups } from '@/composables/useSiteGroups'
import RevenueRowDetails from '@/components/RevenueRowDetails.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import DataPanel from '@/components/DataPanel.vue'

const userStore = useUserStore()
const overview = ref({})
const revenue = ref({})
const overviewLoading = ref(true)
const revenueLoading = ref(false)
const revenueTab = ref('personal')
const dateKey = date => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
const currentMonthRange = () => {
  const today = new Date()
  const first = new Date(today.getFullYear(), today.getMonth(), 1)
  return [dateKey(first), dateKey(today)]
}
const revenueDateRange = ref(currentMonthRange())
const siteCreatedMonth = ref(currentMonthRange()[0].slice(0, 7))
const userGroup = ref('')
const { groupOptions, loadSiteGroups } = useSiteGroups()
const userRoles = computed(() => userStore.userInfo?.roles || [])
const isAdmin = computed(() => userRoles.value.some(role => String(role).toUpperCase() === 'ROLE_ADMIN'))
const canViewLeaderSummary = computed(() => isAdmin.value)

const todayLabel = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(new Date())
const toNumber = value => Number(value || 0)
const formatNumber = value => value === undefined || value === null ? '—' : toNumber(value).toLocaleString('en-US')
const formatMoney = value => value === undefined || value === null ? '—' : `$${toNumber(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const formatPlainMoney = value => value === undefined || value === null ? '—' : toNumber(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatCommission = value => value === undefined || value === null ? '—' : `¥${formatPlainMoney(value)}`
const formatPercent = value => value === undefined || value === null ? '—' : `${toNumber(value).toFixed(2)}%`
const formatRate = value => value === undefined || value === null ? '—' : `${(toNumber(value) * 100).toFixed(2)}%`
const revenueParameters = computed(() => revenue.value.parameters || {})
const personalPerformance = computed(() => revenue.value.personal_performance || [])
const leaderSummary = computed(() => revenue.value.leader_summary || [])
const monthlyConversion = computed(() => revenue.value.monthly_conversion || [])
const personalDetails = row => [
  { label: '账号', value: row.accounts || '—' },
  { label: '去重订单', value: formatNumber(row.deduplicated_orders) },
  { label: '有效出单站点', value: formatNumber(row.valid_ordered_site_count) },
  { label: '成功订单', value: formatNumber(row.successful_orders) },
  { label: '原成交金额', value: formatMoney(row.original_amount) },
  { label: '实习生同步', value: formatMoney(row.synced_amount) },
  { label: '批量站点', value: formatNumber(row.batch_site_count) },
  { label: '批量成交额', value: formatMoney(row.batch_site_amount) },
  { label: '批量提成基数', value: formatCommission(row.batch_site_commission_base_rmb) },
  { label: '普通提成', value: formatCommission(row.regular_commission_rmb) },
  { label: `批量提成（${formatRate(row.batch_site_commission_rate)}）`, value: formatCommission(row.batch_site_commission_rmb) },
]
const leaderDetails = row => [
  { label: '所有站点', value: formatNumber(row.site_count) },
  { label: '去重订单', value: formatNumber(row.deduplicated_orders) },
  { label: '有效出单站点', value: formatNumber(row.valid_ordered_site_count) },
  { label: '组长个人业绩', value: formatMoney(row.leader_personal_amount) },
  { label: '小组提成基数', value: formatMoney(row.commission_base_amount) },
  { label: '小组提成', value: formatCommission(row.leader_commission_rmb) },
  { label: '个人提成', value: formatCommission(row.leader_personal_commission_rmb) },
]
const monthlyDetails = row => [
  { label: '账号', value: row.admin_name || '—' },
  { label: '本月去重订单', value: formatNumber(row.deduplicated_orders) },
  { label: '成功订单', value: formatNumber(row.successful_orders) },
  { label: '本月有订单站点', value: formatNumber(row.ordered_site_count) },
  { label: '站点转化率', value: formatPercent(row.site_conversion_rate) },
]
const todayOrders = computed(() => toNumber(overview.value.today_deduplicated_orders))
const yesterdayOrders = computed(() => toNumber(overview.value.yesterday_deduplicated_orders))
const todayOrderChange = computed(() => todayOrders.value - yesterdayOrders.value)
const todayOrderGrowth = computed(() => yesterdayOrders.value
  ? (todayOrderChange.value / yesterdayOrders.value) * 100
  : null)
const todayGrowthLabel = computed(() => todayOrderGrowth.value === null
  ? '昨日暂无基准'
  : `${todayOrderGrowth.value >= 0 ? '+' : ''}${todayOrderGrowth.value.toFixed(1)}%`)
const growthCopy = computed(() => yesterdayOrders.value
  ? `今日去重订单较昨日${todayOrderChange.value >= 0 ? '增加' : '减少'} ${formatNumber(Math.abs(todayOrderChange.value))} 笔，变化 ${todayGrowthLabel.value}。`
  : `今日去重订单 ${formatNumber(todayOrders.value)} 笔，昨日暂无可对比订单。`)
const hasOverviewData = computed(() => Object.keys(overview.value).length > 0)

const comparisons = computed(() => [
  {
    label: '昨日去重订单',
    value: formatNumber(overview.value.yesterday_deduplicated_orders),
    note: `今日 ${todayOrderChange.value >= 0 ? '+' : ''}${formatNumber(todayOrderChange.value)} 笔（${todayGrowthLabel.value}）`,
    tone: todayOrderChange.value > 0 ? 'up' : todayOrderChange.value < 0 ? 'down' : 'neutral',
  },
  {
    label: '昨日有效去重',
    value: formatNumber(overview.value.yesterday_valid_deduplicated_orders),
    note: '平台有效标记为 0',
    tone: 'neutral',
  },
  {
    label: '昨日成交金额',
    value: formatMoney(overview.value.yesterday_successful_amount),
    note: '已支付金额',
    tone: 'neutral',
  },
])

const stats = computed(() => [
  { label: '今日风控订单', value: formatNumber(overview.value.today_risk_orders), icon: WarningFilled, tone: 'amber', trend: '本日', trendTone: 'neutral', note: '支付平台风控标记' },
  { label: '今日去重订单', value: formatNumber(overview.value.today_deduplicated_orders ?? overview.value.deduplicated_orders), icon: ShoppingCart, tone: 'violet', trend: todayGrowthLabel.value, trendTone: todayOrderGrowth.value === null ? 'neutral' : todayOrderGrowth.value >= 0 ? 'up' : 'down', note: `昨日 ${formatNumber(yesterdayOrders.value)} 笔` },
  { label: '今日有效去重', value: formatNumber(overview.value.today_valid_deduplicated_orders), icon: Goods, tone: 'blue', trend: '本日', trendTone: 'neutral', note: '去重后有效订单' },
  { label: '今日成交金额', value: formatMoney(overview.value.today_successful_amount ?? overview.value.successful_amount), icon: Money, tone: 'green', trend: '本日', trendTone: 'up', note: '已支付金额' },
])

const revenueRuleText = computed(() => {
  const rate = revenueParameters.value.exchange_rate || '—'
  const factor = revenueParameters.value.rate_factor || '—'
  const leader = isAdmin.value ? '组长 2% · ' : ''
  return `汇率 ${rate} · 折算系数 ${factor} · 提成规则：${leader}普通 3/5/8% · 批量 2/4/6%`
})

let dashboardRequestId = 0
async function loadDashboard() {
  const requestId = ++dashboardRequestId
  overviewLoading.value = true
  revenueLoading.value = true
  const params = { userGroup: userGroup.value || undefined }
  const revenueParams = { ...params, startDate: revenueDateRange.value?.[0], endDate: revenueDateRange.value?.[1], siteCreatedMonth: siteCreatedMonth.value }
  const [overviewResult, revenueResult] = await Promise.allSettled([
    getOverview(params),
    getRevenueSummary(revenueParams),
  ])
  if (requestId !== dashboardRequestId) return
  if (overviewResult.status === 'fulfilled') overview.value = overviewResult.value.data || {}
  revenue.value = revenueResult.status === 'fulfilled' ? revenueResult.value.data || {} : {}
  overviewLoading.value = false
  revenueLoading.value = false
}

onMounted(async () => {
  await loadSiteGroups()
  await loadDashboard()
})
</script>

<style scoped>
.overview-page {
  max-width: var(--cf-page-max);
  margin: 0 auto;
}

.heading-date {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--cf-line);
  border-radius: var(--cf-radius-sm);
  color: var(--cf-muted);
  background: var(--cf-surface);
  font-size: var(--cf-text-small);
}

/* ---- daily brief ---- */

.daily-brief {
  position: relative;
  display: grid;
  overflow: hidden;
  align-items: end;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 24px;
  padding: 26px 28px;
  border-radius: var(--cf-radius-lg);
  color: var(--cf-on-primary);
  background:
    radial-gradient(circle at 88% 20%, rgb(79 107 237 / 38%), transparent 42%),
    linear-gradient(112deg, var(--cf-nav-bg) 0%, var(--cf-nav-surface) 60%, #2b3b68 100%);
  box-shadow: 0 16px 35px -20px rgb(28 43 74 / 55%);
}

.daily-brief::after {
  position: absolute;
  top: -110px;
  right: -70px;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle, rgb(113 133 255 / 26%), transparent 68%);
  content: '';
  pointer-events: none;
}

.brief-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.brief-kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cf-nav-muted);
  font-size: var(--cf-text-small);
  font-weight: 600;
  letter-spacing: .08em;
}

.brief-kicker i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--cf-success);
  box-shadow: 0 0 0 4px rgb(31 157 116 / 18%);
}

.brief-copy h2 {
  margin-top: 12px;
  color: var(--cf-on-primary);
  font-size: 19px;
  font-weight: 500;
  line-height: 1.55;
}

.brief-copy h2 strong {
  color: var(--cf-primary-soft-strong);
  font-weight: 600;
}

.brief-copy p {
  max-width: 680px;
  margin-top: 8px;
  color: var(--cf-nav-muted);
  font-size: var(--cf-text-small);
  line-height: 1.7;
}

.brief-action {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid rgb(255 255 255 / 14%);
  border-radius: var(--cf-radius-sm);
  color: var(--cf-nav-text);
  background: rgb(255 255 255 / 6%);
  font-size: var(--cf-text-small);
  font-weight: 600;
  white-space: nowrap;
  transition: background-color var(--cf-dur-fast) var(--cf-ease), border-color var(--cf-dur-fast) var(--cf-ease);
}

.brief-action:hover {
  border-color: rgb(255 255 255 / 24%);
  color: var(--cf-on-primary);
  background: rgb(255 255 255 / 12%);
}

/* ---- KPI + comparison ---- */

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.compare-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: 16px;
  padding: 16px 20px;
  border: 1px solid var(--cf-line);
  border-radius: var(--cf-radius);
  background: var(--cf-surface);
  box-shadow: var(--cf-shadow-sm);
}

.compare-head {
  display: grid;
  flex: none;
  gap: 3px;
}

.compare-title {
  color: var(--cf-ink);
  font-size: var(--cf-text-body);
  font-weight: 600;
}

.compare-note {
  color: var(--cf-subtle);
  font-size: var(--cf-text-small);
}

.compare-grid {
  display: grid;
  min-width: 0;
  flex: 1;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.compare-item {
  display: grid;
  min-width: 0;
  gap: 3px;
  padding-left: 16px;
  border-left: 1px solid var(--cf-line-soft);
}

.compare-item > span {
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
}

.compare-item > strong {
  color: var(--cf-ink);
  font-size: 16px;
  font-weight: 600;
}

.compare-item > small {
  overflow: hidden;
  color: var(--cf-subtle);
  font-size: var(--cf-text-small);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.compare-item > small.is-up {
  color: var(--cf-success-text);
}

.compare-item > small.is-down {
  color: var(--cf-danger-text);
}

/* ---- revenue ---- */

.revenue-panel {
  margin-top: 16px;
}

.revenue-date-control {
  display: flex;
  align-items: center;
  gap: 6px;
}

.revenue-date-control > span {
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
  font-weight: 600;
  white-space: nowrap;
}

.revenue-date {
  width: 236px;
}

.revenue-month {
  width: 150px;
}

.revenue-tabs {
  margin-top: 4px;
}

.commission-breakdown {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 4px;
  padding: 3px 0;
}

.commission-breakdown strong {
  color: var(--cf-success-text);
  font-size: var(--cf-text-body);
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
}

.commission-breakdown small {
  display: flex;
  gap: 9px;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
  line-height: 1.2;
  white-space: nowrap;
}

.compact-revenue-table {
  container-type: inline-size;
}

.compact-revenue-table :deep(.el-table__expanded-cell) {
  padding: 0 !important;
}

/* ---- loading skeleton ---- */

.brief-skeleton {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.skeleton-line {
  display: inline-block;
  height: 14px;
  border-radius: 7px;
  background: linear-gradient(90deg, rgb(255 255 255 / 8%), rgb(255 255 255 / 16%), rgb(255 255 255 / 8%));
  background-size: 240% 100%;
  animation: cf-shimmer 1.4s ease-in-out infinite;
}

.skeleton-line.is-title {
  width: min(70%, 460px);
  height: 22px;
}

.skeleton-line.is-copy {
  width: min(52%, 340px);
  height: 12px;
}

.skeleton-line.is-value {
  width: 76px;
  height: 26px;
  background: linear-gradient(90deg, var(--cf-line-soft), var(--cf-surface-sunken), var(--cf-line-soft));
  background-size: 240% 100%;
}

@keyframes cf-shimmer {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}

/* ---- responsive ---- */

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .daily-brief {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .daily-brief {
    align-items: start;
    grid-template-columns: 1fr;
  }

  .brief-action {
    justify-self: start;
  }

  .compare-strip {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .compare-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .compare-item {
    display: grid;
    align-items: baseline;
    grid-template-columns: 120px 1fr auto;
    gap: 8px;
    padding: 8px 0 0;
    border-top: 1px solid var(--cf-line-soft);
    border-left: 0;
  }

  .compare-item > small {
    text-align: right;
  }
}

@media (max-width: 640px) {
  .daily-brief {
    padding: 21px;
  }

  .brief-copy h2 {
    font-size: 16px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .compare-item {
    grid-template-columns: 1fr auto;
  }

  .compare-item > small {
    grid-column: 1 / -1;
    text-align: left;
  }

  .revenue-date-control {
    width: 100%;
    align-items: stretch;
    flex-direction: column;
  }

  .revenue-date,
  .revenue-month {
    width: 100%;
  }
}
@media (min-width: 561px) and (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
