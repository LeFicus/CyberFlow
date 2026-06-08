<template>
  <el-card>
    <template #header>站点收录统计</template>
    <el-form :model="form" :rules="rules" label-width="120px" style="max-width: 500px;">
      <el-form-item label="管理平台账号" prop="username">
        <el-input v-model="form.username" placeholder="登录管理平台的账号" />
      </el-form-item>
      <el-form-item label="管理平台密码" prop="password">
        <el-input v-model="form.password" type="password" placeholder="登录管理平台的密码" show-password />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleTrigger">启动收录统计</el-button>
      </el-form-item>
    </el-form>

    <el-divider />

    <div v-if="taskResult" class="task-result">
      <el-alert :type="taskResult.state === 'SUCCESS' ? 'success' : 'info'" :closable="false">
        <template #title>
          任务状态: {{ taskResult.state }}
          <span style="margin-left: 16px; font-size: 13px;">Task ID: {{ taskResult.task_id }}</span>
        </template>
        {{ taskResult.result || '等待任务结果...' }}
      </el-alert>
    </div>
  </el-card>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { triggerCollectCrawler, getTaskStatus } from '@/api/crawler'

const loading = ref(false)
const taskResult = ref(null)
const form = reactive({ username: 'admin', password: 'password123' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleTrigger() {
  loading.value = true
  taskResult.value = null
  try {
    const res = await triggerCollectCrawler(form.username, form.password)
    await sleep(1000)
    const statusRes = await getTaskStatus(res.data.task_id)
    taskResult.value = { ...statusRes.data, task_id: res.data.task_id }
  } finally {
    loading.value = false
  }
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)) }
</script>
