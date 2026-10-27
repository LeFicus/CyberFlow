<!--
  UserListPage - 用户管理页面
  管理系统用户，提供用户的新增、编辑、删除功能，以及为用户分配角色。
  编辑模式下用户名不可修改，新增时需填写密码。
-->
<template>
  <div class="user-page">
    <PageHeader
      eyebrow="SYSTEM"
      title="用户管理"
      description="管理成员账号、状态、数据归属与角色权限"
    >
      <template #actions>
        <el-button v-if="canCreate" type="primary" @click="openDialog()">新增用户</el-button>
      </template>
    </PageHeader>

    <DataPanel :padded="true">
    <p v-if="isNarrow" class="table-hint">左右滑动表格可查看邮箱、状态与操作列</p>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column label="本人数据归属" min-width="150">
        <template #default="{ row }">{{ row.dataOwner || '未配置' }}</template>
      </el-table-column>
      <el-table-column label="可查看其他成员" min-width="190">
        <template #default="{ row }">{{ formatOwners(row.sharedDataOwners) }}</template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" width="180" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column v-if="canManage" label="操作" width="240" :fixed="isNarrow ? false : 'right'">
        <template #default="{ row }">
          <el-button v-if="canUpdate" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button v-if="canAssign" size="small" @click="openRoleDialog(row)">分配角色</el-button>
          <el-button v-if="canDelete" size="small" type="danger" :disabled="row.id === currentUserIdValue" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="user-pagination"
      v-model:current-page="page" :page-size="size"
      :page-sizes="[10, 20, 50, 100]"
      :total="total" layout="total, sizes, prev, pager, next"
      @current-change="fetchData"
      @size-change="handleSizeChange"
    />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="min(760px, 94vw)">
      <el-form :model="form" label-width="120px">
        <el-form-item label="用户名">
          <!-- 编辑时用户名不可修改 -->
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="本人数据归属">
          <el-input v-model="form.dataOwner" placeholder="只能配置一个站点/订单管理员名称" />
        </el-form-item>
        <el-form-item label="其他成员">
          <el-select v-model="form.sharedDataOwners" multiple filterable allow-create default-first-option
            placeholder="输入并回车，选择允许查看的其他成员" style="width: 100%">
            <el-option v-for="owner in form.sharedDataOwners" :key="owner" :label="owner" :value="owner" />
          </el-select>
        </el-form-item>
        <el-form-item label="他人字段权限">
          <div class="field-permissions">
            <section v-for="group in sharedFieldGroups" :key="group.label">
              <strong>{{ group.label }}</strong>
              <el-checkbox-group v-model="form.sharedDataFields">
                <el-checkbox v-for="field in group.fields" :key="field.value" :value="field.value">
                  {{ field.label }}
                </el-checkbox>
              </el-checkbox-group>
            </section>
            <small>未勾选字段不会由后端返回；本人数据不受这些选项限制。</small>
          </div>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <!-- 新增时需要设置密码，编辑时不显示密码字段 -->
        <el-form-item label="密码" v-if="!isEdit">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" :disabled="isEdit && currentUserId === currentUserIdValue" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗：checkbox 多选角色 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="min(420px, 94vw)">
      <el-checkbox-group v-model="selectedRoles">
          <el-checkbox v-for="r in allRoles" :key="r.id" :value="r.id">{{ r.roleName }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssignRoles">保存</el-button>
      </template>
    </el-dialog>
    </DataPanel>
  </div>
</template>

<script setup>
/**
 * @fileoverview 用户管理页面
 * @description 提供系统用户的后台管理功能，包含用户 CRUD 和角色分配。
 *              编辑状态下用户名不可修改，新增时需设置初始密码。
 */
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMediaQuery } from '@vueuse/core'
import { getUsers, createUser, updateUser, deleteUser, assignUserRoles, getAllRoles, getUserRoleIds } from '@/api/system'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const isNarrow = useMediaQuery('(max-width: 900px)')
const canCreate = computed(() => userStore.hasPermission('system:user:create'))
const canUpdate = computed(() => userStore.hasPermission('system:user:update'))
const canDelete = computed(() => userStore.hasPermission('system:user:delete'))
const canAssign = computed(() => userStore.hasPermission('system:user:assign'))
const canManage = computed(() => canUpdate.value || canDelete.value || canAssign.value)
const currentUserIdValue = computed(() => Number(userStore.userInfo?.id || 0))

/** @type {import('vue').Ref<boolean>} 列表加载状态 */
const loading = ref(false)
/** @type {import('vue').Ref<boolean>} 保存按钮加载状态 */
const saving = ref(false)
const assigning = ref(false)
/** @type {import('vue').Ref<Array>} 用户列表数据 */
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
/** @type {import('vue').Ref<boolean>} 用户编辑弹窗是否可见 */
const dialogVisible = ref(false)
/** @type {import('vue').Ref<boolean>} 角色分配弹窗是否可见 */
const roleDialogVisible = ref(false)
/** @type {import('vue').Ref<boolean>} 是否为编辑模式 */
const isEdit = ref(false)
/** @type {import('vue').Ref<number|null>} 当前操作的用户 ID */
const currentUserId = ref(null)
/** @type {import('vue').Ref<Array>} 所有角色列表 */
const allRoles = ref([])
/** @type {import('vue').Ref<number[]>} 当前选中的角色 ID 列表 */
const selectedRoles = ref([])

/** 用户表单数据 */
const form = reactive({ username: '', nickname: '', dataOwner: '', sharedDataOwners: [], sharedDataFields: [], email: '', password: '', status: 1 })

