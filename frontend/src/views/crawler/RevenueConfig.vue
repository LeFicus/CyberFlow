<template>
  <div class="crawler-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>收入参数</span>
          <el-button v-if="canEditConfig" type="primary" :loading="saving" @click="handleSave">保存参数</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="150px" class="config-form">
        <el-form-item label="实时汇率">
          <el-input-number v-model="form.exchangeRate" :disabled="!canEditConfig" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="折算系数">
          <el-input-number v-model="form.rateFactor" :disabled="!canEditConfig" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="组长提成比例">
          <el-input-number v-model="form.leaderCommissionRate" :disabled="!canEditConfig" :precision="4" :step="0.01" />
        </el-form-item>
        <el-form-item label="提成阶梯">
          <el-input v-model="commissionTiersText" :disabled="!canEditConfig" type="textarea" :rows="5" />
          <div class="help-text">JSON 示例：[{"threshold":30000,"rate":0.03},{"threshold":"","rate":0.08}]</div>
        </el-form-item>
        <el-form-item label="组长配置">
          <el-input v-model="leaderConfigText" :disabled="!canEditConfig" type="textarea" :rows="3" placeholder='{"A":"A-黄伟","B":"B-李榕"}' />
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
const form = reactive({ exchangeRate: 6.73, rateFactor: 0.42, leaderCommissionRate: 0.02 })

async function loadConfig() {
  const response = await getRevenueConfig()
  Object.assign(form, response.data || {})
  commissionTiersText.value = JSON.stringify(response.data?.commissionTiers || [], null, 2)
  leaderConfigText.value = JSON.stringify(response.data?.leaderConfig || {}, null, 2)
  teacherMapText.value = JSON.stringify(response.data?.teacherMap || {}, null, 2)
  userMergeMapText.value = JSON.stringify(response.data?.userMergeMap || {}, null, 2)
}

async function handleSave() {
  saving.value = true
  try {
    const payload = {
      exchangeRate: form.exchangeRate,
      rateFactor: form.rateFactor,
      leaderCommissionRate: form.leaderCommissionRate,
      commissionTiers: JSON.parse(commissionTiersText.value || '[]'),
      leaderConfig: JSON.parse(leaderConfigText.value || '{}'),
      teacherMap: JSON.parse(teacherMapText.value || '{}'),
      userMergeMap: JSON.parse(userMergeMapText.value || '{}'),
    }
    await updateRevenueConfig(payload)
    ElMessage.success('收入参数已保存')
    await loadConfig()
  } catch {
    ElMessage.error('保存失败，请检查 JSON 格式')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.crawler-page { max-width: 900px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.config-form { max-width: 720px; }
.help-text { margin-top: 5px; color: #8b97aa; font-size: 12px; line-height: 1.5; }
</style>
