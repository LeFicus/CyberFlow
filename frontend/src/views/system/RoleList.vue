<!--
  RoleListPage - 角色管理页面
  提供角色 CRUD 和角色菜单/按钮权限分配。
-->
<template>
  <el-card>
    <template #header>
      <span>角色管理</span>
      <el-button type="primary" size="small" style="float: right" @click="openDialog()">新增角色</el-button>
    </template>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="roleName" label="角色名称" width="150" />
      <el-table-column prop="roleCode" label="角色编码" width="170" />
      <el-table-column prop="description" label="描述" min-width="200" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="primary" @click="openMenuDialog(row)">分配菜单</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end;"
      v-model:current-page="page" :page-size="size"
      :page-sizes="[10, 20, 50, 100]"
      :total="total" layout="total, sizes, prev, pager, next"
      @current-change="fetchData"
      @size-change="handleSizeChange"
    />

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input v-model="form.roleCode" :disabled="isEdit" placeholder="例如 ROLE_OPERATOR" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
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

    <el-dialog v-model="menuDialogVisible" title="分配菜单权限" width="460px">
      <el-tree
        ref="treeRef"
        :data="menuTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'menuName', children: 'children' }"
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssignMenus">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignRoleMenus,
  createRole,
  deleteRole,
  getMenuTree,
  getRoleMenuIds,
  getRoles,
  updateRole,
} from '@/api/system'

const loading = ref(false)
const saving = ref(false)
const assigning = ref(false)
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const menuDialogVisible = ref(false)
const isEdit = ref(false)
const currentRoleId = ref(null)
const menuTree = ref([])
const treeRef = ref(null)
const form = reactive({ roleName: '', roleCode: '', description: '', status: 1 })

async function fetchData() {
  loading.value = true
  try {
    const res = await getRoles({ page: page.value, size: size.value })
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

function handleSizeChange(value) {
  size.value = value
  page.value = 1
  fetchData()
}

function openDialog(row) {
  isEdit.value = Boolean(row)
  currentRoleId.value = row?.id || null
  Object.assign(form, row
    ? { roleName: row.roleName, roleCode: row.roleCode, description: row.description || '', status: row.status }
    : { roleName: '', roleCode: '', description: '', status: 1 })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.roleName.trim() || !form.roleCode.trim()) {
    ElMessage.warning('请填写角色名称和角色编码')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) await updateRole(currentRoleId.value, { ...form })
    else await createRole({ ...form })
    ElMessage.success(isEdit.value ? '角色更新成功' : '角色创建成功')
    dialogVisible.value = false
    await fetchData()
  } catch {
    ElMessage.error(isEdit.value ? '角色更新失败' : '角色创建失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除角色“${row.roleName}”？`, '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('角色已删除')
    if (tableData.value.length === 1 && page.value > 1) page.value -= 1
    await fetchData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('角色删除失败')
  }
}

async function openMenuDialog(row) {
  currentRoleId.value = row.id
  try {
    const [treeRes, selectedRes] = await Promise.all([getMenuTree(), getRoleMenuIds(row.id)])
    menuTree.value = treeRes.data || []
    menuDialogVisible.value = true
    await nextTick()
    treeRef.value?.setCheckedKeys((selectedRes.data || []).map(Number), false)
  } catch {
    ElMessage.error('加载角色菜单权限失败')
  }
}

async function handleAssignMenus() {
  assigning.value = true
  try {
    await assignRoleMenus(currentRoleId.value, treeRef.value?.getCheckedKeys(false) || [])
    ElMessage.success('菜单权限已保存')
    menuDialogVisible.value = false
  } catch {
    ElMessage.error('菜单权限保存失败')
  } finally {
    assigning.value = false
  }
}

onMounted(fetchData)
</script>
