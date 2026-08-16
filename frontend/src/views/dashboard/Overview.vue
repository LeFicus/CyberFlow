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
        <h2>今日成功 <strong>{{ formatNumber(overview.today_orders ?? overview.successful_orders) }}</strong> 笔订单，成功金额 <strong>{{ formatMoney(overview.today_amount ?? overview.successful_amount) }}</strong></h2>
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
        <div><h2>收入转化与提成</h2><p>按 monthly_revenue_conversion.py 口径实时汇总</p></div>
        <div class="revenue-rules">
          <div class="revenue-date-control"><span>订单统计</span><el-date-picker v-model="revenueDateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" class="revenue-date" @change="loadDashboard" /></div>
          <div class="revenue-date-control"><span>建站月份</span><el-date-picker v-model="siteCreatedMonth" type="month" value-format="YYYY-MM" placeholder="选择建站月份" class="revenue-month" @change="loadDashboard" /></div>
          <span>汇率 {{ revenueParameters.exchange_rate || '—' }}</span>
          <span>折算系数 {{ revenueParameters.rate_factor || '—' }}</span>
          <span>组长比例 {{ formatRate(revenueParameters.leader_commission_rate) }}</span>
        </div>
      </div>
      <el-tabs v-model="revenueTab" class="revenue-tabs">
        <el-tab-pane label="个人绩效" name="personal">
          <el-table :data="personalPerformance" stripe max-height="390" empty-text="暂无绩效数据">
            <el-table-column prop="user_group" label="组别" width="70" />
            <el-table-column prop="real_name" label="姓名" min-width="120" />
            <el-table-column prop="site_count" label="全局站点" width="90" align="right" />
            <el-table-column prop="deduplicated_orders" label="去重订单" width="95" align="right" />
            <el-table-column label="转化率" width="95" align="right"><template #default="{ row }">{{ formatPercent(row.conversion_rate) }}</template></el-table-column>
            <el-table-column label="原成交金额" min-width="120" align="right"><template #default="{ row }">{{ formatMoney(row.original_amount) }}</template></el-table-column>
            <el-table-column label="实习生同步" min-width="115" align="right"><template #default="{ row }">{{ formatMoney(row.synced_amount) }}</template></el-table-column>
            <el-table-column label="成功金额" min-width="115" align="right"><template #default="{ row }">{{ formatMoney(row.successful_amount) }}</template></el-table-column>
            <el-table-column label="个人提成(RMB)" min-width="135" align="right"><template #default="{ row }"><strong class="commission-value">¥{{ formatPlainMoney(row.commission_rmb) }}</strong></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane v-if="canViewLeaderSummary" label="组长汇总" name="leaders">
          <el-table :data="leaderSummary" stripe empty-text="暂无组长汇总">
            <el-table-column prop="user_group" label="组别" width="70" />
            <el-table-column prop="leader_name" label="组长" min-width="120" />
            <el-table-column prop="member_count" label="成员" width="80" align="right" />
            <el-table-column prop="site_count" label="全局站点" width="95" align="right" />
            <el-table-column prop="deduplicated_orders" label="去重订单" width="100" align="right" />
            <el-table-column label="转化率" width="100" align="right"><template #default="{ row }">{{ formatPercent(row.conversion_rate) }}</template></el-table-column>
            <el-table-column label="小组原成交金额" min-width="145" align="right"><template #default="{ row }">{{ formatMoney(row.original_amount) }}</template></el-table-column>
            <el-table-column label="组长提成(RMB)" min-width="145" align="right"><template #default="{ row }"><strong class="commission-value">¥{{ formatPlainMoney(row.leader_commission_rmb) }}</strong></template></el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="月度转化" name="monthly">
          <el-table :data="monthlyConversion" stripe max-height="390" empty-text="暂无月度数据">
            <el-table-column prop="site_month" label="建站月份" width="105" />
            <el-table-column prop="user_group" label="组别" width="70" />
            <el-table-column prop="real_name" label="姓名" min-width="120" />
            <el-table-column prop="admin_name" label="账号" min-width="130" />
            <el-table-column prop="site_count" label="建站数" width="90" align="right" />
            <el-table-column prop="deduplicated_orders" label="本月去重订单" width="115" align="right" />
            <el-table-column prop="ordered_site_count" label="本月有订单站点" width="125" align="right" />
            <el-table-column label="订单转化率" width="105" align="right"><template #default="{ row }">{{ formatPercent(row.order_conversion_rate ?? row.conversion_rate) }}</template></el-table-column>
            <el-table-column label="站点转化率" width="105" align="right"><template #default="{ row }">{{ formatPercent(row.site_conversion_rate) }}</template></el-table-column>
            <el-table-column label="成功金额" min-width="120" align="right"><template #default="{ row }">{{ formatMoney(row.successful_amount) }}</template></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <section class="content-grid">
      <article class="panel chart-panel">
        <div class="panel-heading">
          <div><h2>去重订单与交易趋势</h2><p>近 30 天按订单 ID 去重的订单量和成功金额变化</p></div>
          <span class="panel-chip blue">最近 30 天</span>
        </div>
        <v-chart :option="orderTrendOption" autoresize class="chart chart-large" />
      </article>

      <article class="panel ranking-panel">
        <div class="panel-heading"><div><h2>管理员订单贡献</h2><p>按订单数量排序的运营表现</p></div></div>
        <div v-if="adminRanking.length" class="ranking-list">
          <div v-for="(item, index) in adminRanking" :key="item.name" class="ranking-row">
            <span class="ranking-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <div class="ranking-main">
              <div><strong>{{ item.name }}</strong><span>{{ formatNumber(item.count) }} 笔</span></div>
              <div class="ranking-track"><i :style="{ width: `${item.percent}%` }"></i></div>
            </div>
          </div>
        </div>
        <el-empty v-else :image-size="54" description="暂无管理员订单数据" />
        <button class="panel-link" @click="router.push('/dashboard/orders')">查看全部订单<el-icon><ArrowRight /></el-icon></button>
      </article>
    </section>

    <section class="bottom-grid">
      <article class="panel chart-panel">
        <div class="panel-heading">
          <div><h2>站点收录趋势</h2><p>Google 收录指数的近 30 天变化</p></div>
          <span :class="['panel-chip', indexGrowth >= 0 ? 'green' : 'red']">{{ indexGrowthLabel }}</span>
        </div>
        <v-chart :option="indexTrendOption" autoresize class="chart" />
      </article>

      <article class="panel action-panel">
        <div class="panel-heading"><div><h2>快捷任务</h2><p>选择采集类型并进入对应配置</p></div></div>
        <div class="quick-actions">
          <button v-for="action in quickActions" :key="action.label" class="quick-action" @click="router.push(action.path)">
            <span :class="`quick-icon ${action.tone}`"><el-icon><component :is="action.icon" /></el-icon></span>
            <span><strong>{{ action.label }}</strong><small>{{ action.description }}</small></span>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
        <div class="coverage-strip">
          <div v-for="item in coverageItems" :key="item.label"><strong>{{ item.value }}</strong><span>{{ item.label }}</span></div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ArrowRight, Calendar, DataBoard, Goods, Money, ShoppingCart } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { getCharts, getOverview, getRevenueSummary } from '@/api/dashboard'

