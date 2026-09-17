<template>
  <div class="sync-page">
    <section class="page-heading">
      <div>
        <p class="eyebrow">DATA SYNC CENTER</p>
        <h1>站点、收录与订单同步</h1>
        <span>三类数据在同一页更新，每个任务独立执行、独立显示进度。</span>
      </div>
      <el-button v-if="canViewConfig" :type="canSaveConfig && configDirty ? 'warning' : 'default'" @click="configOpen = !configOpen">
        {{ canSaveConfig && configDirty ? '配置待保存' : '同步配置' }}
      </el-button>
    </section>

    <section class="sync-grid">
      <el-card class="sync-card" shadow="never">
        <div class="sync-card-heading">
          <span class="sync-icon blue"><el-icon><Connection /></el-icon></span>
          <div><small>第一步</small><h2>同步站点资料</h2><p>更新域名、建站者、服务器、主题和分类等基础信息。</p></div>
        </div>
        <div class="sync-status"><span>任务状态</span><el-tag :type="statusTone(siteTask)">{{ statusLabel(siteTask, '尚未运行') }}</el-tag></div>
        <el-button v-if="canTriggerSite" type="primary" :loading="siteTriggering" :disabled="siteTaskActive" class="sync-action" @click="triggerSites">{{ siteTaskActive ? '站点同步中' : '立即同步站点' }}</el-button>
        <el-alert v-else title="当前账号没有站点同步权限" type="info" :closable="false" />
        <TaskProgress :task="siteTask" />
      </el-card>

      <el-card class="sync-card" shadow="never">
        <div class="sync-card-heading">
          <span class="sync-icon green"><el-icon><TrendCharts /></el-icon></span>
          <div><small>第二步</small><h2>更新收录数据</h2><p>使用 statistics/theme/list 的 ymd 日期更新收录数、商品数和 Sitemap 状态。</p></div>
        </div>
        <div class="sync-status"><span>任务状态</span><el-tag :type="statusTone(indexTask)">{{ statusLabel(indexTask, '尚未运行') }}</el-tag></div>
        <el-button v-if="canTriggerIndex" type="success" :loading="indexTriggering" :disabled="indexTaskActive" class="sync-action" @click="triggerIndexes">{{ indexTaskActive ? '收录更新中' : '立即更新收录' }}</el-button>
        <el-alert v-else title="当前账号没有收录更新权限" type="info" :closable="false" />
        <TaskProgress :task="indexTask" />
      </el-card>

      <el-card v-if="canUseOrder" class="sync-card order-card" shadow="never">
        <div class="sync-card-heading">
          <span class="sync-icon amber"><el-icon><ShoppingCart /></el-icon></span>
          <div><small>第三步</small><h2>同步订单数据</h2><p>按站点分组增量获取支付订单，不同分组可以独立执行。</p></div>
        </div>
        <div class="sync-status"><span>任务状态</span><el-tag :type="activeGroupCount ? 'warning' : statusTone(orderTask)">{{ orderStatus }}</el-tag></div>
        <div v-if="canTriggerOrder && siteGroups.length" class="group-actions">
          <el-button v-for="item in siteGroups" :key="item.user_group" :loading="orderTriggering === item.user_group" :disabled="isGroupActive(item.user_group)" @click="triggerOrders(item.user_group)">
            {{ isGroupActive(item.user_group) ? `${item.user_group} 组同步中` : `同步 ${item.user_group} 组` }}
          </el-button>
        </div>
        <el-empty v-else-if="canTriggerOrder" :image-size="48" description="暂无站点分组" />
        <el-alert v-else title="当前账号没有订单同步权限" type="info" :closable="false" />
        <TaskProgress :task="orderTask" />
      </el-card>
    </section>

    <el-collapse-transition>
      <el-card v-show="configOpen && canViewConfig" shadow="never" class="config-card">
        <template #header>
          <div class="config-heading">
            <div><strong>同步配置</strong><span>站点、收录与订单任务所需的连接和增量规则</span></div>
            <el-tag v-if="canSaveConfig && configDirty" type="warning">有未保存修改</el-tag>
          </div>
        </template>
        <el-form :model="form" label-position="top">
          <template v-if="canUseSite">
            <div class="section-title"><strong>站点与收录</strong><span>共享 Admin API 连接信息</span></div>
            <div class="config-grid">
              <el-form-item label="Admin API 地址"><el-input v-model="form.adminApi.baseUrl" :disabled="!canEditSiteConfig" placeholder="https://admin.example.com" /></el-form-item>
              <el-form-item label="账号"><el-input v-model="form.adminApi.username" :disabled="!canEditSiteConfig" /></el-form-item>
              <el-form-item label="密码"><el-input v-model="form.adminApi.password" :disabled="!canEditSiteConfig" type="password" show-password /></el-form-item>
              <el-form-item label="分页大小"><el-input-number v-model="form.siteStrategy.pageSize" :disabled="!canEditSiteConfig" :min="20" :max="500" :step="20" /></el-form-item>
            </div>
            <div class="switch-grid">
              <el-form-item label="SSL 校验"><el-switch v-model="form.adminApi.verifySsl" :disabled="!canEditSiteConfig" /></el-form-item>
              <el-form-item label="跳过站点检测"><el-switch v-model="form.siteStrategy.skipSiteCheck" :disabled="!canEditSiteConfig" /></el-form-item>
              <el-form-item label="获取后台登录地址"><el-switch v-model="form.siteStrategy.fetchAdminLoginUrl" :disabled="!canEditSiteConfig" /></el-form-item>
              <el-form-item label="仅同步已建站"><el-switch v-model="form.siteStrategy.filterBuiltOnly" :disabled="!canEditSiteConfig" /></el-form-item>
            </div>
          </template>

          <template v-if="canUseOrder">
            <div class="section-title order-title"><strong>订单同步</strong><span>按分组维护支付平台连接</span></div>
            <el-empty v-if="!siteGroups.length" :image-size="52" description="暂无站点分组" />
            <div v-else class="payment-grid">
              <section v-for="item in siteGroups" :key="item.user_group" class="payment-panel">
                <strong>{{ item.user_group }} 组 Payment API</strong>
                <template v-if="form.paymentApis[item.user_group]">
                  <el-form-item label="Base URL"><el-input v-model="form.paymentApis[item.user_group].baseUrl" :disabled="!canEditOrderConfig" /></el-form-item>
                  <el-form-item label="账号"><el-input v-model="form.paymentApis[item.user_group].account" :disabled="!canEditOrderConfig" /></el-form-item>
                  <el-form-item label="密码"><el-input v-model="form.paymentApis[item.user_group].password" :disabled="!canEditOrderConfig" type="password" show-password /></el-form-item>
                  <el-form-item label="SSL 校验"><el-switch v-model="form.paymentApis[item.user_group].verifySsl" :disabled="!canEditOrderConfig" /></el-form-item>
                </template>
              </section>
            </div>
            <div class="order-strategy">
              <el-form-item label="初始订单 ID"><el-input v-model="form.orderStrategy.initialOrderId" :disabled="!canEditOrderConfig" /></el-form-item>
              <el-form-item label="分页大小"><el-input-number v-model="form.orderStrategy.pageSize" :disabled="!canEditOrderConfig" :min="20" :max="500" :step="20" /></el-form-item>
              <el-form-item label="排除卡号"><el-input v-model="excludedCardsText" :disabled="!canEditOrderConfig" type="textarea" :rows="3" placeholder="每行一个卡号" /></el-form-item>
            </div>
          </template>

          <div v-if="canSaveConfig" class="config-actions">
            <el-button @click="loadConfig">撤销修改</el-button>
            <el-button type="primary" :loading="saving" :disabled="!configDirty" @click="saveConfig">保存配置</el-button>
          </div>
          <el-alert v-else title="当前为只读配置，敏感信息已脱敏" type="info" :closable="false" />
        </el-form>
      </el-card>
    </el-collapse-transition>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, ShoppingCart, TrendCharts } from '@element-plus/icons-vue'
