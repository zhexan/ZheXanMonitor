# 故障分类系统使用说明

## 概述

本系统实现了基于规则自动标注 + 人工确认的故障数据采集功能，与异常检测系统（孤立森林）分离但协同工作。

## 核心功能

### 1. 告警记录与训练数据分离

- **告警记录** (`tb_anomaly_alarm`): 用于展示和历史查询
- **训练数据** (`tb_fault_training_data`): 用于机器学习训练，带故障类型标签

### 2. 自动标注规则

系统根据异常检测结果自动标注故障类型：

| 故障类型 | 代码 | 标注规则 | 是否需要审核 |
|---------|------|---------|-------------|
| 正常 | 0 | 未检测到异常 | 不存入 |
| CPU 过载 | 1 | CPU 使用率 > 85% | 否（直接确认） |
| 内存泄漏 | 2 | 内存使用率 > 85% | 否（直接确认） |
| 磁盘已满 | 3 | 磁盘使用率 > 90% | 否（直接确认） |
| 网络拥塞 | 4 | 网络上传/下载 > 10000 KB/s | 否（直接确认） |
| IO 瓶颈 | 5 | 磁盘读取/写入 > 200 MB/s | 否（直接确认） |
| 复合故障 | 6 | 多个指标同时异常 (≥2 个) | 否（直接确认） |
| 检测到异常 | 7 | 无法明确分类但 ML 检测到异常 | 是（需人工确认） |

### 3. 预标注 + 人工确认机制

**设计理念**：
- 明确的故障类型：自动确认，直接用于模型训练
- 不明确的异常：标记为待审核，由运维人员确认故障类型

**处理流程**：
```
异常检测 → 自动标注
    ↓
判断故障类型是否明确？
    ↓
┌─────────────────────┬─────────────────────┐
│ 明确 (代码 1-6)      │ 不明确 (代码 0, 7)    │
│ → status = 1 (已确认) │ → status = 0 (待审核)  │
└─────────────────────┴─────────────────────┘
    ↓
只有 status=1 的数据用于模型训练
```

## 文件清单

### 1. 实体类
- `FaultType.java` - 故障类型枚举
- `FaultTrainingData.java` - 训练数据实体
- `FaultClassificationResultVO.java` - 故障分类结果 VO

### 2. Mapper
- `FaultTrainingDataMapper.java` - 训练数据 Mapper

### 3. Service
- `FaultClassificationService.java` - 故障分类服务接口
- `FaultClassificationServiceImpl.java` - 故障分类服务实现（核心）

### 4. Controller
- `FaultClassificationController.java` - 故障分类控制器

### 5. 数据库
- `V2__create_fault_training_table.sql` - 建表 SQL

### 6. 集成
- `AnomalyDetectionServiceImpl.java` - 已集成训练数据采集
- `AnomalyDetectionScheduler.java` - 定时任务（可选集成）

## API 接口

### 1. 实时故障分类
```http
POST /api/fault/classify?clientId={客户端 ID}
Content-Type: application/json
Authorization: Bearer {jwt_token}

{
  "timestamp": 1234567890,
  "cpuUsage": 0.95,
  "memoryUsage": 0.85,
  "diskUsage": 0.67,
  "networkUpload": 1200,
  "networkDownload": 3500,
  "diskRead": 120,
  "diskWrite": 85
}
```

### 2. 批量故障分类
```http
POST /api/fault/classify-batch?clientId={客户端 ID}
Content-Type: application/json

[
  {...},  // 多个 RuntimeDetailVO
]
```

### 3. 手动添加训练数据（用于人工标注）
```http
POST /api/fault/add-training-data?clientId=1&faultTypeCode=1
Content-Type: application/json

{
  "cpuUsage": 0.95,
  "memoryUsage": 0.45,
  "diskUsage": 0.67,
  "networkUpload": 1200,
  "networkDownload": 3500,
  "diskRead": 120,
  "diskWrite": 85
}
```

故障类型代码：
- 0: 正常
- 1: CPU 过载
- 2: 内存泄漏
- 3: 磁盘已满
- 4: 网络拥塞
- 5: IO 瓶颈
- 6: 复合故障
- 7: 检测到异常

### 4. 查询待审核的训练数据
```http
GET /api/fault/pending?clientId=1&limit=50
```

响应示例：
```json
{
  "code": 200,
  "data": [
    {
      "id": 123,
      "clientId": 1,
      "cpuUsage": 0.65,
      "memoryUsage": 0.70,
      "faultTypeCode": 7,
      "status": 0,
      "dataTime": "2026-03-18T10:00:00"
    }
  ]
}
```

### 5. 审核训练数据（确认或修改标签）
```http
POST /api/fault/review?id=123&faultTypeCode=2&approved=true
```

参数说明：
- `id`: 训练数据 ID
- `faultTypeCode`: 修改后的故障类型代码（只有 approved=true 时生效）
- `approved`: true=确认入库，false=拒绝

响应示例：
```json
{
  "code": 200,
  "data": {
    "id": 123,
    "clientId": 1,
    "faultTypeCode": 2,
    "status": 1
  }
}
```

### 6. 查询训练数据统计
```http
GET /api/fault/stats?clientId={客户端 ID}
```