use([CanvasRenderer, LineChart, GridComponent, LegendComponent, TooltipComponent])
const router = useRouter()
const userStore = useUserStore()
const overview = ref({})
const charts = ref({})
const revenue = ref({})
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
const groupOptions = [{ label: '全部', value: '' }, { label: 'A组', value: 'A' }, { label: 'B组', value: 'B' }]
const userRoles = computed(() => userStore.userInfo?.roles || [])
const isAdmin = computed(() => userRoles.value.some(role => String(role).toUpperCase() === 'ROLE_ADMIN'))
const canViewLeaderSummary = computed(() => isAdmin.value || userRoles.value.some(role => String(role).toUpperCase() === 'ROLE_OPERATOR'))

const todayLabel = new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(new Date())
const toNumber = value => Number(value || 0)
const formatNumber = value => value === undefined || value === null ? '—' : toNumber(value).toLocaleString('en-US')
const formatMoney = value => value === undefined || value === null ? '—' : `$${toNumber(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const formatPlainMoney = value => toNumber(value).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatPercent = value => `${toNumber(value).toFixed(2)}%`
const formatRate = value => value === undefined || value === null ? '—' : `${(toNumber(value) * 100).toFixed(2)}%`
const revenueParameters = computed(() => revenue.value.parameters || {})
const personalPerformance = computed(() => revenue.value.personal_performance || [])
const leaderSummary = computed(() => revenue.value.leader_summary || [])
const monthlyConversion = computed(() => revenue.value.monthly_conversion || [])
const orderTrend = computed(() => {
  const source = new Map((charts.value.order_trend || []).map(item => [String(item.date || '').slice(0, 10), item]))
  const today = new Date()
  return Array.from({ length: 31 }, (_, index) => {
    const date = new Date(today)
    date.setHours(0, 0, 0, 0)
    date.setDate(today.getDate() - 30 + index)
    const key = dateKey(date)
    const item = source.get(key) || {}
    return { date: key, count: toNumber(item.count), amount: toNumber(item.amount) }
  })
})
const recentOrders = computed(() => orderTrend.value.slice(-7).reduce((sum, item) => sum + toNumber(item.count), 0))
const previousOrders = computed(() => orderTrend.value.slice(-14, -7).reduce((sum, item) => sum + toNumber(item.count), 0))
const recentAmount = computed(() => orderTrend.value.slice(-30).reduce((sum, item) => sum + toNumber(item.amount), 0))
const orderGrowth = computed(() => previousOrders.value ? ((recentOrders.value - previousOrders.value) / previousOrders.value) * 100 : 0)
const growthLabel = computed(() => `${orderGrowth.value >= 0 ? '+' : ''}${orderGrowth.value.toFixed(1)}%`)
const growthCopy = computed(() => previousOrders.value
  ? `近 7 天去重订单较此前 7 天${orderGrowth.value >= 0 ? '增长' : '下降'} ${Math.abs(orderGrowth.value).toFixed(1)}%，可结合趋势图进一步定位变化日期。`
  : '订单趋势数据正在积累，完成更多同步任务后可查看周期变化。')
const averageOrderValue = computed(() => toNumber(overview.value.today_orders)
  ? toNumber(overview.value.today_amount ?? overview.value.successful_amount) / toNumber(overview.value.today_orders ?? overview.value.successful_orders)
  : 0)

const briefMetrics = computed(() => [
  { label: '近 7 天去重订单', value: formatNumber(recentOrders.value), note: growthLabel.value },
  { label: '近 30 天交易额', value: formatMoney(recentAmount.value), note: '趋势数据汇总' },
  { label: '今日客单价', value: formatMoney(averageOrderValue.value), note: '今日成功金额 / 订单' },
])

const stats = computed(() => [
  { label: '纳管站点', value: formatNumber(overview.value.total_sites), icon: DataBoard, tone: 'blue', trend: `${(charts.value.sites_by_admin || []).length} 位`, trendTone: 'neutral', note: '站点管理员' },
  { label: '去重订单', value: formatNumber(overview.value.deduplicated_orders ?? overview.value.total_orders), icon: ShoppingCart, tone: 'violet', trend: growthLabel.value, trendTone: orderGrowth.value >= 0 ? 'up' : 'down', note: '优先按订单 ID 去重' },
  { label: '成功订单', value: formatNumber(overview.value.successful_orders), icon: Goods, tone: 'amber', trend: `${formatNumber(overview.value.today_orders)} 笔`, trendTone: 'neutral', note: '今日成功' },
  { label: '成功金额', value: formatMoney(overview.value.successful_amount), icon: Money, tone: 'green', trend: formatMoney(overview.value.today_amount), trendTone: 'up', note: '今日成功金额' },
])

const quickActions = [
  { label: '商品采集', description: '管理站点与选择器模板', path: '/crawler/site-config', icon: Goods, tone: 'violet' },
  { label: '站点同步', description: '同步最新站点信息', path: '/crawler/site', icon: DataBoard, tone: 'blue' },
  { label: '订单同步', description: '拉取最新订单数据', path: '/crawler/order', icon: ShoppingCart, tone: 'green' },
]

const adminRanking = computed(() => {
  const source = [...(charts.value.orders_by_admin || [])].sort((a, b) => toNumber(b.count) - toNumber(a.count)).slice(0, 5)
  const max = Math.max(...source.map(item => toNumber(item.count)), 1)
  return source.map(item => ({ name: item.admin_name || '未分配', count: toNumber(item.count), percent: (toNumber(item.count) / max) * 100 }))
})
const latestIndex = computed(() => (charts.value.index_trend || []).at(-1)?.total_index || 0)
const indexGrowth = computed(() => {
  const source = charts.value.index_trend || []
  const current = toNumber(source.at(-1)?.total_index)
  const previous = toNumber(source.at(-8)?.total_index || source[0]?.total_index)
  return previous ? ((current - previous) / previous) * 100 : 0
})
const indexGrowthLabel = computed(() => `近 7 天 ${indexGrowth.value >= 0 ? '+' : ''}${indexGrowth.value.toFixed(1)}%`)
const coverageItems = computed(() => [
  { label: '管理员', value: formatNumber((charts.value.sites_by_admin || []).length) },
  { label: '站点分类', value: formatNumber((charts.value.sites_by_category || []).length) },
  { label: '结算币种', value: formatNumber((charts.value.orders_by_currency || []).length) },
  { label: '当前收录', value: formatNumber(latestIndex.value) },
])

const orderTrendOption = computed(() => ({
  animationDuration: 650,
  grid: { left: 5, right: 8, top: 38, bottom: 4, containLabel: true },
  legend: { top: 3, left: 0, itemWidth: 14, itemHeight: 4, textStyle: { color: '#7f8ba0', fontSize: 10 } },
  tooltip: { trigger: 'axis', backgroundColor: '#17243b', borderWidth: 0, textStyle: { color: '#fff' } },
  xAxis: { type: 'category', boundaryGap: false, data: orderTrend.value.map(item => item.date?.slice(5)), axisLine: { lineStyle: { color: '#edf1f7' } }, axisLabel: { color: '#9aa7ba', fontSize: 10, interval: 4 } },
  yAxis: [
    { type: 'value', splitLine: { lineStyle: { color: '#f0f3f8' } }, axisLabel: { color: '#9aa7ba', fontSize: 10 } },
    { type: 'value', splitLine: { show: false }, axisLabel: { color: '#9aa7ba', fontSize: 10 } },
  ],
  series: [
    { name: '去重订单', type: 'line', smooth: true, symbol: 'none', data: orderTrend.value.map(item => item.count), lineStyle: { width: 3, color: '#536ff1' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#536ff12f' }, { offset: 1, color: '#536ff100' }] } } },
    { name: '成功金额', type: 'line', yAxisIndex: 1, smooth: true, symbol: 'none', data: orderTrend.value.map(item => item.amount), lineStyle: { width: 2, color: '#45bc8d' } },
  ],
}))

const indexTrendOption = computed(() => {
  const source = charts.value.index_trend || []
  return {
    animationDuration: 650,
    grid: { left: 4, right: 8, top: 16, bottom: 4, containLabel: true },
    tooltip: { trigger: 'axis', backgroundColor: '#17243b', borderWidth: 0, textStyle: { color: '#fff' } },
    xAxis: { type: 'category', boundaryGap: false, data: source.map(item => item.date?.slice(5)), axisLine: { lineStyle: { color: '#edf1f7' } }, axisLabel: { color: '#9aa7ba', fontSize: 10, interval: 4 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f3f8' } }, axisLabel: { color: '#9aa7ba', fontSize: 10 } },
    series: [{ name: '收录数', type: 'line', smooth: true, symbol: 'none', data: source.map(item => item.total_index), lineStyle: { width: 3, color: '#45bc8d' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#45bc8d30' }, { offset: 1, color: '#45bc8d00' }] } } }],
  }
})

async function loadDashboard() {
  const params = { userGroup: userGroup.value || undefined }
  const revenueParams = { ...params, startDate: revenueDateRange.value?.[0], endDate: revenueDateRange.value?.[1], siteCreatedMonth: siteCreatedMonth.value }
  const [overviewResponse, chartResponse] = await Promise.all([getOverview(params), getCharts(params)])
  overview.value = overviewResponse.data || {}
  charts.value = chartResponse.data || {}
  try {
    const revenueResponse = await getRevenueSummary(revenueParams)
    revenue.value = revenueResponse.data || {}
  } catch {
    revenue.value = {}
  }
}

onMounted(loadDashboard)
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
.revenue-panel { margin-top: 15px; }.revenue-heading { gap: 18px; }.revenue-rules { display: flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 7px; }.revenue-rules span { padding: 5px 9px; border-radius: 999px; color: #536ff1; background: #eef1ff; font-size: 9px; font-weight: 700; }.revenue-date-control { display: flex; align-items: center; gap: 5px; }.revenue-date-control > span { padding: 0; color: #8490a4; background: transparent; font-size: 9px; font-weight: 700; white-space: nowrap; }.revenue-tabs { margin-top: 12px; }.commission-value { color: #36a77e; font-size: 12px; }
.metric-card, .panel { border: 1px solid var(--cf-line); border-radius: 14px; background: #fff; box-shadow: var(--cf-shadow-sm); }
.metric-card { padding: 18px 20px 16px; }.metric-top, .metric-foot, .panel-heading { display: flex; align-items: center; justify-content: space-between; }.metric-label { color: var(--cf-muted); font-size: 11px; }.metric-icon { display: grid; width: 34px; height: 34px; place-items: center; border-radius: 10px; font-size: 16px; }.tone-blue { color: #536ff1; background: #eef1ff; }.tone-violet { color: #8a64e8; background: #f3edff; }.tone-amber { color: #d99a37; background: #fff6e3; }.tone-green { color: #36ad82; background: #eaf9f3; }
.metric-value { margin: 14px 0 11px; color: var(--cf-ink); font-size: 25px; font-weight: 750; letter-spacing: -.04em; }.metric-foot { color: var(--cf-subtle); font-size: 10px; }.metric-trend { font-weight: 700; }.metric-trend.up { color: var(--cf-green); }.metric-trend.down { color: #df6577; }.metric-trend.neutral { color: var(--cf-blue); }
.content-grid { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(280px, .7fr); gap: 15px; margin-top: 15px; }.bottom-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(340px, .9fr); gap: 15px; margin-top: 15px; }.panel { min-width: 0; padding: 21px; }.panel-heading { align-items: flex-start; }.panel-heading h2 { color: #263550; font-size: 14px; letter-spacing: -.02em; }.panel-heading p { margin-top: 5px; color: #9aa7b8; font-size: 10px; }.panel-chip { padding: 5px 9px; border-radius: 999px; font-size: 9px; font-weight: 700; }.panel-chip.blue { color: var(--cf-blue); background: #eef1ff; }.panel-chip.green { color: #36a77e; background: #eaf9f3; }.panel-chip.red { color: #d85b70; background: #fff0f2; }
.chart { width: 100%; height: 225px; margin-top: 12px; }.chart-large { height: 270px; }
.ranking-list { display: grid; gap: 16px; margin-top: 23px; }.ranking-row { display: flex; align-items: center; gap: 11px; }.ranking-index { color: #b2bbc9; font-size: 9px; font-weight: 750; }.ranking-main { min-width: 0; flex: 1; }.ranking-main > div:first-child { display: flex; justify-content: space-between; margin-bottom: 7px; }.ranking-main strong { overflow: hidden; color: #4a5870; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }.ranking-main span { color: #8d99ab; font-size: 10px; }.ranking-track { overflow: hidden; height: 5px; border-radius: 999px; background: #f0f2f7; }.ranking-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #536ff1, #8a75f1); }.panel-link { display: flex; align-items: center; gap: 5px; margin-top: 20px; padding: 0; border: 0; color: var(--cf-blue); background: transparent; font-size: 10px; cursor: pointer; }
.quick-actions { display: grid; gap: 8px; margin-top: 17px; }.quick-action { display: flex; align-items: center; gap: 11px; min-width: 0; padding: 11px 12px; border: 1px solid var(--cf-line-soft); border-radius: 11px; text-align: left; background: #fbfcfe; cursor: pointer; transition: transform .18s, border .18s, box-shadow .18s; }.quick-action:hover { border-color: #cbd5ff; box-shadow: 0 7px 15px #536ff11a; transform: translateY(-1px); }.quick-icon { display: grid; width: 31px; height: 31px; flex: 0 0 auto; place-items: center; border-radius: 8px; font-size: 14px; }.quick-icon.blue { color: #536ff1; background: #eef1ff; }.quick-icon.violet { color: #8a64e8; background: #f3edff; }.quick-icon.green { color: #36ad82; background: #eaf9f3; }.quick-action strong, .quick-action small { display: block; }.quick-action strong { color: #32415b; font-size: 11px; }.quick-action small { margin-top: 3px; color: #a0aabd; font-size: 9px; }.quick-arrow { margin-left: auto; color: #b4becd; font-size: 11px; }
.coverage-strip { display: grid; grid-template-columns: repeat(4, 1fr); gap: 1px; margin-top: 13px; overflow: hidden; border-radius: 10px; background: var(--cf-line-soft); }.coverage-strip > div { display: grid; gap: 3px; padding: 10px 6px; text-align: center; background: #f8f9fb; }.coverage-strip strong { color: #536179; font-size: 12px; }.coverage-strip span { color: #9ba6b7; font-size: 8px; }
@media (max-width: 1120px) { .daily-brief { grid-template-columns: 1fr; }.content-grid, .bottom-grid { grid-template-columns: 1fr; }.revenue-heading { align-items: stretch; flex-direction: column; }.revenue-rules { justify-content: flex-start; } }
@media (max-width: 760px) { .heading-controls { flex-wrap: wrap; }.revenue-date, .revenue-month { width: 100%; }.revenue-date-control { width: 100%; }.stats-grid { grid-template-columns: repeat(2, 1fr); }.daily-brief { padding: 21px; }.brief-metrics { gap: 8px; }.brief-metric { padding: 4px 8px; }.brief-copy h2 { font-size: 16px; } }
@media (max-width: 560px) { .page-heading { align-items: flex-start; flex-direction: column; gap: 14px; }.heading-date { display: none; }.stats-grid { gap: 9px; }.metric-card { padding: 15px; }.metric-value { font-size: 20px; }.brief-metrics { grid-template-columns: 1fr; }.brief-metric { display: grid; grid-template-columns: 1fr auto; align-items: center; padding: 9px 0; border-top: 1px solid #ffffff12; border-left: 0; }.brief-metric strong { margin: 0; font-size: 14px; }.brief-metric small { display: none; }.panel { padding: 17px; } }
</style>
