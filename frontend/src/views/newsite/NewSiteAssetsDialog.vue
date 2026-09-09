<template>
  <el-dialog
    :model-value="modelValue"
    :title="site ? `${site.domain} · 品牌素材` : '品牌素材'"
    width="1080px"
    top="4vh"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
    @open="start"
    @closed="stop"
  >
    <div class="asset-toolbar">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="AI 会生成透明 Logo、桌面 Banner、移动 Banner，并从 Logo 自动派生多尺寸 Icon。请审核后选择正式素材。"
      />
      <div class="asset-actions">
        <el-button :loading="loading" @click="fetchAssets">刷新</el-button>
        <el-button
          v-if="canManage"
          type="primary"
          :loading="generating"
          :disabled="hasActive"
          @click="generate"
        >{{ assets.length ? '重新生成一套' : '生成品牌素材' }}</el-button>
        <el-button :disabled="!hasSelected" :loading="downloadingPack" @click="downloadPack">下载已选素材包</el-button>
      </div>
    </div>

    <el-empty v-if="!loading && !assets.length" description="暂未生成品牌素材" />
    <div v-else v-loading="loading" class="asset-sections">
      <section v-for="section in sections" :key="section.key" class="asset-section">
        <div class="section-heading">
          <div>
            <h3>{{ section.title }}</h3>
            <p>{{ section.description }}</p>
          </div>
          <el-tag v-if="section.key === 'icon'" type="info" effect="plain">随 Logo 自动选择</el-tag>
        </div>
        <div class="asset-grid" :class="{ 'banner-grid': section.key === 'banner' }">
          <article v-for="asset in section.items" :key="asset.id" class="asset-card" :class="{ selected: asset.isSelected === 1 }">
            <div class="preview" :class="[`preview-${asset.assetType}`, `preview-${asset.variant}`]">
              <img v-if="previews[asset.id]" :src="previews[asset.id]" :alt="assetLabel(asset)" />
              <div v-else-if="isActive(asset)" class="asset-state">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>{{ asset.status === 'queued' ? '排队中' : 'AI 生成中' }}</span>
              </div>
              <div v-else-if="asset.status === 'failed'" class="asset-state failed">
                <el-icon><WarningFilled /></el-icon>
                <span>生成失败</span>
              </div>
              <div v-else class="asset-state"><span>加载预览中</span></div>
            </div>
            <div class="asset-meta">
              <div class="asset-title">
                <strong>{{ assetLabel(asset) }}</strong>
                <el-tag v-if="asset.isSelected === 1" type="success" size="small">已选择</el-tag>
              </div>
              <small>{{ asset.width && asset.height ? `${asset.width} × ${asset.height}` : asset.model || '' }}</small>
              <el-tooltip v-if="asset.errorMessage" :content="asset.errorMessage" placement="top">
                <p class="asset-error">{{ asset.errorMessage }}</p>
              </el-tooltip>
              <div v-if="asset.status === 'ready'" class="card-actions">
                <el-button
                  v-if="canManage && asset.assetType !== 'icon'"
                  type="success"
                  link
                  :disabled="asset.isSelected === 1"
                  :loading="selecting === asset.id"
                  @click="select(asset)"
                >{{ asset.isSelected === 1 ? '已选用' : '选用' }}</el-button>
                <el-button link @click="downloadOne(asset)">下载</el-button>
              </div>
            </div>
          </article>
        </div>
      </section>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, WarningFilled } from '@element-plus/icons-vue'
import {
  downloadNewSiteAssetPack,
  generateNewSiteAssets,
  getNewSiteAssetContent,
  listNewSiteAssets,
  selectNewSiteAsset,
} from '@/api/newSite'

const props = defineProps({
  modelValue: { type: Boolean, required: true },
  site: { type: Object, default: null },
  canManage: { type: Boolean, default: false },
})
defineEmits(['update:modelValue'])

const assets = ref([])
const loading = ref(false)
const generating = ref(false)
const downloadingPack = ref(false)
const selecting = ref(null)
const previews = reactive({})
let timer = null
let requestId = 0

const hasActive = computed(() => assets.value.some(isActive))
const hasSelected = computed(() => {
  const selected = assets.value.filter(asset => asset.status === 'ready' && asset.isSelected === 1)
  return selected.some(asset => asset.assetType === 'logo')
    && selected.some(asset => asset.assetType === 'banner' && asset.variant === 'desktop')
    && selected.some(asset => asset.assetType === 'banner' && asset.variant === 'mobile')
})
const sections = computed(() => [
  {
    key: 'logo', title: 'Logo 候选', description: '透明背景主标识；选用 Logo 后会同步选择同一套 Icon。',
    items: assets.value.filter(asset => asset.assetType === 'logo'),
  },
  {
    key: 'banner', title: 'Banner 候选', description: '桌面和移动端分别审核，标题与按钮由站点模板叠加。',
    items: assets.value.filter(asset => asset.assetType === 'banner'),
  },
  {
    key: 'icon', title: 'Icon', description: '从 Logo 自动生成，可用于 favicon、PWA 与移动端图标。',
    items: assets.value.filter(asset => asset.assetType === 'icon'),
  },
].filter(section => section.items.length))

