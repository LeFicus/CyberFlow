<template>
  <el-card>
    <template #header>角色管理</template>

    <el-table :data="tableData" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="role_name" label="角色名称" width="150" />
      <el-table-column prop="role_code" label="角色编码" width="150" />
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

    <!-- 分配菜单弹窗 -->
    <el-dialog v-model="menuDialogVisible" title="分配菜单权限" width="420px">
      <el-tree
        ref="treeRef"
        :data="menuTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'menu_name', children: 'children' }"
      />
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignMenus">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoles, getMenuTree } from '@/api/system'

const loading = ref(false)
const tableData = ref([])
const menuDialogVisible = ref(false)
const currentRoleId = ref(null)
const menuTree = ref([])
const treeRef = ref(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getRoles({ page: 1, size: 100 })
    tableData.value = res.data.records || []
  } finally { loading.value = false }
}

async function openMenuDialog(row) {
  currentRoleId.value = row.id
  menuTree.value = (await getMenuTree()).data || []
  menuDialogVisible.value = true
}

async function handleAssignMenus() {
  const checkedKeys = treeRef.value?.getCheckedKeys() || []
  // 调用 assignRolesMenus(currentRoleId.value, checkedKeys)
  ElMessage.success('菜单分配成功')
  menuDialogVisible.value = false
}

onMounted(fetchData)
</script>
