<template>
  <el-card>
    <template #header>操作日志</template>

    <el-form :inline="true" :model="filters" style="margin-bottom: 16px;">
      <el-form-item label="操作人">
        <el-input v-model="filters.username" placeholder="按操作人筛选" clearable />
      </el-form-item>
      <el-form-item label="模块">
        <el-select v-model="filters.module" placeholder="按模块筛选" clearable>
          <el-option label="系统管理" value="SYSTEM" />
          <el-option label="爬虫管理" value="CRAWLER" />
          <el-option label="数据看板" value="DASHBOARD" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetchData">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="操作人" width="100" />
      <el-table-column prop="operation" label="操作" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.operation === 'CREATE'" type="success">新增</el-tag>
          <el-tag v-else-if="row.operation === 'UPDATE'" type="warning">修改</el-tag>
          <el-tag v-else-if="row.operation === 'DELETE'" type="danger">删除</el-tag>
          <el-tag v-else-if="row.operation === 'TRIGGER_CRAWLER'" type="primary">触发爬虫</el-tag>
          <span v-else>{{ row.operation }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="100" />
      <el-table-column prop="target" label="操作对象" width="120" />
      <el-table-column prop="request_method" label="方法" width="70" />
      <el-table-column prop="request_url" label="请求路径" min-width="200" />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="cost_time" label="耗时(ms)" width="90" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="created_at" label="时间" width="180" />
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end;"
      v-model:current-page="page" :page-size="size"
      :total="total" layout="total, prev, pager, next"
      @current-change="fetchData"
    />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getLogs } from '@/api/system'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filters = reactive({ username: '', module: '' })

async function fetchData() {
  loading.value = true
  try {
    const res = await getLogs({ page: page.value, size: size.value, ...filters })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

function resetFilters() {
  filters.username = ''
  filters.module = ''
  page.value = 1
  fetchData()
}

onMounted(fetchData)
</script>
