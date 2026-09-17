<template>
  <div class="crawler-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>收入参数</span>
          <div class="header-actions">
            <el-tag v-if="hasChanges" type="warning">有未保存修改</el-tag>
            <el-button v-if="canEditConfig" type="primary" :disabled="!hasChanges" :loading="saving" @click="handleSave">保存参数</el-button>
          </div>
        </div>
      </template>

      <el-alert
        title="提成比例采用固定业务规则，无需配置；此处仅维护汇率、折算系数和人员归属。"
        type="info"
        :closable="false"
        show-icon
        class="config-tip"
      />
      <div class="rate-summary">
        <div><span>当前汇率</span><strong>{{ number(form.exchangeRate, 4) }}</strong></div>
        <div><span>折算系数</span><strong>{{ percent(form.rateFactor) }}</strong></div>
        <div><span>组长提成</span><strong class="fixed-rule">固定 2%</strong></div>
        <div><span>普通提成阶梯</span><strong class="fixed-rule">3% / 5% / 8%</strong><small>≤3万 / ≤8万 / &gt;8万</small></div>
        <div><span>批量站点提成</span><strong class="fixed-rule">2% / 4% / 6%</strong><small>&lt;5万 / 5–15万 / &gt;15万</small></div>
      </div>

      <el-form :model="form" label-width="150px" class="config-form">
        <el-form-item label="实时汇率">
          <el-input-number v-model="form.exchangeRate" :disabled="!canEditConfig" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="折算系数">
          <el-input-number v-model="form.rateFactor" :disabled="!canEditConfig" :min="0" :max="1" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="组长配置">
          <el-input v-model="leaderConfigText" :disabled="!canEditConfig" type="textarea" :rows="3" placeholder='{"业务一组":"组长账号"}' />
        </el-form-item>
        <el-form-item label="导师后缀映射">
          <el-input v-model="teacherMapText" :disabled="!canEditConfig" type="textarea" :rows="7" placeholder='{"B-许晓龙":"-xxl"}' />
          <div class="help-text">导师账号作为键，实习生账号后缀作为值；导师归属会自动纳入匹配实习生金额。</div>
        </el-form-item>
        <el-form-item label="多账号合并">
          <el-input v-model="userMergeMapText" :disabled="!canEditConfig" type="textarea" :rows="5" placeholder='{"B-姓名":["B-账号1","B-账号2"]}' />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getRevenueConfig, updateRevenueConfig } from '@/api/crawler'

const userStore = useUserStore()
const canEditConfig = computed(() => userStore.hasPermission('crawler:revenue:update'))
const saving = ref(false)
const leaderConfigText = ref('{}')
const teacherMapText = ref('{}')
const userMergeMapText = ref('{}')
const savedSnapshot = ref('')
const form = reactive({ exchangeRate: 6.73, rateFactor: 0.42 })
const hasChanges = computed(() => savedSnapshot.value !== snapshot())

function snapshot() {
  return JSON.stringify({
    ...form,
    leaderConfig: leaderConfigText.value,
    teacherMap: teacherMapText.value,
    userMergeMap: userMergeMapText.value,
  })
}

function number(value, digits = 2) {
  return Number(value || 0).toFixed(digits)
}

function percent(value) { return `${(Number(value || 0) * 100).toFixed(2)}%` }

async function loadConfig() {
  const response = await getRevenueConfig()
  form.exchangeRate = Number(response.data?.exchangeRate ?? 6.73)
  form.rateFactor = Number(response.data?.rateFactor ?? 0.42)
  leaderConfigText.value = JSON.stringify(response.data?.leaderConfig || {}, null, 2)
  teacherMapText.value = JSON.stringify(response.data?.teacherMap || {}, null, 2)
  userMergeMapText.value = JSON.stringify(response.data?.userMergeMap || {}, null, 2)
  savedSnapshot.value = snapshot()
}

async function handleSave() {
  let payload
  try {
    payload = buildPayload()
  } catch (error) {
    ElMessage.error(error.message || '参数格式不正确')
    return
  }
  saving.value = true
  try {
    await updateRevenueConfig(payload)
    ElMessage.success('收入参数已保存')
    await loadConfig()
  } catch (error) {
    ElMessage.error(error?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function parseObject(text, label) {
  let value
  try { value = JSON.parse(text || '{}') } catch { throw new Error(`${label} JSON 格式不正确`) }
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${label}必须是对象`)
  return value
}

function buildPayload() {
  const numeric = [form.exchangeRate, form.rateFactor]
  if (numeric.some(value => !Number.isFinite(Number(value)) || Number(value) < 0)
      || Number(form.exchangeRate) <= 0
      || Number(form.rateFactor) > 1) {
    throw new Error('汇率必须大于 0，折算系数必须在 0% 到 100% 之间')
  }
  return {
    exchangeRate: Number(form.exchangeRate),
    rateFactor: Number(form.rateFactor),
    leaderConfig: parseObject(leaderConfigText.value, '组长配置'),
    teacherMap: parseObject(teacherMapText.value, '导师后缀映射'),
    userMergeMap: parseObject(userMergeMapText.value, '多账号合并'),
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.crawler-page { max-width: 900px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }.header-actions { display: flex; align-items: center; gap: 10px; }
.config-form { max-width: 720px; }
.config-tip { margin-bottom: 16px; }.rate-summary { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; margin-bottom: 20px; }.rate-summary > div { padding: 13px 15px; border: 1px solid #edf0f5; border-radius: 8px; background: #fafbfd; }.rate-summary span { display: block; color: #8b98ad; font-size: 12px; }.rate-summary strong { display: block; margin-top: 7px; color: #2f4262; font-size: 20px; }.rate-summary small { display: block; margin-top: 4px; color: #9aa5b5; font-size: 11px; white-space: nowrap; }
.help-text { margin-top: 5px; color: #8b97aa; font-size: 12px; line-height: 1.5; }
.fixed-rule { font-size: 16px !important; }
@media (max-width: 1000px) { .rate-summary { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 700px) { .rate-summary { grid-template-columns: repeat(2, 1fr); } }
</style>
