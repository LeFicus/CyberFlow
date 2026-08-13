<!--
  TaskHistoryPage - 爬虫任务历史页面
  展示最近所有爬虫任务（站点爬虫、收录统计、订单爬虫）的执行记录，
  包含任务 ID、类型、状态和结果信息。数据从 API 获取并以表格形式展示。
-->
<template>
  <el-card>
    <template #header>
      任务历史
      <el-button type="primary" size="small" style="float: right;" @click="fetchData">刷新</el-button>
    </template>

    <!-- 任务历史表格 -->
    <el-table :data="tableData" v-loading="loading" stripe empty-text="暂无任务记录">
      <el-table-column prop="taskId" label="Task ID" min-width="280" />
      <el-table-column prop="type" label="类型" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.type === 'site_crawl'" type="primary">站点爬虫</el-tag>
          <el-tag v-else-if="row.type === 'site_index'" type="success">收录统计</el-tag>
          <el-tag v-else-if="row.type === 'order_crawl'" type="warning">订单爬虫</el-tag>
          <el-tag v-else-if="row.type === 'product_crawl'">商品爬虫</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" min-width="210">
        <template #default="{ row }">
          <el-progress
            :percentage="row.status === 'SUCCESS' ? 100 : (row.progress || 0)"
            :status="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'exception' : ''"
            :stroke-width="10"
          />
          <span class="progress-message">{{ row.progressMessage || (row.status === 'PENDING' ? '等待执行' : '正在执行') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" min-width="200">
        <template #default="{ row }">
          {{ row.errorMsg || `处理 ${row.rowsAffected || 0} 条` }}
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getRecentTasks } from '@/api/crawler'

/** 表格 loading 状态 */
const loading = ref(false)
/** 任务列表数据 */
const tableData = ref([])

/**
 * 获取最近的爬虫任务列表
 * API 返回 MyBatis-Plus 分页对象，任务列表位于 records 字段。
 */
async function fetchData() {
  loading.value = true
  try {
    const res = await getRecentTasks()
    tableData.value = res.data?.records || []
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function statusTagType(status) {
  if (status === 'SUCCESS') return 'success'
  if (status === 'PENDING' || status === 'RUNNING') return 'warning'
  return 'danger'
}

onMounted(fetchData)
</script>

<style scoped>
.progress-message { display: block; margin-top: 5px; color: #909399; font-size: 12px; }
</style>
