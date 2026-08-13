<template>
  <div class="crawler-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>站点采集</span>
          <el-button type="primary" :loading="triggering" @click="handleTrigger">立即采集</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="140px" class="config-form">
        <el-divider content-position="left">Admin API</el-divider>
        <el-form-item label="Base URL">
          <el-input v-model="form.adminApi.baseUrl" placeholder="http://216.152.147.6" />
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.adminApi.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.adminApi.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="SSL 校验">
          <el-switch v-model="form.adminApi.verifySsl" />
        </el-form-item>

        <el-divider content-position="left">增量策略</el-divider>
        <el-form-item label="跳过站点检测">
          <el-switch v-model="form.siteStrategy.skipSiteCheck" />
        </el-form-item>
        <el-form-item label="获取后台登录地址">
          <el-switch v-model="form.siteStrategy.fetchAdminLoginUrl" />
        </el-form-item>
        <el-form-item label="仅已建站">
          <el-switch v-model="form.siteStrategy.filterBuiltOnly" />
        </el-form-item>
        <el-form-item label="分页大小">
          <el-input-number v-model="form.siteStrategy.pageSize" :min="20" :max="500" :step="20" />
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
  triggerSiteCrawler,
  updateCrawlerConfig,
  updateCrawlerSchedule,
} from '@/api/crawler'

const saving = ref(false)
const triggering = ref(false)
const { task, track } = useTaskProgress()

const form = reactive({
  adminApi: { baseUrl: '', username: '', password: '', verifySsl: true },
  siteStrategy: {
    skipSiteCheck: true,
    fetchAdminLoginUrl: false,
    filterBuiltOnly: false,
    pageSize: 100,
  },
})

const schedule = reactive({
  enabled: true,
  cronExpression: '0 0 2 * * ?',
})

async function loadConfig() {
  const [configRes, schedulesRes] = await Promise.all([
    getCrawlerConfig(),
    listCrawlerSchedules(),
  ])
  Object.assign(form.adminApi, configRes.data?.adminApi || {})
  Object.assign(form.siteStrategy, configRes.data?.siteStrategy || {})
  const siteSchedule = (schedulesRes.data || []).find(item => item.taskType === 'site_crawl')
  if (siteSchedule) {
    schedule.enabled = siteSchedule.enabled === 1
    schedule.cronExpression = siteSchedule.cronExpression
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateCrawlerConfig({
      adminApi: form.adminApi,
      siteStrategy: form.siteStrategy,
    })
    await updateCrawlerSchedule('site_crawl', {
      enabled: schedule.enabled,
      cronExpression: schedule.cronExpression,
    })
    ElMessage.success('保存成功')
    await loadConfig()
  } finally {
    saving.value = false
  }
}

async function handleTrigger() {
  triggering.value = true
  try {
    const res = await triggerSiteCrawler()
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
  max-width: 860px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.config-form {
  max-width: 640px;
}

.task-result {
  margin-top: 16px;
}

.task-id {
  margin-left: 16px;
  font-size: 13px;
}
</style>
