<!--
  ProductListPage - 商品列表页面
  展示所有爬取到的商品数据，支持组合筛选、图片预览和选择删除。
-->
<template>
  <el-card>
    <!-- 筛选栏 -->
    <el-form :inline="true" :model="filters" style="margin-bottom: 16px;">
      <el-form-item label="来源域名">
        <el-input v-model="filters.domain" placeholder="按域名筛选" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="自定义分类">
        <el-input v-model="filters.category" placeholder="按自定义分类筛选/导出" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="商品名称">
        <el-input v-model="filters.name" placeholder="按商品名称筛选" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <el-button
          v-if="userStore.hasPermission('dashboard:product:delete')"
          type="danger"
          :disabled="selectedRows.length === 0"
          :loading="deleting"
          @click="handleDeleteSelected"
        >删除所选<span v-if="selectedRows.length">（{{ selectedRows.length }}）</span></el-button>
        <el-button type="success" :loading="exporting" @click="handleExport">按当前条件导出 Excel</el-button>
      </el-form-item>
    </el-form>

    <!-- 商品表格 -->
    <el-table :data="tableData" v-loading="loading" stripe @selection-change="handleSelectionChange">
      <el-table-column
        v-if="userStore.hasPermission('dashboard:product:delete')"
        type="selection"
        width="52"
        align="center"
      />
      <el-table-column label="图片" width="92" align="center">
        <template #default="{ row }">
          <el-image
            v-if="firstImage(row.images)"
            class="product-image"
            :src="firstImage(row.images)"
            :preview-src-list="[firstImage(row.images)]"
            preview-teleported
            fit="cover"
            lazy
          >
            <template #error><div class="image-placeholder">暂无图片</div></template>
          </el-image>
          <div v-else class="image-placeholder">暂无图片</div>
        </template>
      </el-table-column>
      <el-table-column prop="sku" label="SKU" width="140" />
      <el-table-column prop="name" label="商品名称" min-width="220" show-overflow-tooltip />
      <el-table-column label="价格" width="100">
        <template #default="{ row }">
          {{ formatPrice(row.regular_price) }}
        </template>
      </el-table-column>
      <el-table-column prop="categories" label="分类" min-width="130" show-overflow-tooltip />
      <el-table-column prop="custom_category" label="自定义分类" min-width="130" show-overflow-tooltip />
      <el-table-column prop="source_domain" label="来源域名" min-width="190" show-overflow-tooltip />
      <el-table-column prop="language" label="语言" width="80" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProducts, exportProductsExcel, deleteProducts } from '@/api/dashboard'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

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
const deleting = ref(false)
const selectedRows = ref([])
/** 筛选条件 */
const filters = reactive({ domain: '', category: '', name: '' })

function formatPrice(value) {
  const amount = Number(value)
  return Number.isFinite(amount)
    ? new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(amount)
    : '-'
}

function firstImage(value) {
  if (!value) return ''
  const text = String(value).trim()
  if (!text) return ''
  if (text.startsWith('[')) {
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) return String(parsed[0] || '')
    } catch {
      // Fall through to common delimited formats.
    }
  }
  return text.split(/[,|\n]/)[0].trim()
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function handleDeleteSelected() {
  const rows = selectedRows.value
  if (!rows.length) return
  try {
    await ElMessageBox.confirm(
      `将永久删除已选择的 ${rows.length} 条商品数据，确定继续吗？`,
      '删除所选商品',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  deleting.value = true
  try {
    const res = await deleteProducts(rows.map(row => row.id))
    ElMessage.success(`已删除 ${res.data.deleted_count} 条商品数据`)
    if (rows.length === tableData.value.length && page.value > 1) page.value -= 1
    await fetchData()
  } finally {
    deleting.value = false
  }
}

/**
 * 获取商品分页列表
 */
async function fetchData() {
  loading.value = true
  try {
    const res = await getProducts({
      page: page.value,
      size: size.value,
      domain: filters.domain || undefined,
      category: filters.category || undefined,
      name: filters.name || undefined,
    })
    tableData.value = Array.isArray(res.data?.list) ? res.data.list : []
    total.value = Number(res.data?.total || 0)
    selectedRows.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 重置筛选条件并重新加载
 */
function resetFilters() {
  filters.domain = ''
  filters.category = ''
  filters.name = ''
  page.value = 1
  fetchData()
}

function handleSearch() {
  page.value = 1
  fetchData()
}

async function handleExport() {
  exporting.value = true
  try {
    const res = await exportProductsExcel({
      domain: filters.domain || undefined,
      customCategory: filters.category || undefined,
    })
    const url = URL.createObjectURL(res.data)
    const link = document.createElement('a')
    link.href = url
    const disposition = res.headers?.['content-disposition'] || ''
    const encodedName = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
    link.download = encodedName ? decodeURIComponent(encodedName) : 'products.xlsx'
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('Excel 导出完成')
  } finally {
    exporting.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.product-image,
.image-placeholder {
  width: 64px;
  height: 64px;
  border-radius: 8px;
}

.product-image {
  display: block;
  margin: 0 auto;
}

.image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  font-size: 12px;
}
</style>
