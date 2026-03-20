# 异常检测与告警系统集成说明

## 功能概述

本系统实现了基于孤立森林（Isolation Forest）的异常检测功能，支持自动监控、实时告警和模型增量更新。

## 核心功能

### 1. 自动异常检测
- **定时检测**：每 5 分钟自动对所有已训练模型的客户端执行异常检测
- **实时监控**：当客户端上报数据时自动触发增量更新
- **智能告警**：检测到异常时自动推送告警到前端

### 2. AI 根因分析与故障分类（增强版）
检测到异常后，自动执行三层分析：

```
步骤 1: 异常检测 (孤立森林 ML)
    ↓ anomalyScore = 0.577
步骤 2: 根因分析 (RCA)
    ↓ topContributor = memoryUsage, 贡献度 = 64.9%
步骤 3: 故障分类 (Random Forest / 规则)
    ↓ 输出故障类型 + 解决方案建议
```

**根因分析**：
- 计算各指标偏离正常范围的程度
- 识别主要异常指标及贡献度
- 生成根因描述

**故障分类**：
- 有训练数据：使用 Random Forest 模型分类
- 无训练数据：使用规则降级分类
- 输出故障类型和解决方案建议

### 3. 预标注 + 人工确认机制
- **明确故障**：自动确认，直接用于模型训练
- **不明确异常**：标记为待审核（status=0），需人工确认后（status=1）才能用于训练

详见：[FAULT_CLASSIFICATION_README.md](./FAULT_CLASSIFICATION_README.md)

### 4. WebSocket 实时告警
- **连接地址**：`ws://服务器地址/alarm/{userId}`
- **消息格式**：JSON
- **推送内容**：异常分数、阈值、各项指标详情、异常描述

### 3. 模型增量更新
- **自动累积**：每次收到新数据时自动添加到缓冲区
- **阈值触发**：缓冲区达到 50 条数据自动触发重训练
- **滑动窗口**：保留最新 150 条数据，防止内存溢出

## 数据库表结构

执行以下 SQL 创建告警记录表：

```sql
-- 异常告警记录表
CREATE TABLE IF NOT EXISTS `tb_anomaly_alarm` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT '告警 ID',
    `client_id` INT NOT NULL COMMENT '客户端 ID',
    `anomaly_score` DOUBLE NOT NULL COMMENT '异常分数',
    `threshold` DOUBLE NOT NULL COMMENT '检测阈值',
    `cpu_usage` DOUBLE NOT NULL COMMENT 'CPU 使用率',
    `memory_usage` DOUBLE NOT NULL COMMENT '内存使用率',
    `disk_usage` DOUBLE NOT NULL COMMENT '磁盘使用率',
    `network_upload` DOUBLE NOT NULL COMMENT '网络上传 (KB/s)',
    `network_download` DOUBLE NOT NULL COMMENT '网络下载 (KB/s)',
    `disk_read` DOUBLE NOT NULL COMMENT '磁盘读取 (MB/s)',
    `disk_write` DOUBLE NOT NULL COMMENT '磁盘写入 (MB/s)',
    `description` VARCHAR(500) COMMENT '异常描述',
    `is_handled` TINYINT DEFAULT 0 COMMENT '是否已处理 (0-未处理，1-已处理)',
    `alarm_time` DATETIME NOT NULL COMMENT '告警时间',
    INDEX idx_client_id (`client_id`),
    INDEX idx_alarm_time (`alarm_time`),
    INDEX idx_is_handled (`is_handled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常告警记录表';
```

## API 接口说明

### 1. 获取未处理告警
```http
GET /api/alarm/unhandled?clientId={客户端 ID}
```

响应示例：
```json
{
  "alarms": [...],
  "count": 5
}
```

### 2. 获取告警历史
```http
GET /api/alarm/history?clientId={客户端 ID}&limit=100
```

### 3. 标记告警为已处理
```http
POST /api/alarm/handle
Content-Type: application/json

{
  "alarmId": 1
}
```

### 4. 批量标记告警
```http
POST /api/alarm/batch-handle
Content-Type: application/json

{
  "alarmIds": [1, 2, 3]
}
```

### 5. 测试 WebSocket 推送
```http
POST /api/alarm/test-push
Content-Type: application/json

{}
```

## WebSocket 消息格式

### 接收到的告警消息
```json
{
  "type": "anomaly_alarm",
  "clientId": 12345678,
  "timestamp": "2026-03-03T15:30:00",
  "anomalyScore": 0.75,
  "threshold": 0.6,
  "isAnomaly": true,
  "description": "检测到异常行为 (异常分数：0.750), CPU 使用率过高",
  "metrics": {
    "cpuUsage": 0.95,
    "memoryUsage": 0.45,
    "diskUsage": 0.67,
    "networkUpload": 1200,
    "networkDownload": 3500,
    "diskRead": 120,
    "diskWrite": 85
  }
}
```

### 连接成功消息
```json
{
  "type": "connected",
  "message": "告警服务已连接"
}
```

## 前端集成示例

### Vue 3 中使用 WebSocket

```javascript
// 在组件中建立 WebSocket 连接
import { ref, onMounted, onUnmounted } from 'vue'

const ws = ref(null)
const alarms = ref([])

