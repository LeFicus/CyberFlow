<!--
  UserListPage - 用户管理页面
  管理系统用户，提供用户的新增、编辑、删除功能，以及为用户分配角色。
  编辑模式下用户名不可修改，新增时需填写密码。
-->
<template>
  <el-card>
    <template #header>
      用户管理
      <el-button type="primary" size="small" style="float: right;" @click="openDialog()">新增用户</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column prop="email" label="邮箱" width="180" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" @click="openRoleDialog(row)">分配角色</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <!-- 编辑时用户名不可修改 -->
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <!-- 新增时需要设置密码，编辑时不显示密码字段 -->
        <el-form-item label="密码" v-if="!isEdit">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色弹窗：checkbox 多选角色 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="420px">
      <el-checkbox-group v-model="selectedRoles">
        <el-checkbox v-for="r in allRoles" :key="r.id" :label="r.id" :value="r.id">{{ r.roleName }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignRoles">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
/**
 * @fileoverview 用户管理页面
 * @description 提供系统用户的后台管理功能，包含用户 CRUD 和角色分配。
 *              编辑状态下用户名不可修改，新增时需设置初始密码。
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUsers, createUser, updateUser, deleteUser, assignUserRoles, getAllRoles } from '@/api/system'

/** @type {import('vue').Ref<boolean>} 列表加载状态 */
const loading = ref(false)
/** @type {import('vue').Ref<boolean>} 保存按钮加载状态 */
const saving = ref(false)
/** @type {import('vue').Ref<Array>} 用户列表数据 */
const tableData = ref([])
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
const form = reactive({ username: '', nickname: '', email: '', password: '', status: 1 })

/**
 * 获取用户列表（不分页，取前 100 条）
 */
async function fetchData() {
  loading.value = true
  try {
    const res = await getUsers({ page: 1, size: 100 })
    tableData.value = res.data.records || []
  } finally { loading.value = false }
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
    Object.assign(form, { username: row.username, nickname: row.nickname, email: row.email, password: '', status: row.status })
  } else {
    currentUserId.value = null
    Object.assign(form, { username: '', nickname: '', email: '', password: '', status: 1 })
  }
  dialogVisible.value = true
}

/**
 * 保存用户（新增或更新）
 * 编辑模式下仅当密码非空时才将其包含在请求体中
 */
async function handleSave() {
  saving.value = true
  try {
    if (isEdit.value) {
      await updateUser(currentUserId.value, { ...form, password: form.password || undefined })
      ElMessage.success('更新成功')
    } else {
      await createUser(form)
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
  await ElMessageBox.confirm('确定删除该用户？', '提示', { type: 'warning' })
  await deleteUser(id)
  ElMessage.success('已删除')
  fetchData()
}

/**
 * 打开角色分配弹窗
 * 获取所有角色列表并设置默认选中管理员角色
 * @param {Object} row - 用户行数据
 */
async function openRoleDialog(row) {
  currentUserId.value = row.id
  selectedRoles.value = [1] // 默认选中管理员
  allRoles.value = (await getAllRoles()).data || []
  roleDialogVisible.value = true
}

/**
 * 保存角色分配
 * 将选中的角色 ID 列表提交到后端
 */
async function handleAssignRoles() {
  await assignUserRoles(currentUserId.value, selectedRoles.value)
  ElMessage.success('角色分配成功')
  roleDialogVisible.value = false
}

onMounted(fetchData)
</script>
