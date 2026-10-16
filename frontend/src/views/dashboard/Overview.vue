<template>
  <div class="overview-page">
    <section class="page-heading">
      <div>
        <p class="eyebrow">OPERATIONS CENTER</p>
        <h1>运营总览</h1>
        <p class="heading-caption">聚合站点、商品、订单与采集数据，快速判断今日经营状态</p>
      </div>
      <div class="heading-controls">
        <el-segmented v-if="isAdmin" v-model="userGroup" :options="groupOptions" @change="loadDashboard" />
        <div class="heading-date"><el-icon><Calendar /></el-icon>{{ todayLabel }}</div>
      </div>
    </section>

    <section class="daily-brief">
      <div class="brief-copy">
        <span class="brief-kicker"><i></i>今日经营快照</span>
        <h2>今日有效去重 <strong>{{ formatNumber(overview.today_valid_deduplicated_orders) }}</strong> 笔订单，成交金额 <strong>{{ formatMoney(overview.today_amount ?? overview.successful_amount) }}</strong></h2>
        <p>{{ growthCopy }}</p>
      </div>
      <div class="brief-metrics">
        <div v-for="item in briefMetrics" :key="item.label" class="brief-metric">
          <span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.note }}</small>
        </div>
      </div>
    </section>

    <section class="stats-grid">
      <article v-for="card in stats" :key="card.label" class="metric-card">
        <div class="metric-top">
          <span class="metric-label">{{ card.label }}</span>
          <span class="metric-icon" :class="`tone-${card.tone}`"><el-icon><component :is="card.icon" /></el-icon></span>
        </div>
        <div class="metric-value">{{ card.value }}</div>
        <div class="metric-foot"><span :class="['metric-trend', card.trendTone]">{{ card.trend }}</span><span>{{ card.note }}</span></div>
      </article>
    </section>

    <section class="panel revenue-panel">
      <div class="panel-heading revenue-heading">
        <div><h2>收入转化与提成</h2><p>按当前统计周期汇总{{ isAdmin ? '个人绩效、团队提成与月度转化' : '个人绩效与月度转化' }}</p></div>
        <div class="revenue-rules">
          <el-button text size="small" :loading="revenueLoading" @click="loadDashboard">刷新统计</el-button>
          <div class="revenue-date-control"><span>订单统计</span><el-date-picker v-model="revenueDateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" class="revenue-date" @change="loadDashboard" /></div>
          <div class="revenue-date-control"><span>域名申请月份</span><el-date-picker v-model="siteCreatedMonth" type="month" value-format="YYYY-MM" placeholder="选择申请月份" class="revenue-month" @change="loadDashboard" /></div>
          <span>汇率 {{ revenueParameters.exchange_rate || '—' }}</span>
          <span>折算系数 {{ revenueParameters.rate_factor || '—' }}</span>
          <span>固定规则：{{ isAdmin ? '组长 2% · ' : '' }}普通 3/5/8% · 批量 2/4/6%</span>
        </div>
      </div>
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
    </section>

  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useUserStore } from '@/store/user'
import { Calendar, DataBoard, Goods, Money, ShoppingCart } from '@element-plus/icons-vue'
import { getOverview, getRevenueSummary } from '@/api/dashboard'
import { useSiteGroups } from '@/composables/useSiteGroups'
import RevenueRowDetails from '@/components/RevenueRowDetails.vue'

const userStore = useUserStore()
const overview = ref({})
const revenue = ref({})
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

const briefMetrics = computed(() => [
  { label: '昨日去重订单', value: formatNumber(overview.value.yesterday_deduplicated_orders), note: '不包含卡号' },
  { label: '昨日有效去重', value: formatNumber(overview.value.yesterday_valid_deduplicated_orders), note: '平台有效标记为 0' },
  { label: '昨日成交金额', value: formatMoney(overview.value.yesterday_successful_amount), note: '已支付金额' },
])

