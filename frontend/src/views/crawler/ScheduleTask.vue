<template>
  <div class="schedule-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span>计划任务</span>
            <small>统一管理站点、订单和收录任务的自动执行计划</small>
          </div>
          <el-button :loading="loading" @click="loadSchedules">刷新</el-button>
        </div>
      </template>

      <el-alert
        title="Cron 使用 Quartz 六段格式（秒 分 时 日 月 星期），修改后会立即应用到后台调度器。"
        type="info"
        :closable="false"
        show-icon
        class="schedule-tip"
      />

      <el-table :data="schedules" v-loading="loading" stripe empty-text="暂无计划任务">
        <el-table-column label="任务名称" min-width="180">
          <template #default="{ row }">
            <div class="task-name">{{ taskName(row.taskType) }}</div>
            <div class="task-code">{{ row.taskType }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Cron 表达式" min-width="290">
          <template #default="{ row }">
            <el-input v-model="row.cronExpression" :disabled="!canEdit" placeholder="例如：0 0 */6 * * ?" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" :disabled="!canEdit" />
          </template>
        </el-table-column>
        <el-table-column label="最近触发时间" min-width="180">
          <template #default="{ row }">
            {{ formatDate(row.lastTriggeredAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canEdit" type="primary" link :loading="row.saving" @click="saveSchedule(row)">保存</el-button>
            <el-button v-if="canTrigger" type="success" link :loading="row.triggering" @click="triggerSchedule(row)">立即执行</el-button>
            <el-tag v-if="!canEdit && !canTrigger" type="info">只读</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { listCrawlerSchedules, triggerCrawlerSchedule, updateCrawlerSchedule } from '@/api/crawler'

const userStore = useUserStore()
const loading = ref(false)
const schedules = ref([])
const canEdit = computed(() => userStore.hasPermission('crawler:schedule:update'))
const canTrigger = computed(() => userStore.hasPermission('crawler:schedule:trigger'))

const taskNames = {
  site_crawl: '站点爬取',
  order_crawl: '订单爬取',
  site_index: '站点收录',
}

function taskName(taskType) {
  return taskNames[taskType] || taskType
}

function formatDate(value) {
  if (!value) return '尚未触发'
  return String(value).replace('T', ' ')
}

async function loadSchedules() {
  loading.value = true
  try {
    const res = await listCrawlerSchedules()
    schedules.value = (res.data || []).map(item => ({
      ...item,
      taskType: item.taskType ?? item.task_type,
      cronExpression: item.cronExpression ?? item.cron_expression ?? '',
      lastTriggeredAt: item.lastTriggeredAt ?? item.last_triggered_at,
      enabled: Number(item.enabled) === 1 ? 1 : 0,
      saving: false,
      triggering: false,
    }))
  } finally {
    loading.value = false
  }
}

async function saveSchedule(row) {
  row.saving = true
  try {
    const res = await updateCrawlerSchedule(row.taskType, {
      enabled: row.enabled === 1,
      cronExpression: row.cronExpression,
    })
    const updated = res.data || {}
    row.enabled = Number(updated.enabled ?? row.enabled) === 1 ? 1 : 0
    row.cronExpression = updated.cronExpression ?? row.cronExpression
    row.lastTriggeredAt = updated.lastTriggeredAt ?? row.lastTriggeredAt
    ElMessage.success(`${taskName(row.taskType)}计划已保存`)
  } finally {
    row.saving = false
  }
}

async function triggerSchedule(row) {
  row.triggering = true
  try {
    await triggerCrawlerSchedule(row.taskType)
    ElMessage.success(`${taskName(row.taskType)}任务已下发`)
    await loadSchedules()
  } finally {
    row.triggering = false
  }
}

onMounted(loadSchedules)
</script>

<style scoped>
.schedule-page { max-width: 1180px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-header > div { display: grid; gap: 4px; }
.card-header small { color: #8b98ad; font-size: 12px; }
.schedule-tip { margin-bottom: 18px; }
.task-name { color: #25344f; font-weight: 700; }
.task-code { margin-top: 4px; color: #98a5b8; font-size: 11px; }
</style>
