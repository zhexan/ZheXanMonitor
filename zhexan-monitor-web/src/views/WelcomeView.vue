<template>
    <div class="welcome-container">
        <!-- 左侧装饰区域 -->
        <div class="side-panel left-panel">
            <svg class="line-animation" viewBox="0 0 400 600">
                <line class="animated-line" x1="50" y1="100" x2="350" y2="150" />
                <line class="animated-line" x1="80" y1="250" x2="320" y2="300" />
                <line class="animated-line" x1="60" y1="400" x2="340" y2="450" />
                <line class="animated-line" x1="100" y1="530" x2="300" y2="570" />
            </svg>
        </div>

        <!-- 中间登录区域 -->
        <div class="center-panel">
            <div class="login-wrapper">
                <div class="title-area">
                    <div class="system-title">智慧运维监控系统</div>
                    <div class="system-subtitle">为您提供全方位的运维监控与管理能力</div>
                    <div class="system-subtitle">登录后即可查看系统状态、告警信息和历史数据</div>
                </div>
                <router-view v-slot="{ Component }">
                    <transition name="el-fade-in-linear" mode="out-in">
                        <component :is="Component" style="height: 100%"/>
                    </transition>
                </router-view>
            </div>
        </div>

        <!-- 右侧装饰区域 -->
        <div class="side-panel right-panel">
            <svg class="line-animation" viewBox="0 0 400 600">
                <line class="animated-line" x1="50" y1="120" x2="350" y2="170" />
                <line class="animated-line" x1="70" y1="280" x2="330" y2="320" />
                <line class="animated-line" x1="40" y1="420" x2="360" y2="460" />
                <line class="animated-line" x1="90" y1="550" x2="310" y2="580" />
            </svg>
        </div>
    </div>
</template>

<script setup>

</script>

<style scoped>
.welcome-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  background: #ffffff;
  overflow: hidden;
}

/* 侧边面板 */
.side-panel {
  flex: 1;
  background: linear-gradient(135deg, #f5f5f5 0%, #f9f9f9 100%);
  overflow: hidden;
  position: relative;
}

.left-panel::before,
.right-panel::before {
  content: '';
  position: absolute;
  width: 100%;
  height: 100%;
  background: 
    radial-gradient(circle at 20% 50%, rgba(220, 220, 220, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 80%, rgba(200, 200, 200, 0.08) 0%, transparent 50%);
  pointer-events: none;
}

.line-animation {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0;
  left: 0;
}

.animated-line {
  stroke: #d0d0d0;
  stroke-width: 2;
  opacity: 0.6;
  animation: lineMove 8s ease-in-out infinite;
}

.animated-line:nth-child(2) {
  animation-delay: 1s;
  stroke: #c8c8c8;
}

.animated-line:nth-child(3) {
  animation-delay: 2s;
  stroke: #d5d5d5;
}

.animated-line:nth-child(4) {
  animation-delay: 3s;
  stroke: #cacaca;
}

/* 中间登录区域 */
.center-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  position: relative;
  z-index: 10;
}

.login-wrapper {
  width: 100%;
  max-width: 420px;
  padding: 40px 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
  transition: all 0.3s ease;
}

.title-area {
  text-align: center;
  margin-bottom: 40px;
}

.system-title {
  font-size: 28px;
  font-weight: bold;
  color: #1a1a1a;
  margin-bottom: 15px;
}

.system-subtitle {
  font-size: 13px;
  color: #666666;
  line-height: 1.6;
  margin-top: 8px;
}

/* 毛玻璃效果 - 当表单获得焦点时 */
.login-wrapper.glass-effect {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 16px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.05);
  padding: 50px 40px;
}

/* 动画 */
@keyframes lineMove {
  0% {
    transform: translateX(-400px) translateY(-200px);
    opacity: 0;
  }
  50% {
    opacity: 0.6;
  }
  100% {
    transform: translateX(400px) translateY(200px);
    opacity: 0;
  }
}
</style>
