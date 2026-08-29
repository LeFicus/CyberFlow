<template>
  <div class="product-workspace">
    <el-card shadow="never">
      <div class="section-heading">
        <div><h2>筛选商品</h2><p>先缩小范围，再批量导出。填写条件后点击查询。</p></div>
        <div class="saved-filters">
          <el-select v-model="selectedPreset" clearable placeholder="常用筛选方案" @change="loadPreset"><el-option v-for="p in presets" :key="p.name" :label="p.name" :value="p.name" /></el-select>
          <el-button @click="savePreset">保存方案</el-button><el-button v-if="selectedPreset" text type="danger" @click="removePreset">移除</el-button>
        </div>
      </div>
      <el-form label-position="top" @submit.prevent="handleSearch">
        <div class="filter-grid">
          <el-form-item label="来源域名 · 精确匹配">
            <el-select v-model="draft.domains" multiple filterable remote remote-show-suffix allow-create default-first-option
              :remote-method="searchDomains" :loading="domainLoading" collapse-tags collapse-tags-tooltip clearable
              placeholder="搜索来源域名，或输入完整域名" @visible-change="openDomains">
              <el-option v-for="domain in domainOptions" :key="domain" :label="domain" :value="domain" />
            </el-select>
          </el-form-item>
          <el-form-item label="自定义分类 · 精确匹配">
            <CustomCategorySelect v-model="draft.customCategories" multiple include-disabled />
          </el-form-item>
          <el-form-item label="商品分类 · 包含匹配"><el-select v-model="draft.productCategories" multiple filterable allow-create default-first-option collapse-tags collapse-tags-tooltip clearable placeholder="输入分类并按 Enter，可多选" /></el-form-item>
          <el-form-item label="商品名称"><div class="name-search">
            <el-select v-model="draft.nameMatch" aria-label="名称匹配方式"><el-option label="开头匹配" value="prefix" /><el-option label="包含匹配" value="contains" /></el-select>
            <el-input v-model="draft.name" clearable placeholder="输入名称关键词" @keyup.enter="handleSearch" />
          </div></el-form-item>
          <el-form-item label="SKU · 开头匹配"><el-input v-model="draft.sku" clearable placeholder="输入完整 SKU 或前缀" @keyup.enter="handleSearch" /></el-form-item>
          <el-form-item label="产品标签"><el-select v-model="draft.productRoles" multiple clearable placeholder="全部标签"><el-option label="主产品" value="main" /><el-option label="补充产品" value="supplement" /></el-select></el-form-item>
        </div>
        <div v-if="advanced" class="filter-grid advanced-filters">
          <el-form-item label="价格范围（USD）"><div class="price-range"><el-input-number v-model="draft.minPrice" :min="0" :precision="2" :controls="false" placeholder="最低价" /><span>—</span><el-input-number v-model="draft.maxPrice" :min="0" :precision="2" :controls="false" placeholder="最高价" /></div></el-form-item>
          <el-form-item label="采集日期"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item>
          <el-form-item label="语言"><el-select v-model="draft.language" clearable filterable allow-create placeholder="全部语言"><el-option v-for="v in ['en','zh','de','fr','es','it','ja']" :key="v" :label="v" :value="v" /></el-select></el-form-item>
        </div>
        <div class="filter-footer">
          <div><el-button type="primary" :loading="loading" native-type="submit" :icon="Search">查询商品</el-button><el-button @click="resetFilters">重置</el-button><el-button text @click="advanced = !advanced">{{ advanced ? '收起高级筛选' : '价格 / 时间 / 语言' }}</el-button></div>
          <span v-if="dirty" class="pending-hint">筛选已修改，查询后生效</span><span v-else class="filter-tip">开头匹配更适合大数据量；包含匹配建议搭配来源域名</span>
        </div>
      </el-form>
    </el-card>
    <el-card class="results-card" shadow="never">
      <div class="section-heading">
        <div><h2>商品结果 <span class="result-count">{{ total == null ? '快速浏览' : `${number(total)} 条匹配` }}</span></h2><p>已过滤空图与已识别默认图 · 价格高于 150 USD 调整为 120–150 USD</p></div>
        <div class="result-actions">
          <el-button :disabled="loading" :icon="Refresh" @click="refreshResults">刷新</el-button>
          <el-button :loading="counting" :disabled="loading || !!error" @click="countResults">统计匹配数量</el-button>
          <el-button type="primary" :icon="Download" :disabled="loading || !!error || !rows.length" @click="openExport">导出当前结果</el-button>
          <el-badge :value="activeJobs.length" :hidden="!activeJobs.length"><el-button @click="openJobs">导出任务</el-button></el-badge>
        </div>
      </div>
      <div class="applied-filters"><span>已应用</span><el-tag v-if="!summary.length" type="info" effect="plain">全部商品</el-tag><el-tag v-for="item in summary" :key="item" effect="plain" type="info" :title="item">{{ item }}</el-tag></div>
      <el-alert v-if="dirty" title="导出使用上方“已应用”的条件；未查询的新条件不会影响结果。" type="warning" :closable="false" show-icon />
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-table ref="tableRef" v-loading="loading" :data="rows" row-key="id" stripe :max-height="640" @selection-change="selectedRows = $event" empty-text="暂无匹配商品，请调整筛选条件">
        <el-table-column v-if="canDelete" type="selection" width="44" />
        <el-table-column label="商品" min-width="330"><template #default="{row}"><div class="product-cell">
          <el-image v-if="firstImage(row.images)" :src="firstImage(row.images)" :preview-src-list="[firstImage(row.images)]" preview-teleported fit="cover" lazy class="product-image"><template #error><span class="image-placeholder">无图片</span></template></el-image><span v-else class="image-placeholder">无图片</span>
          <div class="product-info"><strong :title="row.name">{{ row.name || '未命名商品' }}</strong><span>SKU · {{ row.sku || '—' }}</span><small>ID {{ row.id }}</small></div>
        </div></template></el-table-column>
        <el-table-column label="分类" min-width="185"><template #default="{row}"><div class="category-cell"><span :title="row.custom_category">{{ row.custom_category || '未分类' }}</span><small :title="row.categories">{{ row.categories || '—' }}</small></div></template></el-table-column>
        <el-table-column label="价格 / USD" width="110" align="right"><template #default="{row}"><strong>{{ formatPrice(row.regular_price) }}</strong></template></el-table-column>
        <el-table-column label="产品标签" width="115" align="center"><template #default="{row}"><el-tag :type="row.product_role === 'supplement' ? 'warning' : 'success'" effect="light" round size="small">{{ row.product_role === 'supplement' ? '补充产品' : '主产品' }}</el-tag></template></el-table-column>
        <el-table-column prop="source_domain" label="来源域名" min-width="190" show-overflow-tooltip /><el-table-column prop="language" label="语言" width="70" />
        <el-table-column label="采集时间" width="170"><template #default="{row}">{{ dateTime(row.created_at) }}</template></el-table-column>
      </el-table>
      <div class="table-footer"><div class="selection-actions"><template v-if="canDelete"><span>已选 {{ selectedRows.length }} 条</span><el-button type="danger" text :disabled="!selectedRows.length || loading || bulkDeleting" :loading="deleting" @click="deleteSelected">删除所选</el-button><el-button type="danger" plain :disabled="loading || deleting || bulkDeleting || !!error || !rows.length || dirty" :loading="bulkDeleting" :title="dirty ? '请先查询，使筛选条件正式生效' : '按当前已应用筛选每次删除最多 500 条'" @click="deleteFilteredBatch">批量删除筛选结果</el-button></template></div>
        <div class="cursor-pagination"><span>第 {{ pageIndex + 1 }} 页 · 本页 {{ rows.length }} 条</span><el-select v-model="size" aria-label="每页条数" :disabled="loading" @change="refreshResults"><el-option v-for="n in [20,50,100,200]" :key="n" :label="`${n} 条 / 页`" :value="n" /></el-select><el-button :disabled="loading || pageIndex === 0" @click="previousPage">上一页</el-button><el-button :disabled="loading || !hasMore" @click="nextPage">下一页</el-button></div>
      </div>
    </el-card>
    <el-dialog v-model="exportVisible" title="导出筛选结果" width="min(600px, 94vw)" :close-on-click-modal="false">
      <el-alert title="后台分批生成，可离开此页面。完成后在“导出任务”下载，文件保留 24 小时。" type="info" :closable="false" show-icon />
      <div class="export-scope"><span>导出范围</span><p>{{ exportSummary || '全部商品' }}</p><small>按当前列表的 ID 上界导出；期间已有商品的更新或删除仍可能影响结果。</small></div>
      <el-form label-position="top">
        <el-form-item label="文件格式"><el-radio-group v-model="exportSettings.format"><el-radio-button value="csv">CSV 压缩包 · 适合大数据</el-radio-button><el-radio-button value="xlsx">Excel 压缩包</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="最多导出条数"><el-select v-model="exportSettings.maxRows"><el-option v-for="n in [10000,50000,100000,500000,1000000,5000000]" :key="n" :label="`${number(n)} 条`" :value="n" /></el-select></el-form-item>
        <el-form-item label="每个文件条数"><el-select v-model="exportSettings.partRows"><el-option v-for="n in [10000,50000,100000]" :key="n" :label="`${number(n)} 条 / 文件`" :value="n" /></el-select></el-form-item>
      </el-form><p class="export-note">按 ID 从小到大导出。达到条数上限会明确标记，不会伪装成全量导出。Excel 超长单元格会截断，需保留完整文本请选择 CSV。</p>
      <template #footer><el-button @click="exportVisible = false">取消</el-button><el-button type="primary" :loading="creatingExport" @click="submitExport">创建导出任务</el-button></template>
    </el-dialog>
    <el-drawer v-model="jobsVisible" title="我的导出任务" size="min(600px, 100vw)">
      <div class="jobs-toolbar"><span>文件保留 24 小时 · 一次执行一个任务</span><el-button :loading="jobsLoading" @click="refreshJobs">刷新</el-button></div><el-empty v-if="!jobs.length" description="暂无导出任务" />
      <article v-for="job in jobs" :key="job.id" class="export-job">
        <div class="job-heading"><strong>{{ job.format.toUpperCase() }} 压缩包</strong><el-tag :type="jobTone(job.state)">{{ jobLabel(job.state) }}</el-tag></div>
        <p class="job-scope">{{ productFilterSummary(job.filters).join('；') || '全部商品' }}</p><div class="job-progress"><strong>{{ number(job.processed) }}</strong><span>条已处理 / 上限 {{ number(job.maxRows) }}</span></div>
        <p v-if="isActive(job)" class="job-note">{{ job.state === 'packaging' ? '正在写入压缩包，请稍候' : '无需保持页面打开，后台持续处理' }}</p>
        <el-alert v-if="job.limited" title="已达到条数上限，未包含全部匹配商品。请缩小筛选范围或提高上限重新导出。" type="warning" :closable="false" /><el-alert v-if="job.error" :title="job.error" type="error" :closable="false" />
        <div class="job-footer"><small>{{ dateTime(job.createdAt) }}<template v-if="job.state === 'completed'"> · {{ job.parts }} 个文件 · {{ fileSize(job.bytes) }}</template></small><el-button v-if="job.state === 'completed'" type="primary" :loading="downloading === job.id" @click="downloadJob(job)">下载</el-button><el-button v-if="isActive(job)" :disabled="cancelling === job.id" @click="cancelJob(job)">取消任务</el-button></div>
      </article>
    </el-drawer>
  </div>