import TaskProgress from '@/components/TaskProgress.vue'
import { useTaskProgress } from '@/composables/useTaskProgress'
import { useSiteGroups } from '@/composables/useSiteGroups'
import { useUserStore } from '@/store/user'
import { getCrawlerConfig, getTaskOverview, triggerCollectCrawler, triggerOrderCrawler, triggerSiteCrawler, updateCrawlerConfig } from '@/api/crawler'

const user = useUserStore()
const canTriggerSite = computed(() => user.hasPermission('crawler:site:start'))
const canTriggerIndex = computed(() => user.hasPermission('crawler:collect:start'))
const canTriggerOrder = computed(() => user.hasPermission('crawler:order:start'))
const canEditOrderConfig = computed(() => user.hasPermission('crawler:order:config'))
const canEditSiteConfig = computed(() => canTriggerSite.value || user.hasPermission('crawler:site:config:update'))
const canUseSite = computed(() => canTriggerSite.value || canTriggerIndex.value)
const canUseOrder = computed(() => canTriggerOrder.value || canEditOrderConfig.value || user.hasPermission('crawler:order:view'))
const canViewConfig = computed(() => canUseSite.value || canUseOrder.value)
const canSaveConfig = computed(() => canEditSiteConfig.value || canEditOrderConfig.value)
const saving = ref(false), siteTriggering = ref(false), indexTriggering = ref(false), orderTriggering = ref(''), configOpen = ref(false), savedSnapshot = ref('')
const siteProgress = useTaskProgress()
const indexProgress = useTaskProgress()
const orderProgress = useTaskProgress()
const siteTask = siteProgress.task, indexTask = indexProgress.task, orderTask = orderProgress.task
const activeStates = ['PENDING', 'RUNNING', 'PAUSED']
const siteTaskActive = computed(() => activeStates.includes(siteTask.value?.state))
const indexTaskActive = computed(() => activeStates.includes(indexTask.value?.state))
const activeGroups = ref({})
const excludedCardsText = ref('')
const { siteGroups, loadSiteGroups } = useSiteGroups()
let activityTimer = null

