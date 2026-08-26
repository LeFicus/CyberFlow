<template>
  <el-card class="new-site-page">
    <template #header>
      <div class="page-toolbar">
        <div>
          <span class="toolbar-title">新站点列表</span>
          <span class="toolbar-hint">AI 生成站点标题、标语与可注册域名</span>
        </div>
        <div class="toolbar-actions">
          <el-button v-if="canConfigureAi" @click="openAiConfig">AI 配置</el-button>
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
        @keyup.enter="fetchList"
      />
      <el-select v-model="status" clearable placeholder="全部使用状态" style="width: 170px" @change="fetchList">
        <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" plain @click="fetchList">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
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
      <el-table-column label="站点使用状态" width="150" fixed="right">
        <template #default="{ row }">
          <el-select
            :model-value="row.status"
            size="small"
            :loading="statusUpdating === row.id"
            @change="value => handleStatusChange(row, value)"
          >
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
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
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import {
  createNewSites,
  getNewSiteAiConfig,
  getNewSiteOptions,
  listNewSites,
  updateNewSiteAiConfig,
  updateNewSiteStatus,
} from '@/api/newSite'

const statusOptions = [
  { value: 'pending_review', label: '待审核' },
  { value: 'enabled', label: '启用' },
  { value: 'disabled', label: '停用' },
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
const statusUpdating = ref(null)
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
const userStore = useUserStore()
const canConfigureAi = computed(() => userStore.hasPermission('newsite:config'))
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

async function handleStatusChange(row, value) {
  if (value === row.status) return
  const oldValue = row.status
  statusUpdating.value = row.id
  try {
    const res = await updateNewSiteStatus(row.id, value)
    row.status = res.data?.status || value
  } catch {
    row.status = oldValue
  } finally {
    statusUpdating.value = null
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listNewSites({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      status: status.value || undefined,
    })
    rows.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
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
