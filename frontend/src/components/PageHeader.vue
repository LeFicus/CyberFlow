<!--
  PageHeader - 统一的页面标题区。
  所有路由页面顶部使用；标题区不再由 el-card header 承担。
-->
<template>
  <header class="cf-page-header">
    <div class="cf-page-header__copy">
      <p v-if="eyebrow" class="cf-page-header__eyebrow">{{ eyebrow }}</p>
      <h1 class="cf-page-header__title">{{ title }}</h1>
      <p v-if="description" class="cf-page-header__description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="cf-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  /** 英文小标签，全大写，仅用于建立层级 */
  eyebrow: { type: String, default: '' },
  /** 页面主标题 */
  title: { type: String, required: true },
  /** 一句话说明页面用途 */
  description: { type: String, default: '' },
})
</script>

<style scoped>
.cf-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--cf-space-6);
  margin-bottom: var(--cf-space-6);
}

.cf-page-header__copy {
  min-width: 0;
}

.cf-page-header__eyebrow {
  margin-bottom: 8px;
  color: var(--cf-primary-hover);
  font-size: var(--cf-text-micro);
  font-weight: 700;
  letter-spacing: .14em;
  text-transform: uppercase;
}

.cf-page-header__title {
  color: var(--cf-ink);
  font-size: var(--cf-text-h1);
  font-weight: 600;
  letter-spacing: -.02em;
  line-height: 1.3;
}

.cf-page-header__description {
  margin-top: 8px;
  color: var(--cf-muted);
  font-size: var(--cf-text-body);
  line-height: 1.6;
}

.cf-page-header__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--cf-space-2);
}

@media (max-width: 900px) {
  .cf-page-header {
    align-items: flex-start;
    flex-direction: column;
    gap: var(--cf-space-3);
  }

  .cf-page-header__actions {
    width: 100%;
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .cf-page-header__title {
    font-size: 22px;
  }
}
</style>
