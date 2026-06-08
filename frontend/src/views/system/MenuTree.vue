<template>
  <el-card>
    <template #header>菜单管理</template>

    <el-table :data="treeData" v-loading="loading" row-key="id" stripe default-expand-all>
      <el-table-column prop="menu_name" label="菜单名称" min-width="200" />
      <el-table-column prop="menu_type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.menu_type === 0" type="info">目录</el-tag>
          <el-tag v-else-if="row.menu_type === 1">菜单</el-tag>
          <el-tag v-else type="warning">按钮</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="perms" label="权限标识" min-width="200" />
      <el-table-column prop="path" label="路由路径" width="200" />
      <el-table-column prop="icon" label="图标" width="120" />
      <el-table-column prop="sort_order" label="排序" width="80" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMenuTree } from '@/api/system'

const loading = ref(false)
const treeData = ref([])

// 树形数据扁平化为表格
function flattenTree(nodes, level = 0) {
  const result = []
  nodes.forEach(node => {
    result.push({ ...node, menu_name: '  '.repeat(level) + node.menu_name })
    if (node.children?.length) {
      result.push(...flattenTree(node.children, level + 1))
    }
  })
  return result
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getMenuTree()
    treeData.value = flattenTree(res.data || [])
  } finally { loading.value = false }
}

onMounted(fetchData)
</script>
