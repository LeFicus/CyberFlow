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
        title="参数保存后会立即用于收入统计；金额按汇率 × 折算系数计算，批量建站（site_tag=1）按独立比例提成。"
        type="info"
        :closable="false"
        show-icon
        class="config-tip"
      />
      <div class="rate-summary">
        <div><span>当前汇率</span><strong>{{ number(form.exchangeRate, 4) }}</strong></div>
        <div><span>折算系数</span><strong>{{ percent(form.rateFactor) }}</strong></div>
        <div><span>组长比例</span><strong>{{ percent(form.leaderCommissionRate) }}</strong></div>
        <div><span>批量站点比例</span><strong>{{ percent(form.batchSiteCommissionRate) }}</strong></div>
      </div>

      <el-form :model="form" label-width="150px" class="config-form">
        <el-form-item label="实时汇率">
          <el-input-number v-model="form.exchangeRate" :disabled="!canEditConfig" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="折算系数">
          <el-input-number v-model="form.rateFactor" :disabled="!canEditConfig" :min="0" :max="1" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="组长提成比例">
          <el-input-number v-model="form.leaderCommissionRate" :disabled="!canEditConfig" :min="0" :max="1" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="批量站点提成比例">
          <el-input-number v-model="form.batchSiteCommissionRate" :disabled="!canEditConfig" :min="0" :max="1" :precision="4" :step="0.01" />
          <div class="help-text">site_tag=1 的批量建站成交额不进入普通阶梯，按此比例单独计算。</div>
        </el-form-item>
        <el-form-item label="提成阶梯">
          <el-input v-model="commissionTiersText" :disabled="!canEditConfig" type="textarea" :rows="5" />
          <div class="help-text">JSON 示例：[{"threshold":30000,"rate":0.03},{"threshold":"","rate":0.08}]</div>
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
const commissionTiersText = ref('[]')
const leaderConfigText = ref('{}')
const teacherMapText = ref('{}')
const userMergeMapText = ref('{}')
const savedSnapshot = ref('')
const form = reactive({ exchangeRate: 6.73, rateFactor: 0.42, leaderCommissionRate: 0.02, batchSiteCommissionRate: 0.02 })
const hasChanges = computed(() => savedSnapshot.value !== snapshot())

function snapshot() {
  return JSON.stringify({
    ...form,
    commissionTiers: commissionTiersText.value,
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
  Object.assign(form, response.data || {})
  commissionTiersText.value = JSON.stringify(response.data?.commissionTiers || [], null, 2)
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
  const numeric = [form.exchangeRate, form.rateFactor, form.leaderCommissionRate, form.batchSiteCommissionRate]
  if (numeric.some(value => !Number.isFinite(Number(value)) || Number(value) < 0)
      || Number(form.exchangeRate) <= 0
      || Number(form.rateFactor) > 1
      || Number(form.leaderCommissionRate) > 1
      || Number(form.batchSiteCommissionRate) > 1) {
    throw new Error('汇率必须大于 0，各比例必须在 0% 到 100% 之间')
  }
  let tiers
  try { tiers = JSON.parse(commissionTiersText.value || '[]') } catch { throw new Error('提成阶梯 JSON 格式不正确') }
  if (!Array.isArray(tiers) || tiers.length === 0) throw new Error('提成阶梯至少配置一档')
  let previous = -1
  tiers.forEach((tier, index) => {
    const threshold = tier?.threshold === '' || tier?.threshold === null ? null : Number(tier?.threshold)
    const rate = Number(tier?.rate)
    if ((!Number.isFinite(rate) || rate < 0 || rate > 1) || (threshold !== null && (!Number.isFinite(threshold) || threshold < 0))) {
      throw new Error(`第 ${index + 1} 档提成参数无效`)
    }
    if (threshold !== null && threshold < previous) throw new Error('提成阶梯金额必须按从小到大排列')
    if (threshold !== null) previous = threshold
  })
  return {
    exchangeRate: Number(form.exchangeRate),
    rateFactor: Number(form.rateFactor),
    leaderCommissionRate: Number(form.leaderCommissionRate),
    batchSiteCommissionRate: Number(form.batchSiteCommissionRate),
    commissionTiers: tiers,
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
.config-tip { margin-bottom: 16px; }.rate-summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin-bottom: 20px; }.rate-summary > div { padding: 13px 15px; border: 1px solid #edf0f5; border-radius: 8px; background: #fafbfd; }.rate-summary span { display: block; color: #8b98ad; font-size: 12px; }.rate-summary strong { display: block; margin-top: 7px; color: #2f4262; font-size: 20px; }
.help-text { margin-top: 5px; color: #8b97aa; font-size: 12px; line-height: 1.5; }
@media (max-width: 700px) { .rate-summary { grid-template-columns: repeat(2, 1fr); } }
</style>
