<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <span>选择器模板库</span>
        <el-button type="primary" size="small" @click="handleCreate">新建模板</el-button>
      </div>
    </template>

    <!-- 筛选 -->
    <el-form inline style="margin-bottom:12px;">
      <el-form-item label="平台">
        <el-select v-model="filterPlatform" clearable placeholder="全部" @change="fetchList" style="width:160px;">
          <el-option label="WooCommerce" value="woo" />
          <el-option label="Shopify" value="shopify" />
          <el-option label="Magento" value="magento" />
          <el-option label="自定义" value="custom" />
        </el-select>
      </el-form-item>
    </el-form>

    <!-- 列表 -->
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

    <!-- 编辑弹窗 -->
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
              <el-input v-model="form.name" placeholder="如 WooCommerce Default" />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="平台" required>
              <el-select v-model="form.platform" style="width:100%;">
                <el-option label="WooCommerce" value="woo" />
                <el-option label="Shopify" value="shopify" />
                <el-option label="Magento" value="magento" />
                <el-option label="Custom" value="custom" />
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

const templates = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEditing = ref(false)
const filterPlatform = ref('')

const form = reactive({
  id: null,
  name: '',
  platform: 'woo',
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

function resetForm() {
  form.id = null
  form.name = ''
  form.platform = 'woo'
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

async function fetchList() {
  loading.value = true
  try {
    const res = await listTemplates(filterPlatform.value || undefined)
    templates.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEditing.value = false
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row) {
  isEditing.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

async function handleClone(row) {
  try {
    await cloneTemplate(row.id)
    ElMessage.success('克隆成功')
    fetchList()
  } catch {
    ElMessage.error('克隆失败')
  }
}

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
