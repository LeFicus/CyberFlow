<template>
  <el-card class="indexing-shell" shadow="never">
    <div class="indexing-workspace">
      <aside class="dimension-sidebar">
        <div v-if="isAdmin" class="group-navigation">
          <span>收录分组</span>
          <el-segmented v-model="filters.userGroup" :options="groupOptions" @change="handleGroupChange" />
        </div>
        <el-segmented v-model="navigationMode" :options="navigationOptions" @change="handleModeChange" />
        <el-input v-model="navigationSearch" clearable class="navigation-search" :placeholder="navigationMode === 'builder' ? '搜索账号或真实姓名' : '搜索服务器名称或 IP'" />

        <el-scrollbar class="dimension-scroll">
          <button class="dimension-item" :class="{ active: activeNavigationKey === 'all' }" @click="selectNavigation(null)">
            <span class="dimension-primary">全部</span>
            <span class="dimension-secondary">{{ number(globalTotal) }} 个站点</span>
          </button>
          <button
            v-for="item in filteredNavigationItems"
            :key="navigationKey(item)"
            class="dimension-item"
            :class="{ active: activeNavigationKey === navigationKey(item) }"
            @click="selectNavigation(item)"
          >
            <template v-if="navigationMode === 'builder'">
              <span class="dimension-primary">{{ item.builder_username || '未分配账号' }}</span>
              <span class="dimension-secondary">{{ item.admin_name || '未分配姓名' }}</span>
            </template>
            <template v-else>
              <span class="dimension-primary">{{ item.dimension_name || '未分配服务器' }}</span>
              <span class="dimension-secondary">{{ item.server_ip || '未提供 IP' }}</span>
            </template>
            <span class="dimension-count">{{ number(item.site_count) }}</span>
          </button>
        </el-scrollbar>
      </aside>

      <main class="indexing-main">
        <section class="summary-strip">
          <div><span>域名数</span><strong>{{ number(summary.site_count) }}</strong></div>
          <div><span>站点数</span><strong>{{ number(summary.site_count) }}</strong></div>
          <div><span>谷歌总收录数</span><strong>{{ number(summary.index_count) }}</strong></div>
          <div><span>站点总商品数</span><strong>{{ number(summary.product_count) }}</strong></div>
          <div><span>站点平均收录</span><strong>{{ decimal(summary.average_index_count) }}</strong></div>
          <div><span>收录数变化</span><strong :class="changeClass(summary.index_change)">{{ signed(summary.index_change) }}</strong></div>
        </section>

        <el-form :inline="true" :model="filters" class="primary-filter" @submit.prevent="handleSearch">
          <el-form-item label="域名">
            <el-input v-model="filters.domain" clearable placeholder="请输入域名搜索" @keyup.enter="handleSearch" />
          </el-form-item>
          <el-form-item label="建站时间">
            <el-date-picker v-model="filters.siteDateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" />
          </el-form-item>
          <el-form-item>
            <el-button native-type="submit" type="primary">查询</el-button>
            <el-button @click="resetFilters">重置</el-button>
            <el-button text type="primary" @click="advancedVisible = !advancedVisible">{{ advancedVisible ? '收起筛选' : '更多筛选' }}</el-button>
          </el-form-item>
        </el-form>

        <el-collapse-transition>
          <el-form v-show="advancedVisible" :inline="true" :model="filters" class="advanced-filter" @submit.prevent="handleSearch">
            <el-form-item label="建站者"><el-input v-model="filters.adminName" clearable placeholder="账号或真实姓名" /></el-form-item>
            <el-form-item label="服务器"><el-input v-model="filters.serverName" clearable placeholder="名称或 IP" /></el-form-item>
            <el-form-item label="主题"><el-input v-model="filters.themeName" clearable placeholder="主题" /></el-form-item>
            <el-form-item label="产品分类"><el-input v-model="filters.productCategory" clearable placeholder="产品分类" /></el-form-item>
            <el-form-item label="最后提交">
              <el-date-picker v-model="filters.submittedDateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始" end-placeholder="结束" />
            </el-form-item>
            <el-form-item label="收录更新">
              <el-date-picker v-model="filters.updatedDateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始" end-placeholder="结束" />
            </el-form-item>
            <el-form-item label="收录范围">
              <div class="number-range">
                <el-input-number v-model="filters.minIndexCount" :min="0" :controls="false" placeholder="最小" />
                <span>—</span>
                <el-input-number v-model="filters.maxIndexCount" :min="0" :controls="false" placeholder="最大" />
              </div>
            </el-form-item>
            <el-form-item label="收录变化">
              <el-select v-model="filters.changeDirection" clearable placeholder="全部" style="width: 110px">
                <el-option label="上升" value="up" /><el-option label="下降" value="down" /><el-option label="不变" value="flat" />
              </el-select>
            </el-form-item>
            <el-form-item><el-button native-type="submit" type="primary">应用筛选</el-button></el-form-item>
          </el-form>
        </el-collapse-transition>

        <div class="section-heading">
          <div>
            <h2>{{ currentTitle }}</h2>
            <el-tag size="small" effect="plain">{{ navigationMode === 'builder' ? '按建站者' : '按服务器' }}</el-tag>
          </div>
          <span>共 {{ number(total) }} 个站点</span>
        </div>

        <el-table :data="tableData" v-loading="loading" class="index-table" row-key="site_domain">
          <el-table-column prop="site_domain" label="域名" min-width="190" fixed />
          <el-table-column label="谷歌收录数" min-width="225">
            <template #default="{ row }">
              <div class="detail-stack">
                <div><span>数量：</span><strong>{{ number(row.index_count) }}</strong></div>
                <div><span>变化：</span><strong :class="changeClass(row.index_change)">{{ signed(row.index_change) }}</strong></div>
                <div><span>最近更新：</span>{{ dateTime(row.index_updated_at) }}</div>
                <div><span>提交地图：</span>{{ dateTime(row.last_submitted_at) }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="product_count" label="总商品数" width="125" align="right">
            <template #default="{ row }"><strong>{{ number(row.product_count) }}</strong></template>
          </el-table-column>
          <el-table-column label="历程" min-width="210">
            <template #default="{ row }">
              <div class="detail-stack">
                <div><span>域名申请：</span>{{ dateTime(row.domain_applied_at) }}</div>
                <div><span>建站时间：</span>{{ dateTime(row.created_at) }}</div>
                <div><span>主题：</span>{{ row.theme_name || '—' }}</div>
                <div><span>产品分类：</span>{{ row.product_category || '—' }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="负责人" min-width="165">
            <template #default="{ row }">
              <div class="detail-stack">
                <div><span>用户名：</span>{{ row.builder_username || '—' }}</div>
                <div><span>真实姓名：</span>{{ row.admin_name || '—' }}</div>
                <div><span>所属小组：</span><el-tag v-if="row.user_group" size="small" effect="plain">{{ row.user_group }}组</el-tag><span v-else>—</span></div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="服务器" min-width="220">
            <template #default="{ row }">
              <div class="detail-stack">
                <div><span>服务器名称：</span>{{ row.server_name || '—' }}</div>
                <div><span>服务器 IP：</span>{{ row.server_ip || '—' }}</div>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination v-model:current-page="page" v-model:page-size="size" :page-sizes="[20, 50, 100, 200]" :total="total" layout="total, sizes, prev, pager, next" @current-change="loadData" @size-change="handleSizeChange" />
      </main>
    </div>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { getSiteIndexes } from '@/api/dashboard'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const isAdmin = computed(() => (userStore.userInfo?.roles || []).some(role => String(role).toUpperCase() === 'ROLE_ADMIN'))
const groupOptions = [{ label: '全部', value: '' }, { label: 'A组', value: 'A' }, { label: 'B组', value: 'B' }]
const navigationOptions = [{ label: '按建站者', value: 'builder' }, { label: '按服务器', value: 'server' }]
const navigationMode = ref('builder')
const navigationSearch = ref('')
const navigationItems = ref([])
const selectedNavigation = ref(null)
const globalTotal = ref(0)
const advancedVisible = ref(false)
const loading = ref(false)
const tableData = ref([])
const summary = ref({})
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive({ userGroup: '', adminName: '', serverName: '', domain: '', themeName: '', productCategory: '', siteDateRange: [], submittedDateRange: [], updatedDateRange: [], minIndexCount: null, maxIndexCount: null, changeDirection: '' })

const number = value => Number(value || 0).toLocaleString('en-US')
const decimal = value => Number(value || 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const signed = value => `${Number(value || 0) > 0 ? '+' : ''}${number(value)}`
const changeClass = value => Number(value || 0) > 0 ? 'change-up' : Number(value || 0) < 0 ? 'change-down' : 'change-flat'
const dateTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '—'
const navigationKey = item => navigationMode.value === 'builder'
  ? `builder:${item.builder_username || ''}:${item.admin_name || ''}`
  : `server:${item.dimension_name || ''}:${item.server_ip || ''}`
const activeNavigationKey = computed(() => selectedNavigation.value ? navigationKey(selectedNavigation.value) : 'all')
const currentTitle = computed(() => {
  if (!selectedNavigation.value) return '全部站点'
  return navigationMode.value === 'builder'
    ? `${selectedNavigation.value.builder_username || '未分配账号'} · ${selectedNavigation.value.admin_name || '未分配姓名'}`
    : `${selectedNavigation.value.dimension_name || '未分配服务器'} · ${selectedNavigation.value.server_ip || '未提供 IP'}`
})
const filteredNavigationItems = computed(() => {
  const keyword = navigationSearch.value.trim().toLowerCase()
  if (!keyword) return navigationItems.value
  return navigationItems.value.filter(item => {
    const text = navigationMode.value === 'builder'
      ? `${item.builder_username || ''} ${item.admin_name || ''}`
      : `${item.dimension_name || ''} ${item.server_ip || ''}`
    return text.toLowerCase().includes(keyword)
  })
})

function queryParams() {
  const selected = selectedNavigation.value
  const selectedBuilderFallback = navigationMode.value === 'builder' && selected && !selected.builder_username
    ? selected.admin_name
    : ''
  const selectedServerFallback = navigationMode.value === 'server' && selected && !selected.server_ip
    ? selected.dimension_name
    : ''
  return {
    page: page.value, size: size.value, dimension: 'site',
    userGroup: filters.userGroup || undefined,
    builderUsername: navigationMode.value === 'builder' && selected ? selected.builder_username : undefined,
    serverIp: navigationMode.value === 'server' && selected ? selected.server_ip : undefined,
    adminName: filters.adminName.trim() || selectedBuilderFallback || undefined,
    serverName: filters.serverName.trim() || selectedServerFallback || undefined,
    domain: filters.domain.trim() || undefined,
    themeName: filters.themeName.trim() || undefined,
    productCategory: filters.productCategory.trim() || undefined,
    siteStartDate: filters.siteDateRange?.[0], siteEndDate: filters.siteDateRange?.[1],
    submittedStartDate: filters.submittedDateRange?.[0], submittedEndDate: filters.submittedDateRange?.[1],
    updatedStartDate: filters.updatedDateRange?.[0], updatedEndDate: filters.updatedDateRange?.[1],
    minIndexCount: filters.minIndexCount ?? undefined, maxIndexCount: filters.maxIndexCount ?? undefined,
    changeDirection: filters.changeDirection || undefined,
  }
}

async function loadNavigation() {
  const response = await getSiteIndexes({ dimension: navigationMode.value, page: 1, size: 200, userGroup: filters.userGroup || undefined })
  navigationItems.value = response.data?.list || []
  globalTotal.value = Number(response.data?.summary?.site_count || 0)
}
async function loadData() {
  loading.value = true
  try {
    const response = await getSiteIndexes(queryParams())
    tableData.value = response.data?.list || []
    summary.value = response.data?.summary || {}
    total.value = Number(response.data?.total || 0)
  } finally { loading.value = false }
}
async function handleSearch() {
  page.value = 1
  await Promise.all([loadNavigation(), loadData()])
}
async function handleModeChange() {
  selectedNavigation.value = null
  navigationSearch.value = ''
  page.value = 1
  await Promise.all([loadNavigation(), loadData()])
}
async function handleGroupChange() {
  selectedNavigation.value = null
  navigationSearch.value = ''
  page.value = 1
  await Promise.all([loadNavigation(), loadData()])
}
function selectNavigation(item) { selectedNavigation.value = item; page.value = 1; loadData() }
function handleSizeChange() { page.value = 1; loadData() }
async function resetFilters() {
  Object.assign(filters, { userGroup: '', adminName: '', serverName: '', domain: '', themeName: '', productCategory: '', siteDateRange: [], submittedDateRange: [], updatedDateRange: [], minIndexCount: null, maxIndexCount: null, changeDirection: '' })
  selectedNavigation.value = null
  navigationSearch.value = ''
  page.value = 1
  await Promise.all([loadNavigation(), loadData()])
}
onMounted(() => Promise.all([loadNavigation(), loadData()]))
</script>

<style scoped>
.indexing-shell { border: 0; }
.indexing-shell :deep(.el-card__body) { padding: 0; }
.indexing-workspace { display: flex; min-height: calc(100vh - 150px); background: #fff; }
.dimension-sidebar { display: flex; width: 268px; flex: 0 0 268px; flex-direction: column; padding: 18px 14px; border-right: 1px solid #edf0f5; background: #fafbfe; }
.dimension-sidebar :deep(.el-segmented) { width: 100%; }
.group-navigation { margin-bottom: 14px; padding-bottom: 14px; border-bottom: 1px solid #e7ebf2; }
.group-navigation > span { display: block; margin-bottom: 8px; color: #667085; font-size: 11px; font-weight: 700; }
.navigation-search { margin: 14px 0 10px; }
.dimension-scroll { flex: 1; min-height: 420px; }
.dimension-item { position: relative; display: grid; width: 100%; grid-template-columns: 1fr auto; gap: 3px 10px; padding: 10px 12px; border: 0; border-radius: 7px; background: transparent; color: #344054; text-align: left; cursor: pointer; }
.dimension-item:hover { background: #f0f4fb; }
.dimension-item.active { background: #eaf2ff; color: #2878e3; }
.dimension-primary { overflow: hidden; font-size: 13px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.dimension-secondary { overflow: hidden; grid-column: 1; color: #8a94a6; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.dimension-count { grid-column: 2; grid-row: 1 / span 2; align-self: center; color: #98a2b3; font-size: 11px; }
.indexing-main { min-width: 0; flex: 1; padding: 0 22px 22px; }
.summary-strip { display: flex; flex-wrap: wrap; min-height: 72px; align-items: center; border-bottom: 1px solid #edf0f5; }
.summary-strip div { min-width: 145px; padding: 8px 24px 8px 0; }
.summary-strip span { color: #667085; font-size: 12px; }
.summary-strip strong { margin-left: 8px; color: #1d2939; font-size: 16px; }
.primary-filter { padding: 16px 0 4px; border-bottom: 1px solid #edf0f5; }
.primary-filter :deep(.el-input) { width: 240px; }
.primary-filter :deep(.el-date-editor) { width: 280px; }
.advanced-filter { padding: 14px 14px 2px; border-bottom: 1px solid #edf0f5; background: #fafbfc; }
.advanced-filter :deep(.el-input) { width: 155px; }
.advanced-filter :deep(.el-date-editor) { width: 235px; }
.number-range { display: flex; align-items: center; gap: 6px; }
.number-range :deep(.el-input-number) { width: 90px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; padding: 18px 0 12px; }
.section-heading > div { display: flex; align-items: center; gap: 10px; }
.section-heading h2 { margin: 0; color: #1d2939; font-size: 20px; }
.section-heading > span { color: #667085; font-size: 12px; }
.index-table { width: 100%; }
.index-table :deep(.el-table__cell) { padding: 14px 0; }
.detail-stack { display: flex; flex-direction: column; gap: 5px; color: #344054; font-size: 12px; line-height: 1.35; }
.detail-stack span { color: #667085; }
.el-pagination { justify-content: flex-end; margin-top: 16px; }
.change-up { color: #15976b !important; font-weight: 700; }
.change-down { color: #dc4c55 !important; font-weight: 700; }
.change-flat { color: #8490a4 !important; font-weight: 700; }
@media (max-width: 1050px) { .indexing-workspace { flex-direction: column; } .dimension-sidebar { width: auto; flex-basis: auto; border-right: 0; border-bottom: 1px solid #edf0f5; } .dimension-scroll { min-height: 0; max-height: 260px; } }
@media (max-width: 720px) { .indexing-main { padding: 0 12px 16px; } .summary-strip div { min-width: 50%; padding-right: 8px; } .primary-filter :deep(.el-form-item), .primary-filter :deep(.el-input), .primary-filter :deep(.el-date-editor) { width: 100%; } }
</style>
