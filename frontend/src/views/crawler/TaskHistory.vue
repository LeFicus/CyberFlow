<template>
  <el-card>
    <template #header>
      任务历史
      <el-button type="primary" size="small" style="float: right;" @click="fetchData">刷新</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" stripe empty-text="暂无任务记录">
      <el-table-column prop="task_id" label="Task ID" min-width="280" />
      <el-table-column prop="type" label="类型" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.type === 'site'" type="primary">站点爬虫</el-tag>
          <el-tag v-else-if="row.type === 'site_index'" type="success">收录统计</el-tag>
          <el-tag v-else-if="row.type === 'order'" type="warning">订单爬虫</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="state" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.state === 'SUCCESS' ? 'success' : 'danger'">{{ row.state }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="result" label="结果" min-width="200" />
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getRecentTasks } from '@/api/crawler'

const loading = ref(false)
const tableData = ref([])

async function fetchData() {
  loading.value = true
  try {
    const res = await getRecentTasks()
    tableData.value = Object.values(res.data || {})
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>
