<template>
  <div class="health-page">
    <div class="summary-grid">
      <div class="summary-card"><span>{{ hasFilters ? '筛选结果' : '已检查站点' }}</span><strong>{{ summary.total || 0 }}</strong></div>
      <div class="summary-card healthy"><span>正常</span><strong>{{ summary.healthy || 0 }}</strong></div>
      <div class="summary-card unhealthy"><span>异常</span><strong>{{ summary.unhealthy || 0 }}</strong></div>
      <div class="summary-card"><span>最近检查</span><strong class="time">{{ formatBeijingDateTime(summary.lastCheckedAt, '暂无记录') }}</strong></div>
    </div>

    <el-card>
      <template #header>
        <div class="header">
          <div><span>站点健康状态</span><small>检测访问状态、错误页面、空白页面及 WordPress 关键图片</small></div>
          <div>
            <el-button v-if="canConfigure" @click="openSettings">检查策略</el-button>
            <el-button :loading="loading" @click="refreshAll">刷新</el-button>
            <el-button v-if="canTrigger" type="primary" :loading="triggering" @click="runCheck">立即检查</el-button>
          </div>
        </div>
      </template>

      <div class="filters">
        <el-select v-model="filters.status" clearable placeholder="全部状态" @change="search">
          <el-option label="正常" value="HEALTHY" /><el-option label="异常" value="UNHEALTHY" />
        </el-select>
        <el-select v-model="filters.reason" clearable filterable placeholder="全部检查结果" @change="search">
          <el-option v-for="reason in filterOptions.reasons" :key="reason" :label="reason" :value="reason" />
        </el-select>
        <el-select v-model="filters.userGroup" clearable placeholder="全部分组" @change="search">
          <el-option v-for="group in filterOptions.userGroups" :key="group" :label="group" :value="group" />
        </el-select>
        <el-select v-model="filters.adminName" clearable filterable placeholder="全部员工" @change="search">
          <el-option v-for="name in filterOptions.adminNames" :key="name" :label="name" :value="name" />
        </el-select>
        <el-input v-model="filters.keyword" clearable placeholder="域名 / 服务器 / IP / 原因" @keyup.enter="search" @clear="search" />
        <el-button type="primary" @click="search">查询</el-button>
        <el-button :disabled="!hasFilters" @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe :empty-text="hasFilters ? '没有符合筛选条件的站点' : '尚无检查结果，请先执行检查'">
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="statusTone(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="siteDomain" label="域名" min-width="210">
          <template #default="{ row }"><a :href="row.finalUrl || `https://${row.siteDomain}`" target="_blank" rel="noopener noreferrer">{{ row.siteDomain }}</a></template>
        </el-table-column>
        <el-table-column prop="reason" label="检查结果" min-width="230" show-overflow-tooltip />
        <el-table-column label="响应" width="110">
          <template #default="{ row }"><div>{{ row.httpStatus ?? 'Error' }}</div><small>{{ row.latencyMs ?? 0 }} ms</small></template>
        </el-table-column>
        <el-table-column label="服务器" min-width="180">
          <template #default="{ row }"><div>{{ row.serverName || '未知服务器' }}</div><small>{{ row.serverIp || '未知 IP' }}</small></template>
        </el-table-column>
        <el-table-column label="归属" min-width="140">
          <template #default="{ row }"><div>{{ row.adminName || '—' }}</div><small>{{ row.userGroup || '未分组' }}</small></template>
        </el-table-column>
        <el-table-column label="连续失败" width="95" align="center">
          <template #default="{ row }"><strong :class="{ 'failure-count': row.consecutiveFailures > 0 }">{{ row.consecutiveFailures ?? 0 }}</strong></template>
        </el-table-column>
        <el-table-column label="检查时间" min-width="170">
          <template #default="{ row }"><div>{{ formatBeijingDateTime(row.checkedAt) }}</div><small v-if="row.failureSince">异常始于 {{ formatBeijingDateTime(row.failureSince) }}</small></template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="resize" />
    </el-card>

    <el-dialog v-model="settingsVisible" title="站点健康检查策略" width="620px">
      <el-form label-width="150px">
        <el-form-item label="站点分组"><el-input v-model="settings.userGroups" placeholder="留空检查全部；多个分组用逗号分隔" /></el-form-item>
        <el-form-item label="排除域名"><el-input v-model="settings.excludeDomains" type="textarea" :rows="3" placeholder="每行一个域名，或用逗号分隔" /></el-form-item>
        <el-form-item label="排除服务器 IP"><el-input v-model="settings.excludeServerIps" type="textarea" :rows="2" placeholder="每行一个 IP，或用逗号分隔" /></el-form-item>
        <el-form-item label="建站最少天数"><el-input-number v-model="settings.minimumAgeDays" :min="0" :max="365" /></el-form-item>
        <el-form-item label="失败重试次数"><el-input-number v-model="settings.maxRetries" :min="1" :max="10" /></el-form-item>
        <el-form-item label="重试间隔（秒）"><el-input-number v-model="settings.retryDelaySeconds" :min="0" :max="60" /></el-form-item>
        <el-form-item label="服务器并发数"><el-input-number v-model="settings.maxParallelServers" :min="1" :max="100" /></el-form-item>
        <el-form-item label="同服务器间隔（秒）"><el-input-number v-model="settings.sameServerDelaySeconds" :min="0" :max="60" /></el-form-item>
        <el-form-item label="连接超时（秒）"><el-input-number v-model="settings.connectTimeoutSeconds" :min="1" :max="60" /></el-form-item>
        <el-form-item label="请求超时（秒）"><el-input-number v-model="settings.requestTimeoutSeconds" :min="2" :max="120" /></el-form-item>
        <el-form-item label="最小页面长度"><el-input-number v-model="settings.minimumBodyLength" :min="0" :max="100000" /></el-form-item>
        <el-form-item label="无异常也发汇总"><el-switch v-model="settings.notifyEveryRun" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="settingsVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveSettings">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSiteHealth, getSiteHealthConfig, getSiteHealthFilters, getSiteHealthSummary, triggerSiteHealth, updateSiteHealthConfig } from '@/api/crawler'
