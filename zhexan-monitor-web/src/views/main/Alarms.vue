<script setup>
import {ref, watch, onMounted, onUnmounted, computed} from "vue"
import {get, post, postWithParams} from "@/net"
import {useStore} from "@/store"
import {ElMessage} from "element-plus"

const store = useStore()
const activeTab = ref('unhandled')

const isAdmin = computed(() => store.user?.role === 'admin')

const unhandledAlarms = ref([])
const historyAlarms = ref([])
const pendingData = ref([])
const clients = ref([])

const loading = ref(false)
const batchFaultType = ref(1)

const historyOffset = ref(0)
const historyLimit = ref(10)
const historyTotal = ref(0)
const historyHasMore = ref(true)
const historyLoading = ref(false)

const unhandledOffset = ref(0)
const unhandledLimit = ref(10)
const unhandledTotal = ref(0)
const unhandledHasMore = ref(true)
const unhandledLoading = ref(false)

const pendingOffset = ref(0)
const pendingLimit = ref(10)
const pendingTotal = ref(0)
const pendingHasMore = ref(true)
const pendingLoading = ref(false)

const faultTypes = [
  {code: 1, name: 'CPU过载'},
  {code: 2, name: '内存泄漏'},
  {code: 3, name: '磁盘已满'},
  {code: 4, name: '网络拥塞'},
  {code: 5, name: 'IO瓶颈'},
  {code: 6, name: '复合故障'}
]

const loadClients = () => {
  get('/api/monitor/list', data => {
    clients.value = data
  })
}

const loadUnhandledAlarms = (isLoadMore = false) => {
  if (!isLoadMore) {
    unhandledOffset.value = 0
    unhandledAlarms.value = []
  }
  
  unhandledLoading.value = true
  get(`/api/alarm/unhandled?offset=${unhandledOffset.value}&limit=${unhandledLimit.value}`, data => {
    if (isLoadMore) {
      unhandledAlarms.value = [...unhandledAlarms.value, ...(data.alarms || [])]
    } else {
      unhandledAlarms.value = data.alarms || []
    }
    unhandledTotal.value = data.total || 0
    unhandledHasMore.value = data.hasMore !== false
    unhandledLoading.value = false
  }, msg => {
    ElMessage.error(msg)
    unhandledLoading.value = false
  })
}

const loadMoreUnhandled = () => {
  if (unhandledLoading.value || !unhandledHasMore.value) return
  unhandledLoading.value = true
  unhandledOffset.value += unhandledLimit.value
  get(`/api/alarm/unhandled?offset=${unhandledOffset.value}&limit=${unhandledLimit.value}`, data => {
    unhandledAlarms.value = [...unhandledAlarms.value, ...(data.alarms || [])]
    unhandledTotal.value = data.total || 0
    unhandledHasMore.value = data.hasMore !== false
    unhandledLoading.value = false
  }, msg => {
    ElMessage.error(msg)
    unhandledLoading.value = false
  })
}

const loadHistoryAlarms = (isLoadMore = false) => {
  if (!isLoadMore) {
    historyOffset.value = 0
    historyAlarms.value = []
  }
  
  historyLoading.value = true
  get(`/api/alarm/history?offset=${historyOffset.value}&limit=${historyLimit.value}`, data => {
    if (isLoadMore) {
      historyAlarms.value = [...historyAlarms.value, ...(data.alarms || [])]
    } else {
      historyAlarms.value = data.alarms || []
    }
    historyTotal.value = data.total || 0
    historyHasMore.value = data.hasMore !== false
  }, msg => {
    ElMessage.error(msg)
  })
}

const loadMoreHistory = () => {
  if (historyLoading.value || !historyHasMore.value) return
  historyLoading.value = true
  historyOffset.value += historyLimit.value
  get(`/api/alarm/history?offset=${historyOffset.value}&limit=${historyLimit.value}`, data => {
    historyAlarms.value = [...historyAlarms.value, ...(data.alarms || [])]
    historyTotal.value = data.total || 0
    historyHasMore.value = data.hasMore !== false
    historyLoading.value = false
  }, msg => {
    ElMessage.error(msg)
    historyLoading.value = false
  })
}

