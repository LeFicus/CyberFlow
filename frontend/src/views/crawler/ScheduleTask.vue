<template>
  <div class="schedule-page">
    <PageHeader
      eyebrow="DATA SYNC"
      title="计划任务"
      description="统一管理站点、订单和收录数据的自动执行计划"
    />

    <section class="overview-grid" aria-label="任务概览">
      <StatCard
        label="正在执行"
        :value="overview.active"
        :note="overview.active ? '状态自动刷新中' : '当前队列空闲'"
        tone="blue"
        icon="VideoPlay"
      />
      <StatCard
        label="今日成功"
        :value="overview.successToday"
        note="已完成的同步任务"
        tone="green"
        icon="CircleCheck"
      />
      <StatCard
        label="今日失败"
        :value="overview.failedToday"
        :note="overview.failedToday ? '请到任务历史查看日志' : '今日运行正常'"
        tone="red"
        icon="WarningFilled"
      />
      <StatCard label="最近同步完成" tone="neutral" icon="Clock">
        <template #value>
          <span class="metric-time">{{ formatBeijingDateTime(overview.latestSuccessAt, '暂无记录') }}</span>
        </template>
        <template #note>所有数据源最近成功时间（北京时间）</template>
      </StatCard>
    </section>

    <DataPanel title="同步控制台" description="统一查看站点、订单和收录数据的计划与最近执行结果">
      <template #actions>
        <el-button @click="goHistory">查看任务历史</el-button>
        <el-button :loading="loading" @click="refreshAll">刷新</el-button>
      </template>

      <el-alert
        title="Cron 使用北京时间和 Quartz 六段格式（秒 分 时 日 月 星期）；有任务执行时状态每 8 秒自动刷新。"
        type="info"
        :closable="false"
        show-icon
        class="schedule-tip"
      />

      <p class="cf-table-hint">左右滑动表格查看更多列</p>
      <el-table :data="schedules" v-loading="loading" stripe empty-text="暂无计划任务">
        <el-table-column label="数据源" min-width="180">
          <template #default="{ row }">
            <div class="task-name">{{ taskName(row.taskType) }}</div>
            <div class="task-description">{{ taskDescription(row.taskType) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="执行计划" min-width="280">
          <template #default="{ row }">
            <el-input v-model="row.cronExpression" :disabled="!canEdit" placeholder="例如：0 0 */6 * * ?" />
            <div class="cron-description">{{ cronDescription(row.cronExpression) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="自动同步" width="110" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" :disabled="!canEdit" :aria-label="`${taskName(row.taskType)}自动同步`" />
          </template>
        </el-table-column>
        <el-table-column label="最近执行" min-width="230">
          <template #default="{ row }">
            <template v-if="latestTask(row.taskType)">
              <div class="latest-status">
                <el-tag size="small" :type="taskStatus(latestTask(row.taskType)).tone">
                  {{ taskStatus(latestTask(row.taskType)).label }}
                </el-tag>
                <span>{{ formatBeijingDateTime(latestTask(row.taskType).createdAt) }}</span>
              </div>
              <div class="latest-result">{{ latestResult(latestTask(row.taskType)) }}</div>
            </template>
            <span v-else class="muted">尚无执行记录</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" :fixed="isNarrow ? false : 'right'">
          <template #default="{ row }">
            <el-button
              v-if="canEdit"
              type="primary"
              link
              :disabled="!isDirty(row)"
              :loading="row.saving"
              @click="saveSchedule(row)"
            >保存</el-button>
            <el-button
              v-if="canTrigger"
              type="success"
              link
              :disabled="isActive(row.taskType)"
              :loading="row.triggering"
              @click="triggerSchedule(row)"
            >{{ isActive(row.taskType) ? '执行中' : '立即执行' }}</el-button>
            <el-tag v-if="!canEdit && !canTrigger" type="info">只读</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </DataPanel>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMediaQuery } from '@vueuse/core'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { taskStatus } from '@/data/taskPresentation'
import { getTaskOverview, listCrawlerSchedules, triggerCrawlerSchedule, updateCrawlerSchedule } from '@/api/crawler'
import { formatBeijingDateTime } from '@/utils/dateTime'

const router = useRouter()
const userStore = useUserStore()
const isNarrow = useMediaQuery('(max-width: 900px)')
const loading = ref(false)
const overviewLoading = ref(false)
const schedules = ref([])
const overview = ref({ active: 0, successToday: 0, failedToday: 0, latestSuccessAt: null, activeByType: {}, latestTasks: [] })
const canEdit = computed(() => userStore.hasPermission('crawler:schedule:update'))
const canTrigger = computed(() => userStore.hasPermission('crawler:schedule:trigger'))
let refreshTimer = null
let disposed = false

const taskNames = { site_crawl: '站点信息', order_crawl: '订单数据', site_index: '搜索收录' }
const taskDescriptions = {
  site_crawl: '同步站点、分类、标签及所属分组',
  order_crawl: '按数据库站点分组同步支付订单',
  site_index: '更新站点搜索引擎收录结果',
}

function taskName(taskType) { return taskNames[taskType] || taskType }
function taskDescription(taskType) { return taskDescriptions[taskType] || '数据同步任务' }

function cronDescription(expression) {
  const value = String(expression || '').trim()
  const known = {
    '0 0 */6 * * ?': '每 6 小时执行一次',
    '0 0 0 * * ?': '每天 00:00 执行',
    '0 0 2 * * ?': '每天 02:00 执行',
    '0 */30 * * * ?': '每 30 分钟执行一次',
  }
  return known[value] || (value ? '按自定义 Cron 计划执行' : '请输入执行计划')
}

function latestTask(type) {
  return overview.value.latestTasks?.find(item => item.type === type)
}

function latestResult(task) {
  if (!task) return ''
  if (task.status === 'FAILED') return task.errorMsg || '执行失败，请查看日志'
  if (task.status === 'SUCCESS') return `处理 ${Number(task.rowsAffected || 0).toLocaleString()} 条 · ${formatDuration(task.durationMs)}`
  return task.progressMessage || '等待状态更新'
}

function formatDuration(value) {
  const ms = Number(value || 0)
  if (!ms) return '耗时未记录'
  if (ms < 1000) return `${ms} 毫秒`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)} 秒`
  return `${Math.floor(ms / 60000)} 分 ${Math.round((ms % 60000) / 1000)} 秒`
}

function isActive(type) { return Number(overview.value.activeByType?.[type] || 0) > 0 }
function isDirty(row) { return row.cronExpression !== row.originalCron || row.enabled !== row.originalEnabled }

async function loadSchedules() {
  const res = await listCrawlerSchedules()
  schedules.value = (res.data || []).map(item => {
    const cronExpression = item.cronExpression ?? item.cron_expression ?? ''
    const enabled = Number(item.enabled) === 1 ? 1 : 0
    return {
      ...item,
      taskType: item.taskType ?? item.task_type,
      cronExpression,
      enabled,
      originalCron: cronExpression,
      originalEnabled: enabled,
      saving: false,
      triggering: false,
    }
  })
}

async function loadOverview() {
  if (overviewLoading.value) return
  overviewLoading.value = true
  try {
    const res = await getTaskOverview()
    overview.value = { ...overview.value, ...(res.data || {}) }
    scheduleRefresh()
  } finally {
    overviewLoading.value = false
  }
}

function scheduleRefresh() {
  if (refreshTimer) window.clearTimeout(refreshTimer)
  refreshTimer = null
  if (!disposed && Number(overview.value.active || 0) > 0) {
    refreshTimer = window.setTimeout(loadOverview, 8000)
  }
}

async function refreshAll() {
  loading.value = true
  try {
    await Promise.all([loadSchedules(), loadOverview()])
  } finally {
    loading.value = false
  }
}

async function saveSchedule(row) {
  if (!isDirty(row)) return
  row.saving = true
  try {
    const res = await updateCrawlerSchedule(row.taskType, {
      enabled: row.enabled === 1,
      cronExpression: row.cronExpression,
    })
    const updated = res.data || {}
    row.enabled = Number(updated.enabled ?? row.enabled) === 1 ? 1 : 0
    row.cronExpression = updated.cronExpression ?? row.cronExpression
    row.originalEnabled = row.enabled
    row.originalCron = row.cronExpression
    ElMessage.success(`${taskName(row.taskType)}计划已保存`)
  } finally {
    row.saving = false
  }
}

async function triggerSchedule(row) {
  if (isActive(row.taskType)) return
  row.triggering = true
  try {
    await triggerCrawlerSchedule(row.taskType)
    ElMessage.success(`${taskName(row.taskType)}任务已下发`)
    await Promise.all([loadSchedules(), loadOverview()])
  } finally {
    row.triggering = false
  }
}

function goHistory() { router.push('/crawler/history') }

onMounted(refreshAll)
onUnmounted(() => {
  disposed = true
  if (refreshTimer) window.clearTimeout(refreshTimer)
})
</script>

<style scoped>
.schedule-page {
  max-width: var(--cf-page-max);
  margin: 0 auto;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--cf-space-4);
  margin-bottom: var(--cf-space-4);
}

.metric-time {
  color: var(--cf-ink);
  font-size: var(--cf-text-h3);
  font-weight: 600;
  white-space: nowrap;
}

.schedule-tip {
  margin-bottom: var(--cf-space-4);
}

.task-name {
  color: var(--cf-ink);
  font-weight: 600;
}

.task-description,
.cron-description,
.latest-result {
  margin-top: 5px;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
}

.latest-status {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cf-ink-2);
  font-size: var(--cf-text-small);
}

.latest-result {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.muted {
  color: var(--cf-subtle);
}

@media (max-width: 1200px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .overview-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }
}
@media (min-width: 561px) and (max-width: 900px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
