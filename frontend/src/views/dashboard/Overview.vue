<template>
  <div>
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6" v-for="card in stats" :key="card.label">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="12">
        <el-card>
          <template #header>订单变化趋势（近 30 天）</template>
          <v-chart :option="orderTrendOption" style="height: 320px;" autoresize />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>收入变化趋势（近 30 天）</template>
          <v-chart :option="indexTrendOption" style="height: 320px;" autoresize />
        </el-card>
      </el-col>
    </el-row> 
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { getOverview, getCharts } from '@/api/dashboard'

use([CanvasRenderer, LineChart, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const overview = ref({})
const charts = ref({})

const stats = computed(() => [
  { label: '历史站点数', value: overview.value.total_sites ?? '-', color: '#409EFF' },
  { label: '历史订单数', value: overview.value.total_orders ?? '-', color: '#67C23A' },
  { label: '历史收入', value: overview.value.total_products ?? '-', color: '#E6A23C' },
  { label: '本月收入', value: overview.value.total_products ?? '-', color: '#E6A23C' },
  { label: '今日收入', value: overview.value.today_orders ?? '-', color: '#F56C6C' },
  { label: '今日订单数', value: overview.value.today_orders ?? '-', color: '#F56C6C' },
  { label: '本周建站数', value: overview.value.today_orders ?? '-', color: '#F56C6C' },
  { label: '本周商品大于4万站点数', value: overview.value.today_orders ?? '-', color: '#F56C6C' },
])

const orderTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 60, right: 40, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: charts.value.order_trend?.map(i => i.date)?.map(d => d.slice(5)) || [] },
  yAxis: { type: 'value' },
  series: [
    { name: '订单数', type: 'line', data: charts.value.order_trend?.map(i => i.count) || [], smooth: true, itemStyle: { color: '#409EFF' } },
    { name: '金额', type: 'line', data: charts.value.order_trend?.map(i => i.amount) || [], smooth: true, itemStyle: { color: '#67C23A' } },
  ],
}))

const indexTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 60, right: 40, top: 20, bottom: 40 },
  xAxis: { type: 'category', data: charts.value.index_trend?.map(i => i.date.slice(5)) || [] },
  yAxis: { type: 'value' },
  series: [
    { name: '收录数', type: 'line', data: charts.value.index_trend?.map(i => i.total_index) || [], smooth: true, areaStyle: { opacity: 0.15 } },
  ],
}))



onMounted(async () => {
  overview.value = (await getOverview()).data
  charts.value = (await getCharts()).data
})
</script>

<style scoped>
.stat-item { text-align: center; padding: 8px 0; }
.stat-label { font-size: 14px; color: #909399; margin-bottom: 8px; }
.stat-value { font-size: 28px; font-weight: bold; }
</style>
