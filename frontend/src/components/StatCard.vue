<!--
  StatCard - 统一的关键指标卡。
  只负责视觉；数值格式化、权限与数据获取仍由页面负责。
-->
<template>
  <article class="cf-stat" :class="`is-${tone}`">
    <div class="cf-stat__top">
      <span class="cf-stat__label">{{ label }}</span>
      <span v-if="icon" class="cf-stat__icon" aria-hidden="true">
        <el-icon><component :is="icon" /></el-icon>
      </span>
    </div>

    <p class="cf-stat__value cf-num">
      <slot name="value">{{ value }}</slot>
    </p>

    <div v-if="trend || note || $slots.note" class="cf-stat__foot">
      <span v-if="trend" class="cf-stat__trend" :class="`is-${trendTone}`">{{ trend }}</span>
      <span class="cf-stat__note">
        <slot name="note">{{ note }}</slot>
      </span>
    </div>
  </article>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '—' },
  note: { type: String, default: '' },
  trend: { type: String, default: '' },
  /** up | down | neutral */
  trendTone: { type: String, default: 'neutral' },
  /** blue | violet | amber | green | neutral */
  tone: { type: String, default: 'blue' },
  /** Element Plus 图标组件，或全局注册的图标名称 */
  icon: { type: [Object, Function, String], default: null },
})
</script>

<style scoped>
.cf-stat {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 18px 20px 16px;
  border: 1px solid var(--cf-line);
  border-radius: var(--cf-radius);
  background: var(--cf-surface);
  box-shadow: var(--cf-shadow-sm);
  transition:
    transform var(--cf-dur-fast) var(--cf-ease),
    box-shadow var(--cf-dur-fast) var(--cf-ease);
}

.cf-stat:hover {
  transform: translateY(-1px);
  box-shadow: var(--cf-shadow-md);
}

.cf-stat__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-3);
}

.cf-stat__label {
  color: var(--cf-muted);
  font-size: var(--cf-text-small);
}

.cf-stat__icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: none;
  place-items: center;
  border-radius: 10px;
  color: var(--cf-primary);
  background: var(--cf-primary-soft);
  font-size: 16px;
}

.cf-stat.is-violet .cf-stat__icon {
  color: var(--cf-accent);
  background: var(--cf-accent-soft);
}

.cf-stat.is-amber .cf-stat__icon {
  color: var(--cf-warning-text);
  background: var(--cf-warning-soft);
}

.cf-stat.is-green .cf-stat__icon {
  color: var(--cf-success-text);
  background: var(--cf-success-soft);
}

.cf-stat.is-neutral .cf-stat__icon {
  color: var(--cf-muted);
  background: var(--cf-surface-sunken);
}

.cf-stat.is-red .cf-stat__icon {
  color: var(--cf-danger-text);
  background: var(--cf-danger-soft);
}

.cf-stat__value {
  margin: 14px 0 10px;
  overflow: hidden;
  color: var(--cf-ink);
  font-size: 30px;
  font-weight: 600;
  letter-spacing: -.03em;
  line-height: 1.1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cf-stat__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--cf-space-2);
  margin-top: auto;
  color: var(--cf-subtle);
  font-size: var(--cf-text-small);
}

.cf-stat__trend {
  font-weight: 600;
}

.cf-stat__trend.is-up {
  color: var(--cf-success-text);
}

.cf-stat__trend.is-down {
  color: var(--cf-danger-text);
}

.cf-stat__trend.is-neutral {
  color: var(--cf-primary);
}

.cf-stat__note {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 640px) {
  .cf-stat {
    padding: 15px;
  }

  .cf-stat__value {
    margin: 10px 0 8px;
    font-size: 24px;
  }
}
</style>
