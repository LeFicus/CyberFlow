<!--
  TaskHistoryPage - 爬虫任务历史页面
  展示最近所有爬虫任务（站点爬虫、收录统计、订单爬虫）的执行记录，
  包含任务 ID、类型、状态和结果信息。数据从 API 获取并以表格形式展示。
-->
<template>
  <div class="task-history-page">
    <PageHeader
      eyebrow="DATA SYNC"
      title="任务历史"
      description="检索同步记录、追踪实时进度并查看分块日志 · 时间均为北京时间"
    >
      <template #actions>
        <span class="auto-refresh-label">执行中自动刷新</span>
        <el-switch v-model="autoRefresh" aria-label="执行中自动刷新" @change="scheduleAutoRefresh" />
        <el-button type="primary" :loading="loading" @click="refreshPage">刷新</el-button>
      </template>
    </PageHeader>

    <section class="status-summary" aria-label="任务统计">
      <StatCard label="正在执行" :value="overview.active" note="状态自动刷新" tone="blue" icon="VideoPlay" />
      <StatCard label="今日成功" :value="overview.successToday" note="已完成的任务" tone="green" icon="CircleCheck" />
      <StatCard label="今日失败" :value="overview.failedToday" :note="overview.failedToday ? '请查看日志' : '今日运行正常'" tone="red" icon="WarningFilled" />
      <StatCard label="累计任务" :value="overview.total" note="当前账号可见范围" tone="neutral" icon="Tickets" />
    </section>

    <DataPanel>
      <div class="filter-toolbar">
        <el-radio-group v-model="taskType" size="small" @change="handleTypeChange" class="task-type-tabs">
          <el-radio-button value="all">全部（{{ counts.all || 0 }}）</el-radio-button>
          <el-radio-button value="site_crawl">站点（{{ counts.site_crawl || 0 }}）</el-radio-button>
          <el-radio-button value="site_index">收录（{{ counts.site_index || 0 }}）</el-radio-button>
          <el-radio-button value="site_health">健康检查（{{ counts.site_health || 0 }}）</el-radio-button>
          <el-radio-button value="order_crawl">订单（{{ counts.order_crawl || 0 }}）</el-radio-button>
          <el-radio-button v-if="userStore.hasPermission('dashboard:product:view')" value="product_crawl">商品（{{ counts.product_crawl || 0 }}）</el-radio-button>
        </el-radio-group>
        <div class="filter-fields">
          <el-select v-model="statusFilter" aria-label="任务状态" placeholder="全部状态" clearable style="width: 128px" @change="handleFilterChange">
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
      <p class="cf-table-hint">左右滑动表格查看更多列</p>
    <el-table :data="tableData" v-loading="loading" :max-height="640" stripe empty-text="暂无任务记录">
      <el-table-column label="任务" min-width="210">
        <template #default="{ row }">
          <div class="task-id-cell">{{ row.taskId }}</div>
          <div class="task-meta">{{ formatBeijingDateTime(row.createdAt) }} · {{ triggerLabel(row) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.type === 'site_crawl'" type="primary">站点爬虫</el-tag>
          <el-tag v-else-if="row.type === 'site_index'" type="success">收录统计</el-tag>
          <el-tag v-else-if="row.type === 'order_crawl'" type="warning">订单爬虫</el-tag>
          <el-tag v-else-if="row.type === 'product_crawl'">商品爬虫</el-tag>
          <el-tag v-else-if="row.type === 'site_health'" type="danger">健康检查</el-tag>
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
            :aria-label="`任务进度 ${row.status === 'SUCCESS' ? 100 : (row.progress || 0)}%`"
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
      <el-table-column label="操作" width="160" :fixed="isNarrow ? false : 'right'">
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
            v-if="['site_crawl', 'site_index', 'order_crawl', 'product_crawl', 'site_health'].includes(row.type)"
            link
            type="primary"
            @click="openLog(row)"
          >查看日志</el-button>
          <el-button v-if="userStore.hasPermission('crawler:task:delete')" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

      <el-pagination
        class="history-pagination"
        v-model:current-page="page"
        :page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="fetchData"
        @size-change="handleSizeChange"
      />
    </DataPanel>

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
import { useMediaQuery } from '@vueuse/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRecentTasks, getTaskSummary, getTaskOverview, getTaskCrawlLog, downloadTaskCrawlLog, pauseTask, resumeTask, deleteTask } from '@/api/crawler'
import { useUserStore } from '@/store/user'
import { formatBeijingDateTime } from '@/utils/dateTime'

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
const counts = reactive({ all: 0, site_crawl: 0, site_index: 0, order_crawl: 0, product_crawl: 0, site_health: 0 })
const overview = reactive({ total: 0, active: 0, successToday: 0, failedToday: 0 })
const userStore = useUserStore()
const isNarrow = useMediaQuery('(max-width: 900px)')
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
    Object.assign(counts, { all: 0, site_crawl: 0, site_index: 0, order_crawl: 0, product_crawl: 0, site_health: 0 })
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
    site_health: '站点健康检查',
  }[type] || '采集任务'
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
.task-history-page {
  max-width: var(--cf-page-max);
  margin: 0 auto;
}

.status-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--cf-space-4);
  margin-bottom: var(--cf-space-4);
}

.auto-refresh-label {
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
}

.filter-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--cf-space-3);
  margin-bottom: var(--cf-space-4);
}

.task-type-tabs {
  flex-shrink: 0;
}

.filter-fields {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
}

.task-id-cell {
  max-width: 230px;
  overflow: hidden;
  color: var(--cf-ink-2);
  font: 12px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  margin-top: 5px;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
}

.progress-message {
  display: block;
  margin-top: 5px;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
}

.muted {
  color: var(--cf-subtle);
}

.history-pagination {
  margin-top: var(--cf-space-4);
  justify-content: flex-end;
  flex-wrap: wrap;
}

.log-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-4);
  margin-bottom: var(--cf-space-3);
}

.log-task-id {
  margin-left: 10px;
  color: var(--cf-ink-2);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--cf-text-small);
}

.live-indicator {
  margin-left: 12px;
  color: var(--cf-warning-text);
  font-size: var(--cf-text-small);
}

.log-notice {
  margin-bottom: var(--cf-space-3);
}

.log-viewer {
  min-height: 360px;
  height: calc(100vh - 150px);
  overflow: auto;
  border-radius: var(--cf-radius-sm);
  background: var(--cf-nav-bg);
}

.log-viewer pre {
  min-height: 100%;
  box-sizing: border-box;
  margin: 0;
  padding: 16px;
  color: var(--cf-nav-text);
  font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

@media (max-width: 900px) {
  .status-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .filter-fields {
    width: 100%;
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .status-summary {
    grid-template-columns: 1fr;
  }
}
@media (min-width: 561px) and (max-width: 900px) {
  .status-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
