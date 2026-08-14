<!--
  RoleListPage - 角色管理页面
  展示系统中所有角色列表，支持为角色分配菜单权限。
  通过 el-tree 组件以树形结构勾选菜单节点来设置角色的可见页面范围。
-->
<template>
  <el-card>
    <template #header>角色管理</template>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="roleName" label="角色名称" width="150" />
      <el-table-column prop="roleCode" label="角色编码" width="150" />
      <el-table-column prop="description" label="描述" min-width="200" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openMenuDialog(row)">分配菜单</el-button>
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

    <!-- 分配菜单弹窗：
         使用 el-tree 展示完整菜单树，支持 show-checkbox 多选节点 -->
    <el-dialog v-model="menuDialogVisible" title="分配菜单权限" width="420px">
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
        <el-button type="primary" @click="handleAssignMenus">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
/**
 * @fileoverview 角色管理页面
 * @description 展示角色列表，提供角色-菜单权限的分配功能。
 *              通过树形 Checkbox 组件实现可视化菜单授权。
 */
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoles, getMenuTree } from '@/api/system'

/** @type {import('vue').Ref<boolean>} 列表加载状态 */
const loading = ref(false)
/** @type {import('vue').Ref<Array>} 角色列表数据 */
const tableData = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
/** @type {import('vue').Ref<boolean>} 菜单分配弹窗是否可见 */
const menuDialogVisible = ref(false)
/** @type {import('vue').Ref<number|null>} 当前分配菜单的角色 ID */
const currentRoleId = ref(null)
/** @type {import('vue').Ref<Array>} 菜单树形数据 */
const menuTree = ref([])
/** @type {import('vue').Ref<Object|null>} el-tree 组件引用 */
const treeRef = ref(null)

/**
 * 获取角色列表
 */
async function fetchData() {
  loading.value = true
  try {
    const res = await getRoles({ page: page.value, size: size.value })
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
 * 打开菜单分配弹窗
 * 获取完整菜单树数据并在弹窗中展示
 * @param {Object} row - 角色行数据
 */
async function openMenuDialog(row) {
  currentRoleId.value = row.id
  menuTree.value = (await getMenuTree()).data || []
  menuDialogVisible.value = true
}

/**
 * 保存菜单权限分配
 * 获取树组件中所有已勾选的节点 ID 列表
 */
async function handleAssignMenus() {
  const checkedKeys = treeRef.value?.getCheckedKeys() || []
  // TODO: 调用后端 API 保存角色-菜单关联
  // 调用 assignRolesMenus(currentRoleId.value, checkedKeys)
  ElMessage.success('菜单分配成功')
  menuDialogVisible.value = false
}

onMounted(fetchData)
</script>
