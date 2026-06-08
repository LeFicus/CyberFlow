<template>
  <el-card>
    <el-form :inline="true" :model="filters" style="margin-bottom: 16px;">
      <el-form-item label="来源域名">
        <el-input v-model="filters.domain" placeholder="按域名筛选" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetchData">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="SKU" label="SKU" width="120" />
      <el-table-column prop="Name" label="商品名称" min-width="200" />
      <el-table-column label="价格" width="100">
        <template #default="{ row }">
          <span v-currency="row['Regular price']" />
        </template>
      </el-table-column>
      <el-table-column prop="Categories" label="分类" width="100" />
      <el-table-column prop="原站域名" label="来源域名" width="150" />
      <el-table-column prop="语言" label="语言" width="60" />
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
import { getProducts } from '@/api/dashboard'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filters = reactive({ domain: '' })

async function fetchData() {
  loading.value = true
  try {
    const res = await getProducts({ page: page.value, size: size.value, domain: filters.domain || undefined })
    tableData.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.domain = ''
  page.value = 1
  fetchData()
}

onMounted(fetchData)
</script>
