<template>
  <el-card class="new-site-page">
    <template #header>
      <div class="page-toolbar">
        <div>
          <span class="toolbar-title">新站点列表</span>
          <span class="toolbar-hint">AI 生成站点标题、标语与可注册域名</span>
        </div>
        <div class="toolbar-actions">
          <el-button v-if="canConfigureAi" @click="openAiConfig">文案 AI 配置</el-button>
          <el-button v-if="canConfigureAi" @click="openImageAiConfig">图像 AI 配置</el-button>
          <el-button type="primary" @click="openCreate(false)">创建新站点</el-button>
          <el-button type="success" plain @click="openCreate(true)">批量创建</el-button>
        </div>
      </div>
    </template>

    <div class="filters">
      <el-input
        v-model="keyword"
        clearable
        placeholder="搜索域名、分类或站点标题"
        style="width: 280px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select v-model="status" clearable placeholder="全部使用状态" style="width: 170px" @change="handleSearch">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" plain @click="handleSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="id" border stripe>
      <el-table-column prop="domain" label="新域名" min-width="190" fixed="left" />
      <el-table-column prop="customCategory" label="自定义分类" min-width="130" />
      <el-table-column label="主产品分类" min-width="180">
        <template #default="{ row }">
          {{ formatCategories(row.mainProductCategories, row.mainProductCategory) }}
        </template>
      </el-table-column>
      <el-table-column label="副产品分类" min-width="180">
        <template #default="{ row }">
          {{ formatCategories(row.supplementProductCategories, row.supplementProductCategory) }}
        </template>
      </el-table-column>
      <el-table-column label="源站点" min-width="190">
        <template #default="{ row }">
          <div class="source-list">{{ formatSources(row.sourceDomains) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="siteTitle" label="site_title" min-width="170" />
      <el-table-column prop="tagLine" label="tag_line" min-width="230" show-overflow-tooltip />
      <el-table-column label="站点使用状态" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-dropdown
            v-if="canUpdateStatus"
            trigger="click"
            :disabled="isRowBusy(row.id)"
            @command="value => handleStatusChange(row, value)"
          >
            <button
              type="button"
              class="status-badge status-trigger"
              :class="`status-${statusMeta(row.status).tone}`"
              :disabled="isRowBusy(row.id)"
              :aria-label="`${row.domain}：${statusMeta(row.status).label}，点击修改状态`"
              :aria-busy="statusUpdating.has(row.id)"
            >
              <el-icon v-if="statusUpdating.has(row.id)" class="is-loading"><Loading /></el-icon>
              <span v-else class="status-dot" aria-hidden="true" />
              <span>{{ statusMeta(row.status).label }}</span>
              <el-icon class="status-chevron"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="item in statusOptions"
                  :key="item.value"
                  :command="item.value"
                  :disabled="item.value === row.status || isRowBusy(row.id)"
                >
                  <span class="status-menu-option" :class="`status-${item.tone}`">
                    <span class="status-dot" aria-hidden="true" />
                    <span>{{ item.label }}</span>
                    <el-icon v-if="item.value === row.status"><Check /></el-icon>
                  </span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span v-else class="status-badge" :class="`status-${statusMeta(row.status).tone}`">
            <span class="status-dot" aria-hidden="true" />
            {{ statusMeta(row.status).label }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="178" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :icon="Picture" @click="openAssets(row)">品牌素材</el-button>
          <el-button
            v-if="canDelete"
            type="danger"
            link
            :icon="Delete"
            :loading="deleting.has(row.id)"
            :disabled="isRowBusy(row.id)"
            :aria-label="`删除 ${row.domain}`"
            @click="handleDelete(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      class="pagination"
      :page-sizes="[10, 20, 50]"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @current-change="fetchList"
      @size-change="handleSizeChange"
    />

    <el-dialog
      v-model="dialogVisible"
      :title="batchMode ? '批量创建新站点' : '创建新站点'"
      width="980px"
      top="5vh"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="系统会逐条调用已配置的 AI 供应商生成站点信息，并通过 RDAP 查询域名注册状态；批量创建可能需要数十秒到数分钟，请勿重复提交。"
      />
      <div class="create-toolbar">
        <span v-if="batchMode" class="batch-tip">本批次副产品分类不能重复，后端也会再次校验已有站点。</span>
        <el-button v-if="batchMode" type="primary" link @click="addRow">+ 添加一行</el-button>
      </div>

      <div v-for="(item, index) in draftRows" :key="item.key" class="draft-row">
        <div class="row-heading">
          <span>站点 {{ index + 1 }}</span>
          <el-button v-if="batchMode && draftRows.length > 1" type="danger" link @click="removeRow(index)">移除</el-button>
        </div>
        <el-form label-position="top" class="draft-form">
          <el-form-item label="自定义分类" required>
            <el-input v-model="item.customCategory" placeholder="例如：户外露营装备" maxlength="255" show-word-limit />
          </el-form-item>
          <el-form-item label="主产品商品分类" required>
            <el-select
              v-model="item.mainProductCategories"
              multiple
              filterable
              allow-create
              collapse-tags
              collapse-tags-tooltip
              placeholder="可选择多个分类"
            >
              <el-option v-for="category in productCategories" :key="category" :label="category" :value="category" />
            </el-select>
          </el-form-item>
          <el-form-item label="副产品商品分类" required>
            <el-select
              v-model="item.supplementProductCategories"
              multiple
              filterable
              allow-create
              collapse-tags
              collapse-tags-tooltip
              placeholder="可选择多个分类"
            >
              <el-option v-for="category in productCategories" :key="category" :label="category" :value="category" />
            </el-select>
          </el-form-item>
          <el-form-item label="源站点（可多选）" required>
            <el-select
              v-model="item.sourceDomains"
              multiple
              filterable
              allow-create
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择源站点"
            >
              <el-option v-for="domain in sourceDomains" :key="domain" :label="domain" :value="domain" />
            </el-select>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">
          {{ batchMode ? `批量创建（${draftRows.length}）` : '生成并创建' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="aiDialogVisible" title="AI 供应商配置" width="620px" :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="支持 DeepSeek、OpenAI、通义千问等 OpenAI 兼容接口；更换供应商时填写对应的 Base URL 和模型名称。"
        class="ai-tip"
      />
      <el-form :model="aiConfig" label-width="125px" class="ai-form">
        <el-form-item label="供应商">
          <el-select v-model="aiConfig.provider" style="width: 100%">
            <el-option v-for="item in aiProviders" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="aiConfig.baseUrl" placeholder="https://api.deepseek.com 或供应商兼容接口地址" />
        </el-form-item>
        <el-form-item label="API Key" required>
          <el-input v-model="aiConfig.apiKey" type="password" show-password placeholder="输入新 Key；****** 表示保持原 Key" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="aiConfig.model" placeholder="例如 deepseek-v4-flash、gpt-4o-mini" />
        </el-form-item>
        <el-form-item label="固定提示词">
          <el-input v-model="aiConfig.prompt" type="textarea" :rows="5" placeholder="留空使用系统默认提示词" />
        </el-form-item>
        <el-form-item label="最大重试次数">
          <el-input-number v-model="aiConfig.maxAttempts" :min="1" :max="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aiDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="aiSaving" @click="saveAiConfig">保存配置</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="imageAiDialogVisible" title="图像 AI 配置" width="650px" :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="使用兼容 POST /images/generations 的图像服务。图像生成与文案生成使用独立模型和 API Key。"
        class="ai-tip"
      />
      <el-form :model="imageAiConfig" label-width="125px" class="ai-form">
        <el-form-item label="供应商">
          <el-select v-model="imageAiConfig.provider" style="width: 100%">
            <el-option label="OpenAI" value="openai" />
            <el-option label="其他兼容供应商" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="imageAiConfig.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" required>
          <el-input v-model="imageAiConfig.apiKey" type="password" show-password placeholder="输入新 Key；****** 表示保持原 Key" />
        </el-form-item>
        <el-form-item label="图像模型" required>
          <el-input v-model="imageAiConfig.model" placeholder="例如 gpt-image-2" />
        </el-form-item>
        <el-form-item label="生成质量">
          <el-select v-model="imageAiConfig.quality" style="width: 100%">
            <el-option label="低（更快）" value="low" />
            <el-option label="中（推荐）" value="medium" />
            <el-option label="高" value="high" />
            <el-option label="自动" value="auto" />
          </el-select>
        </el-form-item>
        <el-form-item label="统一视觉提示词">
          <el-input v-model="imageAiConfig.stylePrompt" type="textarea" :rows="6" placeholder="定义所有站点素材共用的视觉风格和限制" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="imageAiDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="imageAiSaving" @click="saveImageAiConfig">保存配置</el-button>
      </template>
    </el-dialog>

    <NewSiteAssetsDialog
      v-model="assetsDialogVisible"
      :site="activeAssetSite"
      :can-manage="canManageAssets"
    />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Check, Delete, Loading, Picture } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import NewSiteAssetsDialog from './NewSiteAssetsDialog.vue'
import {
  createNewSites,
  deleteNewSite,
  getNewSiteAiConfig,
  getNewSiteImageAiConfig,
  getNewSiteOptions,
  listNewSites,
  updateNewSiteAiConfig,
  updateNewSiteImageAiConfig,
  updateNewSiteStatus,
} from '@/api/newSite'

const statusOptions = [
  { value: 'pending_review', label: '待审核', tone: 'pending' },
  { value: 'enabled', label: '启用', tone: 'enabled' },
  { value: 'disabled', label: '停用', tone: 'disabled' },
]
const aiProviders = [
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'openai', label: 'OpenAI' },
  { value: 'qwen', label: '通义千问（兼容接口）' },
  { value: 'custom', label: '其他 OpenAI 兼容供应商' },
]

const rows = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')
const status = ref('')
const loading = ref(false)
const creating = ref(false)
const statusUpdating = reactive(new Set())
const deleting = reactive(new Set())
const dialogVisible = ref(false)
const batchMode = ref(false)
const draftRows = ref([])
const aiDialogVisible = ref(false)
const aiSaving = ref(false)
const aiConfig = reactive({
  provider: 'deepseek',
  baseUrl: 'https://api.deepseek.com',
  apiKey: '',
  model: 'deepseek-v4-flash',
  prompt: '',
  maxAttempts: 5,
})
const imageAiDialogVisible = ref(false)
const imageAiSaving = ref(false)
const imageAiConfig = reactive({
  provider: 'openai',
  baseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  model: 'gpt-image-2',
  quality: 'medium',
  stylePrompt: '',
})
const assetsDialogVisible = ref(false)
const activeAssetSite = ref(null)
const userStore = useUserStore()
const canConfigureAi = computed(() => userStore.hasPermission('newsite:config'))
const canManageAssets = computed(() => userStore.hasPermission('newsite:asset'))
const canUpdateStatus = computed(() => userStore.hasPermission('newsite:status'))
const canDelete = computed(() => userStore.hasPermission('newsite:delete'))
const productCategories = ref([])
const sourceDomains = ref([])
let draftKey = 0

function blankDraft() {
  return {
    key: ++draftKey,
    customCategory: '',
    mainProductCategories: [],
    supplementProductCategories: [],
    sourceDomains: [],
  }
}

function openCreate(batch) {
  batchMode.value = batch
  draftRows.value = [blankDraft()]
  dialogVisible.value = true
}

async function openAiConfig() {
  const res = await getNewSiteAiConfig()
  Object.assign(aiConfig, res.data || {})
  aiConfig.maxAttempts = Number(res.data?.maxAttempts || 5)
  aiDialogVisible.value = true
}

async function saveAiConfig() {
  if (!aiConfig.baseUrl.trim() || !aiConfig.apiKey.trim() || !aiConfig.model.trim()) {
    ElMessage.warning('请填写 Base URL、API Key 和模型名称')
    return
  }
  aiSaving.value = true
  try {
    const res = await updateNewSiteAiConfig({
      provider: aiConfig.provider,
      baseUrl: aiConfig.baseUrl.trim(),
      apiKey: aiConfig.apiKey.trim(),
      model: aiConfig.model.trim(),
      prompt: aiConfig.prompt.trim(),
      maxAttempts: aiConfig.maxAttempts,
    })
    Object.assign(aiConfig, res.data || {})
    aiConfig.maxAttempts = Number(res.data?.maxAttempts || aiConfig.maxAttempts)
    ElMessage.success('AI 配置已保存')
    aiDialogVisible.value = false
  } finally {
    aiSaving.value = false
  }
}

async function openImageAiConfig() {
  const res = await getNewSiteImageAiConfig()
  Object.assign(imageAiConfig, res.data || {})
  imageAiDialogVisible.value = true
}

async function saveImageAiConfig() {
  if (!imageAiConfig.baseUrl.trim() || !imageAiConfig.apiKey.trim() || !imageAiConfig.model.trim()) {
    ElMessage.warning('请填写图像 AI Base URL、API Key 和模型名称')
    return
  }
  imageAiSaving.value = true
  try {
    const res = await updateNewSiteImageAiConfig({
      provider: imageAiConfig.provider,
      baseUrl: imageAiConfig.baseUrl.trim(),
      apiKey: imageAiConfig.apiKey.trim(),
      model: imageAiConfig.model.trim(),
      quality: imageAiConfig.quality,
      stylePrompt: imageAiConfig.stylePrompt.trim(),
    })
    Object.assign(imageAiConfig, res.data || {})
    ElMessage.success('图像 AI 配置已保存')
    imageAiDialogVisible.value = false
  } finally {
    imageAiSaving.value = false
  }
}

function openAssets(row) {
  activeAssetSite.value = row
  assetsDialogVisible.value = true
}

function addRow() {
  draftRows.value.push(blankDraft())
}

function removeRow(index) {
  draftRows.value.splice(index, 1)
}

function formatSources(value) {
  if (Array.isArray(value)) return value.join('、')
  try {
    const parsed = JSON.parse(value || '[]')
    return Array.isArray(parsed) ? parsed.join('、') : String(value || '')
  } catch {
    return String(value || '')
  }
}

function formatCategories(value, fallback = '') {
  if (Array.isArray(value)) return value.join('、')
  try {
    const parsed = JSON.parse(value || '[]')
    if (Array.isArray(parsed)) return parsed.join('、')
  } catch {
    // Keep the legacy single-category display value below.
  }
  return fallback || String(value || '')
}

function normalizedCategories(value) {
  return (Array.isArray(value) ? value : [])
    .map(item => String(item).trim())
    .filter(Boolean)
}

function validateDraft() {
  const batchSupplementKeys = new Set()
  for (const [index, item] of draftRows.value.entries()) {
    const mainCategories = normalizedCategories(item.mainProductCategories)
    const supplementCategories = normalizedCategories(item.supplementProductCategories)
    if (!item.customCategory.trim() || !mainCategories.length || !supplementCategories.length) {
      ElMessage.warning(`请完整填写站点 ${index + 1} 的分类信息`)
      return false
    }
    if (!item.sourceDomains.length) {
      ElMessage.warning(`请为站点 ${index + 1} 至少选择一个源站点`)
      return false
    }
    const rowKeys = new Set()
    for (const category of supplementCategories) {
      const key = category.toLowerCase()
      if (rowKeys.has(key) || batchSupplementKeys.has(key)) {
        ElMessage.warning(`本批次副产品分类重复：${category}`)
        return false
      }
      rowKeys.add(key)
      batchSupplementKeys.add(key)
    }
  }
  return true
}

async function submitCreate() {
  if (!validateDraft()) return
  creating.value = true
  try {
    await createNewSites(draftRows.value.map(item => ({
      customCategory: item.customCategory.trim(),
      mainProductCategories: normalizedCategories(item.mainProductCategories),
      supplementProductCategories: normalizedCategories(item.supplementProductCategories),
      sourceDomains: [...new Set(item.sourceDomains.map(value => String(value).trim()).filter(Boolean))],
    })))
    ElMessage.success(batchMode.value ? '批量新站点创建成功' : '新站点创建成功')
    dialogVisible.value = false
    page.value = 1
    await fetchList()
  } catch (error) {
    if (error?.code === 'ECONNABORTED' || error?.message?.includes('timeout')) {
      ElMessage.warning('站点生成耗时较长，请先刷新列表确认结果后再决定是否重试，避免重复创建')
    }
  } finally {
    creating.value = false
  }
}

function statusMeta(value) {
  return statusOptions.find(item => item.value === value) || { label: '未知状态', tone: 'disabled' }
}

function isRowBusy(id) {
  return statusUpdating.has(id) || deleting.has(id)
}

async function handleStatusChange(row, value) {
  if (!canUpdateStatus.value || value === row.status || isRowBusy(row.id)) return
  statusUpdating.add(row.id)
  try {
    const res = await updateNewSiteStatus(row.id, value)
    row.status = res.data?.status || value
    ElMessage.success('站点状态已更新')
    if (status.value && row.status !== status.value) await fetchList()
  } catch {
    // The request interceptor reports errors; keep the last confirmed status.
  } finally {
    statusUpdating.delete(row.id)
  }
}

async function handleDelete(row) {
  if (!canDelete.value || isRowBusy(row.id)) return
  deleting.add(row.id)
  try {
    await ElMessageBox.confirm(
      `确定删除新站点「${row.domain}」吗？将同时删除该站点的品牌素材，不影响源站点和已采集商品。删除后无法恢复。`,
      '删除新站点',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消',
        confirmButtonType: 'danger', autofocus: false, closeOnClickModal: false },
    )
    await deleteNewSite(row.id)
    ElMessage.success('新站点已删除')
    await fetchList()
  } catch {
    // Cancellation leaves the record intact; API errors are shown by the interceptor.
  } finally {
    deleting.delete(row.id)
  }
}

let listRequestId = 0
async function fetchList() {
  const requestId = ++listRequestId
  loading.value = true
  try {
    const res = await listNewSites({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
    })
    if (requestId !== listRequestId) return
    total.value = Number(res.data?.total || 0)
    const lastPage = Math.max(1, Math.ceil(total.value / size.value))
    if (page.value > lastPage) {
      page.value = lastPage
      return await fetchList()
    }
    rows.value = res.data?.records || []
  } finally {
    if (requestId === listRequestId) loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  return fetchList()
}

async function fetchOptions() {
  const res = await getNewSiteOptions()
  productCategories.value = res.data?.productCategories || []
  sourceDomains.value = res.data?.sourceDomains || []
}

function resetFilters() {
  keyword.value = ''
  status.value = ''
  page.value = 1
  fetchList()
}

function handleSizeChange(value) {
  size.value = value
  page.value = 1
  fetchList()
}

onMounted(async () => {
  await Promise.all([fetchList(), fetchOptions()])
})
</script>

<style scoped>
.page-toolbar,
.toolbar-actions,
.filters,
.create-toolbar,
.row-heading {
  display: flex;
  align-items: center;
}

.page-toolbar,
.create-toolbar,
.row-heading {
  justify-content: space-between;
}

.toolbar-title {
  font-size: 16px;
  font-weight: 700;
}

.toolbar-hint,
.batch-tip {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.filters {
  gap: 10px;
  margin-bottom: 16px;
}

.source-list {
  line-height: 1.6;
  white-space: normal;
}

.status-pending {
  --status-color: var(--el-color-warning-dark-2);
  --status-bg: var(--el-color-warning-light-9);
  --status-border: var(--el-color-warning-light-7);
}

.status-enabled {
  --status-color: var(--el-color-success-dark-2);
  --status-bg: var(--el-color-success-light-9);
  --status-border: var(--el-color-success-light-7);
}

.status-disabled {
  --status-color: var(--el-text-color-secondary);
  --status-bg: var(--el-fill-color-light);
  --status-border: var(--el-border-color-light);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-width: 94px;
  padding: 5px 11px;
  border: 1px solid var(--status-border);
  border-radius: 999px;
  background: var(--status-bg);
  color: var(--status-color);
  font-family: inherit;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  white-space: nowrap;
  box-sizing: border-box;
}

.status-trigger {
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.status-trigger:hover:not(:disabled) {
  border-color: var(--status-color);
}

.status-trigger:focus-visible {
  outline: 2px solid var(--status-color);
  outline-offset: 2px;
}

.status-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.status-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--status-color);
}

.status-chevron {
  margin-left: 2px;
  font-size: 10px;
}

.status-menu-option {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 90px;
}

.status-menu-option .el-icon {
  margin-left: auto;
  color: var(--status-color);
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.create-toolbar {
  margin: 18px 0 10px;
}

.ai-tip {
  margin-bottom: 18px;
}

.ai-form {
  margin-top: 18px;
}

.draft-row {
  padding: 14px 16px 4px;
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.row-heading {
  margin-bottom: 8px;
  font-weight: 600;
}

.draft-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 14px;
}

.draft-form :deep(.el-select),
.draft-form :deep(.el-input) {
  width: 100%;
}

@media (max-width: 900px) {
  .page-toolbar,
  .filters {
    align-items: flex-start;
    flex-direction: column;
  }

  .draft-form {
    grid-template-columns: 1fr;
  }
}
</style>
