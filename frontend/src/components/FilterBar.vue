<!--
  FilterBar - 列表页筛选区容器。
  高频条件放默认插槽；低频条件放 advanced 插槽，点击“更多筛选”展开。
-->
<template>
  <section class="cf-filter">
    <div class="cf-filter__row">
      <div class="cf-filter__fields">
        <slot />
      </div>
      <div v-if="$slots.actions || $slots.advanced" class="cf-filter__actions">
        <slot name="actions" />
        <el-button v-if="$slots.advanced" text @click="expanded = !expanded">
          {{ expanded ? collapseLabel : advancedLabel }}
        </el-button>
      </div>
    </div>

    <div v-if="$slots.advanced" v-show="expanded" class="cf-filter__advanced">
      <slot name="advanced" />
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  advancedLabel: { type: String, default: '更多筛选' },
  collapseLabel: { type: String, default: '收起筛选' },
  defaultExpanded: { type: Boolean, default: false },
})

const expanded = ref(props.defaultExpanded)
</script>

<style scoped>
.cf-filter {
  padding: 16px;
  border: 1px solid var(--cf-line);
  border-radius: var(--cf-radius);
  background: var(--cf-surface-sunken);
}

.cf-filter__row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--cf-space-3);
}

.cf-filter__fields {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--cf-space-3);
}

.cf-filter__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--cf-space-2);
}

.cf-filter__advanced {
  margin-top: var(--cf-space-4);
  padding-top: var(--cf-space-4);
  border-top: 1px dashed var(--cf-line-strong);
}

@media (max-width: 900px) {
  .cf-filter__row {
    align-items: stretch;
    flex-direction: column;
  }

  .cf-filter__actions {
    justify-content: flex-end;
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .cf-filter {
    padding: 12px;
  }

  .cf-filter__fields {
    align-items: stretch;
    flex-direction: column;
  }

  .cf-filter__fields > :deep(*) {
    width: 100%;
  }

  .cf-filter__actions {
    justify-content: stretch;
  }
}
</style>