const stats = computed(() => [
  { label: '今日新增站点', value: formatNumber(overview.value.today_sites ?? overview.value.total_sites), icon: DataBoard, tone: 'blue', trend: '本日', trendTone: 'neutral', note: '今日建站' },
  { label: '今日去重订单', value: formatNumber(overview.value.today_deduplicated_orders ?? overview.value.deduplicated_orders), icon: ShoppingCart, tone: 'violet', trend: todayGrowthLabel.value, trendTone: todayOrderGrowth.value === null ? 'neutral' : todayOrderGrowth.value >= 0 ? 'up' : 'down', note: `昨日 ${formatNumber(yesterdayOrders.value)} 笔` },
  { label: '今日有效去重', value: formatNumber(overview.value.today_valid_deduplicated_orders), icon: Goods, tone: 'amber', trend: '本日', trendTone: 'neutral', note: '去重后有效订单' },
  { label: '今日成交金额', value: formatMoney(overview.value.today_successful_amount ?? overview.value.successful_amount), icon: Money, tone: 'green', trend: '本日', trendTone: 'up', note: '已支付金额' },
  { label: '昨日新增站点', value: formatNumber(overview.value.yesterday_sites), icon: DataBoard, tone: 'blue', trend: '昨日', trendTone: 'neutral', note: '昨日建站' },
  { label: '昨日去重订单', value: formatNumber(overview.value.yesterday_deduplicated_orders), icon: ShoppingCart, tone: 'violet', trend: '昨日', trendTone: 'neutral', note: '不包含卡号' },
  { label: '昨日有效去重', value: formatNumber(overview.value.yesterday_valid_deduplicated_orders), icon: Goods, tone: 'amber', trend: '昨日', trendTone: 'neutral', note: '去重后有效订单' },
  { label: '昨日成交金额', value: formatMoney(overview.value.yesterday_successful_amount), icon: Money, tone: 'green', trend: '昨日', trendTone: 'neutral', note: '已支付金额' },
])

let dashboardRequestId = 0
async function loadDashboard() {
  const requestId = ++dashboardRequestId
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
  revenueLoading.value = false
}

onMounted(async () => {
  await loadSiteGroups()
  await loadDashboard()
})
</script>

