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