const sharedFieldGroups = [
  { label: '个人绩效', fields: [
    ['组别', 'performance.user_group'], ['姓名 / 账号', 'performance.real_name'],
    ['申请月份', 'performance.site_month'], ['站点数', 'performance.site_count'],
    ['有效去重及订单数', 'performance.valid_deduplicated_orders'],
    ['订单转化率', 'performance.order_conversion_rate'], ['站点转化率', 'performance.site_conversion_rate'],
    ['百站转化率', 'performance.hundred_site_conversion_rate'], ['成功金额', 'performance.successful_amount'],
    ['提成', 'performance.commission'], ['分类 / 国家明细', 'performance.breakdown'],
  ].map(([label, value]) => ({ label, value })) },
  { label: '订单', fields: [
    ['订单号', 'order.id'], ['商品信息 / 图片', 'order.product_info'], ['金额', 'order.amount'],
    ['币种', 'order.currency'], ['订单站点', 'order.product_host'], ['分类明细', 'order.category'],
    ['建站类型', 'order.site_tag'], ['支付状态', 'order.pay_status'], ['国家 / 地区', 'order.country'],
    ['平台风控 / 有效状态', 'order.fraud_status'], ['收货邮箱', 'order.shipping_email'], ['收货地址', 'order.shipping_address'],
    ['管理员', 'order.admin_name'], ['用户组', 'order.user_group'], ['创建时间', 'order.create_time'],
  ].map(([label, value]) => ({ label, value })) },
]

function parseOwners(value) {
  if (Array.isArray(value)) return value
  return String(value || '').split(/[,，、\n]/).map(item => item.trim()).filter(Boolean)
}

function formatOwners(value) {
  return parseOwners(value).join('、') || '未配置'
}

function parseFields(value) {
  return String(value || '').split(',').map(item => item.trim()).filter(Boolean)
}

/**
 * 获取用户列表
 */
async function fetchData() {
  loading.value = true
  try {
    const res = await getUsers({ page: page.value, size: size.value })
    tableData.value = res.data.records || []
    total.value = Number(res.data.total || 0)
  } finally { loading.value = false }
}

function handleSizeChange(value) {
  size.value = value
  page.value = 1
  fetchData()
}

/**
 * 打开新增/编辑用户弹窗
 * 编辑模式下从行数据回填表单，新增模式下重置为空值
 * @param {Object} [row] - 可选，传入行数据为编辑模式，不传为新增模式
 */
function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    currentUserId.value = row.id
    Object.assign(form, {
      username: row.username,
      nickname: row.nickname,
      dataOwner: row.dataOwner || '',
      sharedDataOwners: parseOwners(row.sharedDataOwners),
      sharedDataFields: parseFields(row.sharedDataFields),
      email: row.email,
      password: '',
      status: row.status,
    })
  } else {
    currentUserId.value = null
    Object.assign(form, { username: '', nickname: '', dataOwner: '', sharedDataOwners: [], sharedDataFields: [], email: '', password: '', status: 1 })
  }
  dialogVisible.value = true
}

/**
 * 保存用户（新增或更新）
 * 编辑模式下仅当密码非空时才将其包含在请求体中
 */
async function handleSave() {
  if (!form.username.trim()) return ElMessage.warning('请填写用户名')
  if (!isEdit.value && (form.password.length < 8 || form.password.length > 72)) {
    return ElMessage.warning('密码长度须为 8–72 个字符')
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateUser(currentUserId.value, {
        username: form.username,
        nickname: form.nickname,
        dataOwner: form.dataOwner,
        sharedDataOwners: form.sharedDataOwners.join(','),
        sharedDataFields: form.sharedDataFields.join(','),
        email: form.email,
        status: form.status,
        password: form.password || undefined,
      })
      ElMessage.success('更新成功')
    } else {
      await createUser({
        username: form.username,
        nickname: form.nickname,
        dataOwner: form.dataOwner,
        sharedDataOwners: form.sharedDataOwners.join(','),
        sharedDataFields: form.sharedDataFields.join(','),
        email: form.email,
        password: form.password,
        status: form.status,
      })
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } finally { saving.value = false }
}

/**
 * 删除用户（二次确认）
 * @param {number} id - 要删除的用户 ID
 */
async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定删除该用户？关联的角色信息也会一并清理。', '提示', { type: 'warning' })
    await deleteUser(id)
    ElMessage.success('已删除')
    fetchData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('用户删除失败')
  }
}

/**
 * 打开角色分配弹窗
 * 获取所有角色列表并设置默认选中管理员角色
 * @param {Object} row - 用户行数据
 */
async function openRoleDialog(row) {
  currentUserId.value = row.id
  const [rolesRes, selectedRes] = await Promise.all([getAllRoles(), getUserRoleIds(row.id)])
  allRoles.value = rolesRes.data || []
  selectedRoles.value = (selectedRes.data || []).map(Number)
  roleDialogVisible.value = true
}

/**
 * 保存角色分配
 * 将选中的角色 ID 列表提交到后端
 */
async function handleAssignRoles() {
  assigning.value = true
  try {
    await assignUserRoles(currentUserId.value, selectedRoles.value)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
  } catch {
    ElMessage.error('角色分配失败')
  } finally {
    assigning.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.user-page { max-width: var(--cf-page-max); margin: 0 auto; }
.table-hint { margin-bottom: 12px; color: var(--cf-muted); font-size: var(--cf-text-small); }
.user-pagination { margin-top: 16px; justify-content: flex-end; flex-wrap: wrap; }
.field-permissions { display: grid; width: 100%; gap: 14px; }
.field-permissions section { padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; }
.field-permissions strong { display: block; margin-bottom: 8px; color: var(--el-text-color-primary); }
.field-permissions :deep(.el-checkbox-group) { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 4px 10px; }
.field-permissions :deep(.el-checkbox) { margin-right: 0; }
.field-permissions small { color: var(--el-text-color-secondary); }
</style>
