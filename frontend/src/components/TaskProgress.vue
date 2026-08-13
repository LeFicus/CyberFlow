<template>
  <el-card v-if="task" class="task-progress" shadow="never">
    <div class="task-title">
      <span>任务状态：<el-tag :type="tagType">{{ task.state }}</el-tag></span>
      <span class="task-id">{{ task.task_id }}</span>
    </div>
    <el-progress :percentage="percentage" :status="progressStatus" :stroke-width="12" />
    <p>{{ task.progress_message || defaultMessage }}</p>
    <p v-if="task.state === 'SUCCESS'">已处理 {{ task.result?.rows_affected || 0 }} 条数据</p>
    <p v-if="task.state === 'FAILED' && task.result?.error" class="error">{{ task.result.error }}</p>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ task: { type: Object, default: null } })
const percentage = computed(() => props.task?.state === 'SUCCESS' ? 100 : Math.max(0, Math.min(100, props.task?.progress || 0)))
const progressStatus = computed(() => props.task?.state === 'SUCCESS' ? 'success' : props.task?.state === 'FAILED' ? 'exception' : '')
const tagType = computed(() => props.task?.state === 'SUCCESS' ? 'success' : props.task?.state === 'FAILED' ? 'danger' : 'warning')
const defaultMessage = computed(() => props.task?.state === 'PENDING' ? '任务已下发，等待执行' : '正在执行')
</script>

<style scoped>
.task-progress { margin-top: 16px; }
.task-title { display: flex; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.task-id { color: #909399; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
p { margin: 8px 0 0; color: #606266; font-size: 13px; }
.error { color: #f56c6c; }
</style>