const loadPendingData = (isLoadMore = false) => {
  if (!isLoadMore) {
    pendingOffset.value = 0
    pendingData.value = []
  }
  
  pendingLoading.value = true
  get(`/api/fault/pending?offset=${pendingOffset.value}&limit=${pendingLimit.value}`, data => {
    const list = data.data || []
    if (isLoadMore) {
      pendingData.value = [...pendingData.value, ...list.map(item => ({...item, selectedFaultType: item.faultTypeCode}))]
    } else {
      pendingData.value = list.map(item => ({...item, selectedFaultType: item.faultTypeCode}))
    }
    pendingTotal.value = data.total || 0
    pendingHasMore.value = data.hasMore !== false
    pendingLoading.value = false
  }, msg => {
    console.log('加载待审核数据失败:', msg)
    ElMessage.error(msg)
    pendingLoading.value = false
  })
}

const loadMorePending = () => {
  if (pendingLoading.value || !pendingHasMore.value) return
  pendingLoading.value = true
  pendingOffset.value += pendingLimit.value
  get(`/api/fault/pending?offset=${pendingOffset.value}&limit=${pendingLimit.value}`, data => {
    const list = data.data || []
    pendingData.value = [...pendingData.value, ...list.map(item => ({...item, selectedFaultType: item.faultTypeCode}))]
    pendingTotal.value = data.total || 0
    pendingHasMore.value = data.hasMore !== false
    pendingLoading.value = false
  }, msg => {
    console.log('加载待审核数据失败:', msg)
    ElMessage.error(msg)
    pendingLoading.value = false
  })
}

const loadAll = () => {
  if (activeTab.value === 'unhandled') {
    loadUnhandledAlarms()
  }
}

let interval = null
onMounted(() => {
  loadClients()
  loadUnhandledAlarms()
  loadHistoryAlarms()
  loadPendingData()
  interval = setInterval(loadAll, 10000)
})

onUnmounted(() => {
  if (interval) clearInterval(interval)
})

watch(activeTab, (newTab) => {
  console.log('切换到 Tab:', newTab)
  if (newTab === 'pending') {
    if (pendingData.value.length === 0) {
      loadPendingData()
    }
  } else if (newTab === 'unhandled') {
    loadUnhandledAlarms()
  } else if (newTab === 'history') {
    if (historyAlarms.value.length === 0) {
      loadHistoryAlarms()
    }
  }
})

const handleHistoryScroll = (e) => {
  const el = e.target
  const scrollBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  if (scrollBottom < 100 && historyHasMore.value && !historyLoading.value) {
    loadMoreHistory()
  }
}

const handleUnhandledScroll = (e) => {
  const el = e.target
  const scrollBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  if (scrollBottom < 100 && unhandledHasMore.value && !unhandledLoading.value) {
    loadMoreUnhandled()
  }
}

const handlePendingScroll = (e) => {
  const el = e.target
  const scrollBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  if (scrollBottom < 100 && pendingHasMore.value && !pendingLoading.value) {
    loadMorePending()
  }
}

const handleAlarm = (alarmId) => {
  post('/api/alarm/handle', {alarmId}, () => {
    ElMessage.success('告警已处理')
    loadUnhandledAlarms()
    loadHistoryAlarms()
  }, msg => ElMessage.error(msg))
}

const batchHandle = (alarmIds) => {
  post('/api/alarm/batch-handle', {alarmIds}, () => {
    ElMessage.success('批量处理成功')
    loadUnhandledAlarms()
    loadHistoryAlarms()
  }, msg => ElMessage.error(msg))
}

const ignoreAlarm = (alarmId) => {
  post('/api/alarm/ignore', {alarmId}, () => {
    ElMessage.success('已忽略该告警')
    loadUnhandledAlarms()
    loadHistoryAlarms()
  }, msg => ElMessage.error(msg))
}

const batchIgnore = (alarmIds) => {
  post('/api/alarm/batch-ignore', {alarmIds}, () => {
    ElMessage.success('已忽略选中的告警')
    loadUnhandledAlarms()
    loadHistoryAlarms()
  }, msg => ElMessage.error(msg))
}

const labelAlarm = (alarm, faultTypeCode) => {
  const params = {
    clientId: alarm.clientId,
    faultTypeCode: faultTypeCode,
    cpuUsage: alarm.cpuUsage,
    memoryUsage: alarm.memoryUsage,
    diskUsage: alarm.diskUsage,
    networkUpload: alarm.networkUpload,
    networkDownload: alarm.networkDownload,
    diskRead: alarm.diskRead,
    diskWrite: alarm.diskWrite
  }
  post('/api/fault/add-training-data', params, () => {
    ElMessage.success('标注成功，请等待审核')
    handleAlarm(alarm.id)
    loadPendingData()
    loadUnhandledAlarms()
  }, msg => ElMessage.error(msg))
}

