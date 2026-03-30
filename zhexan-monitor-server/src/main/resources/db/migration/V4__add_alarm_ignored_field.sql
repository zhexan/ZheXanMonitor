-- 为告警表添加忽略状态字段
-- @author zhexan
-- @since 2026-03-21

ALTER TABLE tb_anomaly_alarm 
ADD COLUMN `is_ignored` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已忽略: 0=未忽略, 1=已忽略' AFTER is_handled;

CREATE INDEX idx_is_ignored ON tb_anomaly_alarm(is_ignored);
