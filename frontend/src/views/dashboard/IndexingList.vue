<template>
  <div class="index-workspace">
    <div class="index-summary"><div v-for="item in metrics" :key="item.label"><span>{{ item.label }}</span><strong>{{ number(summary[item.key]) }}</strong></div></div>
    <el-card shadow="never">
      <div class="heading"><div><h2>{{ title }}</h2><p>{{ dimension==='site' ? '每个站点一条最新快照，独立查看收录与变化' : '按归属汇总收录，点击“查看站点”展开明细' }}</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
      <el-alert v-if="drilled" :title="`当前范围：${route.query.label || '指定分组'}`" type="info" show-icon :closable="false" class="scope-alert"><template #default><el-button link type="primary" @click="clearScope">查看全部站点</el-button></template></el-alert>
      <el-form label-position="top" @submit.prevent="search">
        <div class="filter-grid">
          <el-form-item label="站点域名"><el-input v-model="filters.domain" clearable placeholder="输入域名关键词" @keyup.enter="search" /></el-form-item>
          <el-form-item label="建站者"><el-input v-model="filters.adminName" clearable placeholder="姓名或账号" @keyup.enter="search" /></el-form-item>
          <el-form-item label="服务器"><el-input v-model="filters.serverName" clearable placeholder="名称或 IP" @keyup.enter="search" /></el-form-item>
          <el-form-item v-if="isAdmin" label="分组"><el-select v-model="filters.userGroup" clearable placeholder="全部分组"><el-option value="A" label="A组" /><el-option value="B" label="B组" /></el-select></el-form-item>
        </div>
        <div v-if="advanced" class="filter-grid">
          <el-form-item label="主题"><el-input v-model="filters.themeName" clearable /></el-form-item>
          <el-form-item label="商品分类"><el-input v-model="filters.productCategory" clearable /></el-form-item>
          <el-form-item label="建站日期"><el-date-picker v-model="filters.siteDateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" /></el-form-item>
          <el-form-item label="Sitemap 提交日期"><el-date-picker v-model="filters.submittedDateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" /></el-form-item>
          <el-form-item label="收录更新日期"><el-date-picker v-model="filters.updatedDateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始" end-placeholder="结束" /></el-form-item>
          <el-form-item label="收录变化"><el-select v-model="filters.changeDirection" clearable><el-option label="增长" value="up" /><el-option label="下降" value="down" /><el-option label="持平" value="flat" /></el-select></el-form-item>
          <el-form-item label="最小收录数"><el-input-number v-model="filters.minIndexCount" :min="0" :precision="0" :controls="false" /></el-form-item>
          <el-form-item label="最大收录数"><el-input-number v-model="filters.maxIndexCount" :min="0" :precision="0" :controls="false" /></el-form-item>
        </div>
        <el-button type="primary" native-type="submit" :loading="loading">查询</el-button><el-button @click="reset">重置</el-button><el-button text @click="advanced=!advanced">{{ advanced ? '收起' : '高级筛选' }}</el-button>
      </el-form>
    </el-card>
    <el-card shadow="never" class="table-card">
      <el-table :data="rows" v-loading="loading" :max-height="640" stripe empty-text="暂无匹配的收录数据">
        <el-table-column v-if="dimension==='site'" prop="site_domain" label="站点域名" min-width="210" fixed show-overflow-tooltip />
        <el-table-column v-else :label="dimension==='builder' ? '建站者' : '服务器'" min-width="230"><template #default="{row}"><div class="stack"><strong>{{ row.dimension_name }}</strong><small>{{ dimension==='builder' ? row.builder_username : row.server_ip || '未分配 IP' }}</small></div></template></el-table-column>
        <el-table-column v-if="dimension!=='site'" prop="site_count" label="站点数" width="100" align="right" />
        <el-table-column label="收录数量" width="125" align="right"><template #default="{row}"><strong>{{ row.index_updated_at ? number(row.index_count) : '未采集' }}</strong></template></el-table-column>
        <el-table-column label="较上次变化" width="130" align="right"><template #default="{row}"><el-tag :type="Number(row.index_change)>0 ? 'success' : Number(row.index_change)<0 ? 'danger' : 'info'" effect="plain">{{ row.index_updated_at ? signed(row.index_change) : '—' }}</el-tag></template></el-table-column>
        <el-table-column label="商品数" width="110" align="right"><template #default="{row}">{{ number(row.product_count) }}</template></el-table-column>
        <el-table-column v-if="dimension==='site'" label="归属" min-width="170"><template #default="{row}"><div class="stack"><span>{{ row.admin_name || row.builder_username || '未分配' }}</span><small>{{ row.user_group ? `${row.user_group}组` : '未分组' }}</small></div></template></el-table-column>
        <el-table-column v-if="dimension==='site'" label="服务器" min-width="180"><template #default="{row}"><div class="stack"><span>{{ row.server_name || '未分配' }}</span><small>{{ row.server_ip || '—' }}</small></div></template></el-table-column>
        <el-table-column label="最近收录更新" width="170"><template #default="{row}">{{ date(row.index_updated_at) }}</template></el-table-column>
        <el-table-column label="Sitemap 提交" width="170"><template #default="{row}">{{ date(row.last_submitted_at) }}</template></el-table-column>
        <el-table-column v-if="dimension==='site'" label="站点信息" min-width="190"><template #default="{row}"><div class="stack"><span>{{ row.theme_name || '未设置主题' }}</span><small>{{ row.product_category || '未设置分类' }}</small><small>建站 {{ date(row.created_at) }}</small></div></template></el-table-column>
        <el-table-column v-if="dimension!=='site'" label="操作" width="120" fixed="right"><template #default="{row}"><el-button link type="primary" @click="drill(row)">查看站点</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[20,50,100,200]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" />
    </el-card>
  </div>
