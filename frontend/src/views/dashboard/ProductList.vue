<!--
  ProductListPage - 商品列表页面
  展示所有爬取到的商品数据，支持按来源域名筛选，分页浏览。
  （当前路由已注释，页面保留备用）
-->
<template>
  <el-card>
    <!-- 筛选栏 -->
    <el-form :inline="true" :model="filters" style="margin-bottom: 16px;">
      <el-form-item label="来源域名">
        <el-input v-model="filters.domain" placeholder="按域名筛选" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetchData">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-dropdown @command="handleExport">
          <el-button type="success" :loading="exporting">导出导入表<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="shopify">Shopify CSV</el-dropdown-item>
              <el-dropdown-item command="woocommerce">WooCommerce CSV</el-dropdown-item>
              <el-dropdown-item command="bigcommerce">BigCommerce CSV</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-form-item>
    </el-form>

    <!-- 商品表格 -->
    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="SKU" label="SKU" width="120" />
      <el-table-column prop="Name" label="商品名称" min-width="200" />
      <el-table-column label="价格" width="100">
        <template #default="{ row }">
          <!-- 使用 v-currency 自定义指令格式化价格 -->
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
import { ArrowDown } from '@element-plus/icons-vue'
import { getProducts, exportProducts } from '@/api/dashboard'

/** 表格 loading 状态 */
const loading = ref(false)
/** 商品列表数据 */
const tableData = ref([])
/** 当前页码 */
const page = ref(1)
/** 每页条数 */
const size = ref(10)
/** 总条数 */
const total = ref(0)
const exporting = ref(false)
/** 筛选条件 */
const filters = reactive({ domain: '' })

/**
 * 获取商品分页列表
 */
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

/**
 * 重置筛选条件并重新加载
 */
function resetFilters() {
  filters.domain = ''
  page.value = 1
  fetchData()
}

async function handleExport(engine) {
  exporting.value = true
  try {
    const res = await exportProducts({ engine, domain: filters.domain || undefined })
    const url = URL.createObjectURL(new Blob([res], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `products-${engine}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

onMounted(fetchData)
</script>
