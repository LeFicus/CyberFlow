<!--
  SelectorTemplatePage - 选择器模板管理页面
  管理商品信息爬取用的 CSS/XPath 选择器模板，支持按平台类型筛选。
  功能包括：新建、编辑、克隆、删除模板。
  系统内置模板（isSystem=1）不可删除但可克隆，防止误删基础配置。
-->
<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <span>选择器模板库</span>
        <el-button type="primary" size="small" @click="handleCreate">新建模板</el-button>
      </div>
    </template>

    <el-alert
      style="margin-bottom:12px;"
      type="info"
      :closable="false"
      title="除 Shopify 外，BigCommerce、OpenCart、Magento 等商城引擎暂统一复用 WooCommerce 选择器模板。"
      show-icon
    />

    <!-- 平台筛选 -->
    <el-form inline style="margin-bottom:12px;">
      <el-form-item label="平台">
        <el-select v-model="filterPlatform" clearable placeholder="全部" @change="fetchList" style="width:160px;">
          <el-option label="Shopify" value="shopify" />
          <el-option label="WooCommerce（非 Shopify 通用）" value="woocommerce" />
        </el-select>
      </el-form-item>
    </el-form>

    <!-- 模板列表 -->
    <el-table :data="templates" border stripe v-loading="loading" style="width:100%;">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="模板名" min-width="160" />
      <el-table-column prop="platform" label="平台" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.platform }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="titleSelector" label="标题选择器" min-width="200" show-overflow-tooltip />
      <el-table-column prop="priceSelector" label="价格选择器" min-width="200" show-overflow-tooltip />
      <el-table-column prop="currency" label="币种" width="80" />
      <el-table-column label="系统" width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isSystem === 1" type="info" size="small">系统</el-tag>
          <el-tag v-else type="success" size="small">自定义</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="success" size="small" @click="handleClone(row)">克隆</el-button>
          <el-button link type="danger" size="small" :disabled="row.isSystem === 1" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑/新建弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑模板' : '新建模板'"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="140px" size="small">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="模板名" required>
              <el-input v-model="form.name" placeholder="如 Shopify Default" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="平台" required>
              <el-select v-model="form.platform" style="width:100%;">
                <el-option label="Shopify" value="shopify" />
                <el-option label="WooCommerce（非 Shopify 通用）" value="woocommerce" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="币种">
          <el-input v-model="form.currency" placeholder="USD" style="width:120px;" />
        </el-form-item>
        <el-form-item label="标题选择器">
          <el-input v-model="form.titleSelector" placeholder="//h1/text() | //h1[@class='product_title']/text()" />
        </el-form-item>
        <el-form-item label="价格选择器">
          <el-input v-model="form.priceSelector" placeholder="//p[@class='price']//bdi/text() | //meta[@itemprop='price']/@content" />
        </el-form-item>
        <el-form-item label="价格正则">
          <el-input v-model="form.priceRegex" placeholder="[\d.,]+" />
        </el-form-item>
        <el-form-item label="描述选择器">
          <el-input v-model="form.descriptionSelector" placeholder="//div[contains(@class, 'description')]//text()" />
        </el-form-item>
        <el-form-item label="图片选择器">
          <el-input v-model="form.imagesSelector" placeholder="//div[@class='gallery']//img/@src | //meta[@property='og:image']/@content" />
        </el-form-item>
        <el-form-item label="面包屑链接选择器">
          <el-input v-model="form.breadcrumbLinksSelector" placeholder="//nav[contains(@class, 'breadcrumb')]//a/text()" />
        </el-form-item>
        <el-form-item label="面包屑末级选择器">
          <el-input v-model="form.breadcrumbLastSelector" placeholder="//nav[contains(@class, 'breadcrumb')]//span/text()" />
        </el-form-item>
        <el-form-item label="Sitemap 选择器">
          <el-input v-model="form.siteMapSelector" placeholder="//*[local-name()='sitemap']/*[local-name()='loc'][contains(text(), 'product')]/text()" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ isEditing ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listTemplates, createTemplate, updateTemplate, deleteTemplate, cloneTemplate } from '@/api/selector'

/** 模板列表数据 */
const templates = ref([])
/** 列表加载状态 */
const loading = ref(false)
/** 保存按钮 loading */
const saving = ref(false)
/** 编辑弹窗是否可见 */
const dialogVisible = ref(false)
/** 是否为编辑模式 */
const isEditing = ref(false)
/** 平台筛选值 */
const filterPlatform = ref('')

/** 模板表单数据 */
const form = reactive({
  id: null,
  name: '',
  platform: 'shopify',
  currency: 'USD',
  titleSelector: '',
  priceSelector: '',
  priceRegex: '[\\d.,]+',
  descriptionSelector: '',
  imagesSelector: '',
  breadcrumbLinksSelector: '',
  breadcrumbLastSelector: '',
  siteMapSelector: '',
})

/**
 * 重置表单数据到初始值
 */
function resetForm() {
  form.id = null
  form.name = ''
  form.platform = 'shopify'
  form.currency = 'USD'
  form.titleSelector = ''
  form.priceSelector = ''
  form.priceRegex = '[\\d.,]+'
  form.descriptionSelector = ''
  form.imagesSelector = ''
  form.breadcrumbLinksSelector = ''
  form.breadcrumbLastSelector = ''
  form.siteMapSelector = ''
}

/**
 * 获取模板列表（可按平台筛选）
 */
async function fetchList() {
  loading.value = true
  try {
    const res = await listTemplates(filterPlatform.value || undefined)
    templates.value = res.data || []
  } finally {
    loading.value = false
  }
}

/**
 * 打开新建模板弹窗
 */
function handleCreate() {
  isEditing.value = false
  resetForm()
  dialogVisible.value = true
}

/**
 * 打开编辑模板弹窗，将行数据复制到表单
 * @param {Object} row - 模板行数据
 */
function handleEdit(row) {
  isEditing.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

/**
 * 克隆模板
 * @param {Object} row - 源模板行数据
 */
async function handleClone(row) {
  try {
    await cloneTemplate(row.id)
    ElMessage.success('克隆成功')
    fetchList()
  } catch {
    ElMessage.error('克隆失败')
  }
}

/**
 * 保存模板（新建或更新）
 */
async function handleSave() {
  saving.value = true
  try {
    if (isEditing.value) {
      await updateTemplate(form.id, { ...form })
      ElMessage.success('更新成功')
    } else {
      await createTemplate({ ...form })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

/**
 * 删除模板（需二次确认，系统模板不可删除）
 * @param {Object} row - 模板行数据
 */
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除模板「${row.name}」？`, '确认删除', { type: 'warning' })
    await deleteTemplate(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // cancelled
  }
}

onMounted(fetchList)
</script>
