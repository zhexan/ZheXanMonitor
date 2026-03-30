<script setup>
import {ref, watch} from "vue";
import {osNameToIcon} from "@/tools";
import {ElMessage} from "element-plus";
import {post} from "@/net";

const props = defineProps({
  clients: Array,
  editData: Object
})
const emits = defineEmits(['edit'])

const checkedClients = ref([])

watch(() => props.editData, (newData) => {
  if (newData && newData.clientList) {
    checkedClients.value = [...newData.clientList]
  }
}, { immediate: true })

function toggleClient(id) {
  const idx = checkedClients.value.indexOf(id)
  if (idx > -1) {
    checkedClients.value.splice(idx, 1)
  } else {
    checkedClients.value.push(id)
  }
}

function submit() {
  if (checkedClients.value.length === 0) {
    ElMessage.warning('请至少选择一个服务器')
    return
  }
  post('/api/user/sub/update', {
    id: props.editData.id,
    clients: [...checkedClients.value]
  }, () => {
    ElMessage.success('修改成功')
    emits('edit')
  }, (message) => {
    ElMessage.warning(message)
  })
}
</script>

<template>
  <div style="padding: 15px 20px;height: 100%">
    <div style="display: flex;flex-direction: column;height: 100%">
      <div>
        <div class="title">
          <i class="fa-solid fa-pen"></i> 修改子账户
        </div>
        <div class="desc">
          用户名：<strong>{{ editData?.username }}</strong>
        </div>
        <el-divider style="margin: 10px 0"/>
      </div>
      <div class="desc">请选择允许子账户访问的服务器列表。</div>
      <el-scrollbar style="flex: 1">
        <div class="client-card" v-for="item in clients">
          <el-checkbox 
            :model-value="checkedClients.includes(item.id)" 
            @change="toggleClient(item.id)"
          />
          <div style="margin-left: 20px">
            <div style="font-size: 14px;font-weight: bold">
              <span :class="`flag-icon flag-icon-${item.location}`"></span>
              <span style="margin: 0 10px">{{ item.name }}</span>
            </div>
            <div style="font-size: 12px;color: grey">
              操作系统:
              <i :style="{color: osNameToIcon(item.osName).color}"
                 :class="`fa-brands ${osNameToIcon(item.osName).icon}`"></i>
              {{`${item.osName} ${item.osVersion}`}}
            </div>
            <div style="font-size: 12px;color: grey">
              <span style="margin-right: 10px">公网IP: {{item.ip}}</span>
            </div>
          </div>
        </div>
      </el-scrollbar>
      <div style="text-align: center;margin-top: 10px">
        <el-button @click="submit" type="success" plain>确认修改</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.title {
  font-size: 18px;
  font-weight: bold;
  color: dodgerblue;
}

.desc {
  font-size: 13px;
  color: grey;
  line-height: 16px;
}

.client-card {
  border-radius: 5px;
  background-color: var(--el-bg-color-page);
  padding: 10px;
  display: flex;
  align-items: center;
  margin: 10px;
}
</style>
