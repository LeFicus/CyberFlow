<!--
  DataPanel - 统一的内容面板。
  替代“用 el-card header 当页面标题”的写法；页面标题请用 PageHeader。
-->
<template>
  <section class="cf-panel">
    <header v-if="title || description || $slots.header || $slots.actions" class="cf-panel__header">
      <div class="cf-panel__heading">
        <h2 v-if="title" class="cf-panel__title">{{ title }}</h2>
        <p v-if="description" class="cf-panel__description">{{ description }}</p>
        <slot name="header" />
      </div>
      <div v-if="$slots.actions" class="cf-panel__actions">
        <slot name="actions" />
      </div>
    </header>

    <div class="cf-panel__body" :class="{ 'is-flush': !padded }">
      <slot />
    </div>

    <footer v-if="$slots.footer" class="cf-panel__footer">
      <slot name="footer" />
    </footer>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  /** false 时内容区去掉内边距，适合表格贴边 */
  padded: { type: Boolean, default: true },
})
</script>

<style scoped>
.cf-panel {
  min-width: 0;
  border: 1px solid var(--cf-line);
  border-radius: var(--cf-radius-lg);
  background: var(--cf-surface);
  box-shadow: var(--cf-shadow-sm);
}

.cf-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cf-space-4);
  padding: 20px 24px;
  border-bottom: 1px solid var(--cf-line-soft);
}

.cf-panel__heading {
  min-width: 0;
}

.cf-panel__title {
  color: var(--cf-ink);
  font-size: var(--cf-text-h3);
  font-weight: 600;
  letter-spacing: -.01em;
  line-height: 1.4;
}

.cf-panel__description {
  margin-top: 6px;
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
  line-height: 1.6;
}

.cf-panel__actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--cf-space-2);
  flex-wrap: wrap;
}

.cf-panel__body {
  min-width: 0;
  padding: 24px;
}

.cf-panel__body.is-flush {
  padding: 0;
}

.cf-panel__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-3);
  padding: 16px 24px;
  border-top: 1px solid var(--cf-line-soft);
}

@media (max-width: 900px) {
  .cf-panel__header {
    align-items: stretch;
    flex-direction: column;
  }

  .cf-panel__actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .cf-panel__header,
  .cf-panel__body,
  .cf-panel__footer {
    padding-right: 16px;
    padding-left: 16px;
  }
}
</style>
