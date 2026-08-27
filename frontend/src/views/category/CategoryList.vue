<template>
  <el-card shadow="never">
    <div class="heading"><div><h2>自定义分类</h2><p>商品列表和数据源站点共用此目录，支持两级分类。</p></div><el-button v-if="canManage" type="primary" @click="edit()">添加分类</el-button></div>
    <el-alert title="停用后不可用于新的数据源，但仍可筛选历史商品。已使用的分类不能改名或删除，避免历史数据失去关联。" type="info" :closable="false" show-icon />
    <div class="toolbar"><el-input v-model="keyword" clearable placeholder="搜索分类名称" style="max-width:320px" /><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-table :data="filtered" row-key="id" v-loading="loading" :tree-props="{children:'children'}" :default-expand-all="false">
      <el-table-column prop="name" label="分类名称" min-width="280" />
      <el-table-column label="状态" width="180"><template #default="{row}"><el-tag :type="row.effectiveEnabled ? 'success' : 'info'">{{ row.effectiveEnabled ? '启用' : row.enabled ? '上级已停用' : '已停用' }}</el-tag></template></el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="100" />
      <el-table-column v-if="canManage" label="操作" width="250"><template #default="{row}"><el-button v-if="!row.parentId" link type="primary" @click="edit(null,row.id)">添加子分类</el-button><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" :title="form.id ? '编辑分类' : '添加分类'" width="min(500px,94vw)" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent="save">
        <el-form-item label="上级分类"><el-select v-model="form.parentId"><el-option :value="0" label="无（一级分类）" /><el-option v-for="row in parents" :key="row.id" :value="row.id" :label="row.name" /></el-select></el-form-item>
        <el-form-item label="分类名称" required><el-input v-model="form.name" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="排序（越小越靠前）"><el-input-number v-model="form.sortOrder" :min="0" :max="100000" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </el-card>
</template>
<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCategories, createCategory, updateCategory, deleteCategory } from '@/api/category'
import { categoryTree } from '@/data/customCategories'
import { useUserStore } from '@/store/user'
const user = useUserStore(), canManage = computed(() => user.hasPermission('category:manage'))
const rows=ref([]), loading=ref(false), keyword=ref(''), visible=ref(false), saving=ref(false)
const form=reactive({id:null,parentId:0,name:'',enabled:true,sortOrder:0})
const parents=computed(() => rows.value.filter(r => !r.parentId && r.id!==form.id))
const filtered=computed(() => {
  const tree=categoryTree(rows.value,true), term=keyword.value.trim().toLowerCase()
  return tree.filter(n => n.name.toLowerCase().includes(term) || n.children.some(c => c.name.toLowerCase().includes(term)))
    .map(n => ({...n, children:n.name.toLowerCase().includes(term) ? n.children : n.children.filter(c => c.name.toLowerCase().includes(term))}))
})
async function load() { loading.value=true; try { rows.value=(await listCategories()).data || [] } finally { loading.value=false } }
function edit(row, parentId=0) { Object.assign(form,{id:null,parentId,name:'',enabled:true,sortOrder:0},row || {}); visible.value=true }
async function save() {
  if (saving.value) return
  if (!form.name.trim()) return ElMessage.warning('请输入分类名称')
  saving.value=true
  try { await (form.id ? updateCategory(form.id,form) : createCategory(form)); visible.value=false; ElMessage.success('分类已保存'); await load() } finally { saving.value=false }
}
async function remove(row) {
  try { await ElMessageBox.confirm(`删除分类“${row.name}”？已使用的分类只能停用。`,'删除分类',{type:'warning',confirmButtonType:'danger'}); await deleteCategory(row.id); await load(); ElMessage.success('分类已删除') } catch { /* interceptor displays server errors; cancellation is silent */ }
}
onMounted(load)
</script>
<style scoped>
.heading,.toolbar { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:20px; }.heading h2 { margin:0 0 8px; }.heading p { margin:0; color:var(--el-text-color-secondary); }.toolbar { margin-top:20px; justify-content:flex-start; }
</style>
