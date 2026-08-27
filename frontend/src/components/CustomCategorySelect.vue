<template>
  <el-tree-select :model-value="modelValue" :data="options" :multiple="multiple" :show-checkbox="multiple"
    check-strictly filterable clearable collapse-tags collapse-tags-tooltip :loading="loading"
    :placeholder="multiple ? '选择一个或多个分类' : '选择自定义分类'" style="width:100%"
    @update:model-value="$emit('update:modelValue', $event)" @visible-change="open" />
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import { listCategories } from '@/api/category'
import { categoryTree } from '@/data/customCategories'
const props = defineProps({ modelValue: [String, Array], multiple: Boolean, includeDisabled: Boolean })
defineEmits(['update:modelValue'])
const rows = ref([]), loading = ref(false)
const options = computed(() => {
  const tree = categoryTree(rows.value, props.includeDisabled)
  const available = new Set(tree.flatMap(n => [n.value, ...n.children.map(c => c.value)]))
  const selected = Array.isArray(props.modelValue) ? props.modelValue : [props.modelValue]
  for (const value of selected.filter(Boolean)) if (!available.has(value)) tree.push({ value, label: `${value}（历史分类）`, disabled: !props.includeDisabled })
  return tree
})
async function load() {
  if (loading.value) return
  loading.value = true
  try { rows.value = (await listCategories()).data || [] } finally { loading.value = false }
}
function open(visible) { if (visible) load() }
onMounted(load)
</script>
