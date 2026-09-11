<template>
  <div class="crawler-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>订单采集</span>
          <div>
            <el-button v-for="item in siteGroups" :key="item.user_group" type="primary"
              :loading="triggering === item.user_group" :disabled="isGroupActive(item.user_group)" @click="handleTrigger(item.user_group)">
              {{ isGroupActive(item.user_group) ? '采集中' : `采集 ${item.user_group} 组` }}
            </el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="150px" class="config-form">
        <el-empty v-if="!siteGroups.length" description="数据库中暂无站点分组，请先采集或维护站点分组" />
        <template v-for="item in siteGroups" :key="item.user_group">
          <template v-if="form.paymentApis[item.user_group]">
          <el-divider content-position="left">{{ item.user_group }} 组 Payment API</el-divider>
          <el-form-item :label="`${item.user_group}组 Base URL`"><el-input v-model="form.paymentApis[item.user_group].baseUrl" :disabled="!canEditConfig" /></el-form-item>
          <el-form-item :label="`${item.user_group}组账号`"><el-input v-model="form.paymentApis[item.user_group].account" :disabled="!canEditConfig" /></el-form-item>
          <el-form-item :label="`${item.user_group}组密码`"><el-input v-model="form.paymentApis[item.user_group].password" :disabled="!canEditConfig" type="password" show-password /></el-form-item>
          <el-form-item :label="`${item.user_group}组 SSL 校验`"><el-switch v-model="form.paymentApis[item.user_group].verifySsl" :disabled="!canEditConfig" /></el-form-item>
          </template>
        </template>

        <el-divider content-position="left">增量策略</el-divider>
        <el-form-item label="初始订单 ID">
          <el-input v-model="form.orderStrategy.initialOrderId" :disabled="!canEditConfig" />
        </el-form-item>
        <el-form-item label="分页大小">
          <el-input-number v-model="form.orderStrategy.pageSize" :disabled="!canEditConfig" :min="20" :max="500" :step="20" />
        </el-form-item>
        <el-form-item label="排除卡号">
          <el-input v-model="excludedCardsText" :disabled="!canEditConfig" type="textarea" :rows="3" />
        </el-form-item>

        <el-form-item>
          <el-button v-if="canEditConfig" type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
          <el-tag v-else type="info">普通用户只读，敏感信息已脱敏</el-tag>
        </el-form-item>
      </el-form>

      <TaskProgress :task="task" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import TaskProgress from '@/components/TaskProgress.vue'
import { useTaskProgress } from '@/composables/useTaskProgress'
import { useSiteGroups } from '@/composables/useSiteGroups'
import {
  getCrawlerConfig,
  getTaskOverview,
  triggerOrderCrawler,
  updateCrawlerConfig,
} from '@/api/crawler'

const saving = ref(false)
const userStore = useUserStore()
const canEditConfig = computed(() => userStore.hasPermission('crawler:order:config'))
const triggering = ref('')
const { task, track } = useTaskProgress()
// Kept for the shared task presentation; order groups are guarded independently.
const taskActive = computed(() => ['PENDING', 'RUNNING', 'PAUSED'].includes(task.value?.state))
const activeGroups = ref({})
const excludedCardsText = ref('')
const { siteGroups, loadSiteGroups } = useSiteGroups()
let activityTimer = null

const form = reactive({
  paymentApis: {},
  orderStrategy: {
    initialOrderId: '0',
    pageSize: 100,
    filterCardNumberExclude: [],
  },
})

async function loadConfig() {
  const [configResult, groupsResult, overviewResult] = await Promise.allSettled([getCrawlerConfig(), loadSiteGroups(), getTaskOverview()])
  if (configResult.status !== 'fulfilled') throw configResult.reason
  const configRes = configResult.value
  const groups = groupsResult.status === 'fulfilled' ? groupsResult.value : []
  activeGroups.value = overviewResult.status === 'fulfilled' ? overviewResult.value.data?.activeByScope || {} : {}
  scheduleActivityRefresh()
  for (const item of groups) {
    const group = item.user_group
    form.paymentApis[group] = {
      baseUrl: '', account: '', password: '', verifySsl: true,
      ...(configRes.data?.[`paymentApi${group}`] || {}),
    }
  }
  Object.assign(form.orderStrategy, configRes.data?.orderStrategy || {})
  excludedCardsText.value = (form.orderStrategy.filterCardNumberExclude || []).join('\n')
}

function isGroupActive(group) {
  return Number(activeGroups.value[`group-${group}`] || 0) > 0
}

function scheduleActivityRefresh() {
  if (activityTimer) window.clearTimeout(activityTimer)
  activityTimer = null
  if (Object.values(activeGroups.value).some(value => Number(value) > 0)) {
    activityTimer = window.setTimeout(refreshActivity, 3000)
  }
}

async function refreshActivity() {
  try {
    const response = await getTaskOverview()
    activeGroups.value = response.data?.activeByScope || {}
  } finally {
    scheduleActivityRefresh()
  }
}

async function handleSave() {
  saving.value = true
  try {
    form.orderStrategy.filterCardNumberExclude = excludedCardsText.value
      .split(/\n|,/)
      .map(item => item.trim())
      .filter(Boolean)
    const payload = { orderStrategy: form.orderStrategy }
    for (const item of siteGroups.value) {
      payload[`paymentApi${item.user_group}`] = form.paymentApis[item.user_group]
    }
    await updateCrawlerConfig(payload)
    ElMessage.success('保存成功')
    await loadConfig()
  } catch {
    ElMessage.error('保存失败，请检查配置')
  } finally {
    saving.value = false
  }
}

async function handleTrigger(userGroup) {
  if (isGroupActive(userGroup)) return
  triggering.value = userGroup
  try {
    const res = await triggerOrderCrawler(userGroup)
    track(res.data.task_id)
    activeGroups.value = { ...activeGroups.value, [`group-${userGroup}`]: 1 }
    scheduleActivityRefresh()
    ElMessage.success(`${userGroup} 组订单任务已下发`)
  } finally {
    triggering.value = ''
  }
}

onMounted(loadConfig)
onUnmounted(() => {
  if (activityTimer) window.clearTimeout(activityTimer)
})
</script>

<style scoped>
.crawler-page {
  max-width: 900px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.config-form {
  max-width: 680px;
}

.task-result {
  margin-top: 16px;
}

.task-id {
  margin-left: 16px;
  font-size: 13px;
}
</style>
