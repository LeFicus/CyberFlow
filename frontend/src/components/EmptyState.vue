<!--
  EmptyState - 统一空状态。
  页面需说明“为什么空”和“下一步做什么”，操作放默认插槽。
-->
<template>
  <div class="cf-empty" :class="{ 'is-compact': compact }" role="status">
    <span class="cf-empty__icon" aria-hidden="true">
      <el-icon><component :is="resolvedIcon" /></el-icon>
    </span>
    <h3 class="cf-empty__title">{{ title }}</h3>
    <p v-if="description" class="cf-empty__description">{{ description }}</p>
    <div v-if="$slots.default" class="cf-empty__actions">
      <slot />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Box } from '@element-plus/icons-vue'

const props = defineProps({
  title: { type: String, default: '暂无数据' },
  description: { type: String, default: '' },
  icon: { type: Object, default: null },
  compact: { type: Boolean, default: false },
})

const resolvedIcon = computed(() => props.icon || Box)
</script>

<style scoped>
.cf-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 48px 24px;
  text-align: center;
}

.cf-empty.is-compact {
  padding: 24px 16px;
}

.cf-empty__icon {
  display: grid;
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  place-items: center;
  border-radius: 50%;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
  font-size: 26px;
}

.is-compact .cf-empty__icon {
  width: 44px;
  height: 44px;
  margin-bottom: 12px;
  font-size: 20px;
}

.cf-empty__title {
  color: var(--cf-ink);
  font-size: var(--cf-text-body);
  font-weight: 600;
}

.cf-empty__description {
  max-width: 420px;
  margin-top: 6px;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
  line-height: 1.6;
}

.cf-empty__actions {
  display: flex;
  align-items: center;
  gap: var(--cf-space-2);
  margin-top: 16px;
}
</style>
