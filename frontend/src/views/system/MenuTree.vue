<!--
  MenuTreePage - 菜单与权限管理页面
  支持菜单/目录/按钮权限的新增、编辑、删除和查看。
-->
<template>
  <el-card>
    <template #header>
      <span>菜单管理</span>
      <el-button type="primary" size="small" style="float: right" @click="openDialog()">新增菜单</el-button>
    </template>

    <el-table :data="treeData" v-loading="loading" row-key="id" stripe>
      <el-table-column prop="menuNameDisplay" label="菜单名称" min-width="200" />
      <el-table-column prop="menuType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.menuType === 0" type="info">目录</el-tag>
          <el-tag v-else-if="row.menuType === 1">菜单</el-tag>
          <el-tag v-else type="warning">按钮</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="perms" label="权限标识" min-width="200" />
      <el-table-column prop="path" label="路由路径" width="200" />
      <el-table-column prop="icon" label="图标" width="120" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑菜单' : '新增菜单'" width="560px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="父级菜单">
          <el-select v-model="form.parentId" style="width: 100%">
            <el-option label="顶级目录" :value="0" />
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.menuNameDisplay" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单名称" required>
          <el-input v-model="form.menuName" />
        </el-form-item>
        <el-form-item label="菜单类型">
          <el-radio-group v-model="form.menuType">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.perms" placeholder="例如 system:user:create" />
        </el-form-item>
        <el-form-item label="路由路径">
          <el-input v-model="form.path" placeholder="目录/菜单填写，按钮可留空" />
        </el-form-item>
        <el-form-item label="组件路径">
          <el-input v-model="form.component" placeholder="例如 system/UserList" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
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
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createMenu, deleteMenu, getMenuTree, updateMenu } from '@/api/system'

const loading = ref(false)
const saving = ref(false)
const treeData = ref([])
const parentOptions = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentMenuId = ref(null)
const form = reactive({
  parentId: 0,
  menuName: '',
  menuType: 1,
  perms: '',
  path: '',
  component: '',
  icon: '',
  sortOrder: 1,
  status: 1,
})

function flattenTree(nodes, level = 0, result = []) {
  nodes.forEach(node => {
    result.push({ ...node, menuNameDisplay: `${'　'.repeat(level)}${node.menuName || ''}` })
    if (node.children?.length) flattenTree(node.children, level + 1, result)
  })
  return result
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getMenuTree()
    const flattened = flattenTree(res.data || [])
    treeData.value = flattened
    parentOptions.value = flattened.filter(item => item.menuType !== 2)
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  isEdit.value = Boolean(row)
  currentMenuId.value = row?.id || null
  Object.assign(form, row
    ? {
        parentId: row.parentId,
        menuName: row.menuName,
        menuType: row.menuType,
        perms: row.perms || '',
        path: row.path || '',
        component: row.component || '',
        icon: row.icon || '',
        sortOrder: row.sortOrder || 0,
        status: row.status,
      }
    : {
        parentId: 0,
        menuName: '',
        menuType: 1,
        perms: '',
        path: '',
        component: '',
        icon: '',
        sortOrder: 1,
        status: 1,
      })
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.menuName.trim()) {
    ElMessage.warning('请填写菜单名称')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) await updateMenu(currentMenuId.value, { ...form })
    else await createMenu({ ...form })
    ElMessage.success(isEdit.value ? '菜单更新成功' : '菜单创建成功')
    dialogVisible.value = false
    await fetchData()
  } catch {
    ElMessage.error(isEdit.value ? '菜单更新失败' : '菜单创建失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除菜单“${row.menuName}”？`, '提示', { type: 'warning' })
    await deleteMenu(row.id)
    ElMessage.success('菜单已删除')
    await fetchData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('菜单删除失败')
  }
}

onMounted(fetchData)
</script>
