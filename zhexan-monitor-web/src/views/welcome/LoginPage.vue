<template>
  <div @focus="activateGlass" @blur="deactivateGlass" class="login-form-container" style="text-align: center;margin: 0 20px;width: 100%">
    <div style="margin-top: 40px">
      <div style="font-size: 25px;font-weight: bold">运维监控系统登录</div>
      <div style="font-size: 14px;color: grey">请输入您的运维账号和密码以访问系统</div>
    </div>
    <div style="margin-top: 50px">
      <el-form :model="form" :rules="rules" ref="formRef">
        <el-form-item prop="username">
          <el-input v-model="form.username" maxlength="10" type="text" placeholder="用户名/邮箱">
            <template #prefix>
              <el-icon>
                <User/>
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" maxlength="20" style="margin-top: 10px" placeholder="密码">
            <template #prefix>
              <el-icon>
                <Lock/>
              </el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-row style="margin-top: 5px">
          <el-col :span="12" style="text-align: left">
            <el-form-item prop="remember">
              <el-checkbox v-model="form.remember" label="记住我"/>
            </el-form-item>
          </el-col>
          <el-col :span="12" style="text-align: right">
            <el-link @click="router.push('/forget')">忘记密码？</el-link>
          </el-col>
        </el-row>
      </el-form>
    </div>
    <div style="margin-top: 40px">
      <el-button @click="userLogin()" style="width: 270px" type="success" plain>立即登录</el-button>
    </div>
  </div>
</template>

<script setup>
import {User, Lock} from '@element-plus/icons-vue'
import router from "@/router";
import {reactive, ref} from "vue";
import {login} from '@/net'
import {getCurrentInstance} from 'vue'

const formRef = ref()
const form = reactive({
  username: '',
  password: '',
  remember: false
})

const rules = {
  username: [
    { required: true, message: '请输入用户名' }
  ],
  password: [
    { required: true, message: '请输入密码'}
  ]
}

function userLogin() {
  formRef.value.validate((isValid) => {
    if(isValid) {
      login(form.username, form.password, form.remember, () => router.push("/index"))
    }
  });
}

const activateGlass = () => {
  const parent = getCurrentInstance()?.parent?.$el?.closest('.login-wrapper')
  if(parent) {
    parent.classList.add('glass-effect')
  }
}

const deactivateGlass = () => {
  const parent = getCurrentInstance()?.parent?.$el?.closest('.login-wrapper')
  if(parent && formRef.value && !formRef.value.$el.querySelector('input:focus')) {
    parent.classList.remove('glass-effect')
  }
}
</script>

<style scoped>
.login-form-container {
  outline: none;
}


/* 表单项样式优化 */
:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-input__wrapper) {
  background-color: #f5f5f5;
  border-radius: 8px;
  transition: all 0.3s ease;
}

:deep(.el-input:focus-within .el-input__wrapper),
:deep(.el-input__wrapper:focus-within) {
  background-color: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(8px);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.08);
  border-color: rgba(100, 150, 255, 0.3);
}

:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.3s ease;
}

:deep(.el-link) {
  transition: color 0.3s ease;
}

:deep(.el-link:hover) {
  color: #409eff;
}

:deep(.el-checkbox__label) {
  color: #666;
}
</style>
