<template>
  <el-card>
    <template #header>订单爬取</template>
    <el-form :model="form" :rules="rules" label-width="120px" style="max-width: 500px;">
      <el-form-item label="开始时间" prop="startTime">
        <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" value-format="YYYY-MM-DD HH:mm:ss" />
      </el-form-item>
      <el-form-item label="结束时间" prop="endTime">
        <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" value-format="YYYY-MM-DD HH:mm:ss" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleTrigger">启动订单爬虫</el-button>
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
import { triggerOrderCrawler, getTaskStatus } from '@/api/crawler'

const loading = ref(false)
const taskResult = ref(null)
const form = reactive({
  startTime: '2026-04-01 00:00:00',
  endTime: '2026-04-29 23:59:59',
})
const rules = {
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}

async function handleTrigger() {
  loading.value = true
  taskResult.value = null
  try {
    const res = await triggerOrderCrawler(form.startTime, form.endTime)
    await sleep(1000)
    const statusRes = await getTaskStatus(res.data.task_id)
    taskResult.value = { ...statusRes.data, task_id: res.data.task_id }
  } finally {
    loading.value = false
  }
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)) }
</script>
