<template>
  <div class="crawler-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>订单采集</span>
          <el-button type="primary" :loading="triggering" @click="handleTrigger">立即采集</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="150px" class="config-form">
        <el-divider content-position="left">Payment API</el-divider>
        <el-form-item label="Base URL">
          <el-input v-model="form.paymentApi.baseUrl" placeholder="https://c4partypay.com" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.paymentApi.account" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.paymentApi.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="SSL 校验">
          <el-switch v-model="form.paymentApi.verifySsl" />
        </el-form-item>

        <el-divider content-position="left">增量策略</el-divider>
        <el-form-item label="初始订单 ID">
          <el-input v-model="form.orderStrategy.initialOrderId" />
        </el-form-item>
        <el-form-item label="分页大小">
          <el-input-number v-model="form.orderStrategy.pageSize" :min="20" :max="500" :step="20" />
        </el-form-item>
        <el-form-item label="排除卡号">
          <el-input v-model="excludedCardsText" type="textarea" :rows="3" />
        </el-form-item>

        <el-divider content-position="left">收入参数</el-divider>
        <el-form-item label="实时汇率">
          <el-input-number v-model="form.revenue.exchangeRate" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="折算系数">
          <el-input-number v-model="form.revenue.rateFactor" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="组长提成">
          <el-input-number v-model="form.revenue.leaderCommissionRate" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="提成阶梯">
          <el-input v-model="commissionTiersText" type="textarea" :rows="5" />
        </el-form-item>

        <el-divider content-position="left">定时任务</el-divider>
        <el-form-item label="启用">
          <el-switch v-model="schedule.enabled" />
        </el-form-item>
        <el-form-item label="Cron">
          <el-input v-model="schedule.cronExpression" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
        </el-form-item>
      </el-form>

      <TaskProgress :task="task" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import TaskProgress from '@/components/TaskProgress.vue'
import { useTaskProgress } from '@/composables/useTaskProgress'
import {
  getCrawlerConfig,
  listCrawlerSchedules,
  triggerOrderCrawler,
  updateCrawlerConfig,
  updateCrawlerSchedule,
} from '@/api/crawler'

const saving = ref(false)
const triggering = ref(false)
const { task, track } = useTaskProgress()
const excludedCardsText = ref('')
const commissionTiersText = ref('')

const form = reactive({
  paymentApi: { baseUrl: '', account: '', password: '', verifySsl: true },
  orderStrategy: {
    initialOrderId: '0',
    pageSize: 100,
    filterCardNumberExclude: [],
  },
  revenue: {
    exchangeRate: 6.73,
    rateFactor: 0.42,
    leaderCommissionRate: 0.02,
    commissionTiers: [],
  },
})

const schedule = reactive({
  enabled: true,
  cronExpression: '0 0 3 * * ?',
})

async function loadConfig() {
  const [configRes, schedulesRes] = await Promise.all([
    getCrawlerConfig(),
    listCrawlerSchedules(),
  ])
  Object.assign(form.paymentApi, configRes.data?.paymentApi || {})
  Object.assign(form.orderStrategy, configRes.data?.orderStrategy || {})
  Object.assign(form.revenue, configRes.data?.revenue || {})
  excludedCardsText.value = (form.orderStrategy.filterCardNumberExclude || []).join('\n')
  commissionTiersText.value = JSON.stringify(form.revenue.commissionTiers || [], null, 2)
  const orderSchedule = (schedulesRes.data || []).find(item => item.taskType === 'order_crawl')
  if (orderSchedule) {
    schedule.enabled = orderSchedule.enabled === 1
    schedule.cronExpression = orderSchedule.cronExpression
  }
}

async function handleSave() {
  saving.value = true
  try {
    form.orderStrategy.filterCardNumberExclude = excludedCardsText.value
      .split(/\n|,/)
      .map(item => item.trim())
      .filter(Boolean)
    form.revenue.commissionTiers = JSON.parse(commissionTiersText.value || '[]')
    await updateCrawlerConfig({
      paymentApi: form.paymentApi,
      orderStrategy: form.orderStrategy,
      revenue: form.revenue,
    })
    await updateCrawlerSchedule('order_crawl', {
      enabled: schedule.enabled,
      cronExpression: schedule.cronExpression,
    })
    ElMessage.success('保存成功')
    await loadConfig()
  } catch {
    ElMessage.error('保存失败，请检查提成阶梯 JSON')
  } finally {
    saving.value = false
  }
}

async function handleTrigger() {
  triggering.value = true
  try {
    const res = await triggerOrderCrawler()
    track(res.data.task_id)
    ElMessage.success('任务已下发')
  } finally {
    triggering.value = false
  }
}

onMounted(loadConfig)
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
