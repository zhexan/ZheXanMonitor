-- 为训练数据表添加审核状态字段
-- @author zhexan
-- @since 2026-03-18

ALTER TABLE tb_fault_training_data 
ADD COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '审核状态: 0=待审核, 1=已确认, 2=已拒绝' AFTER fault_type_code;

CREATE INDEX idx_status ON tb_fault_training_data(status);

-- 将已有的 NORMAL(0) 数据标记为已确认（因为 NORMAL 是明确的）
UPDATE tb_fault_training_data SET status = 1 WHERE fault_type_code = 0;

-- 将非 NORMAL 的数据标记为待审核（需要人工确认）
UPDATE tb_fault_training_data SET status = 0 WHERE fault_type_code != 0;
