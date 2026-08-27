<!--
  SiteConfigPage - 数据源站点管理页面
  管理待爬取的独立站商品数据源，支持常见独立站商城引擎。
  功能包括：批量添加、编辑、删除、单个运行和批量运行数据源。
-->
<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <span>数据源站点</span>
        <div>
          <el-button type="primary" size="small" @click="handleCreate">添加数据源</el-button>
          <el-button type="success" size="small" @click="handleBatchCreate">批量添加</el-button>
          <el-button
            type="warning"
            size="small"
            :disabled="selectedRows.length === 0"
            :loading="batchRunning"
            @click="handleBatchTrigger"
          >批量运行<span v-if="selectedRows.length">（{{ selectedRows.length }}）</span></el-button>
        </div>
      </div>
    </template>

    <el-alert
      class="engine-tip"
      type="info"
      :closable="false"
      show-icon
      title="支持 Shopify、WooCommerce、BigCommerce、Magento、Wix、Ecwid、SHOPLINE。各平台独立接入；动态站点或高变体商品可能需要公开 API 授权配置。"
    />

    <el-collapse class="engine-tip">
      <el-collapse-item title="本次采集选项（应用于单个运行和批量运行，不修改站点默认配置）" name="crawl-options">
        <el-form :model="crawlOptions" label-width="150px" size="small">
          <el-form-item label="商品价格上限 USD">
            <el-checkbox v-model="crawlOptions.limitPrice">启用上限</el-checkbox>
            <el-input-number v-model="crawlOptions.maximum" :min="0.01" :precision="2" :disabled="!crawlOptions.limitPrice" style="margin-left:16px" />
            <span class="selector-hint">默认不限制；按换算后的美元价格筛选</span>
          </el-form-item>
          <el-form-item label="必须有商品描述"><el-switch v-model="crawlOptions.requireDescription" /></el-form-item>
          <el-form-item label="必须有商品图片"><el-switch v-model="crawlOptions.requireImage" /></el-form-item>
          <el-form-item label="缺失币种时使用">
            <el-select v-model="crawlOptions.currency" style="width:220px">
              <el-option label="自动识别 / 站点配置" value="" />
              <el-option v-for="code in ['AUD', 'USD', 'CAD', 'GBP', 'EUR', 'NZD']" :key="code" :label="code" :value="code" />
            </el-select>
            <span class="selector-hint">页面与商品数据明确的币种优先；无法识别时会报告失败</span>
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>

    <el-table :data="configs" border stripe v-loading="loading" style="width:100%;" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="52" align="center" />
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="domain" label="域名" min-width="200" />
      <el-table-column prop="type" label="商城引擎" width="140">
        <template #default="{ row }">
          <el-tag :type="engineTagType(row.type)" size="small">
            {{ engineLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="产品标签" width="110">
        <template #default="{ row }">
          <el-tag :type="row.productRole === 'supplement' ? 'warning' : 'success'" size="small">
            {{ row.productRole === 'supplement' ? '补充产品' : '主产品' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="120" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
            {{ row.status === 'active' ? '启用' : '暂停' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleTrigger(row)">运行</el-button>
          <el-button link type="success" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 16px; justify-content: flex-end;"
      v-model:current-page="page"
      :page-size="size"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @current-change="fetchList"
      @size-change="handleSizeChange"
    />
    <TaskProgress :task="task" />

    <!-- 注册/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑数据源' : (batchMode ? '批量添加数据源' : '添加数据源')"
      width="680px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="100px" size="small">
        <el-form-item label="域名" required>
          <el-input
            v-if="batchMode"
            v-model="form.domain"
            type="textarea"
            :rows="7"
            placeholder="每行一个域名，也支持逗号分隔"
          />
          <el-input v-else v-model="form.domain" placeholder="example.com 或 https://example.com" />
          <div v-if="batchMode" class="selector-hint">将按当前商城引擎和类目批量创建数据源站点</div>
        </el-form-item>
        <el-form-item label="商城引擎" required>
          <el-select v-model="form.type" style="width:240px;" filterable @change="handleEngineChange">
            <el-option v-for="engine in ENGINES" :key="engine.value" :label="engine.label" :value="engine.value" />
          </el-select>
          <span class="selector-hint">{{ form.type === 'shopify' ? '使用 Shopify 专用采集器' : '复用 WooCommerce 选择器' }}</span>
        </el-form-item>
        <el-form-item label="产品标签" required>
          <el-radio-group v-model="form.productRole">
            <el-radio-button label="main">主产品</el-radio-button>
            <el-radio-button label="supplement">补充产品</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分类" required>
          <el-tree-select
            v-model="form.category"
            :data="categoryTree"
            :props="categoryTreeProps"
            check-strictly
            filterable
            clearable
            default-expand-all
            style="width:360px;"
            placeholder="选择商品类目"
          />
        </el-form-item>
        <el-form-item label="爬取模板" required>
          <el-select
            v-model="form.templateId"
            filterable
            clearable
            :loading="templateLoading"
            style="width:360px;"
            placeholder="选择商品解析模板"
          >
            <el-option
              v-for="template in templates"
              :key="template.id"
              :label="`${template.name}${template.isSystem === 1 ? '（系统）' : ''}`"
              :value="template.id"
            />
          </el-select>
          <span class="selector-hint">
            {{ form.type === 'shopify' ? 'Shopify 使用商品接口，模板仅作配置记录' : '该模板会用于本次商品页面解析' }}
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ isEditing ? '保存' : (batchMode ? '批量添加' : '添加') }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
/**
 * @fileoverview 站点配置管理页面
 * @description 管理商品数据源的添加、编辑、删除和任务下发。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listSiteConfigs,
  getSiteConfig,
  createSiteConfig,
  updateSiteConfig,
  triggerSiteCrawl,
  deleteSiteConfig,
  listTemplates,
} from '@/api/selector'
import TaskProgress from '@/components/TaskProgress.vue'
import { useTaskProgress } from '@/composables/useTaskProgress'
import { useUserStore } from '@/store/user'
import {
  PRODUCT_CATEGORIES,
  PRODUCT_CATEGORY_TREE,
  productCategoryLabel,
  resolveProductCategoryPath,
} from '@/data/productCategories'

/** @type {string[]} 商品分类常量列表 */
const ENGINES = [
  { label: 'Shopify', value: 'shopify' },
  { label: 'WooCommerce', value: 'woocommerce' },
  { label: 'BigCommerce', value: 'bigcommerce' },
  { label: 'Magento / Adobe Commerce', value: 'magento' },
  { label: 'SHOPLINE', value: 'shopline' },
  { label: 'Ecwid', value: 'ecwid' },
  { label: 'Wix Stores', value: 'wix' },
]

/** @type {import('vue').Ref<Array>} 站点配置列表 */
const configs = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
/** @type {import('vue').Ref<boolean>} 列表加载状态 */
const loading = ref(false)
/** @type {import('vue').Ref<boolean>} 保存按钮加载状态 */
const saving = ref(false)
const batchRunning = ref(false)
const crawlOptions = reactive({ limitPrice: false, maximum: 130, requireDescription: false, requireImage: true, currency: '' })

function taskCrawlOptions() {
  return {
    max_product_price_usd: crawlOptions.limitPrice ? crawlOptions.maximum : null,
    require_description: crawlOptions.requireDescription,
    require_image: crawlOptions.requireImage,
    currency: crawlOptions.currency,
  }
}
const selectedRows = ref([])
/** @type {import('vue').Ref<boolean>} 配置弹窗是否可见 */
const dialogVisible = ref(false)
/** @type {import('vue').Ref<boolean>} 是否为编辑模式 */
const isEditing = ref(false)
const batchMode = ref(false)
/** @type {import('vue').Ref<number|null>} 当前编辑的站点 ID */
const editingId = ref(null)
/** 编辑时保留站点当前绑定的选择器模板。 */
const editingMappings = ref([])
/** 当前商城引擎可用的选择器模板。 */
const templates = ref([])
const templateLoading = ref(false)
/** 商品分类树，值为完整的 `一级|||二级` 路径。 */
const categoryTree = PRODUCT_CATEGORY_TREE
const categoryTreeProps = { value: 'value', label: 'label', children: 'children' }
const userStore = useUserStore()
const { task, track } = useTaskProgress()

/** 站点配置表单数据 */
const form = reactive({
  domain: '',
  type: 'shopify',
  productRole: 'main',
  category: PRODUCT_CATEGORIES[0],
  templateId: null,
})

/**
 * 重置表单数据到初始值
 */
function resetForm() {
  form.domain = ''
  form.type = 'shopify'
  form.productRole = 'main'
  form.category = PRODUCT_CATEGORIES[0]
  form.templateId = null
  editingId.value = null
  editingMappings.value = []
}

/**
 * 加载当前商城引擎可用的模板。
 * 非 Shopify 引擎统一使用 WooCommerce 选择器模板；Shopify 模板仅作配置记录。
 */
async function fetchTemplates() {
  templateLoading.value = true
  try {
    const platform = form.type === 'shopify' ? 'shopify' : 'woocommerce'
    const res = await listTemplates({ platform, page: 1, size: 100 })
    templates.value = res.data?.records || []
    if (!templates.value.some(template => Number(template.id) === Number(form.templateId))) {
      form.templateId = templates.value[0]?.id || null
    }
  } catch {
    templates.value = []
    form.templateId = null
    ElMessage.error('加载爬取模板失败')
  } finally {
    templateLoading.value = false
  }
}

/**
 * 获取站点配置列表
 */
async function fetchList() {
  loading.value = true
  try {
    const configsRes = await listSiteConfigs({ page: page.value, size: size.value })
    configs.value = configsRes.data?.records || []
    total.value = Number(configsRes.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function handleSizeChange(value) {
  size.value = value
  page.value = 1
  fetchList()
}

/** 打开注册新站点弹窗 */
function handleCreate() {
  isEditing.value = false
  batchMode.value = false
  resetForm()
  fetchTemplates()
  dialogVisible.value = true
}

function handleBatchCreate() {
  isEditing.value = false
  batchMode.value = true
  resetForm()
  fetchTemplates()
  dialogVisible.value = true
}

/** 切换商城引擎时刷新可选模板，避免把其他平台模板提交给爬虫。 */
async function handleEngineChange() {
  form.templateId = null
  await fetchTemplates()
}

/**
 * 打开编辑站点弹窗
 * 从后端获取站点完整配置（含模板映射）填充到表单
 * @param {Object} row - 站点行数据
 */
async function handleEdit(row) {
  isEditing.value = true
  batchMode.value = false
  editingId.value = row.id
  try {
    const res = await getSiteConfig(row.id)
    const data = res.data
    Object.assign(form, {
      domain: data.config?.domain || row.domain,
      type: data.config?.type || row.type,
      productRole: data.config?.productRole || row.productRole || 'main',
      category: resolveProductCategoryPath(data.config?.category || row.category),
    })
    editingMappings.value = (data.mappings || []).map(mapping => ({
      template_id: mapping.templateId,
      extra_selectors: mapping.extraSelectors,
    }))
    await fetchTemplates()
    form.templateId = editingMappings.value[0]?.template_id || templates.value[0]?.id || null
    dialogVisible.value = true
  } catch {
    ElMessage.error('获取站点详情失败')
  }
}

/**
 * 保存站点配置（新建或更新）
 * 将表单数据序列化为 config + mappings 的 payload 结构
 */
async function handleSave() {
  const domains = batchMode.value
    ? [...new Set(form.domain.split(/[\n,，;；]+/).map(item => item.trim()).filter(Boolean))]
    : [form.domain.trim()].filter(Boolean)
  if (!domains.length) {
    ElMessage.warning(batchMode.value ? '请输入至少一个数据源域名' : '请输入站点域名')
    return
  }
  if (!form.category) {
    ElMessage.warning('请选择商品类目')
    return
  }
  saving.value = true
  try {
    const category = productCategoryLabel(normalizeCategoryValue(form.category))
    if (!form.templateId && form.type !== 'shopify') {
      ElMessage.warning('请选择商品爬取模板')
      return
    }
    const selectedMapping = editingMappings.value.find(
      mapping => Number(mapping.template_id) === Number(form.templateId),
    )
    const buildPayload = domain => ({
      config: { domain, type: form.type, productRole: form.productRole, category },
      mappings: form.templateId ? [{
        template_id: Number(form.templateId),
        extra_selectors: selectedMapping?.extra_selectors || null,
      }] : [],
    })
    if (isEditing.value) {
      await updateSiteConfig(editingId.value, buildPayload(domains[0]))
      ElMessage.success('更新成功')
    } else {
      const results = await Promise.allSettled(domains.map(domain => createSiteConfig(buildPayload(domain))))
      const successCount = results.filter(result => result.status === 'fulfilled').length
      const failedCount = results.length - successCount
      ElMessage[failedCount ? 'warning' : 'success'](
        failedCount ? `成功添加 ${successCount} 个，失败 ${failedCount} 个` : `成功添加 ${successCount} 个数据源`
      )
    }
    dialogVisible.value = false
    await fetchList()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

/**
 * 下发商品爬取任务
 * 弹出二次确认后调用 API 触发站点的商品爬取
 * @param {Object} row - 站点行数据
 */
async function handleTrigger(row) {
  try {
    await ElMessageBox.confirm(`确定立即爬取「${row.domain}」？`, '确认爬取', { type: 'info' })
    const res = await triggerSiteCrawl(row.id, userStore.userInfo?.id || 0, taskCrawlOptions())
    track(res.data.task_id)
    ElMessage.success('爬取任务已下发')
  } catch {
    // 用户取消或操作失败
  }
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function handleBatchTrigger() {
  if (!selectedRows.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定批量运行选中的 ${selectedRows.value.length} 个数据源？`,
      '批量运行数据源',
      { type: 'info', confirmButtonText: '开始运行', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  batchRunning.value = true
  try {
    const results = await Promise.allSettled(
      selectedRows.value.map(row => triggerSiteCrawl(row.id, userStore.userInfo?.id || 0, taskCrawlOptions()))
    )
    let successCount = 0
    let lastTaskId = ''
    for (const result of results) {
      if (result.status !== 'fulfilled') continue
      successCount += 1
      if (result.value.data?.task_id) lastTaskId = result.value.data.task_id
    }
    if (lastTaskId) track(lastTaskId)
    const failedCount = results.length - successCount
    ElMessage[failedCount ? 'warning' : 'success'](
      failedCount ? `已启动 ${successCount} 个，失败 ${failedCount} 个` : `已启动 ${successCount} 个数据源任务`
    )
  } finally {
    batchRunning.value = false
  }
}

function normalizeCategoryValue(value) {
  if (value && typeof value === 'object' && !Array.isArray(value)) return String(value.value || value.label || '')
  return String(value || '').trim()
}

/**
 * 删除站点配置
 * 弹出二次确认后调用 API 删除站点
 * @param {Object} row - 站点行数据
 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除站点「${row.domain}」？`, '确认删除', { type: 'warning' })
    await deleteSiteConfig(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // 用户取消或操作失败
  }
}

onMounted(fetchList)

function engineTagType(type) {
  return { shopify: 'success', woocommerce: 'primary', bigcommerce: 'warning', opencart: 'danger' }[type] || 'info'
}

function engineLabel(type) {
  return ENGINES.find(engine => engine.value === type)?.label || type
}
</script>

<style scoped>
.engine-tip { margin-bottom: 16px; }
.selector-hint { margin-left: 10px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