const form = reactive({
  adminApi: { baseUrl: '', username: '', password: '', verifySsl: true },
  siteStrategy: { skipSiteCheck: true, fetchAdminLoginUrl: false, filterBuiltOnly: false, pageSize: 100 },
  paymentApis: {},
  orderStrategy: { initialOrderId: '0', pageSize: 100, filterCardNumberExclude: [] },
})
const configSnapshot = () => JSON.stringify({ form, excludedCardsText: excludedCardsText.value })
const configDirty = computed(() => !!savedSnapshot.value && savedSnapshot.value !== configSnapshot())
const activeGroupCount = computed(() => Object.values(activeGroups.value).filter(value => Number(value) > 0).length)
const statusLabel = (task, fallback) => ({ PENDING: '等待执行', RUNNING: '执行中', PAUSED: '已暂停', SUCCESS: '已完成', FAILED: '执行失败', CANCELLED: '已取消' }[task?.state] || fallback)
const statusTone = task => ({ SUCCESS: 'success', FAILED: 'danger', CANCELLED: 'info', RUNNING: 'primary', PENDING: 'warning', PAUSED: 'warning' }[task?.state] || 'info')
const orderStatus = computed(() => activeGroupCount.value ? `${activeGroupCount.value} 个分组执行中` : statusLabel(orderTask.value, '尚未运行'))

async function loadConfig() {
  const [configResult, groupsResult, overviewResult] = await Promise.allSettled([getCrawlerConfig(), loadSiteGroups(), getTaskOverview()])
  if (configResult.status !== 'fulfilled') throw configResult.reason
  const config = configResult.value.data || {}
  Object.assign(form.adminApi, config.adminApi || {})
  Object.assign(form.siteStrategy, config.siteStrategy || {})
  form.siteStrategy.pageSize = Number(form.siteStrategy.pageSize || 100)
  const groups = groupsResult.status === 'fulfilled' ? groupsResult.value : []
  for (const item of groups) {
    const group = item.user_group
    form.paymentApis[group] = { baseUrl: '', account: '', password: '', verifySsl: true, ...(config[`paymentApi${group}`] || {}) }
  }
  Object.assign(form.orderStrategy, config.orderStrategy || {})
  form.orderStrategy.pageSize = Number(form.orderStrategy.pageSize || 100)
  excludedCardsText.value = (form.orderStrategy.filterCardNumberExclude || []).join('\n')
  activeGroups.value = overviewResult.status === 'fulfilled' ? overviewResult.value.data?.activeByScope || {} : {}
  savedSnapshot.value = configSnapshot()
  scheduleActivityRefresh()
}

