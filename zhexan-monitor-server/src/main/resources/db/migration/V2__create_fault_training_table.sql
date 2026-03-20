-- 故障分类训练数据表
-- 用于存储带标签的训练数据，与告警记录表 (tb_anomaly_alarm) 分离
-- @author zhexan
-- @since 2026-03-14

CREATE TABLE IF NOT EXISTS `tb_fault_training_data` (
    `id` INT AUTO_INCREMENT PRIMARY KEY COMMENT 'ID',
    `client_id` INT NOT NULL COMMENT '客户端 ID',
    `cpu_usage` DOUBLE NOT NULL COMMENT 'CPU 使用率',
    `memory_usage` DOUBLE NOT NULL COMMENT '内存使用率',
    `disk_usage` DOUBLE NOT NULL COMMENT '磁盘使用率',
    `network_upload` DOUBLE NOT NULL COMMENT '网络上传 (KB/s)',
    `network_download` DOUBLE NOT NULL COMMENT '网络下载 (KB/s)',
    `disk_read` DOUBLE NOT NULL COMMENT '磁盘读取 (MB/s)',
    `disk_write` DOUBLE NOT NULL COMMENT '磁盘写入 (MB/s)',
    `fault_type_code` INT NOT NULL COMMENT '故障类型代码 (0-正常，1-CPU 过载，2-内存泄漏，3-磁盘已满，4-网络拥塞，5-IO 瓶颈，6-复合故障)',
    `data_time` DATETIME NOT NULL COMMENT '数据时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_client_id (`client_id`),
    INDEX idx_fault_type (`fault_type_code`),
    INDEX idx_data_time (`data_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障分类训练数据表';

-- 说明：
-- 1. 此表用于存储机器学习训练数据，与 tb_anomaly_alarm（告警记录）分开
-- 2. fault_type_code 由 FaultClassificationServiceImpl 自动标注
-- 3. 数据来源：每次异常检测时自动采集并标注
-- 4. 用途：训练 Random Forest 故障分类模型