<style scoped>
.overview-page { max-width: 1440px; margin: 0 auto; }
.revenue-date { width: 245px; }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 24px; }
.heading-controls { display: flex; align-items: center; gap: 12px; }
.eyebrow { margin: 0 0 7px; color: var(--cf-blue); font-size: 10px; font-weight: 800; letter-spacing: .16em; }
h1, h2, p { margin: 0; } h1 { color: var(--cf-ink); font-size: 28px; letter-spacing: -.04em; }
.heading-caption { margin-top: 7px; color: var(--cf-muted); font-size: 13px; }
.heading-date { display: flex; align-items: center; gap: 8px; padding: 9px 13px; border: 1px solid var(--cf-line); border-radius: 9px; color: var(--cf-muted); background: #fff; font-size: 12px; }
.daily-brief { position: relative; display: grid; overflow: hidden; grid-template-columns: 1.2fr 1fr; gap: 24px; align-items: center; padding: 25px 28px; border-radius: 17px; color: #fff; background: linear-gradient(112deg, #15223a 0%, #1b2b4a 58%, #2f4074 100%); box-shadow: 0 16px 35px #1c2b4a1f; }
.daily-brief::after { position: absolute; top: -90px; right: -55px; width: 270px; height: 270px; border-radius: 50%; background: radial-gradient(circle, #7185ff42, transparent 68%); content: ''; }
.brief-copy { position: relative; z-index: 1; }.brief-kicker { display: flex; align-items: center; gap: 8px; color: #9eafe0; font-size: 10px; font-weight: 700; letter-spacing: .08em; }.brief-kicker i { width: 6px; height: 6px; border-radius: 50%; background: #45d09c; box-shadow: 0 0 0 4px #45d09c1c; }
.brief-copy h2 { margin-top: 12px; font-size: 19px; font-weight: 550; line-height: 1.55; }.brief-copy h2 strong { color: #aab9ff; font-weight: 760; }.brief-copy p { max-width: 660px; margin-top: 8px; color: #8190ae; font-size: 10px; line-height: 1.7; }
.brief-metrics { position: relative; z-index: 1; display: grid; grid-template-columns: repeat(3, 1fr); }.brief-metric { min-width: 0; padding: 4px 18px; border-left: 1px solid #ffffff14; }.brief-metric span, .brief-metric small { display: block; color: #8392b0; font-size: 9px; }.brief-metric strong { display: block; margin: 8px 0 5px; overflow: hidden; color: #fff; font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }.brief-metric small { color: #7383a4; }
.stats-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-top: 15px; }
.revenue-panel { margin-top: 15px; }.revenue-heading { gap: 18px; }.revenue-rules { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 7px; }.revenue-rules span { padding: 5px 9px; border-radius: 999px; color: #536ff1; background: #eef1ff; font-size: 9px; font-weight: 700; }.revenue-date-control { display: flex; align-items: center; gap: 5px; }.revenue-date-control > span { padding: 0; color: #8490a4; background: transparent; font-size: 9px; font-weight: 700; white-space: nowrap; }.revenue-tabs { margin-top: 16px; }.commission-breakdown { display: flex; flex-direction: column; align-items: flex-end; gap: 5px; padding: 3px 0; }.commission-breakdown strong { color: #24956e; font-size: 14px; line-height: 1.2; white-space: nowrap; }.commission-breakdown small { display: flex; gap: 9px; color: #8a96a9; font-size: 9px; line-height: 1.2; white-space: nowrap; }.compact-revenue-table { container-type: inline-size; }.compact-revenue-table :deep(.el-table__expanded-cell) { padding: 0 !important; }
.metric-card, .panel { border: 1px solid var(--cf-line); border-radius: 14px; background: #fff; box-shadow: var(--cf-shadow-sm); }
.metric-card { padding: 18px 20px 16px; }.metric-top, .metric-foot, .panel-heading { display: flex; align-items: center; justify-content: space-between; }.metric-label { color: var(--cf-muted); font-size: 11px; }.metric-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 10px; font-size: 16px; }.tone-blue { color: #536ff1; background: #eef1ff; }.tone-violet { color: #8a64e8; background: #f3edff; }.tone-amber { color: #d99a37; background: #fff6e3; }.tone-green { color: #36ad82; background: #eaf9f3; }
.metric-value { margin: 14px 0 11px; color: var(--cf-ink); font-size: 25px; font-weight: 750; letter-spacing: -.04em; }.metric-foot { color: var(--cf-subtle); font-size: 10px; }.metric-trend { font-weight: 700; }.metric-trend.up { color: var(--cf-green); }.metric-trend.down { color: #df6577; }.metric-trend.neutral { color: var(--cf-blue); }
.panel { min-width: 0; padding: 21px; }.panel-heading { align-items: flex-start; }.panel-heading h2 { color: #263550; font-size: 14px; letter-spacing: -.02em; }.panel-heading p { margin-top: 5px; color: #9aa7b8; font-size: 10px; }
@media (max-width: 1120px) { .daily-brief { grid-template-columns: 1fr; }.revenue-heading { align-items: stretch; flex-direction: column; }.revenue-rules { justify-content: flex-start; } }
@media (max-width: 760px) { .heading-controls { flex-wrap: wrap; }.revenue-date, .revenue-month { width: 100%; }.revenue-date-control { width: 100%; }.stats-grid { grid-template-columns: repeat(2, 1fr); }.daily-brief { padding: 21px; }.brief-metrics { gap: 8px; }.brief-metric { padding: 4px 8px; }.brief-copy h2 { font-size: 16px; } }
@media (max-width: 560px) { .page-heading { align-items: flex-start; flex-direction: column; gap: 14px; }.heading-date { display: none; }.stats-grid { gap: 9px; }.metric-card { padding: 15px; }.metric-value { font-size: 20px; }.brief-metrics { grid-template-columns: 1fr; }.brief-metric { display: grid; grid-template-columns: 1fr auto; align-items: center; padding: 9px 0; border-top: 1px solid #ffffff12; border-left: 0; }.brief-metric strong { margin: 0; font-size: 14px; }.brief-metric small { display: none; }.panel { padding: 17px; } }
</style>
