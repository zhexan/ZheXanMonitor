-- 故障分类训练数据表 (H2测试版本)
CREATE TABLE IF NOT EXISTS tb_fault_training_data (
    id INT PRIMARY KEY AUTO_INCREMENT,
    client_id INT NOT NULL,
    cpu_usage DOUBLE NOT NULL,
    memory_usage DOUBLE NOT NULL,
    disk_usage DOUBLE NOT NULL,
    network_upload DOUBLE NOT NULL,
    network_download DOUBLE NOT NULL,
    disk_read DOUBLE NOT NULL,
    disk_write DOUBLE NOT NULL,
    fault_type_code INT NOT NULL,
    data_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_client_id ON tb_fault_training_data(client_id);
CREATE INDEX IF NOT EXISTS idx_fault_type ON tb_fault_training_data(fault_type_code);
CREATE INDEX IF NOT EXISTS idx_data_time ON tb_fault_training_data(data_time);

-- 告警记录表
CREATE TABLE IF NOT EXISTS tb_anomaly_alarm (
    id INT PRIMARY KEY AUTO_INCREMENT,
    client_id INT NOT NULL,
    anomaly_score DOUBLE NOT NULL,
    description VARCHAR(500),
    is_handled BOOLEAN DEFAULT FALSE,
    is_ignored BOOLEAN DEFAULT FALSE,
    alarm_time TIMESTAMP NOT NULL
);

-- 客户端详情表
CREATE TABLE IF NOT EXISTS tb_client_detail (
    id INT PRIMARY KEY AUTO_INCREMENT,
    client_id INT NOT NULL,
    cpu_name VARCHAR(100),
    cpu_usage DOUBLE,
    memory_total BIGINT,
    memory_usage DOUBLE,
    disk_total BIGINT,
    disk_usage DOUBLE,
    network_ip VARCHAR(50),
    network_upload DOUBLE,
    network_download DOUBLE,
    disk_read DOUBLE,
    disk_write DOUBLE,
    update_time TIMESTAMP NOT NULL
);

-- 客户端表
CREATE TABLE IF NOT EXISTS db_client (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    token VARCHAR(100) NOT NULL UNIQUE,
    location VARCHAR(100),
    node VARCHAR(100),
    register_time TIMESTAMP NOT NULL
);
