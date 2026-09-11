<template>
  <div class="crawler-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>站点采集</span>
          <div class="header-actions">
            <el-tag v-if="configDirty" type="warning">有未保存修改</el-tag>
            <el-button type="primary" :loading="triggering" :disabled="taskActive" @click="handleTrigger">
            {{ taskActive ? '采集中' : '立即采集' }}
            </el-button>
          </div>
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

        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!configDirty" @click="handleSave">保存配置</el-button>
        </el-form-item>
      </el-form>

      <TaskProgress :task="task" />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import TaskProgress from '@/components/TaskProgress.vue'
import { useTaskProgress } from '@/composables/useTaskProgress'
import {
  getCrawlerConfig,
  triggerSiteCrawler,
  updateCrawlerConfig,
} from '@/api/crawler'

const saving = ref(false)
const triggering = ref(false)
const savedSnapshot = ref('')
const { task, track } = useTaskProgress()
const taskActive = computed(() => ['PENDING', 'RUNNING', 'PAUSED'].includes(task.value?.state))

const form = reactive({
  adminApi: { baseUrl: '', username: '', password: '', verifySsl: true },
  siteStrategy: {
    skipSiteCheck: true,
    fetchAdminLoginUrl: false,
    filterBuiltOnly: false,
    pageSize: 100,
  },
})
const configDirty = computed(() => savedSnapshot.value !== JSON.stringify(form))

async function loadConfig() {
  const configRes = await getCrawlerConfig()
  Object.assign(form.adminApi, configRes.data?.adminApi || {})
  Object.assign(form.siteStrategy, configRes.data?.siteStrategy || {})
  savedSnapshot.value = JSON.stringify(form)
}

async function handleSave() {
  if (!form.adminApi.baseUrl || !/^https?:\/\//i.test(form.adminApi.baseUrl.trim())) {
    ElMessage.error('Base URL 必须以 http:// 或 https:// 开头')
    return
  }
  if (Number(form.siteStrategy.pageSize) < 20 || Number(form.siteStrategy.pageSize) > 500) {
    ElMessage.error('分页大小必须在 20 到 500 之间')
    return
  }
  saving.value = true
  try {
    await updateCrawlerConfig({
      adminApi: form.adminApi,
      siteStrategy: form.siteStrategy,
    })
    ElMessage.success('保存成功')
    await loadConfig()
  } finally {
    saving.value = false
  }
}

async function handleTrigger() {
  if (taskActive.value) return
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
.header-actions { display: flex; align-items: center; gap: 10px; }

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
