<!--
  MenuTreePage - 菜单管理页面
  展示系统中完整的菜单树结构（目录/菜单/按钮），通过递归展平后的表格形式呈现。
  每行显示菜单名称（带缩进层级）、类型、权限标识、路由路径、图标、排序和状态。
-->
<template>
  <el-card>
    <template #header>菜单管理</template>

    <!-- 使用 default-expand-all 展开所有树形行，
         以 row-key="id" 确保树形表格正确渲染 -->
    <el-table :data="treeData" v-loading="loading" row-key="id" stripe default-expand-all>
      <el-table-column prop="menuName" label="菜单名称" min-width="200" />
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
    </el-table>
  </el-card>
</template>

<script setup>
/**
 * @fileoverview 菜单管理页面
 * @description 将后端返回的树形菜单数据展平为表格行，通过缩进前缀展示层级关系。
 *              仅作展示用途，菜单的增删改需通过后端 API 操作。
 */
import { ref, onMounted } from 'vue'
import { getMenuTree } from '@/api/system'

/** @type {import('vue').Ref<boolean>} 列表加载状态 */
const loading = ref(false)
/** @type {import('vue').Ref<Array>} 展平后的菜单列表数据 */
const treeData = ref([])

/**
 * 将树形菜单数据递归展平为表格行
 * 子节点通过空格缩进前缀 visual 展示层级关系
 * @param {Array} nodes - 菜单树节点数组
 * @param {number} [level=0] - 当前递归层级（用于缩进计算）
 * @returns {Array} 展平后的菜单列表
 */
function flattenTree(nodes, level = 0) {
  const result = []
  nodes.forEach(node => {
    result.push({ ...node, menuName: '  '.repeat(level) + (node.menuName || '') })
    if (node.children?.length) {
      result.push(...flattenTree(node.children, level + 1))
    }
  })
  return result
}

/**
 * 获取菜单树数据并展平为表格数据
 */
async function fetchData() {
  loading.value = true
  try {
    const res = await getMenuTree()
    treeData.value = flattenTree(res.data || [])
  } finally { loading.value = false }
}

onMounted(fetchData)
</script>
