<template>
  <div class="notification-page">
    <PageHeader
      eyebrow="SYSTEM"
      title="通知配置"
      description="统一管理钉钉、飞书和 Lark 群通知渠道与消息模板"
    >
      <template #actions>
        <el-button v-if="canManage" type="primary" @click="openDialog()">新增机器人</el-button>
      </template>
    </PageHeader>

    <el-alert
      title="Webhook 与签名密钥会加密保存且不会回显；站点健康总体汇总只发送到已启用的飞书机器人。"
      type="info"
      :closable="false"
      show-icon
      class="security-tip"
    />

    <DataPanel>
      <p class="cf-table-hint">左右滑动表格查看状态与操作列</p>
      <el-table :data="channels" v-loading="loading" stripe empty-text="尚未配置通知机器人">
        <el-table-column label="机器人" min-width="180">
          <template #default="{ row }">
            <div class="channel-name">{{ row.name }}</div>
            <div class="channel-meta">更新于 {{ formatBeijingDateTime(row.updatedAt, '—') }}</div>
          </template>
        </el-table-column>
        <el-table-column label="平台" width="120">
          <template #default="{ row }">
            <el-tag :type="platformTone(row.platform)">{{ row.platformLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="凭据状态" min-width="210">
          <template #default="{ row }">
            <div class="credential-state">
              <el-tag size="small" :type="row.webhookConfigured ? 'success' : 'danger'">
                Webhook {{ row.webhookConfigured ? '已配置' : '未配置' }}
              </el-tag>
              <el-tag size="small" :type="row.signingSecretConfigured ? 'success' : 'info'">
                签名 {{ row.signingSecretConfigured ? '已配置' : '未配置' }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="内容模板" min-width="220">
          <template #default="{ row }">
            <div class="template-summary">{{ templateSummary(row.messageTemplate) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '已启用' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" :fixed="isNarrow ? false : 'right'">
          <template #default="{ row }">
            <el-button v-if="canTest" link type="success" :loading="testingId === row.id" @click="openTestDialog(row)">发送测试</el-button>
            <el-button v-if="canManage" link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button v-if="canManage" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataPanel>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑通知机器人' : '新增通知机器人'" width="min(600px, 94vw)" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="机器人名称" required>
          <el-input v-model="form.name" maxlength="100" show-word-limit placeholder="例如：运维告警群" />
        </el-form-item>
        <el-form-item label="通知平台" required>
          <el-select v-model="form.platform" style="width: 100%" @change="handlePlatformChange">
            <el-option label="钉钉" value="DINGTALK" />
            <el-option label="飞书" value="FEISHU" />
            <el-option label="Lark" value="LARK" />
          </el-select>
        </el-form-item>
        <el-form-item label="Webhook" :required="!form.id">
          <el-input
            v-model="form.webhookUrl"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="form.id ? '留空则保留现有 Webhook' : webhookPlaceholder"
          />
          <div class="field-help">{{ webhookHelp }}</div>
        </el-form-item>
        <el-form-item label="签名密钥">
          <el-input
            v-model="form.signingSecret"
            type="password"
            show-password
            autocomplete="new-password"
            :disabled="form.clearSigningSecret"
            :placeholder="form.id && form.secretConfigured ? '留空则保留现有签名密钥' : '可选：机器人安全设置中的签名密钥'"
          />
          <el-checkbox v-if="form.id && form.secretConfigured" v-model="form.clearSigningSecret">清除现有签名密钥</el-checkbox>
        </el-form-item>
        <el-form-item label="通知模板" required>
          <el-input v-model="form.messageTemplate" type="textarea" :rows="7" maxlength="4000" show-word-limit />
          <div class="field-help template-variables">
            可用变量：<code v-pre>{{title}}</code> 标题、<code v-pre>{{content}}</code> 正文、
            <code v-pre>{{time}}</code> 北京时间、<code v-pre>{{channel}}</code> 渠道名、
            <code v-pre>{{platform}}</code> 平台。
          </div>
          <pre class="template-preview">{{ templatePreview }}</pre>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="testDialogVisible" title="发送测试通知" width="min(560px, 94vw)" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="通知渠道">
          <strong>{{ activeTestChannel?.name }}</strong>
        </el-form-item>
        <el-form-item label="通知标题" required>
          <el-input v-model="testMessage.title" maxlength="200" placeholder="例如：订单同步异常" />
        </el-form-item>
        <el-form-item label="通知正文" required>
          <el-input v-model="testMessage.content" type="textarea" :rows="6" maxlength="3000" show-word-limit placeholder="填写需要发送的自定义内容" />
        </el-form-item>
        <el-form-item label="最终预览">
          <pre class="template-preview test-preview">{{ testPreview }}</pre>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="testingId !== null" @click="handleTest">发送测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createNotificationChannel,
  deleteNotificationChannel,
  getNotificationChannels,
  testNotificationChannel,
  updateNotificationChannel,
} from '@/api/system'
import { useUserStore } from '@/store/user'
import { formatBeijingDateTime } from '@/utils/dateTime'

const userStore = useUserStore()
const isNarrow = useMediaQuery('(max-width: 900px)')
const canManage = computed(() => userStore.hasPermission('system:notification:manage'))
const canTest = computed(() => userStore.hasPermission('system:notification:test'))
const loading = ref(false)
const saving = ref(false)
const testingId = ref(null)
const dialogVisible = ref(false)
const channels = ref([])
const DEFAULT_TEMPLATE = '【{{title}}】\n{{content}}\n\n渠道：{{channel}}\n时间：{{time}}（北京时间）'
const testDialogVisible = ref(false)
const activeTestChannel = ref(null)
const testMessage = reactive({ title: 'CyberFlow 测试通知', content: '机器人连接正常，这是一条自定义内容测试。' })
const form = reactive({
  id: null,
  name: '',
  platform: 'FEISHU',
  webhookUrl: '',
  signingSecret: '',
  messageTemplate: DEFAULT_TEMPLATE,
  secretConfigured: false,
  clearSigningSecret: false,
  enabled: true,
})

const webhookPlaceholder = computed(() => ({
  DINGTALK: 'https://oapi.dingtalk.com/robot/send?access_token=…',
  FEISHU: 'https://open.feishu.cn/open-apis/bot/v2/hook/…',
  LARK: 'https://open.larksuite.com/open-apis/bot/v2/hook/…',
}[form.platform]))

const webhookHelp = computed(() => ({
  DINGTALK: '请粘贴钉钉群自定义机器人的完整 Webhook。',
  FEISHU: '请粘贴飞书群自定义机器人的完整 Webhook。',
  LARK: '请粘贴 Lark 群 Custom Bot 的完整 Webhook。',
}[form.platform]))

const sampleVariables = computed(() => ({
  title: '通知标题示例',
  content: '这里将展示实际通知正文。',
  time: formatBeijingDateTime(new Date()),
  channel: form.name.trim() || '当前渠道',
  platform: { DINGTALK: '钉钉', FEISHU: '飞书', LARK: 'Lark' }[form.platform],
}))
const templatePreview = computed(() => renderTemplate(form.messageTemplate, sampleVariables.value))
const testPreview = computed(() => renderTemplate(activeTestChannel.value?.messageTemplate, {
  title: testMessage.title || '通知标题',
  content: testMessage.content || '通知正文',
  time: formatBeijingDateTime(new Date()),
  channel: activeTestChannel.value?.name || '当前渠道',
  platform: activeTestChannel.value?.platformLabel || '通知平台',
}))

function platformTone(platform) {
  return { DINGTALK: 'primary', FEISHU: 'success', LARK: 'warning' }[platform] || 'info'
}

function renderTemplate(template, variables) {
  return String(template || DEFAULT_TEMPLATE).replace(/\{\{(title|content|time|channel|platform)\}\}/g, (_, key) => variables[key] || '')
}

function templateSummary(template) {
  return String(template || DEFAULT_TEMPLATE).replace(/\s+/g, ' ').slice(0, 68)
}

async function loadChannels() {
  loading.value = true
  try {
    const res = await getNotificationChannels()
    channels.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  Object.assign(form, row ? {
    id: row.id,
    name: row.name,
    platform: row.platform,
    webhookUrl: '',
    signingSecret: '',
    messageTemplate: row.messageTemplate || DEFAULT_TEMPLATE,
    secretConfigured: Boolean(row.signingSecretConfigured),
    clearSigningSecret: false,
    enabled: Boolean(row.enabled),
  } : {
    id: null,
    name: '',
    platform: 'FEISHU',
    webhookUrl: '',
    signingSecret: '',
    messageTemplate: DEFAULT_TEMPLATE,
    secretConfigured: false,
    clearSigningSecret: false,
    enabled: true,
  })
  dialogVisible.value = true
}

function handlePlatformChange() {
  form.webhookUrl = ''
}

async function handleSave() {
  if (!form.name.trim()) return ElMessage.warning('请填写机器人名称')
  if (!form.id && !form.webhookUrl.trim()) return ElMessage.warning('请填写机器人 Webhook')
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      platform: form.platform,
      webhookUrl: form.webhookUrl.trim() || null,
      signingSecret: form.signingSecret.trim() || null,
      messageTemplate: form.messageTemplate.trim() || DEFAULT_TEMPLATE,
      clearSigningSecret: form.clearSigningSecret,
      enabled: form.enabled,
    }
    if (form.id) await updateNotificationChannel(form.id, payload)
    else await createNotificationChannel(payload)
    ElMessage.success(form.id ? '通知机器人已更新' : '通知机器人已添加')
    dialogVisible.value = false
    await loadChannels()
  } finally {
    saving.value = false
  }
}

function openTestDialog(row) {
  activeTestChannel.value = row
  Object.assign(testMessage, {
    title: 'CyberFlow 测试通知',
    content: '机器人连接正常，这是一条自定义内容测试。',
  })
  testDialogVisible.value = true
}

async function handleTest() {
  const row = activeTestChannel.value
  if (!row) return
  if (!testMessage.title.trim()) return ElMessage.warning('请填写通知标题')
  if (!testMessage.content.trim()) return ElMessage.warning('请填写通知正文')
  testingId.value = row.id
  try {
    await testNotificationChannel(row.id, {
      title: testMessage.title.trim(),
      content: testMessage.content.trim(),
    })
    ElMessage.success(`测试通知已发送到“${row.name}”`)
    testDialogVisible.value = false
  } finally {
    testingId.value = null
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除通知机器人“${row.name}”？`, '删除通知机器人', { type: 'warning' })
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
  await deleteNotificationChannel(row.id)
  ElMessage.success('通知机器人已删除')
  await loadChannels()
}

onMounted(loadChannels)
</script>

<style scoped>
.notification-page {
  display: grid;
  max-width: var(--cf-page-max);
  margin: 0 auto;
  gap: var(--cf-space-4);
}

.channel-name {
  color: var(--cf-ink);
  font-weight: 600;
}

.channel-meta {
  margin-top: 5px;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
}

.credential-state {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.field-help {
  width: 100%;
  margin-top: 6px;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
  line-height: 1.5;
}

.template-summary {
  overflow: hidden;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-variables code {
  color: var(--cf-primary);
}

.template-preview {
  width: 100%;
  min-height: 72px;
  box-sizing: border-box;
  margin: 8px 0 0;
  padding: 12px;
  overflow: auto;
  border: 1px solid var(--cf-line);
  border-radius: var(--cf-radius-sm);
  background: var(--cf-surface-sunken);
  color: var(--cf-ink-2);
  font: inherit;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.test-preview {
  margin-top: 0;
}
</style>
