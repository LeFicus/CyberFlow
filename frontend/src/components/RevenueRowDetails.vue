<template>
  <div class="revenue-row-details">
    <div class="detail-grid">
      <div v-for="item in details" :key="item.label" class="detail-item">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <section class="category-section">
      <div class="classification-heading">
        <strong>站点商品分类明细</strong>
        <span>按站点分类标签占比</span>
      </div>
      <div v-if="categoryItems.length" class="category-content">
        <VChart :option="categoryChartOption" autoresize class="category-chart" />
        <div class="category-legend">
          <div v-for="(item, index) in categoryItems" :key="item.category" class="category-legend-item">
            <i :style="{ background: chartColors[index % chartColors.length] }"></i>
            <span :title="item.category">{{ item.category }}</span>
            <strong>{{ formatNumber(item.site_count) }} 站 · {{ formatRatio(item.ratio) }}</strong>
          </div>
        </div>
      </div>
      <el-empty v-else :image-size="46" description="暂无商品分类数据" />
    </section>

    <section class="classification-section">
      <div class="classification-heading">
        <strong>订单站点归属</strong>
        <span>按去重订单分类占比</span>
      </div>
      <div class="classification-grid">
        <div v-for="item in normalizedBreakdown" :key="item.type" class="classification-item">
          <div class="classification-copy">
            <el-tag :type="siteTagType(item.type)" effect="plain" size="small">{{ item.label || siteTagLabel(item.type) }}</el-tag>
            <strong>{{ formatNumber(item.orders) }} 笔 · {{ formatRatio(item.ratio) }}</strong>
          </div>
          <div class="classification-track"><i :style="{ width: `${clampRatio(item.ratio)}%` }"></i></div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { siteTagLabel, siteTagType } from '@/utils/sitePresentation'

use([CanvasRenderer, PieChart, TooltipComponent])

const props = defineProps({
  details: { type: Array, default: () => [] },
  breakdown: { type: Array, default: () => [] },
  categoryBreakdown: { type: Array, default: () => [] },
})

const chartColors = ['#536ff1', '#45b98c', '#f0a44b', '#8a64e8', '#e06f83', '#47a7cf', '#8cac48', '#d58449', '#697a9c', '#b46dac']
const categoryItems = computed(() => props.categoryBreakdown
  .map(item => ({
    category: String(item?.category || '未分类'),
    site_count: Number(item?.site_count || 0),
    ratio: Number(item?.ratio || 0),
  }))
  .filter(item => item.site_count > 0))
const categoryChartOption = computed(() => ({
  color: chartColors,
  tooltip: {
    trigger: 'item',
    renderMode: 'richText',
    formatter: params => `${params.name}\n${formatNumber(params.value)} 站 · ${Number(params.percent || 0).toFixed(2)}%`,
  },
  series: [{
    type: 'pie',
    radius: ['48%', '74%'],
    center: ['50%', '50%'],
    minAngle: 2,
    avoidLabelOverlap: true,
    itemStyle: { borderColor: '#fff', borderWidth: 2, borderRadius: 4 },
    label: { show: false },
    emphasis: { scaleSize: 7 },
    data: categoryItems.value.map(item => ({ name: item.category, value: item.site_count })),
  }],
}))

const normalizedBreakdown = computed(() => [0, 1, 2].map(type => {
  const item = props.breakdown.find(entry => Number(entry?.type) === type) || {}
  return { type, label: item.label || siteTagLabel(type), orders: Number(item.orders || 0), ratio: Number(item.ratio || 0) }
}))

const clampRatio = value => Math.min(100, Math.max(0, Number(value || 0)))
const formatNumber = value => Number(value || 0).toLocaleString('en-US')
const formatRatio = value => `${Number(value || 0).toFixed(2)}%`
</script>

<style scoped>
.revenue-row-details { padding: 18px 22px 20px; background: #f8faff; }
.detail-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.detail-item { min-width: 0; padding: 11px 13px; border: 1px solid #e8edf6; border-radius: 9px; background: #fff; }
.detail-item span { display: block; color: #8a96a8; font-size: 11px; }
.detail-item strong { display: block; margin-top: 5px; overflow: hidden; color: #30405c; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.category-section { margin-top: 14px; padding: 14px 16px; border: 1px solid #e8edf6; border-radius: 10px; background: #fff; }
.category-content { display: grid; grid-template-columns: minmax(210px, .7fr) minmax(280px, 1.3fr); gap: 18px; align-items: center; }
.category-chart { width: 100%; height: 220px; }
.category-legend { display: grid; max-height: 210px; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; overflow: auto; padding-right: 5px; }
.category-legend-item { display: grid; min-width: 0; grid-template-columns: 8px minmax(0, 1fr) auto; gap: 7px; align-items: center; }
.category-legend-item i { width: 8px; height: 8px; border-radius: 50%; }
.category-legend-item span { overflow: hidden; color: #65728a; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.category-legend-item strong { color: #34425a; font-size: 10px; white-space: nowrap; }
.classification-section { margin-top: 14px; }
.classification-heading { display: flex; align-items: baseline; gap: 8px; margin-bottom: 9px; }
.classification-heading strong { color: #34425a; font-size: 13px; }
.classification-heading span { color: #95a0b1; font-size: 10px; }
.classification-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.classification-item { padding: 11px 13px; border: 1px solid #e8edf6; border-radius: 9px; background: #fff; }
.classification-copy { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.classification-copy strong { color: #53617a; font-size: 11px; white-space: nowrap; }
.classification-track { height: 5px; margin-top: 9px; overflow: hidden; border-radius: 999px; background: #edf1f7; }
.classification-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #647cf6, #72a1ff); }
@media (max-width: 900px) { .detail-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.classification-grid { grid-template-columns: 1fr; }.category-content { grid-template-columns: 1fr; }.category-chart { height: 190px; }.category-legend { grid-template-columns: 1fr; } }
</style>
