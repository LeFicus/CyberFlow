<template>
  <div class="crawler-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>订单采集</span>
          <div>
            <el-button type="primary" :loading="triggering === 'A'" @click="handleTrigger('A')">采集 A 组</el-button>
            <el-button type="success" :loading="triggering === 'B'" @click="handleTrigger('B')">采集 B 组</el-button>
          </div>
        </div>
      </template>

      <el-form :model="form" label-width="150px" class="config-form">
        <el-divider content-position="left">A 组 Payment API</el-divider>
        <el-form-item label="A组 Base URL">
          <el-input v-model="form.paymentApiA.baseUrl" :disabled="!canEditConfig" placeholder="https://a.example.com" />
        </el-form-item>
        <el-form-item label="A组账号">
          <el-input v-model="form.paymentApiA.account" :disabled="!canEditConfig" />
        </el-form-item>
        <el-form-item label="A组密码">
          <el-input v-model="form.paymentApiA.password" :disabled="!canEditConfig" type="password" show-password />
        </el-form-item>
        <el-form-item label="A组 SSL 校验">
          <el-switch v-model="form.paymentApiA.verifySsl" :disabled="!canEditConfig" />
        </el-form-item>

        <el-divider content-position="left">B 组 Payment API</el-divider>
        <el-form-item label="B组 Base URL">
          <el-input v-model="form.paymentApiB.baseUrl" :disabled="!canEditConfig" placeholder="https://b.example.com" />
        </el-form-item>
        <el-form-item label="B组账号">
          <el-input v-model="form.paymentApiB.account" :disabled="!canEditConfig" />
        </el-form-item>
        <el-form-item label="B组密码">
          <el-input v-model="form.paymentApiB.password" :disabled="!canEditConfig" type="password" show-password />
        </el-form-item>
        <el-form-item label="B组 SSL 校验">
          <el-switch v-model="form.paymentApiB.verifySsl" :disabled="!canEditConfig" />
        </el-form-item>

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

        <el-divider content-position="left">收入参数</el-divider>
        <el-form-item label="实时汇率">
          <el-input-number v-model="form.revenue.exchangeRate" :disabled="!canEditConfig" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="折算系数">
          <el-input-number v-model="form.revenue.rateFactor" :disabled="!canEditConfig" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="组长提成">
          <el-input-number v-model="form.revenue.leaderCommissionRate" :disabled="!canEditConfig" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="提成阶梯">
          <el-input v-model="commissionTiersText" :disabled="!canEditConfig" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="组长配置">
          <el-input v-model="leaderConfigText" :disabled="!canEditConfig" type="textarea" :rows="3" placeholder='{"A":"A-黄伟","B":"B-李榕"}' />
        </el-form-item>
        <el-form-item label="导师后缀映射">
          <el-input v-model="teacherMapText" :disabled="!canEditConfig" type="textarea" :rows="7" placeholder='{"B-许晓龙":"-xxl"}' />
        </el-form-item>
        <el-form-item label="多账号合并">
          <el-input v-model="userMergeMapText" :disabled="!canEditConfig" type="textarea" :rows="5" placeholder='{"B-姓名":["B-账号1","B-账号2"]}' />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import TaskProgress from '@/components/TaskProgress.vue'
import { useTaskProgress } from '@/composables/useTaskProgress'
import {
  getCrawlerConfig,
  triggerOrderCrawler,
  updateCrawlerConfig,
} from '@/api/crawler'

const saving = ref(false)
const userStore = useUserStore()
const canEditConfig = computed(() => userStore.hasPermission('crawler:order:config'))
const triggering = ref('')
const { task, track } = useTaskProgress()
const excludedCardsText = ref('')
const commissionTiersText = ref('')
const leaderConfigText = ref('')
const teacherMapText = ref('')
const userMergeMapText = ref('')

const form = reactive({
  paymentApiA: { baseUrl: '', account: '', password: '', verifySsl: true },
  paymentApiB: { baseUrl: '', account: '', password: '', verifySsl: true },
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
    leaderConfig: {},
    teacherMap: {},
    userMergeMap: {},
  },
})

async function loadConfig() {
  const configRes = await getCrawlerConfig()
  Object.assign(form.paymentApiA, configRes.data?.paymentApiA || {})
  Object.assign(form.paymentApiB, configRes.data?.paymentApiB || {})
  Object.assign(form.orderStrategy, configRes.data?.orderStrategy || {})
  Object.assign(form.revenue, configRes.data?.revenue || {})
  excludedCardsText.value = (form.orderStrategy.filterCardNumberExclude || []).join('\n')
  commissionTiersText.value = JSON.stringify(form.revenue.commissionTiers || [], null, 2)
  leaderConfigText.value = JSON.stringify(form.revenue.leaderConfig || {}, null, 2)
  teacherMapText.value = JSON.stringify(form.revenue.teacherMap || {}, null, 2)
  userMergeMapText.value = JSON.stringify(form.revenue.userMergeMap || {}, null, 2)
}

async function handleSave() {
  saving.value = true
  try {
    form.orderStrategy.filterCardNumberExclude = excludedCardsText.value
      .split(/\n|,/)
      .map(item => item.trim())
      .filter(Boolean)
    form.revenue.commissionTiers = JSON.parse(commissionTiersText.value || '[]')
    form.revenue.leaderConfig = JSON.parse(leaderConfigText.value || '{}')
    form.revenue.teacherMap = JSON.parse(teacherMapText.value || '{}')
    form.revenue.userMergeMap = JSON.parse(userMergeMapText.value || '{}')
    await updateCrawlerConfig({
      paymentApiA: form.paymentApiA,
      paymentApiB: form.paymentApiB,
      orderStrategy: form.orderStrategy,
      revenue: form.revenue,
    })
    ElMessage.success('保存成功')
    await loadConfig()
  } catch {
    ElMessage.error('保存失败，请检查提成阶梯 JSON')
  } finally {
    saving.value = false
  }
}

async function handleTrigger(userGroup) {
  triggering.value = userGroup
  try {
    const res = await triggerOrderCrawler(userGroup)
    track(res.data.task_id)
    ElMessage.success(`${userGroup} 组订单任务已下发`)
  } finally {
    triggering.value = ''
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