响应示例：
```json
{
  "code": 200,
  "data": {
    "total": 150,
    "faultTypeStats": {
      "0": 80,
      "1": 30,
      "2": 25,
      "3": 10,
      "4": 3,
      "5": 2,
      "6": 0
    },
    "isEnoughForTraining": true
  }
}
```

### 5. 清除模型和数据
```http
POST /api/fault/clear?clientId={客户端 ID}
```

## 工作流程

### 自动采集流程
1. 客户端上报运行时数据
2. `AnomalyDetectionService` 执行异常检测
3. 调用 `FaultClassificationService.collectAndLabelTrainingData()`
4. 根据规则自动标注故障类型
5. 保存到 `tb_fault_training_data` 表
6. 添加到内存缓冲区

### 手动标注流程
1. 通过 API 传入监控数据和故障类型代码
2. 调用 `addManualTrainingData()` 方法
3. 保存到数据库和缓冲区

## 数据流向

```
客户端上报数据
    ↓
AnomalyDetectionService.detectAnomaly()
    ↓
┌─────────────────────────────┐
│  异常检测 (孤立森林)          │
│  - 计算异常分数              │
│  - 判断是否异常              │
│  - 生成告警描述              │
└─────────────────────────────┘
    ↓
┌─────────────────────────────┐
│  FaultClassificationService │
│  - collectAndLabelTraining  │
│  - autoLabelFaultType()     │
│  - saveToDatabase()         │
└─────────────────────────────┘
    ↓
┌─────────────────────────────┐
│  tb_anomaly_alarm           │ ← 告警记录
│  tb_fault_training_data     │ ← 训练数据
└─────────────────────────────┘
```

## 数据库表结构

### tb_fault_training_data

| 字段名 | 类型 | 说明 |
|-------|------|------|
| id | INT | 主键 |
| client_id | INT | 客户端 ID |
| cpu_usage | DOUBLE | CPU 使用率 |
| memory_usage | DOUBLE | 内存使用率 |
| disk_usage | DOUBLE | 磁盘使用率 |
| network_upload | DOUBLE | 网络上传 (KB/s) |
| network_download | DOUBLE | 网络下载 (KB/s) |
| disk_read | DOUBLE | 磁盘读取 (MB/s) |
| disk_write | DOUBLE | 磁盘写入 (MB/s) |
| fault_type_code | INT | 故障类型代码 |
| **status** | **TINYINT** | **审核状态: 0=待审核, 1=已确认, 2=已拒绝** |
| data_time | DATETIME | 数据时间 |
| create_time | DATETIME | 创建时间 |

索引：
- idx_client_id (client_id)
- idx_fault_type (fault_type_code)
- idx_data_time (data_time)
- **idx_status (status)**

## 使用示例

### 1. 执行数据库迁移 SQL
```bash
# 先执行 V2 建表
mysql -u root -p monitor < src/main/resources/db/migration/V2__create_fault_training_table.sql

# 再执行 V3 添加审核状态字段
mysql -u root -p monitor < src/main/resources/db/migration/V3__add_training_data_status.sql
```

### 2. 启动应用
系统会自动：
- 加载 `FaultClassificationServiceImpl`
- 在每次异常检测时自动采集训练数据
- 明确故障直接确认，不明确故障标记为待审核

### 3. 查看待审核数据
```bash
curl -X GET "http://localhost:8080/api/fault/pending?clientId=1&limit=50" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 4. 审核训练数据
```bash
# 确认并修改标签（将 7-检测到异常 修改为 2-内存泄漏）
curl -X POST "http://localhost:8080/api/fault/review?id=123&faultTypeCode=2&approved=true" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# 拒绝该数据
curl -X POST "http://localhost:8080/api/fault/review?id=123&faultTypeCode=0&approved=false" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 5. 查看训练数据统计
```bash
curl -X GET "http://localhost:8080/api/fault/stats?clientId=1" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 6. 手动标注数据（可选）
```bash
curl -X POST "http://localhost:8080/api/fault/add-training-data?clientId=1&faultTypeCode=1" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "cpuUsage": 0.95,
    "memoryUsage": 0.45,
    ...
  }'
```

## 注意事项

1. **权限要求**: 所有 API 都需要 JWT 认证
2. **数据量要求**: 建议积累至少 500 条已确认的训练数据后再训练 Random Forest 模型
3. **审核状态**: 只有 `status=1`（已确认）的数据才会用于模型训练
4. **审核流程**: 
   - 明确的故障类型（1-6）自动确认
   - 不明确的异常（7-检测到异常）需要人工审核确认
   - 审核通过后数据才会加入训练缓冲区
5. **NORMAL 数据**: 自动检测为 NORMAL 的数据不会存入训练数据库（避免类别不平衡）

## 审核状态说明

| status | 含义 | 用于模型训练 |
|--------|------|-------------|
| 0 | 待审核 | 否 |
| 1 | 已确认 | 是 |
| 2 | 已拒绝 | 否 |

## 下一步

当前系统已完成数据采集和标注功能，后续可以：

1. 实现 Random Forest 模型训练逻辑（使用 SMILE 库）
2. 实现故障预测功能
3. 优化标注规则
4. 添加数据可视化界面

## 技术栈

- Spring Boot 3.1.2
- MyBatis Plus
- MySQL
- Lombok
- Jakarta Validation
