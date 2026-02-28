<script setup>
import * as echarts from "echarts";
import {onMounted, onUnmounted, watch, nextTick} from "vue";
import {defaultOption, doubleSeries, singleSeries} from "@/echarts";

const charts = []

const props = defineProps({
  data: Object
})

const localTimeLine = list => list.map(item => new Date(item.timestamp).toLocaleString())

function updateCpuUsage(list) {
  const chart = charts[0]
  if (!chart || !list || list.length === 0) return
  let data = list.map(item => {
    // 处理可能的字段名不一致问题
    const cpuUsage = item.cpuUsage || item.cupUsage || 0;
    return (cpuUsage * 100).toFixed(1);
  });
  const option = defaultOption('CPU(%)', localTimeLine(list))
  singleSeries(option, 'CPU使用率(%)', data, ['#72c4fe', '#72d5fe', '#2b6fd733'])
  if (chart && typeof chart.setOption === 'function') {
    chart.setOption(option)
  }
}

function updateMemoryUsage(list) {
  const chart = charts[1]
  if (!chart || !list || list.length === 0) return
  let data = list.map(item => {
    const memoryUsage = item.memoryUsage || 0;
    return (memoryUsage * 1024).toFixed(1);
  });
  const option = defaultOption('内存(MB)', localTimeLine(list))
  singleSeries(option, '内存使用(MB)', data, ['#6be3a3', '#bbfad4', '#A5FFD033'])
  if (chart && typeof chart.setOption === 'function') {
    chart.setOption(option);
  }
}

function updateNetworkUsage(list) {
  const chart = charts[2]
  if (!chart || !list || list.length === 0) return
  let data = [
    list.map(item => item.networkUpload || 0),
    list.map(item => item.networkDownload || 0)
  ]
  const option = defaultOption('网络(KB/s)', localTimeLine(list))
  doubleSeries(option, ['上传(KB/s)', '下载(KB/s)'], data, [
    ['#f6b66e', '#ffd29c', '#fddfc033'],
    ['#79c7ff', '#3cabf3', 'rgba(192,242,253,0.2)']
  ])
  if (chart && typeof chart.setOption === 'function') {
    chart.setOption(option);
  }
}

function updateDiskUsage(list) {
  const chart = charts[3]
  if (!chart || !list || list.length === 0) return
  let data = [
    list.map(item => item.diskRead ? item.diskRead.toFixed(1) : '0.0'),
    list.map(item => item.disKWrite ? item.disKWrite.toFixed(1) : '0.0')
  ]
  const option = defaultOption('磁盘(MB/s)', localTimeLine(list))
  doubleSeries(option, ['读取(MB/s)', '写入(MB/s)'], data, [
    ['#d2d2d2', '#d5d5d5', 'rgba(199,199,199,0.2)'],
    ['#757575', '#7c7c7c', 'rgba(94,94,94,0.2)']
  ])
  if (chart && typeof chart.setOption === 'function') {
    chart.setOption(option);
  }
}

function initCharts() {
  // 清理之前的图表实例
  charts.forEach(chart => {
    if (chart && typeof chart.dispose === 'function') {
      chart.dispose();
    }
  });
  charts.length = 0;
  
  const chartList = [
    document.getElementById('cpuUsage'),
    document.getElementById('memoryUsage'),
    document.getElementById('networkUsage'),
    document.getElementById('diskUsage')
  ]
  for (let i = 0; i < chartList.length; i++) {
    const chartElement = chartList[i]
    if (chartElement) {
      // 检查元素是否已经在页面上可见
      if (chartElement.offsetWidth > 0 && chartElement.offsetHeight > 0) {
        charts[i] = echarts.init(chartElement);
      }
    }
  }
}

onMounted(async () => {
  // 等待 DOM 更新完成后再初始化图表
  await nextTick();
  initCharts();
  
  watch(() => props.data, list => {
    // 确保图表已初始化后再更新数据
    if (charts.every(chart => chart)) {
      updateCpuUsage(list)
      updateMemoryUsage(list)
      updateNetworkUsage(list)
      updateDiskUsage(list)
    }
  }, { immediate: true, deep: true })
})

onUnmounted(() => {
  // 组件卸载时清理图表实例
  charts.forEach(chart => {
    if (chart && typeof chart.dispose === 'function') {
      chart.dispose();
    }
  });
})
</script>

<template>
  <div class="charts">
    <div id="cpuUsage" style="width: 100%;height:170px"></div>
    <div id="memoryUsage" style="width: 100%;height:170px"></div>
    <div id="networkUsage" style="width: 100%;height:170px"></div>
    <div id="diskUsage" style="width: 100%;height:170px"></div>
  </div>
</template>

<style scoped>
.charts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-gap: 20px;
}
</style>
