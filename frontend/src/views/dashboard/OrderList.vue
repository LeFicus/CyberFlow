<template>
  <el-card>
    <el-form :inline="true" :model="filters" style="margin-bottom: 16px;">
      <el-form-item label="开始日期">
        <el-date-picker v-model="filters.startDate" type="date" placeholder="开始日期" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="结束日期">
        <el-date-picker v-model="filters.endDate" type="date" placeholder="结束日期" value-format="YYYY-MM-DD" />
      </el-form-item>
      <el-form-item label="管理员">
        <el-input v-model="filters.adminName" placeholder="按管理员筛选" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetchData">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="订单ID" width="80" />
      <el-table-column prop="amount" label="金额" width="100" />
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column prop="product_host" label="产品域名" min-width="160" />
      <el-table-column prop="pay_status_text" label="支付状态" width="90" />
      <el-table-column prop="customer_ip_country" label="国家" width="70" />
      <el-table-column prop="shipping_email" label="收货邮箱" width="180" />
      <el-table-column prop="admin_name" label="管理员" width="90" />
      <el-table-column prop="create_time" label="创建时间" width="180" />
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
import { getOrders } from '@/api/dashboard'

const loading = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filters = reactive({ startDate: '', endDate: '', adminName: '' })

async function fetchData() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filters.startDate && filters.endDate) {
      params.startDate = filters.startDate
      params.endDate = filters.endDate
    }
    if (filters.adminName) params.adminName = filters.adminName
    const res = await getOrders(params)
    tableData.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.startDate = ''
  filters.endDate = ''
  filters.adminName = ''
  page.value = 1
  fetchData()
}

onMounted(fetchData)
</script>