function connectWebSocket() {
  const userId = store.state.userId // 从 store 获取用户 ID
  ws.value = new WebSocket(`ws://your-server.com/alarm/${userId}`)
  
  ws.value.onopen = () => {
    console.log('告警服务已连接')
  }
  
  ws.value.onmessage = (event) => {
    const data = JSON.parse(event.data)
    
    if (data.type === 'anomaly_alarm') {
      // 收到异常告警
      alarms.value.unshift(data)
      showNotification(data) // 显示通知
    }
  }
  
  ws.value.onerror = (error) => {
    console.error('WebSocket 错误:', error)
  }
  
  ws.value.onclose = () => {
    console.log('WebSocket 连接已关闭')
    // 可在此实现自动重连
  }
}

function showNotification(alarm) {
  // 使用 Element Plus 的通知组件
  ElNotification.warning({
    title: '异常告警',
    message: alarm.description,
    duration: 5000
  })
}

onMounted(() => {
  connectWebSocket()
})

onUnmounted(() => {
  if (ws.value) {
    ws.value.close()
  }
})
```

## 配置说明

### 定时任务频率
在 `AnomalyDetectionScheduler.java` 中可调整：

```java
// 异常检测频率（默认 5 分钟）
@Scheduled(fixedRate = 300000) 

// 模型更新频率（默认 1 小时）
@Scheduled(fixedRate = 3600000)
```

### 模型参数
在 `AnomalyDetectionServiceImpl.java` 中可调整：

```java
// 缓冲区大小阈值
private static final int MAX_BUFFER_SIZE = 50;

// 最小重训练数据量
private static final int MIN_RETRAIN_SIZE = 150;

// 特征权重（可根据实际需求调整）
private static final double[] FEATURE_WEIGHTS = {
    2.0,  // CPU 使用率
    2.0,  // 内存使用率
    1.0,  // 磁盘使用率
    0.8,  // 网络上传
    0.8,  // 网络下载
    1.0,  // 磁盘读取
    1.0   // 磁盘写入
};
```

## 使用流程

### 1. 初始训练模型
```bash
POST /api/monitor/anomaly-train?clientId={客户端 ID}
```

需要至少 100 条历史数据才能开始训练。

### 2. 连接 WebSocket
前端连接到 `/alarm/{userId}` 端点，准备接收告警。

### 3. 自动检测和告警
系统会自动：
- 每 5 分钟检测一次所有客户端
- 发现异常时立即推送告警
- 保存告警记录到数据库
- 执行根因分析和故障分类
- 根据标注状态决定是否存入训练数据

### 4. 查看和处理告警
- 通过 `/api/alarm/unhandled` 查看未处理告警
- 通过 `/api/alarm/history` 查看历史记录
- 通过 `/api/alarm/handle` 标记为已处理

### 5. 审核训练数据（可选）
当检测到不明确的异常时：
- 通过 `/api/fault/pending` 查看待审核数据
- 通过 `/api/fault/review` 确认或修改标签
- 确认后的数据才会用于 Random Forest 模型训练

## 完整数据流向

```
客户端上报数据
    ↓
AnomalyDetectionService.detectAnomaly() [孤立森林]
    ↓
┌─────────────────────────────────────────┐
│ 步骤 1: 异常检测                         │
│ - 计算 anomalyScore                      │
│ - 判断 isAnomaly                        │
└─────────────────────────────────────────┘
    ↓
    ↓ isAnomaly = true
┌─────────────────────────────────────────┐
│ 步骤 2: 根因分析 (RootCauseAnalysis)    │
│ - 计算各指标贡献度                       │
│ - 识别 topContributor                    │
└─────────────────────────────────────────┘
    ↓
    ↓ rcaResult
┌─────────────────────────────────────────┐
│ 步骤 3: 故障分类 (FaultClassification)   │
│ - Random Forest 或规则降级               │
│ - 输出故障类型 + 建议                    │
└─────────────────────────────────────────┘
    ↓
    ↓ autoLabelFaultType()
┌─────────────────────────────────────────┐
│ 步骤 4: 训练数据采集                    │
│ - 明确故障 → status=1, 直接用于训练     │
│ - 不明确异常 → status=0, 需人工审核     │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│ 告警与存储                              │
│ - tb_anomaly_alarm (告警记录)           │
│ - tb_fault_training_data (训练数据)     │
└─────────────────────────────────────────┘
    ↓
WebSocket 推送告警到前端
```

## 注意事项

1. **数据量要求**：初始训练需要至少 100 条历史数据；Random Forest 模型训练需要 500+ 条已确认的训练数据
2. **WebSocket 连接**：前端需要在页面加载时建立连接
3. **权限控制**：所有 API 都需要 JWT 认证
4. **性能考虑**：建议单个客户端的训练数据不超过 500 条
5. **训练数据质量**：只有 `status=1`（已确认）的数据才会用于 Random Forest 模型训练

## 故障排查

### 模型未训练
检查日志中是否有"训练数据不足"的错误，确保有 100 条以上的历史数据。

### WebSocket 无法连接
1. 检查 WebSocket 是否启用：`websocket.enabled=true`
2. 检查防火墙设置
3. 确认用户 ID 正确

### 告警未推送
1. 检查用户是否在线
2. 查看日志中的异常信息
3. 确认客户端 ID 与用户的关联关系

## 技术栈

- **机器学习库**：SMILE 3.1.1
- **算法**：孤立森林（Isolation Forest）
- **WebSocket**：Jakarta WebSocket
- **数据库**：MySQL + MyBatis Plus
- **缓存**：ConcurrentHashMap
- **定时任务**：Spring Scheduled
