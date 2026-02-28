<script setup>
import {copyIp, fitByUnit, percentageToStatus, rename} from "@/tools";

const  props = defineProps({
  data: Object,
  update: Function
 })
</script>

<template>
  <div class="instance-card">
    <div style="display: flex;justify-content: space-between">
      <div>
        <div class="name">
          <span :class="`flag-icon flag-icon-${data.location}`"></span>
          <span style="margin: 0 5px">{{ data.name || '未知主机' }}</span>
          <i class="fa-solid fa-pen-to-square interact-item"  @click.stop="rename(data.id,data.name, update)"></i>
        </div>
        <div class="os">
          操作系统:
          <i :style="{}" :class="`fa-brands-ubuntu`"></i>
          <span v-if="data.osName">{{`${data.osName} ${data.osVersion}`}}</span>
          <span v-else>系统信息不可用</span>
        </div>
      </div>
      <div class="status" v-if="data.online">
        <i style="color: #18cb18" class="fa-solid fa-circle-play"></i>
        <span style="margin-left: 5px">运行中</span>
      </div>
      <div class="status" v-else>
        <i style="color: #8a8a8a" class="fa-solid fa-circle-stop"></i>
        <span style="margin-left: 5px">关机中</span>
      </div>
    </div>
    <el-divider style="margin: 10px 0"/>
    <div class="network">
      <span style="margin-right: 10px">公网IP: {{ data.ip || '未知' }}</span>
      <i v-if="data.ip" class="fa-solid fa-copy interact-item" @click.stop="copyIp(data.ip)" style="color: dodgerblue"></i>
    </div>
    <div class="cpu">
      <span style="margin-right: 10px">处理器:{{ data.cpuName || '未知' }}</span>
    </div>
    <div class="hardware">
      <i class="fa-solid fa-microchip"></i>
      <span style="margin-right: 10px" v-if="data.cpuCore"> {{` ${data.cpuCore} CPU`}}</span>
      <span style="margin-right: 10px" v-else> CPU核心数未知</span>
      <i class="fa-solid fa-memory"></i>
      <span v-if="data.memory">{{` ${data.memory.toFixed(1)} GB`}}</span>
      <span v-else> 内存容量未知</span>
    </div>
    <div class="progress">
      <span v-if="typeof data.cpuUsage !== 'undefined'">{{`CPU: ${(data.cpuUsage * 100).toFixed(1)}%`}}</span>
      <span v-else>CPU: 数据不可用</span>
      <el-progress :status="percentageToStatus((data.cpuUsage || 0) * 100)"
          :percentage="(data.cpuUsage || 0) * 100" :stroke-width="5" :show-text="false"/>
    </div>
    <div class="progress">
      <span>内存: <b v-if="data.memoryUsage">{{data.memoryUsage.toFixed(1)}}</b><b v-else>0.0</b> GB</span>
      <el-progress :status="percentageToStatus(((data.memoryUsage || 0)/(data.memory || 1)) * 100)"
          :percentage="((data.memoryUsage || 0)/(data.memory || 1)) * 100" :stroke-width="5" :show-text="false"/>
    </div>
    <div class="network-flow">
      <div>网络流量</div>
      <div>
        <i class="fa-solid fa-arrow-up"></i>
        <span v-if="typeof data.networkUpload !== 'undefined'">{{` ${fitByUnit(data.networkUpload, 'KB')}/s`}}</span>
        <span v-else> 0 KB/s</span>
        <el-divider direction="vertical"/>
        <i class="fa-solid fa-arrow-down"></i>
        <span v-if="typeof data.networkDownload !== 'undefined'">{{` ${fitByUnit(data.networkDownload, 'KB')}/s`}}</span>
        <span v-else> 0 KB/s</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dark .instance-card { color: #d9d9d9 }

.interact-item {
  transition: .3s;

  &:hover {
    cursor: pointer;
    scale: 1.1;
    opacity: 0.8;
  }
}

.instance-card {
  width: 320px;
  padding: 15px;
  background-color: var(--el-bg-color);
  border-radius: 5px;
  box-sizing: border-box;
  color: #606060;
  transition: .3s;

  &:hover {
    cursor: pointer;
    scale: 1.02;
  }

  .name {
    font-size: 15px;
    font-weight: bold;
  }

  .status {
    font-size: 14px;
  }

  .os {
    font-size: 13px;
    color: grey;
  }

  .network {
    font-size: 13px;
  }

  .hardware {
    margin-top: 5px;
    font-size: 13px;
  }

  .progress {
    margin-top: 10px;
    font-size: 12px;
  }

  .cpu {
    font-size: 13px;
  }

  .network-flow {
    margin-top: 10px;
    font-size: 12px;
    display: flex;
    justify-content: space-between;
  }
}
</style>
