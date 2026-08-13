<template>
  <el-card class="crawler-page">
    <template #header>
      <div class="card-header">
        <span>站点收录统计</span>
        <el-button type="primary" :loading="loading" @click="handleTrigger">立即统计</el-button>
      </div>
    </template>

    <el-alert title="收录统计使用站点采集页维护的 Admin API 配置。" type="info" :closable="false" />

    <TaskProgress :task="task" />
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import TaskProgress from '@/components/TaskProgress.vue'
import { useTaskProgress } from '@/composables/useTaskProgress'
import { triggerCollectCrawler } from '@/api/crawler'

const loading = ref(false)
const { task, track } = useTaskProgress()

async function handleTrigger() {
  loading.value = true
  try {
    const res = await triggerCollectCrawler()
    track(res.data.task_id)
    ElMessage.success('任务已下发')
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.crawler-page {
  max-width: 720px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.task-result {
  margin-top: 16px;
}

.task-id {
  margin-left: 16px;
  font-size: 13px;
}
</style>