function isActive(asset) {
  return asset.status === 'queued' || asset.status === 'generating'
}

function assetLabel(asset) {
  if (asset.assetType === 'logo') return 'Logo'
  if (asset.assetType === 'banner') return asset.variant === 'mobile' ? '移动 Banner' : '桌面 Banner'
  return `Icon ${asset.variant}`
}

async function loadPreview(asset) {
  if (asset.status !== 'ready' || previews[asset.id]) return
  try {
    const response = await getNewSiteAssetContent(asset.id)
    previews[asset.id] = URL.createObjectURL(response.data)
  } catch {
    // The global request handler has already reported the error.
  }
}

async function fetchAssets({ quiet = false } = {}) {
  if (!props.site?.id) return
  const current = ++requestId
  if (!quiet) loading.value = true
  try {
    const response = await listNewSiteAssets(props.site.id)
    if (current !== requestId) return
    assets.value = response.data || []
    await Promise.all(assets.value.map(loadPreview))
    schedule()
  } finally {
    if (!quiet && current === requestId) loading.value = false
  }
}

async function generate() {
  generating.value = true
  try {
    const response = await generateNewSiteAssets(props.site.id)
    assets.value = response.data || []
    ElMessage.success('品牌素材已进入后台生成队列')
    schedule(true)
  } finally {
    generating.value = false
  }
}

async function select(asset) {
  selecting.value = asset.id
  try {
    await selectNewSiteAsset(props.site.id, asset.id)
    ElMessage.success(asset.assetType === 'logo' ? 'Logo 与配套 Icon 已选用' : 'Banner 已选用')
    await fetchAssets({ quiet: true })
  } finally {
    selecting.value = null
  }
}

async function downloadOne(asset) {
  const response = await getNewSiteAssetContent(asset.id)
  saveBlob(response.data, `${props.site.domain}-${asset.assetType}-${asset.variant}.png`)
}

async function downloadPack() {
  downloadingPack.value = true
  try {
    const response = await downloadNewSiteAssetPack(props.site.id)
    saveBlob(response.data, `${props.site.domain}-brand-assets.zip`)
  } finally {
    downloadingPack.value = false
  }
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function schedule(immediate = false) {
  clearTimeout(timer)
  timer = null
  if (!props.modelValue || !hasActive.value) return
  timer = setTimeout(() => fetchAssets({ quiet: true }), immediate ? 500 : 3000)
}

function revokePreviews() {
  Object.values(previews).forEach(url => URL.revokeObjectURL(url))
  Object.keys(previews).forEach(key => delete previews[key])
}

function start() {
  revokePreviews()
  assets.value = []
  fetchAssets()
}

function stop() {
  requestId++
  clearTimeout(timer)
  timer = null
  revokePreviews()
}

onBeforeUnmount(stop)
</script>

<style scoped>
.asset-toolbar {
  display: grid;
  gap: 12px;
}

.asset-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.asset-sections {
  min-height: 260px;
  margin-top: 18px;
}

.asset-section + .asset-section {
  margin-top: 24px;
}

.section-heading,
.asset-title,
.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-heading h3,
.section-heading p {
  margin: 0;
}

.section-heading p {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
  margin-top: 12px;
}

.banner-grid {
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
}

.asset-card {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  transition: border-color .2s, box-shadow .2s;
}

.asset-card.selected {
  border-color: var(--el-color-success);
  box-shadow: 0 0 0 1px var(--el-color-success-light-5);
}

.preview {
  display: grid;
  min-height: 180px;
  place-items: center;
  overflow: hidden;
  background-image: linear-gradient(45deg, #eef0f3 25%, transparent 25%),
    linear-gradient(-45deg, #eef0f3 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #eef0f3 75%),
    linear-gradient(-45deg, transparent 75%, #eef0f3 75%);
  background-position: 0 0, 0 8px, 8px -8px, -8px 0;
  background-size: 16px 16px;
}

.preview-banner.preview-desktop { aspect-ratio: 2.5 / 1; min-height: 150px; }
.preview-banner.preview-mobile { aspect-ratio: 3 / 4; max-height: 330px; }
.preview-icon { min-height: 140px; }
.preview img { width: 100%; height: 100%; object-fit: contain; }
.preview-banner img { object-fit: cover; }

.asset-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.asset-state.failed,
.asset-error { color: var(--el-color-danger); }

.asset-meta { padding: 11px 12px; }
.asset-meta small { color: var(--el-text-color-secondary); }
.asset-error { overflow: hidden; margin: 6px 0 0; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.card-actions { justify-content: flex-end; margin-top: 7px; }

@media (max-width: 760px) {
  .asset-actions { align-items: stretch; flex-direction: column; }
  .banner-grid { grid-template-columns: 1fr; }
}
</style>
