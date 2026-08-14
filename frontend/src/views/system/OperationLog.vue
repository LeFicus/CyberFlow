<!--
  OperationLogPage - 操作日志页面
  展示系统中所有用户的操作审计日志，支持按操作人和模块筛选。
  日志包含操作类型、模块、操作对象、请求路径、IP、耗时、状态和时间等字段。
-->
<template>
  <el-card>
    <template #header>操作日志</template>

    <!-- 筛选栏 -->
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

    <!-- 日志表格 -->
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
      <el-table-column prop="requestMethod" label="方法" width="70" />
      <el-table-column prop="requestUrl" label="请求路径" min-width="200" />
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="costTime" label="耗时(ms)" width="90" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="180" />
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end;"
      v-model:current-page="page" :page-size="size"
      :page-sizes="[10, 20, 50, 100]"
      :total="total" layout="total, sizes, prev, pager, next"
      @current-change="fetchData"
      @size-change="handleSizeChange"
    />
  </el-card>
</template>

<script setup>
/**
 * @fileoverview 操作日志页面
 * @description 提供系统审计日志的分页查看和筛选功能。
 *              记录用户在系统中的所有操作行为（新增、修改、删除、触发爬虫等），
 *              包含操作人、模块、操作对象、请求详情及执行结果。
 */
import { ref, reactive, onMounted } from 'vue'
import { getLogs } from '@/api/system'

/** @type {import('vue').Ref<boolean>} 列表加载状态 */
const loading = ref(false)
/** @type {import('vue').Ref<Array>} 日志列表数据 */
const tableData = ref([])
/** @type {import('vue').Ref<number>} 当前页码 */
const page = ref(1)
/** @type {import('vue').Ref<number>} 每页条数 */
const size = ref(10)
/** @type {import('vue').Ref<number>} 总记录数 */
const total = ref(0)
/** 筛选条件（操作人、模块） */
const filters = reactive({ username: '', module: '' })

/**
 * 获取操作日志分页列表
 * 将筛选条件与分页参数合并后发起请求
 */
async function fetchData() {
  loading.value = true
  try {
    const res = await getLogs({ page: page.value, size: size.value, ...filters })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

/**
 * 重置筛选条件并重新加载第一页
 */
function resetFilters() {
  filters.username = ''
  filters.module = ''
  page.value = 1
  fetchData()
}

function handleSizeChange(value) {
  size.value = value
  page.value = 1
  fetchData()
}

onMounted(fetchData)
</script>