</template>
<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Download } from '@element-plus/icons-vue'
import { getProductDomainOptions, createProductExport, listProductExports, cancelProductExport, getProductExportDownload, deleteProducts, deleteFilteredProductBatch } from '@/api/dashboard'
import { useProductWorkspace, emptyProductFilters, productFilterSummary } from '@/composables/useProductWorkspace'
import { useUserStore } from '@/store/user'
import CustomCategorySelect from '@/components/CustomCategorySelect.vue'
const userStore = useUserStore()
const canDelete = computed(() => userStore.hasPermission('dashboard:product:delete'))
const workspace = useProductWorkspace()
const { draft, applied, rows, size, loading, error, snapshotId, hasMore, pageIndex, total, counting, dirty, summary } = workspace
const tableRef = ref(), selectedRows = ref([]), deleting = ref(false), bulkDeleting = ref(false), advanced = ref(false)
const dateRange = computed({ get: () => draft.startDate && draft.endDate ? [draft.startDate, draft.endDate] : [], set: v => { draft.startDate = v?.[0] || null; draft.endDate = v?.[1] || null } })
function clearSelection() { selectedRows.value = []; tableRef.value?.clearSelection() }
async function handleSearch() { try { clearSelection(); await workspace.apply() } catch (e) { ElMessage.warning(e.message) } }
async function resetFilters() { selectedPreset.value = ''; clearSelection(); await workspace.reset() }
async function refreshResults() { clearSelection(); await workspace.refresh() }
async function nextPage() { clearSelection(); await workspace.next() }
async function previousPage() { clearSelection(); await workspace.previous() }
async function countResults() { try { await workspace.count() } catch { /* Request interceptor reports errors. */ } }
const domainOptions = ref([]), domainLoading = ref(false)
let domainTimer, domainSequence = 0, disposed = false
function searchDomains(keyword) {
  const sequence = ++domainSequence
  clearTimeout(domainTimer); domainLoading.value = true
  domainTimer = setTimeout(async () => {
    try { const res = await getProductDomainOptions(keyword); if (sequence === domainSequence && !disposed) domainOptions.value = res.data || [] }
    catch { /* Manual entry remains available. */ }
    finally { if (sequence === domainSequence && !disposed) domainLoading.value = false }
  }, 250)
}
function openDomains(open) { if (open && !domainOptions.value.length) searchDomains('') }
const presets = ref([]), selectedPreset = ref('')
const presetKey = () => `cyberflow:product-filters:${userStore.userInfo?.username || userStore.userInfo?.id || 'local'}`
function persistPresets() { try { localStorage.setItem(presetKey(), JSON.stringify(presets.value)) } catch { ElMessage.warning('当前浏览器无法保存筛选方案') } }
async function savePreset() {
  try {
    const { value } = await ElMessageBox.prompt('保存当前填写的筛选条件，仅存于此浏览器。', '保存筛选方案', { inputPlaceholder: '例如：园艺主产品', inputValidator: v => !!v?.trim() && v.trim().length <= 30 || '请输入 1–30 字的方案名称' })
    const name = value.trim(); presets.value = [{ name, filters: JSON.parse(JSON.stringify(draft)) }, ...presets.value.filter(p => p.name !== name)].slice(0,10)
    selectedPreset.value = name; persistPresets()
  } catch { /* Prompt cancelled. */ }
}
function loadPreset(name) { const p = presets.value.find(p => p.name === name); if (p) { Object.assign(draft, emptyProductFilters(), JSON.parse(JSON.stringify(p.filters))); advanced.value = true } }
function removePreset() { presets.value = presets.value.filter(p => p.name !== selectedPreset.value); selectedPreset.value = ''; persistPresets() }
const exportVisible = ref(false), creatingExport = ref(false), jobsVisible = ref(false), jobsLoading = ref(false)
const exportSettings = reactive({ format: 'csv', maxRows: 100000, partRows: 50000 })
const exportFilters = ref(null), exportSnapshot = ref(null)
const exportSummary = computed(() => exportFilters.value ? productFilterSummary(exportFilters.value).join('；') : '')
const jobs = ref([]), downloading = ref(null), cancelling = ref(null)
const isActive = job => ['queued', 'running', 'packaging'].includes(job.state)
const activeJobs = computed(() => jobs.value.filter(isActive))
let pollTimer
function openExport() { exportFilters.value = JSON.parse(JSON.stringify(applied.value)); exportSnapshot.value = snapshotId.value; exportVisible.value = true }
async function submitExport() {
  if (creatingExport.value) return
  creatingExport.value = true
  try { await createProductExport({ filters: exportFilters.value, snapshotId: exportSnapshot.value, ...exportSettings }); exportVisible.value = false; jobsVisible.value = true; ElMessage.success('已创建后台导出任务'); await refreshJobs() }
  catch { /* API reports errors. */ } finally { creatingExport.value = false }
}
async function refreshJobs() {
  clearTimeout(pollTimer)
  if (jobsLoading.value || disposed) return
  jobsLoading.value = true
  try { const res = await listProductExports(); if (!disposed) jobs.value = res.data || [] }
  catch { /* Manual refresh remains available. */ }
  finally { jobsLoading.value = false; if (!disposed) pollTimer = setTimeout(refreshJobs, activeJobs.value.length ? 4000 : 30000) }
}
async function openJobs() { jobsVisible.value = true; await refreshJobs() }
async function cancelJob(job) { cancelling.value = job.id; try { await cancelProductExport(job.id); await refreshJobs() } catch { /* API reports errors. */ } finally { cancelling.value = null } }
async function downloadJob(job) {
  downloading.value = job.id
  try { const res = await getProductExportDownload(job.id); const link = document.createElement('a'); link.href = res.data.url; link.download = `products-${job.id}.zip`; document.body.appendChild(link); link.click(); link.remove() }
  catch { /* API reports errors. */ } finally { downloading.value = null }
}
async function deleteSelected() {
  const ids = selectedRows.value.map(row => row.id)
  if (!ids.length || deleting.value) return
  try { await ElMessageBox.confirm(`将永久删除已选择的 ${ids.length} 条商品，确定继续吗？`, '删除所选商品', { type: 'warning', confirmButtonType: 'danger', confirmButtonText: '确认删除', cancelButtonText: '取消' }) } catch { return }
  deleting.value = true
  try { const res = await deleteProducts(ids); ElMessage.success(`已删除 ${res.data.deleted_count} 条商品`); await refreshResults() } catch { /* API reports errors. */ } finally { deleting.value = false }
}
async function deleteFilteredBatch() {
  if (bulkDeleting.value || loading.value || dirty.value || !rows.value.length) return
  const scope = summary.value.join('；') || '全部商品'
  try {
    await ElMessageBox.confirm(`将按“${scope}”和当前列表快照永久删除最多 500 条商品。匹配超过 500 条时可分批重复操作，确定继续吗？`, '批量删除筛选结果', { type: 'warning', confirmButtonType: 'danger', confirmButtonText: '确认批量删除', cancelButtonText: '取消' })
  } catch { return }
  bulkDeleting.value = true
  try {
    const res = await deleteFilteredProductBatch({ filters: applied.value, snapshotId: snapshotId.value, limit: 500 })
    const deleted = Number(res.data.deleted_count || 0)
    ElMessage.success(deleted ? `已批量删除 ${number(deleted)} 条商品` : '当前筛选没有可删除的商品')
    clearSelection(); await refreshResults()
  } catch { /* API reports errors. */ } finally { bulkDeleting.value = false }
}
function number(v) { return Number(v || 0).toLocaleString('zh-CN') }
function formatPrice(v) { return v == null || v === '' || !Number.isFinite(Number(v)) ? '—' : Number(v).toLocaleString('en-US', { minimumFractionDigits:2, maximumFractionDigits:2 }) }
function dateTime(v) { return !v ? '—' : String(v).endsWith('Z') ? new Date(v).toLocaleString('zh-CN', { hour12:false }) : String(v).replace('T',' ').replace(/\.\d+$/, '') }
function fileSize(v) { const bytes = Number(v || 0); return bytes < 1048576 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1048576).toFixed(1)} MB` }
function firstImage(v) { if (!v) return ''; if (Array.isArray(v)) return String(v[0] || ''); const text = String(v).trim(); if (text.startsWith('[')) { try { return String(JSON.parse(text)[0] || '') } catch { /* Legacy field. */ } } return text.split(/[,;]\s*(?=(?:https?:)?\/\/)|[\r\n]+/)[0].trim() }
function jobLabel(s) { return ({ queued:'排队中', running:'导出中', packaging:'打包中', completed:'已完成', failed:'失败', cancelled:'已取消' })[s] || s }
function jobTone(s) { return ({ completed:'success', failed:'danger', running:'primary', packaging:'primary', queued:'warning', cancelled:'info' })[s] || 'info' }
onMounted(() => { try { const saved = JSON.parse(localStorage.getItem(presetKey()) || '[]'); presets.value = Array.isArray(saved) ? saved.filter(p => p.name && p.filters).slice(0,10) : [] } catch { presets.value = [] }; refreshResults(); refreshJobs() })
onBeforeUnmount(() => { disposed = true; clearTimeout(domainTimer); clearTimeout(pollTimer) })
</script>
<style scoped>
.product-workspace { display:grid; grid-template-columns:minmax(0,1fr); gap:18px; min-width:0; width:100%; }
.product-workspace > .el-card { min-width:0; }
.section-heading,.filter-footer,.table-footer,.result-actions,.saved-filters,.cursor-pagination,.job-heading,.job-footer,.jobs-toolbar { display:flex; align-items:center; gap:12px; }
.section-heading,.filter-footer,.table-footer,.job-heading,.job-footer,.jobs-toolbar { justify-content:space-between; }
.section-heading { margin-bottom:20px; flex-wrap:wrap; } h2 { margin:0; font-size:17px; color:var(--el-text-color-primary); }
.section-heading p { margin:7px 0 0; color:var(--el-text-color-secondary); font-size:12px; }.saved-filters .el-select { width:180px; }
.filter-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:0 20px; }.filter-grid :deep(.el-form-item) { margin-bottom:16px; }
.filter-grid :deep(.el-select),.filter-grid :deep(.el-date-editor) { width:100%; min-width:0; }
.name-search,.price-range { display:flex; gap:8px; width:100%; align-items:center; }.name-search .el-select { flex:0 0 112px; }.price-range .el-input-number { width:100%; min-width:0; }
.advanced-filters { border-top:1px dashed var(--el-border-color-light); padding-top:16px; }.filter-footer { padding-top:8px; flex-wrap:wrap; }
.filter-tip,.pending-hint,.selection-actions,.cursor-pagination,.jobs-toolbar,.export-note,.job-note { font-size:12px; color:var(--el-text-color-secondary); }.pending-hint { color:var(--el-color-warning-dark-2); }
.result-count { margin-left:10px; font-size:12px; font-weight:400; color:var(--el-text-color-secondary); }.result-actions { flex-wrap:wrap; gap:8px; }.result-actions .el-button + .el-button { margin-left:0; }
.applied-filters { display:flex; align-items:center; gap:8px; flex-wrap:wrap; margin-bottom:16px; font-size:12px; color:var(--el-text-color-secondary); }.applied-filters .el-tag { max-width:360px; height:auto; min-height:24px; white-space:normal; overflow-wrap:anywhere; }.results-card :deep(.el-alert) { margin-bottom:12px; }
.product-cell { display:flex; align-items:center; gap:12px; padding:8px 0; }.product-image,.image-placeholder { width:54px; height:54px; flex-shrink:0; border-radius:8px; }.image-placeholder { display:inline-flex; align-items:center; justify-content:center; background:var(--el-fill-color-light); color:var(--el-text-color-placeholder); font-size:11px; }
.product-info,.category-cell { min-width:0; display:flex; flex-direction:column; gap:4px; }.product-info strong { font-size:13px; font-weight:500; overflow:hidden; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }.product-info span,.product-info small,.category-cell small { font-size:11px; color:var(--el-text-color-secondary); overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.category-cell span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.table-footer { margin-top:18px; flex-wrap:wrap; }.cursor-pagination { gap:10px; flex-wrap:wrap; }.cursor-pagination .el-select { width:125px; }
.export-scope { padding:16px; margin:16px 0; background:var(--el-fill-color-light); border-radius:8px; }.export-scope p { margin:8px 0; line-height:1.7; overflow-wrap:anywhere; }.export-scope small { color:var(--el-text-color-secondary); }.export-note { line-height:1.7; }
.export-job { padding:18px; margin-top:16px; border:1px solid var(--el-border-color-light); border-radius:12px; }.job-scope { font-size:12px; line-height:1.6; color:var(--el-text-color-secondary); overflow-wrap:anywhere; }.job-progress { display:flex; align-items:baseline; gap:8px; margin:16px 0 8px; }.job-progress strong { font-size:24px; }.job-progress span { font-size:12px; color:var(--el-text-color-secondary); }.job-footer { margin-top:16px; }.job-footer small { color:var(--el-text-color-secondary); }.export-job .el-alert { margin-top:12px; }
@media(max-width:1200px) { .filter-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }.filter-tip { flex-basis:100%; } }
@media(max-width:700px) { .filter-grid { grid-template-columns:1fr; }.saved-filters { flex-wrap:wrap; }.section-heading { align-items:flex-start; }.cursor-pagination { gap:6px; }.filter-footer .el-button { margin-bottom:6px; } }
</style>
