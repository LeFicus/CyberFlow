<template>
  <el-card>
    <template #header>
      <div style="display:flex;justify-content:space-between;align-items:center;">
        <span>站点配置管理</span>
        <el-button type="primary" size="small" @click="handleCreate">注册站点</el-button>
      </div>
    </template>

    <el-table :data="configs" border stripe v-loading="loading" style="width:100%;">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="domain" label="域名" min-width="200" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.type === 'shopify' ? 'success' : 'warning'" size="small">
            {{ row.type }}
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
        <el-form-item label="类型" required>
          <el-select v-model="form.type" style="width:200px;" @change="onTypeChange">
            <el-option label="Shopify" value="shopify" />
            <el-option label="WooCommerce" value="woo" />
            <el-option label="Custom" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" style="width:200px;" filterable>
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>

        <!-- 非 Shopify: 选择器模板 -->
        <template v-if="form.type !== 'shopify'">
          <el-divider>选择器模板</el-divider>
          <div v-for="(m, idx) in form.mappings" :key="idx" style="display:flex;gap:8px;margin-bottom:8px;align-items:center;">
            <span style="color:#999;font-size:12px;width:20px;">#{{ idx + 1 }}</span>
            <el-select v-model="m.template_id" placeholder="选择模板" style="flex:1;" filterable>
              <el-option
                v-for="t in availableTemplates"
                :key="t.id"
                :label="t.name"
                :value="t.id"
              />
            </el-select>
            <el-button
              link
              type="primary"
              size="small"
              @click="showExtraSelectors(idx)"
            >额外选择器</el-button>
            <el-button link type="danger" size="small" @click="removeMapping(idx)" :disabled="form.mappings.length <= 1">
              ✕
            </el-button>
          </div>
          <el-button link type="primary" size="small" @click="addMapping">+ 添加模板</el-button>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ isEditing ? '保存' : '注册' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 额外选择器弹窗 -->
    <el-dialog v-model="extraDialogVisible" title="额外选择器 (JSON)" width="500px">
      <el-input
        v-model="currentExtraSelectors"
        type="textarea"
        :rows="8"
        placeholder='{"title": "//h1/text()", "price": "//span/text()"}'
      />
      <template #footer>
        <el-button @click="applyExtraSelectors">确定</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSiteConfigs, getSiteConfig, createSiteConfig, triggerSiteCrawl, deleteSiteConfig } from '@/api/selector'
import { listTemplates } from '@/api/selector'

const CATEGORIES = [
  '五金/硬件', '交通工具/汽车/飞机/船舶', '体育用品', '保健/美容/卫生/护理',
  '办公用品', '动物/宠物用品', '商业/工业', '婴幼儿用品', '媒体', '家具',
  '家居与园艺', '成人', '服饰与配饰', '玩具/游戏', '电子产品', '箱包',
  '艺术与娱乐', '饮食/烟酒', '软件', '相机与光学器件', '宗教/仪式', '未知分类',
]

const configs = ref([])
const availableTemplates = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)
const categories = ref(CATEGORIES)

// Extra selectors dialog
const extraDialogVisible = ref(false)
const currentMappingIdx = ref(-1)
const currentExtraSelectors = ref('')

const form = reactive({
  domain: '',
  type: 'woo',
  category: '未知分类',
  mappings: [{ template_id: null }],
})

function resetForm() {
  form.domain = ''
  form.type = 'woo'
  form.category = '未知分类'
  form.mappings = [{ template_id: null }]
  editingId.value = null
}

function onTypeChange() {
  if (form.type === 'shopify') {
    form.mappings = []
  } else if (form.mappings.length === 0) {
    form.mappings = [{ template_id: null }]
  }
}

function addMapping() {
  form.mappings.push({ template_id: null })
}

function removeMapping(idx) {
  form.mappings.splice(idx, 1)
}

function showExtraSelectors(idx) {
  currentMappingIdx.value = idx
  const existing = form.mappings[idx].extra_selectors
  currentExtraSelectors.value = existing
    ? (typeof existing === 'string' ? existing : JSON.stringify(existing, null, 2))
    : ''
  extraDialogVisible.value = true
}

function applyExtraSelectors() {
  if (currentMappingIdx.value >= 0) {
    form.mappings[currentMappingIdx.value].extra_selectors = currentExtraSelectors.value || null
  }
  extraDialogVisible.value = false
}

async function fetchList() {
  loading.value = true
  try {
    const [configsRes, templatesRes] = await Promise.all([
      listSiteConfigs(),
      listTemplates(),
    ])
    configs.value = configsRes.data || []
    availableTemplates.value = templatesRes.data || []
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEditing.value = false
  resetForm()
  dialogVisible.value = true
}

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
      mappings: (data.mappings || []).map(m => ({
        template_id: m.templateId || m.template_id,
        extra_selectors: m.extraSelectors || m.extra_selectors || null,
      })),
    })
    if (form.mappings.length === 0) form.mappings = [{ template_id: null }]
    dialogVisible.value = true
  } catch {
    ElMessage.error('获取站点详情失败')
  }
}

async function handleSave() {
  saving.value = true
  try {
    const payload = {
      config: {
        domain: form.domain,
        type: form.type,
        category: form.category,
      },
      mappings: form.mappings
        .filter(m => m.template_id)
        .map(m => ({
          template_id: m.template_id,
          extra_selectors: m.extra_selectors || null,
        })),
    }
    await createSiteConfig(payload)
    ElMessage.success(isEditing.value ? '更新成功' : '注册成功')
    dialogVisible.value = false
    fetchList()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleTrigger(row) {
  try {
    await ElMessageBox.confirm(`确定立即爬取「${row.domain}」？`, '确认爬取', { type: 'info' })
    await triggerSiteCrawl(row.id, 1)
    ElMessage.success('爬取任务已下发')
  } catch {
    // cancelled
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除站点「${row.domain}」？`, '确认删除', { type: 'warning' })
    await deleteSiteConfig(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // cancelled
  }
}

onMounted(fetchList)
</script>