const reviewData = (id, faultTypeCode, approved) => {
  postWithParams('/api/fault/review', null, {id, faultTypeCode, approved}, () => {
    if (approved) {
      ElMessage.success('已通过审核')
    } else {
      ElMessage.success('已拒绝')
    }
    loadPendingData()
  }, msg => ElMessage.error(msg))
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

const formatResource = (alarm) => {
  const row1 = []
  const row2 = []
  if (alarm.cpuUsage) row1.push(`CPU:${(alarm.cpuUsage * 100).toFixed(1)}%`)
  if (alarm.memoryUsage) row1.push(`内存:${(alarm.memoryUsage * 100).toFixed(1)}%`)
  if (alarm.diskUsage) row1.push(`磁盘:${(alarm.diskUsage * 100).toFixed(1)}%`)
  if (alarm.networkUpload) row1.push(`上传:${alarm.networkUpload.toFixed(1)}KB/s`)
  if (alarm.networkDownload) row1.push(`下载:${alarm.networkDownload.toFixed(1)}KB/s`)
  if (alarm.diskRead) row2.push(`读:${alarm.diskRead.toFixed(1)}MB/s`)
  if (alarm.diskWrite) row2.push(`写:${alarm.diskWrite.toFixed(1)}MB/s`)
  return { row1: row1.join(' / '), row2: row2.join(' / ') }
}

const selectedAlarms = ref([])
const handleSelectionChange = (val) => {
  selectedAlarms.value = val
}

const batchLabel = (faultTypeCode) => {
  if (selectedAlarms.value.length === 0) {
    ElMessage.warning('请先选择要标注的告警')
    return
  }
  selectedAlarms.value.forEach(alarm => {
    labelAlarm(alarm, faultTypeCode)
  })
  selectedAlarms.value = []
}
</script>

<template>
  <div class="alarm-main">
    <div class="header">
      <div class="title"><i class="fa-solid fa-bell"></i> 告警中心</div>
      <el-radio-group v-model="activeTab" size="large">
        <el-radio-button label="unhandled">未处理</el-radio-button>
        <el-radio-button label="history">历史记录</el-radio-button>
        <el-radio-button v-if="isAdmin" label="pending">待审核</el-radio-button>
      </el-radio-group>
    </div>

    <el-divider style="margin: 10px 0"/>

    <!-- 未处理告警 -->
    <div v-show="activeTab === 'unhandled'" class="tab-content" @scroll="handleUnhandledScroll" style="max-height: 600px; overflow-y: auto;">
      <div v-if="unhandledTotal > 0" style="margin-bottom: 10px;">
        <el-tag type="warning">共 {{ unhandledTotal }} 条未处理告警</el-tag>
      </div>
      <div class="toolbar">
        <el-select v-model="batchFaultType" placeholder="选择故障类型" style="width: 150px;">
          <el-option v-for="type in faultTypes" :key="type.code" :label="type.name" :value="type.code"/>
        </el-select>
        <el-button type="primary" :disabled="selectedAlarms.length === 0" 
                   @click="batchLabel(batchFaultType)">
          批量标注 ({{selectedAlarms.length}})
        </el-button>
        <el-button type="info" :disabled="selectedAlarms.length === 0"
                   @click="batchIgnore(selectedAlarms.map(a => a.id))">
          批量忽略 ({{selectedAlarms.length}})
        </el-button>
      </div>
      
      <el-table :data="unhandledAlarms" @selection-change="handleSelectionChange" 
                stripe style="width: 100%">
        <el-table-column type="selection" width="50"/>
        <el-table-column prop="clientId" label="客户端" width="120">
          <template #default="{row}">
            {{getClientName(row.clientId)}}
          </template>
        </el-table-column>
        <el-table-column prop="alarmTime" label="告警时间" width="180">
          <template #default="{row}">
            {{formatTime(row.alarmTime)}}
          </template>
        </el-table-column>
        <el-table-column prop="anomalyScore" label="异常分数" width="100">
          <template #default="{row}">
            <el-tag type="danger">{{row.anomalyScore?.toFixed(2)}}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资源使用" min-width="300">
          <template #default="{row}">
            <div>{{formatResource(row).row1}}</div>
            <div>{{formatResource(row).row2}}</div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150"/>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{row}">
            <el-dropdown trigger="click" @command="(cmd) => labelAlarm(row, cmd)">
              <el-button type="warning" size="small">标注</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="type in faultTypes" :key="type.code" :command="type.code">
                    {{type.name}}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button type="info" size="small" style="margin-left: 8px" @click="ignoreAlarm(row.id)">
              忽略
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="unhandledHasMore" class="load-more-tip" @click="loadMoreUnhandled">
        <span v-if="unhandledLoading">加载中...</span>
        <span v-else>点击加载更多 ({{ unhandledAlarms.length }} / {{ unhandledTotal }})</span>
      </div>
      <el-empty v-if="unhandledAlarms.length === 0" description="暂无未处理告警"/>
    </div>

    <!-- 历史记录 -->
    <div v-show="activeTab === 'history'" class="tab-content" @scroll="handleHistoryScroll" style="max-height: 600px; overflow-y: auto;">
      <div v-if="historyTotal > 0" style="margin-bottom: 10px;">
        <el-tag type="info">共 {{ historyTotal }} 条记录</el-tag>
      </div>
      <el-table :data="historyAlarms" stripe style="width: 100%">
        <el-table-column prop="clientId" label="客户端" width="120">
          <template #default="{row}">
            {{getClientName(row.clientId)}}
          </template>
        </el-table-column>
        <el-table-column prop="alarmTime" label="告警时间" width="180">
          <template #default="{row}">
            {{formatTime(row.alarmTime)}}
          </template>
        </el-table-column>
        <el-table-column prop="anomalyScore" label="异常分数" width="100">
          <template #default="{row}">
            <el-tag>{{row.anomalyScore?.toFixed(2)}}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资源使用" min-width="300">
          <template #default="{row}">
            <div>{{formatResource(row).row1}}</div>
            <div>{{formatResource(row).row2}}</div>
          </template>
        </el-table-column>
        <el-table-column prop="isHandled" label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="row.isHandled ? 'success' : 'warning'">
              {{row.isHandled ? '已处理' : '未处理'}}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="historyHasMore" class="load-more-tip" @click="loadMoreHistory">
        <span v-if="historyLoading">加载中...</span>
        <span v-else>点击加载更多 ({{ historyAlarms.length }} / {{ historyTotal }})</span>
      </div>
      <el-empty v-if="historyAlarms.length === 0" description="暂无历史记录"/>
    </div>

    <!-- 待审核 -->
    <div v-show="activeTab === 'pending'" class="tab-content" @scroll="handlePendingScroll" style="max-height: 600px; overflow-y: auto;">
      <div v-if="pendingTotal > 0" style="margin-bottom: 10px;">
        <el-tag type="warning">待审核数据: {{ pendingData.length }} / {{ pendingTotal }} 条</el-tag>
      </div>
      <el-table :data="pendingData" :key="activeTab + '-pending'" stripe style="width: 100%">
        <el-table-column prop="clientId" label="客户端" width="120">
          <template #default="{row}">
            {{getClientName(row.clientId)}}
          </template>
        </el-table-column>
        <el-table-column prop="dataTime" label="数据时间" width="180">
          <template #default="{row}">
            {{formatTime(row.dataTime)}}
          </template>
        </el-table-column>
        <el-table-column prop="faultTypeCode" label="标注类型" width="120">
          <template #default="{row}">
            <el-tag type="warning">{{getFaultTypeName(row.faultTypeCode)}}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="资源使用" min-width="350">
          <template #default="{row}">
            <div>{{formatResource(row).row1}}</div>
            <div>{{formatResource(row).row2}}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{row}">
            <el-select v-model="row.selectedFaultType" placeholder="选择标签" style="width: 120px; margin-right: 8px;">
              <el-option v-for="type in faultTypes" :key="type.code" :label="type.name" :value="type.code"/>
            </el-select>
            <el-button type="success" size="small" @click="reviewData(row.id, row.selectedFaultType, true)">
              通过
            </el-button>
            <el-button type="danger" size="small" @click="reviewData(row.id, row.selectedFaultType, false)">
              拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="pendingHasMore" class="load-more-tip" @click="loadMorePending">
        <span v-if="pendingLoading">加载中...</span>
        <span v-else>点击加载更多 ({{ pendingData.length }} / {{ pendingTotal }})</span>
      </div>
      <el-empty v-if="pendingData.length === 0" description="暂无待审核数据"/>
    </div>
  </div>
</template>

<style scoped>
.alarm-main {
  margin: 0 50px;
  
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .title {
      font-size: 22px;
      font-weight: bold;
    }
  }
  
  .tab-content {
    min-height: 400px;
  }
  
  .toolbar {
    margin-bottom: 15px;
  }
  
  .load-more-tip {
    text-align: center;
    padding: 20px;
    color: #909399;
    cursor: pointer;
    &:hover {
      color: #409eff;
    }
  }
}
</style>