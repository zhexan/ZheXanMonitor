<script setup>
import {ref, onMounted} from "vue"
import {get, getWithParams} from "@/net"
import {ElMessage} from "element-plus"

const records = ref([])
const clients = ref([])
const selectedClient = ref(null)
const loading = ref(false)
const stats = ref({})

const faultTypes = [
  {code: 1, name: 'CPU过载'},
  {code: 2, name: '内存泄漏'},
  {code: 3, name: '磁盘已满'},
  {code: 4, name: '网络拥塞'},
  {code: 5, name: 'IO瓶颈'},
  {code: 6, name: '复合故障'},
  {code: 7, name: '检测到异常'}
]

const loadClients = () => {
  get('/api/monitor/list', data => {
    clients.value = data
  }, msg => ElMessage.error(msg))
}

const loadRecords = () => {
  loading.value = true
  const params = {}
  if (selectedClient.value) {
    params.clientId = selectedClient.value
  }
  getWithParams('/api/alarm/faults', params, data => {
    const alarms = data?.alarms || []
    records.value = alarms
    const statMap = {}
    alarms.forEach(alarm => {
      const code = alarm.faultTypeCode
      statMap[code] = (statMap[code] || 0) + 1
    })
    stats.value = statMap
    loading.value = false
  }, msg => {
    ElMessage.error(msg)
    loading.value = false
  })
}

onMounted(() => {
  loadClients()
  loadRecords()
})

const handleClientChange = () => {
  loadRecords()
}

const getFaultTypeName = (code) => {
  const type = faultTypes.find(t => t.code === code)
  return type ? type.name : '未知'
}

const getClientName = (clientId) => {
  const client = clients.value.find(c => c.id === clientId)
  return client ? client.name : `客户端${clientId}`
}

const formatTime = (time) => {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN')
}

const formatResource = (record) => {
  const resources = []
  if (record.cpuUsage) resources.push(`CPU:${(record.cpuUsage * 100).toFixed(1)}%`)
  if (record.memoryUsage) resources.push(`内存:${(record.memoryUsage * 100).toFixed(1)}%`)
  if (record.diskUsage) resources.push(`磁盘:${(record.diskUsage * 100).toFixed(1)}%`)
  if (record.networkUpload) resources.push(`上传:${record.networkUpload.toFixed(1)}KB/s`)
  if (record.networkDownload) resources.push(`下载:${record.networkDownload.toFixed(1)}KB/s`)
  if (record.diskRead) resources.push(`读:${record.diskRead.toFixed(1)}MB/s`)
  if (record.diskWrite) resources.push(`写:${record.diskWrite.toFixed(1)}MB/s`)
  return resources.join(' / ') || '-'
}
</script>

<template>
  <div class="fault-main">
    <div class="header">
      <div class="title"><i class="fa-solid fa-clipboard-list"></i> 故障记录</div>
      <div class="filter">
        <span>按客户端筛选：</span>
        <el-select v-model="selectedClient" placeholder="全部" clearable 
                  @change="handleClientChange" style="width: 200px">
          <el-option v-for="client in clients" :key="client.id" 
                     :label="client.name" :value="client.id"/>
        </el-select>
      </div>
    </div>

    <el-divider style="margin: 10px 0"/>

    <div class="stats-section">
      <div class="stats-title"><i class="fa-solid fa-chart-bar"></i> 故障类型统计</div>
      <div class="stats-grid">
        <div v-for="type in faultTypes" :key="type.code" class="stat-item">
          <div class="stat-name">{{type.name}}</div>
          <div class="stat-value">{{stats[type.code] || 0}}</div>
        </div>
      </div>
    </div>

    <el-divider style="margin: 20px 0"/>

    <div class="table-section">
      <el-table v-loading="loading" :data="records" stripe style="width: 100%">
        <el-table-column prop="clientId" label="客户端" width="120">
          <template #default="{row}">
            {{getClientName(row.clientId)}}
          </template>
        </el-table-column>
        <el-table-column prop="faultTypeCode" label="故障类型" width="120">
          <template #default="{row}">
            <el-tag :type="row.faultTypeCode === 0 ? 'success' : 'danger'">
              {{getFaultTypeName(row.faultTypeCode)}}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alarmTime" label="发生时间" width="180">
          <template #default="{row}">
            {{formatTime(row.alarmTime)}}
          </template>
        </el-table-column>
        <el-table-column label="资源使用" min-width="200">
          <template #default="{row}">
            {{formatResource(row)}}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="row.status === 0 ? 'warning' : 'success'">
              {{ row.status === 0 ? '待审核' : '已确认' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="records.length === 0 && !loading" description="暂无故障记录"/>
    </div>
  </div>
</template>

<style scoped>
.fault-main {
  margin: 0 50px;
  
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .title {
      font-size: 22px;
      font-weight: bold;
    }
    
    .filter {
      display: flex;
      align-items: center;
      gap: 10px;
    }
  }
  
  .stats-section {
    .stats-title {
      font-size: 16px;
      font-weight: bold;
      margin-bottom: 15px;
    }
    
    .stats-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 15px;
      
      .stat-item {
        background: white;
        padding: 12px 20px;
        border-radius: 8px;
        text-align: center;
        min-width: 90px;
        
        .stat-name {
          font-size: 13px;
          color: #666;
          margin-bottom: 5px;
        }
        
        .stat-value {
          font-size: 20px;
          font-weight: bold;
          color: #409eff;
        }
      }
    }
  }
  
  .table-section {
    min-height: 300px;
  }
}

.dark .stats-grid .stat-item {
  background: #1d1d1d;
}
</style>