import { useUserStore } from '@/store/user'
import { formatBeijingDateTime } from '@/utils/dateTime'

const userStore = useUserStore()
const canTrigger = computed(() => userStore.hasPermission('crawler:health:trigger') || userStore.hasPermission('crawler:schedule:trigger'))
const canConfigure = computed(() => userStore.hasPermission('crawler:schedule:update'))
const loading = ref(false)
const triggering = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const summary = reactive({ total: 0, healthy: 0, unhealthy: 0, lastCheckedAt: null })
const filters = reactive({ status: '', reason: '', userGroup: '', adminName: '', keyword: '' })
const filterOptions = reactive({ userGroups: [], adminNames: [], reasons: [] })
const hasFilters = computed(() => Object.values(filters).some(value => String(value || '').trim()))
const settingsVisible = ref(false)
const saving = ref(false)
const settings = reactive({ userGroups: '', excludeDomains: '', excludeServerIps: '', minimumAgeDays: 7, maxRetries: 5, retryDelaySeconds: 2, maxParallelServers: 20, sameServerDelaySeconds: 3, connectTimeoutSeconds: 10, requestTimeoutSeconds: 30, minimumBodyLength: 1200, notifyEveryRun: true })
let listRequestId = 0
let summaryRequestId = 0

async function load() {
  const requestId = ++listRequestId
  loading.value = true
  try {
    const res = await getSiteHealth({ page: page.value, size: size.value, ...filters })
    if (requestId !== listRequestId) return
    rows.value = (res.data?.records || []).map(normalizeRow)
    total.value = Number(res.data?.total || 0)
  } finally {
    if (requestId === listRequestId) loading.value = false
  }
}
async function loadSummary() {
  const requestId = ++summaryRequestId
  const overview = await getSiteHealthSummary({ ...filters })
  if (requestId !== summaryRequestId) return
  Object.assign(summary, { total: 0, healthy: 0, unhealthy: 0, lastCheckedAt: null }, overview.data || {})
  if (!Number(summary.total)) summary.lastCheckedAt = null
}
async function refresh() { await Promise.all([load(), loadSummary()]) }
async function refreshAll() { await Promise.all([loadFilterOptions(), refresh()]) }
function search() { page.value = 1; refresh() }
function resize() { page.value = 1; refresh() }
function resetFilters() {
  Object.assign(filters, { status: '', reason: '', userGroup: '', adminName: '', keyword: '' })
  search()
}
function normalizeRow(row = {}) {
  return {
    ...row,
    siteDomain: row.siteDomain ?? row.site_domain,
    httpStatus: row.httpStatus ?? row.http_status,
    finalUrl: row.finalUrl ?? row.final_url,
    serverName: row.serverName ?? row.server_name,
    serverIp: row.serverIp ?? row.server_ip,
    adminName: row.adminName ?? row.admin_name,
    userGroup: row.userGroup ?? row.user_group,
    latencyMs: row.latencyMs ?? row.latency_ms,
    consecutiveFailures: row.consecutiveFailures ?? row.consecutive_failures,
    failureSince: row.failureSince ?? row.failure_since,
    checkedAt: row.checkedAt ?? row.checked_at,
  }
}
function statusLabel(status) { return ({ HEALTHY: '正常', UNHEALTHY: '异常' })[status] || '未知' }
function statusTone(status) { return ({ HEALTHY: 'success', UNHEALTHY: 'danger' })[status] || 'info' }
async function loadFilterOptions() {
  const res = await getSiteHealthFilters()
  Object.assign(filterOptions, res.data || {})
}
async function runCheck() {
  triggering.value = true
  try {
    await triggerSiteHealth()
    ElMessage.success('站点健康检查已启动，可在任务历史查看实时进度')
  } finally { triggering.value = false }
}
function splitList(value) { return String(value || '').split(/[\n,]/).map(item => item.trim()).filter(Boolean) }
async function openSettings() {
  const res = await getSiteHealthConfig()
  const value = res.data || {}
  Object.assign(settings, value, {
    userGroups: (value.userGroups || []).join(', '),
    excludeDomains: (value.excludeDomains || []).join('\n'),
    excludeServerIps: (value.excludeServerIps || []).join('\n'),
  })
  settingsVisible.value = true
}
async function saveSettings() {
  saving.value = true
  try {
    await updateSiteHealthConfig({ ...settings, userGroups: splitList(settings.userGroups), excludeDomains: splitList(settings.excludeDomains), excludeServerIps: splitList(settings.excludeServerIps) })
    settingsVisible.value = false
    ElMessage.success('检查策略已保存')
  } finally { saving.value = false }
}
onMounted(refreshAll)
</script>

<style scoped>
.health-page { max-width: 1360px; }
.summary-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:14px; margin-bottom:16px; }
.summary-card { padding:18px 20px; border:1px solid #e8edf5; border-radius:10px; background:#fff; box-shadow:0 4px 16px rgb(37 52 79 / 4%); }
.summary-card span,.summary-card small { color:#8793a5; font-size:12px; }
.summary-card strong { display:block; margin-top:9px; color:#25344f; font-size:28px; }
.summary-card.healthy strong { color:#16a085; }.summary-card.unhealthy strong { color:#f56c6c; }
.summary-card .time { padding-top:5px; font-size:16px; }
.header { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.header>div:first-child { display:grid; gap:4px; }.header small,td small { color:#8b98ad; }
.filters { display:flex; flex-wrap:wrap; gap:10px; margin-bottom:16px; }.filters .el-select { width:170px; }.filters .el-select:nth-child(2) { width:230px; }.filters .el-input { width:250px; }
a { color:#409eff; text-decoration:none; }.el-pagination { justify-content:flex-end; margin-top:16px; }
.failure-count { color:#f56c6c; }
@media(max-width:800px){.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.filters{flex-wrap:wrap}}
</style>
