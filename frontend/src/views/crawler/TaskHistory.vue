<!--
  TaskHistoryPage - 爬虫任务历史页面
  展示最近所有爬虫任务（站点爬虫、收录统计、订单爬虫）的执行记录，
  包含任务 ID、类型、状态和结果信息。数据从 API 获取并以表格形式展示。
-->
<template>
  <el-card>
    <template #header>
      任务历史
      <el-button type="primary" size="small" style="float: right;" @click="fetchData">刷新</el-button>
    </template>

    <!-- 任务历史表格 -->
    <el-table :data="tableData" v-loading="loading" stripe empty-text="暂无任务记录">
      <el-table-column prop="taskId" label="Task ID" min-width="280" />
      <el-table-column prop="type" label="类型" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.type === 'site_crawl'" type="primary">站点爬虫</el-tag>
          <el-tag v-else-if="row.type === 'site_index'" type="success">收录统计</el-tag>
          <el-tag v-else-if="row.type === 'order_crawl'" type="warning">订单爬虫</el-tag>
          <el-tag v-else-if="row.type === 'product_crawl'">商品爬虫</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="210">
        <template #default="{ row }">
          <el-progress
            :percentage="row.status === 'SUCCESS' ? 100 : (row.progress || 0)"
            :status="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'exception' : ''"
            :stroke-width="10"
          />
          <span class="progress-message">{{ row.progressMessage || (row.status === 'PENDING' ? '等待执行' : '正在执行') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" min-width="200">
        <template #default="{ row }">
          {{ row.errorMsg || `处理 ${row.rowsAffected || 0} 条` }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.type === 'product_crawl'"
            link
            type="primary"
            @click="openLog(row)"
          >查看日志</el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-drawer
    v-model="logVisible"
    title="商品爬取完整日志"
    size="75%"
    destroy-on-close
    @closed="stopLogPolling"
  >
    <div class="log-toolbar">
      <div>
        <el-tag :type="statusTagType(logStatus)">{{ logStatus || 'UNKNOWN' }}</el-tag>
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
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRecentTasks, getTaskCrawlLog, downloadTaskCrawlLog } from '@/api/crawler'

/** 表格 loading 状态 */
const loading = ref(false)
/** 任务列表数据 */
const tableData = ref([])
const logVisible = ref(false)
const logLoading = ref(false)
const logDownloading = ref(false)
const activeTaskId = ref('')
const logStatus = ref('')
const crawlLog = ref('')
const logTruncated = ref(false)
const logViewer = ref(null)
const MAX_VISIBLE_LOG = 200000
const LOG_CHUNK_SIZE = 65536
let logOffset = null
let logRequestRunning = false
let logTimer = null

/**
 * 获取最近的爬虫任务列表
 * API 返回 MyBatis-Plus 分页对象，任务列表位于 records 字段。
 */
async function fetchData() {
  loading.value = true
  try {
    const res = await getRecentTasks()
    tableData.value = res.data?.records || []
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function statusTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PENDING' || status === 'RUNNING') return 'warning'
  return 'danger'
}

async function openLog(row) {
  stopLogPolling()
  activeTaskId.value = row.taskId
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
    link.download = `product-crawl-${activeTaskId.value}.log`
    link.click()
    URL.revokeObjectURL(url)
  } finally {
    logDownloading.value = false
  }
}

onMounted(fetchData)
onUnmounted(stopLogPolling)
</script>

<style scoped>
.progress-message { display: block; margin-top: 5px; color: #909399; font-size: 12px; }
.muted { color: #c0c4cc; }
.log-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.log-task-id { margin-left: 10px; color: #606266; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.live-indicator { margin-left: 12px; color: #e6a23c; font-size: 12px; }
.log-notice { margin-bottom: 12px; }
.log-viewer { min-height: 360px; height: calc(100vh - 150px); overflow: auto; border-radius: 6px; background: #111827; }
.log-viewer pre { min-height: 100%; box-sizing: border-box; margin: 0; padding: 16px; color: #d1d5db; font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