async function saveConfig() {
  if (canEditSiteConfig.value && (!form.adminApi.baseUrl || !/^https?:\/\//i.test(form.adminApi.baseUrl.trim()))) return ElMessage.error('Admin API 地址必须以 http:// 或 https:// 开头')
  if (canEditSiteConfig.value && (Number(form.siteStrategy.pageSize) < 20 || Number(form.siteStrategy.pageSize) > 500)) return ElMessage.error('站点分页大小必须在 20 到 500 之间')
  if (canEditOrderConfig.value && (Number(form.orderStrategy.pageSize) < 20 || Number(form.orderStrategy.pageSize) > 500)) return ElMessage.error('订单分页大小必须在 20 到 500 之间')
  saving.value = true
  try {
    const payload = {}
    if (canEditSiteConfig.value) Object.assign(payload, { adminApi: form.adminApi, siteStrategy: form.siteStrategy })
    if (canEditOrderConfig.value) {
      form.orderStrategy.filterCardNumberExclude = excludedCardsText.value.split(/\n|,/).map(item => item.trim()).filter(Boolean)
      payload.orderStrategy = form.orderStrategy
      for (const item of siteGroups.value) payload[`paymentApi${item.user_group}`] = form.paymentApis[item.user_group]
    }
    await updateCrawlerConfig(payload)
    ElMessage.success('同步配置已保存')
    await loadConfig()
  } finally { saving.value = false }
}

async function triggerSites() {
  if (siteTaskActive.value) return
  siteTriggering.value = true
  try { const response = await triggerSiteCrawler(); siteProgress.track(response.data.task_id); ElMessage.success('站点同步任务已下发') }
  finally { siteTriggering.value = false }
}
async function triggerIndexes() {
  if (indexTaskActive.value) return
  indexTriggering.value = true
  try { const response = await triggerCollectCrawler(); indexProgress.track(response.data.task_id); ElMessage.success('收录更新任务已下发') }
  finally { indexTriggering.value = false }
}
function isGroupActive(group) { return Number(activeGroups.value[`group-${group}`] || 0) > 0 }
async function triggerOrders(group) {
  if (isGroupActive(group)) return
  orderTriggering.value = group
  try {
    const response = await triggerOrderCrawler(group)
    orderProgress.track(response.data.task_id)
    activeGroups.value = { ...activeGroups.value, [`group-${group}`]: 1 }
    scheduleActivityRefresh()
    ElMessage.success(`${group} 组订单同步任务已下发`)
  } finally { orderTriggering.value = '' }
}
function scheduleActivityRefresh() {
  if (activityTimer) window.clearTimeout(activityTimer)
  activityTimer = null
  if (activeGroupCount.value) activityTimer = window.setTimeout(refreshActivity, 3000)
}
async function refreshActivity() {
  try { const response = await getTaskOverview(); activeGroups.value = response.data?.activeByScope || {} }
  finally { scheduleActivityRefresh() }
}

onMounted(loadConfig)
onUnmounted(() => { if (activityTimer) window.clearTimeout(activityTimer) })
</script>

<style scoped>
.sync-page { display: grid; max-width: 1180px; gap: 16px; margin: 0 auto; }.page-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; margin-bottom: 4px; }.page-heading h1 { margin: 0; color: var(--cf-ink); font-size: 27px; letter-spacing: -.04em; }.page-heading span { display: block; margin-top: 7px; color: var(--cf-muted); font-size: 12px; }.page-heading .eyebrow { margin: 0 0 6px; color: var(--cf-blue); font-size: 9px; font-weight: 800; letter-spacing: .15em; }.sync-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; align-items: start; }.sync-card { min-width: 0; border-radius: 14px; }.sync-card-heading { display: flex; align-items: flex-start; gap: 14px; }.sync-card-heading h2 { margin: 3px 0 7px; color: var(--cf-ink); font-size: 17px; }.sync-card-heading p { margin: 0; color: var(--cf-muted); font-size: 11px; line-height: 1.7; }.sync-card-heading small { color: #9aa7b8; font-size: 9px; font-weight: 750; letter-spacing: .08em; }.sync-icon { display: grid; width: 42px; height: 42px; flex: 0 0 auto; place-items: center; border-radius: 12px; font-size: 19px; }.sync-icon.blue { color: #536ff1; background: #eef1ff; }.sync-icon.green { color: #2aa77a; background: #eaf9f3; }.sync-icon.amber { color: #cb8522; background: #fff5e5; }.sync-status { display: flex; align-items: center; justify-content: space-between; margin: 22px 0 14px; padding-top: 15px; border-top: 1px solid var(--cf-line); }.sync-status > span { color: var(--cf-muted); font-size: 10px; }.sync-action { width: 100%; }.group-actions { display: flex; flex-wrap: wrap; gap: 8px; }.group-actions .el-button { flex: 1 1 110px; margin: 0; }.config-card { border-radius: 14px; }.config-heading { display: flex; align-items: center; justify-content: space-between; }.config-heading strong, .config-heading span { display: block; }.config-heading span, .section-title span { margin-top: 4px; color: var(--cf-muted); font-size: 10px; }.section-title { display: flex; align-items: baseline; gap: 10px; margin-bottom: 15px; }.section-title strong { color: var(--cf-ink); font-size: 15px; }.order-title { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--cf-line); }.config-grid { display: grid; grid-template-columns: 2fr 1fr 1fr 1fr; gap: 0 14px; }.config-grid :deep(.el-input-number), .order-strategy :deep(.el-input-number) { width: 100%; }.switch-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; padding: 14px 16px 0; border-radius: 10px; background: #f8f9fc; }.payment-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }.payment-panel { padding: 16px; border: 1px solid var(--cf-line); border-radius: 12px; background: #fbfcff; }.payment-panel > strong { display: block; margin-bottom: 14px; color: var(--cf-ink); font-size: 12px; }.order-strategy { display: grid; grid-template-columns: 1fr 1fr 2fr; gap: 14px; margin-top: 16px; }.config-actions { display: flex; justify-content: flex-end; margin-top: 12px; }
@media (max-width: 1050px) { .sync-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.order-card { grid-column: 1 / -1; }.config-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .sync-grid, .payment-grid, .order-strategy { grid-template-columns: 1fr; }.order-card { grid-column: auto; } }
@media (max-width: 560px) { .page-heading { align-items: flex-start; flex-direction: column; }.config-grid, .switch-grid { grid-template-columns: 1fr; } }
</style>
