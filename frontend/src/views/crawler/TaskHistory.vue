<!--
  TaskHistoryPage - 爬虫任务历史页面
  展示最近所有爬虫任务（站点爬虫、收录统计、订单爬虫）的执行记录，
  包含任务 ID、类型、状态和结果信息。数据从 API 获取并以表格形式展示。
-->
<template>
  <div class="task-history-page">
    <el-card>
      <template #header>
        <div class="history-header">
          <div>
            <span>任务历史</span>
            <small>检索同步记录、追踪实时进度并查看分块日志</small>
          </div>
          <div class="history-actions">
            <span class="auto-refresh-label">执行中自动刷新</span>
            <el-switch v-model="autoRefresh" @change="scheduleAutoRefresh" />
            <el-button type="primary" size="small" :loading="loading" @click="refreshPage">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="status-summary">
        <div><span>正在执行</span><strong class="running-number">{{ overview.active }}</strong></div>
        <div><span>今日成功</span><strong class="success-number">{{ overview.successToday }}</strong></div>
        <div><span>今日失败</span><strong :class="{ 'failed-number': overview.failedToday }">{{ overview.failedToday }}</strong></div>
        <div><span>累计任务</span><strong>{{ overview.total }}</strong></div>
      </div>

      <div class="filter-toolbar">
        <el-radio-group v-model="taskType" size="small" @change="handleTypeChange" class="task-type-tabs">
          <el-radio-button value="all">全部（{{ counts.all || 0 }}）</el-radio-button>
          <el-radio-button value="site_crawl">站点（{{ counts.site_crawl || 0 }}）</el-radio-button>
          <el-radio-button value="site_index">收录（{{ counts.site_index || 0 }}）</el-radio-button>
          <el-radio-button value="order_crawl">订单（{{ counts.order_crawl || 0 }}）</el-radio-button>
          <el-radio-button v-if="userStore.hasPermission('dashboard:product:view')" value="product_crawl">商品（{{ counts.product_crawl || 0 }}）</el-radio-button>
        </el-radio-group>
        <div class="filter-fields">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 128px" @change="handleFilterChange">
            <el-option label="等待中" value="PENDING" />
            <el-option label="执行中" value="RUNNING" />
            <el-option label="已暂停" value="PAUSED" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-input v-model="keyword" clearable placeholder="搜索任务 ID" style="width: 220px" @keyup.enter="handleSearch" @clear="handleSearch" />
          <el-button @click="handleSearch">查询</el-button>
        </div>
      </div>

    <!-- 任务历史表格 -->
    <el-table :data="tableData" v-loading="loading" :max-height="640" stripe empty-text="暂无任务记录">
      <el-table-column label="任务" min-width="210">
        <template #default="{ row }">
          <div class="task-id-cell">{{ row.taskId }}</div>
          <div class="task-meta">{{ formatDate(row.createdAt) }} · {{ triggerLabel(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.type === 'site_crawl'" type="primary">站点爬虫</el-tag>
          <el-tag v-else-if="row.type === 'site_index'" type="success">收录统计</el-tag>
          <el-tag v-else-if="row.type === 'order_crawl'" type="warning">订单爬虫</el-tag>
          <el-tag v-else-if="row.type === 'product_crawl'">商品爬虫</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="160">
        <template #default="{ row }">
          <el-tag :type="taskStatus(row).tone" :effect="row.status === 'FAILED' ? 'dark' : 'light'">{{ taskStatus(row).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="170">
        <template #default="{ row }">
          <el-progress
            :percentage="row.status === 'SUCCESS' ? 100 : (row.progress || 0)"
            :status="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'exception' : ''"
            :stroke-width="10"
          />
            <span class="progress-message">{{ taskProgressMessage(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" min-width="150">
        <template #default="{ row }">
          <div>{{ taskResult(row) }}</div>
          <div class="task-meta">耗时 {{ formatDuration(row.durationMs) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="userStore.hasPermission('crawler:task:control') && (row.status === 'PENDING' || row.status === 'RUNNING')"
            link type="warning" size="small" @click="handlePause(row)"
          >暂停</el-button>
          <el-button
            v-else-if="userStore.hasPermission('crawler:task:control') && row.status === 'PAUSED'"
            link type="success" size="small" @click="handleResume(row)"
          >继续</el-button>
          <el-button
            v-if="['site_crawl', 'site_index', 'order_crawl', 'product_crawl'].includes(row.type)"
            link
            type="primary"
            @click="openLog(row)"
          >查看日志</el-button>
          <el-button v-if="userStore.hasPermission('crawler:task:delete')" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

      <el-pagination
        style="margin-top: 16px; justify-content: flex-end;"
        v-model:current-page="page"
        :page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="fetchData"
        @size-change="handleSizeChange"
      />
    </el-card>

    <el-drawer
      v-model="logVisible"
      :title="`${taskTypeLabel(activeTaskType)}完整日志`"
      size="75%"
      destroy-on-close
      @closed="stopLogPolling"
    >
      <div class="log-toolbar">
        <div>
          <el-tag :type="statusTagType(logStatus)">{{ taskStatus({status:logStatus}).label }}</el-tag>
          <span class="log-task-id">{{ activeTaskId }}</span>
          <span v-if="logStatus === 'RUNNING'" class="live-indicator">实时刷新中</span>
        </div>
        <div>
          <el-button :loading="logLoading" @click="refreshLog">刷新</el-button>
          <el-button :disabled="!crawlLog" @click="copyLog">复制当前窗口</el-button>
          <el-button type="primary" :loading="logDownloading" @click="downloadLog">下载完整日志</el-button>
        </div>
      </div>
      <el-alert
        v-if="logTruncated"
        title="页面仅显示最近 20 万字符，完整日志请使用下载按钮。"
        type="info"
        :closable="false"
        show-icon
        class="log-notice"
      />
      <div ref="logViewer" v-loading="logLoading && !crawlLog" class="log-viewer">
        <pre>{{ crawlLog || '暂无日志输出' }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { taskStatus, taskProgressMessage, taskResult } from '@/data/taskPresentation'
import { ref, nextTick, onMounted, onUnmounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRecentTasks, getTaskSummary, getTaskOverview, getTaskCrawlLog, downloadTaskCrawlLog, pauseTask, resumeTask, deleteTask } from '@/api/crawler'
import { useUserStore } from '@/store/user'

/** 表格 loading 状态 */
const loading = ref(false)
/** 任务列表数据 */
const tableData = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const taskType = ref('all')
const statusFilter = ref('')
const keyword = ref('')
const appliedKeyword = ref('')
const autoRefresh = ref(true)
const counts = reactive({ all: 0, site_crawl: 0, site_index: 0, order_crawl: 0, product_crawl: 0 })
const overview = reactive({ total: 0, active: 0, successToday: 0, failedToday: 0 })
const userStore = useUserStore()
const logVisible = ref(false)
const logLoading = ref(false)
const logDownloading = ref(false)
const activeTaskId = ref('')
const activeTaskType = ref('')
const logStatus = ref('')
const crawlLog = ref('')
const logTruncated = ref(false)
const logViewer = ref(null)
const MAX_VISIBLE_LOG = 200000
const LOG_CHUNK_SIZE = 65536
let logOffset = null
let logRequestRunning = false
let logTimer = null
let pageRefreshTimer = null
let listRequestId = 0
let disposed = false

/**
 * 获取最近的爬虫任务列表
 * API 返回 MyBatis-Plus 分页对象，任务列表位于 records 字段。
 */
async function fetchData() {
  const requestId = ++listRequestId
  loading.value = true
  try {
    const res = await getRecentTasks({
      page: page.value,
      size: size.value,
      type: taskType.value,
      status: statusFilter.value || undefined,
      keyword: appliedKeyword.value || undefined,
    })
    if (requestId !== listRequestId) return
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch {
    if (requestId === listRequestId) tableData.value = []
  } finally {
    if (requestId === listRequestId) loading.value = false
  }
}

async function loadOverview() {
  try {
    const res = await getTaskOverview()
    Object.assign(overview, res.data || {})
  } finally {
    scheduleAutoRefresh()
  }
}

async function refreshPage() {
  await Promise.all([fetchData(), loadSummary(), loadOverview()])
}

function scheduleAutoRefresh() {
  if (pageRefreshTimer) window.clearTimeout(pageRefreshTimer)
  pageRefreshTimer = null
  if (!disposed && autoRefresh.value && Number(overview.active || 0) > 0) {
    pageRefreshTimer = window.setTimeout(async () => {
      await Promise.all([fetchData(), loadOverview()])
    }, 8000)
  }
}

async function loadSummary() {
  try {
    const res = await getTaskSummary()
    Object.assign(counts, res.data || {})
  } catch {
    // The task list should remain usable when the summary endpoint is unavailable.
    Object.assign(counts, { all: 0, site_crawl: 0, site_index: 0, order_crawl: 0, product_crawl: 0 })
  }
}

function handleTypeChange() {
  page.value = 1
  fetchData()
}

function handleFilterChange() {
  page.value = 1
  fetchData()
}

function handleSearch() {
  appliedKeyword.value = keyword.value.trim()
  page.value = 1
  fetchData()
}

async function handlePause(row) {
  try {
    await ElMessageBox.confirm(`确定暂停任务 ${row.taskId}？`, '暂停任务', { type: 'warning' })
    await pauseTask(row.taskId)
    ElMessage.success('任务已暂停')
    await Promise.all([fetchData(), loadOverview()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('暂停任务失败')
  }
}

async function handleResume(row) {
  try {
    await resumeTask(row.taskId)
    ElMessage.success('任务已继续')
    await Promise.all([fetchData(), loadOverview()])
  } catch {
    ElMessage.error('继续任务失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除任务 ${row.taskId}？运行中的任务也会被取消。`, '删除任务', { type: 'warning' })
    await deleteTask(row.taskId)
    ElMessage.success('任务已删除')
    if (tableData.value.length === 1 && page.value > 1) page.value -= 1
    await Promise.all([fetchData(), loadSummary(), loadOverview()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('删除任务失败')
  }
}

function handleSizeChange(value) {
  size.value = value
  page.value = 1
  fetchData()
}

function statusTagType(status) {
  return taskStatus({status}).tone
}

function taskTypeLabel(type) {
  return {
    site_crawl: '站点爬取',
    site_index: '收录统计',
    order_crawl: '订单爬取',
    product_crawl: '商品爬取',
  }[type] || '采集任务'
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '时间未记录'
}

function formatDuration(value) {
  const ms = Number(value || 0)
  if (!ms) return '—'
  if (ms < 1000) return `${ms} 毫秒`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} 秒`
  return `${Math.floor(ms / 60000)} 分 ${Math.round((ms % 60000) / 1000)} 秒`
}

function triggerLabel(row) {
  if (row.triggerType === 'cron') return '自动计划'
  if (row.triggeredBy) return `手动 · ${row.triggeredBy}`
  return '手动触发'
}

async function openLog(row) {
  stopLogPolling()
  activeTaskId.value = row.taskId
  activeTaskType.value = row.type
  logStatus.value = row.status
  crawlLog.value = ''
  logTruncated.value = false
  logOffset = null
  logVisible.value = true
  await fetchLog()
}

async function fetchLog() {
  if (!activeTaskId.value || logRequestRunning) return
  logRequestRunning = true
  if (!crawlLog.value) logLoading.value = true
  try {
    const params = logOffset === null
      ? { tail: MAX_VISIBLE_LOG }
      : { offset: logOffset, limit: LOG_CHUNK_SIZE }
    const res = await getTaskCrawlLog(activeTaskId.value, params)
    const chunk = res.data?.chunk || ''
    if (logOffset === null) {
      crawlLog.value = chunk
    } else if (chunk) {
      crawlLog.value += chunk
    }
    if (crawlLog.value.length > MAX_VISIBLE_LOG) {
      crawlLog.value = crawlLog.value.slice(-MAX_VISIBLE_LOG)
      logTruncated.value = true
    }
    logTruncated.value = logTruncated.value || Boolean(res.data?.truncated)
    logOffset = Number(res.data?.nextOffset || 0)
    logStatus.value = res.data?.status || logStatus.value
    if (chunk) await nextTick(() => {
      if (logViewer.value) logViewer.value.scrollTop = logViewer.value.scrollHeight
    })
    if (logStatus.value === 'RUNNING' && logVisible.value && !logTimer) {
      logTimer = window.setInterval(fetchLog, 2000)
    } else if (logStatus.value !== 'RUNNING') {
      stopLogPolling()
    }
  } finally {
    logLoading.value = false
    logRequestRunning = false
  }
}

async function refreshLog() {
  await fetchLog()
}

function stopLogPolling() {
  if (logTimer) {
    window.clearInterval(logTimer)
    logTimer = null
  }
}

async function copyLog() {
  await navigator.clipboard.writeText(crawlLog.value)
  ElMessage.success('当前日志窗口已复制')
}

async function downloadLog() {
  if (!activeTaskId.value) return
  logDownloading.value = true
  try {
    const res = await downloadTaskCrawlLog(activeTaskId.value)
    const url = URL.createObjectURL(res.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `${taskTypeLabel(activeTaskType.value)}-${activeTaskId.value}.log`
    link.click()
    URL.revokeObjectURL(url)
  } finally {
    logDownloading.value = false
  }
}

onMounted(refreshPage)
onUnmounted(() => {
  disposed = true
  stopLogPolling()
  if (pageRefreshTimer) window.clearTimeout(pageRefreshTimer)
})
</script>

<style scoped>
.history-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.history-header > div:first-child { display: grid; gap: 4px; }
.history-header small { color: #8b98ad; font-size: 12px; }
.history-actions { display: flex; align-items: center; gap: 8px; }
.auto-refresh-label { color: #7d899c; font-size: 12px; }
.status-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 18px; padding: 15px 18px; border: 1px solid #edf0f5; border-radius: 8px; background: #f9fbfd; }
.status-summary > div { padding: 0 18px; border-right: 1px solid #e6eaf0; }
.status-summary > div:first-child { padding-left: 0; }
.status-summary > div:last-child { border-right: 0; }
.status-summary span { display: block; color: #8b98ad; font-size: 12px; }
.status-summary strong { display: block; margin-top: 6px; color: #25344f; font-size: 23px; }
.status-summary .running-number { color: #409eff; }
.status-summary .success-number { color: #16a085; }
.status-summary .failed-number { color: #f56c6c; }
.filter-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.task-type-tabs { flex-shrink: 0; }
.filter-fields { display: flex; align-items: center; gap: 8px; }
.task-id-cell { max-width: 230px; overflow: hidden; color: #34445f; font: 12px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; text-overflow: ellipsis; white-space: nowrap; }
.task-meta { margin-top: 5px; color: #98a3b4; font-size: 11px; }
.progress-message { display: block; margin-top: 5px; color: #909399; font-size: 12px; }
.muted { color: #c0c4cc; }
.log-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.log-task-id { margin-left: 10px; color: #606266; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.live-indicator { margin-left: 12px; color: #e6a23c; font-size: 12px; }
.log-notice { margin-bottom: 12px; }
.log-viewer { min-height: 360px; height: calc(100vh - 150px); overflow: auto; border-radius: 6px; background: #111827; }
.log-viewer pre { min-height: 100%; box-sizing: border-box; margin: 0; padding: 16px; color: #d1d5db; font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 760px) {
  .history-header { align-items: flex-start; }
  .status-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px 0; }
  .status-summary > div:nth-child(2) { border-right: 0; }
  .filter-fields { width: 100%; flex-wrap: wrap; }
}
</style>