</template>
<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSiteIndexes } from '@/api/dashboard'
import { useUserStore } from '@/store/user'
const route=useRoute(), router=useRouter(), user=useUserStore()
const isAdmin=computed(() => user.userInfo?.roles?.includes('ROLE_ADMIN'))
const dimension=computed(() => route.path.endsWith('/builders') ? 'builder' : route.path.endsWith('/servers') ? 'server' : 'site')
const title=computed(() => ({site:'站点明细',builder:'建站者汇总',server:'服务器汇总'}[dimension.value]))
const drilled=computed(() => !!(route.query.builderUsername || route.query.serverIp || route.query.serverNameExact))
const defaults=() => ({domain:'',adminName:'',serverName:'',userGroup:'',themeName:'',productCategory:'',siteDateRange:[],submittedDateRange:[],updatedDateRange:[],minIndexCount:null,maxIndexCount:null,changeDirection:''})
const filters=reactive(defaults()), rows=ref([]), summary=ref({}), page=ref(1), size=ref(20), total=ref(0), loading=ref(false), advanced=ref(false)
const metrics=[{label:'站点数',key:'site_count'},{label:'收录总数',key:'index_count'},{label:'商品总数',key:'product_count'},{label:'平均收录',key:'average_index_count'},{label:'收录变化',key:'index_change'}]
const number=v => Number(v || 0).toLocaleString('zh-CN'), signed=v => Number(v)>0 ? `+${number(v)}` : number(v)
const date=v => v ? String(v).replace('T',' ').slice(0,16) : '—'
let sequence=0
function params() {
  const p={...filters,page:page.value,size:size.value,dimension:dimension.value}
  for (const [key,prefix] of [['siteDateRange','site'],['submittedDateRange','submitted'],['updatedDateRange','updated']]) { p[`${prefix}StartDate`]=filters[key]?.[0]; p[`${prefix}EndDate`]=filters[key]?.[1]; delete p[key] }
  if (dimension.value==='site') for (const key of ['builderUsername','builderNameExact','serverIp','serverNameExact','serverIpEmpty']) if (route.query[key]) p[key]=route.query[key]
  return p
}
async function load() {
  const request=++sequence; loading.value=true
  try { const res=await getSiteIndexes(params()); if (request!==sequence) return; rows.value=res.data?.list || []; summary.value=res.data?.summary || {}; total.value=Number(res.data?.total || 0) }
  catch { if (request===sequence) { rows.value=[]; total.value=0; summary.value={} } }
  finally { if (request===sequence) loading.value=false }
}
function search() { if (filters.minIndexCount!=null && filters.maxIndexCount!=null && filters.minIndexCount>filters.maxIndexCount) return ElMessage.warning('最小收录数不能大于最大收录数'); page.value=1; load() }
function reset() { Object.assign(filters,defaults()); search() }
function clearScope() { router.replace({path:'/indexing/sites'}) }
function drill(row) {
  const query=dimension.value==='builder' ? {builderUsername:row.builder_username,builderNameExact:row.admin_name,label:row.dimension_name} : {serverNameExact:row.dimension_name,serverIp:row.server_ip || undefined,serverIpEmpty:row.server_ip ? undefined : 'true',label:`${row.dimension_name} ${row.server_ip || ''}`}
  for (const [key,value] of Object.entries(params())) if (!['dimension','page','size'].includes(key) && value!=='' && value!=null) query[key]=String(value)
  router.push({path:'/indexing/sites',query})
}
watch(() => route.fullPath, () => { Object.assign(filters,defaults()); for (const key of Object.keys(defaults())) if (route.query[key] && !key.endsWith('Range')) filters[key]=key.includes('IndexCount') ? Number(route.query[key]) : String(route.query[key]); for (const [key,prefix] of [['siteDateRange','site'],['submittedDateRange','submitted'],['updatedDateRange','updated']]) if (route.query[`${prefix}StartDate`]) filters[key]=[route.query[`${prefix}StartDate`],route.query[`${prefix}EndDate`]]; page.value=1; load() }, {immediate:true})
</script>
<style scoped>
.index-workspace { display:grid; grid-template-columns:minmax(0,1fr); gap:20px; }.index-summary { display:grid; grid-template-columns:repeat(5,minmax(0,1fr)); gap:14px; }.index-summary>div { background:var(--el-bg-color); border:1px solid var(--el-border-color-light); border-radius:12px; padding:20px; }.index-summary span { color:var(--el-text-color-secondary); font-size:13px; }.index-summary strong { display:block; font-size:25px; margin-top:10px; }.heading { display:flex; justify-content:space-between; align-items:center; gap:16px; margin-bottom:20px; }.heading h2 { margin:0 0 8px; }.heading p { margin:0; color:var(--el-text-color-secondary); font-size:13px; }.filter-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:0 16px; }.filter-grid :deep(.el-date-editor),.filter-grid :deep(.el-input-number) { width:100%; min-width:0; }.table-card { min-width:0; }.stack { display:flex; flex-direction:column; gap:5px; }.stack small { color:var(--el-text-color-secondary); }.scope-alert { margin-bottom:18px; }.el-pagination { justify-content:flex-end; margin-top:20px; flex-wrap:wrap; }@media(max-width:1000px) { .filter-grid {grid-template-columns:repeat(2,minmax(0,1fr));}.index-summary {grid-template-columns:repeat(3,minmax(0,1fr));} }@media(max-width:650px) { .filter-grid {grid-template-columns:1fr;}.index-summary {grid-template-columns:repeat(2,minmax(0,1fr));} }
</style>
