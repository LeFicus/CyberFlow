<!--
  SiteConfigPage - 站点配置管理页面
  管理待爬取的独立站商品采集配置，支持常见独立站商城引擎。
  功能包括：注册站点、编辑站点、删除站点、立即触发商品爬取任务。
-->
<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <span>站点配置管理</span>
        <el-button type="primary" size="small" @click="handleCreate">注册站点</el-button>
      </div>
    </template>

    <el-alert
      class="engine-tip"
      type="info"
      :closable="false"
      show-icon
      title="Shopify 使用专用商品接口；其他商城引擎暂统一使用 WooCommerce 通用选择器采集。"
    />

    <el-table :data="configs" border stripe v-loading="loading" style="width:100%;">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="domain" label="域名" min-width="200" />
      <el-table-column prop="type" label="商城引擎" width="140">
        <template #default="{ row }">
          <el-tag :type="engineTagType(row.type)" size="small">
            {{ engineLabel(row.type) }}
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
          <el-button link type="primary" size="small" @click="handleTrigger(row)">爬取</el-button>
          <el-button link type="success" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <TaskProgress :task="task" />

    <!-- 注册/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑站点' : '注册新站点'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="100px" size="small">
        <el-form-item label="域名" required>
          <el-input v-model="form.domain" placeholder="example.com 或 https://example.com" />
        </el-form-item>
        <el-form-item label="商城引擎" required>
          <el-select v-model="form.type" style="width:240px;" filterable>
            <el-option v-for="engine in ENGINES" :key="engine.value" :label="engine.label" :value="engine.value" />
          </el-select>
          <span class="selector-hint">{{ form.type === 'shopify' ? '使用 Shopify 专用采集器' : '复用 WooCommerce 选择器' }}</span>
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" style="width:200px;" filterable>
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ isEditing ? '保存' : '注册' }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
/**
 * @fileoverview 站点配置管理页面
 * @description 管理商品爬取站点的注册、编辑、删除和任务下发。
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
} from '@/api/selector'
import TaskProgress from '@/components/TaskProgress.vue'
import { useTaskProgress } from '@/composables/useTaskProgress'

/** @type {string[]} 商品分类常量列表 */
const CATEGORIES = [
  '五金/硬件', '交通工具/汽车/飞机/船舶', '体育用品', '保健/美容/卫生/护理',
  '办公用品', '动物/宠物用品', '商业/工业', '婴幼儿用品', '媒体', '家具',
  '家居与园艺', '成人', '服饰与配饰', '玩具/游戏', '电子产品', '箱包',
  '艺术与娱乐', '饮食/烟酒', '软件', '相机与光学器件', '宗教/仪式', '未知分类',
]
const ENGINES = [
  { label: 'Shopify', value: 'shopify' },
  { label: 'WooCommerce', value: 'woocommerce' },
  { label: 'BigCommerce', value: 'bigcommerce' },
  { label: 'OpenCart', value: 'opencart' },
  { label: 'Magento / Adobe Commerce', value: 'magento' },
  { label: 'PrestaShop', value: 'prestashop' },
  { label: 'SHOPLINE', value: 'shopline' },
  { label: 'Ecwid', value: 'ecwid' },
  { label: 'Wix Stores', value: 'wix' },
  { label: 'Squarespace Commerce', value: 'squarespace' },
  { label: '其他 / 自建商城', value: 'custom' },
]

/** @type {import('vue').Ref<Array>} 站点配置列表 */
const configs = ref([])
/** @type {import('vue').Ref<boolean>} 列表加载状态 */
const loading = ref(false)
/** @type {import('vue').Ref<boolean>} 保存按钮加载状态 */
const saving = ref(false)
/** @type {import('vue').Ref<boolean>} 配置弹窗是否可见 */
const dialogVisible = ref(false)
/** @type {import('vue').Ref<boolean>} 是否为编辑模式 */
const isEditing = ref(false)
/** @type {import('vue').Ref<number|null>} 当前编辑的站点 ID */
const editingId = ref(null)
/** 编辑时保留站点当前绑定的选择器模板。 */
const editingMappings = ref([])
/** @type {import('vue').Ref<string[]>} 商品分类选项（用于 el-select） */
const categories = ref(CATEGORIES)
const { task, track } = useTaskProgress()

/** 站点配置表单数据 */
const form = reactive({
  domain: '',
  type: 'shopify',
  category: '未知分类',
})

/**
 * 重置表单数据到初始值
 */
function resetForm() {
  form.domain = ''
  form.type = 'shopify'
  form.category = '未知分类'
  editingId.value = null
  editingMappings.value = []
}

/**
 * 获取站点配置列表
 */
async function fetchList() {
  loading.value = true
  try {
    const configsRes = await listSiteConfigs()
    configs.value = configsRes.data || []
  } finally {
    loading.value = false
  }
}

/** 打开注册新站点弹窗 */
function handleCreate() {
  isEditing.value = false
  resetForm()
  dialogVisible.value = true
}

/**
 * 打开编辑站点弹窗
 * 从后端获取站点完整配置（含模板映射）填充到表单
 * @param {Object} row - 站点行数据
 */
async function handleEdit(row) {
  isEditing.value = true
  editingId.value = row.id
  try {
    const res = await getSiteConfig(row.id)
    const data = res.data
    Object.assign(form, {
      domain: data.config?.domain || row.domain,
      type: data.config?.type || row.type,
      category: data.config?.category || row.category,
    })
    editingMappings.value = (data.mappings || []).map(mapping => ({
      template_id: mapping.templateId,
      extra_selectors: mapping.extraSelectors,
    }))
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
  const domain = form.domain.trim()
  if (!domain) {
    ElMessage.warning('请输入站点域名')
    return
  }
  saving.value = true
  try {
    const payload = {
      config: {
        domain,
        type: form.type,
        category: form.category,
      },
      mappings: isEditing.value ? editingMappings.value : [],
    }
    if (isEditing.value) {
      await updateSiteConfig(editingId.value, payload)
    } else {
      await createSiteConfig(payload)
    }
    ElMessage.success(isEditing.value ? '更新成功' : '注册成功')
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
    const res = await triggerSiteCrawl(row.id, 1)
    track(res.data.task_id)
    ElMessage.success('爬取任务已下发')
  } catch {
    // 用户取消或操作失败
  }
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
