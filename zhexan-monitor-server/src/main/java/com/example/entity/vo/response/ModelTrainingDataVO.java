package com.example.entity.vo.response;

import lombok.Data;

/**
 * 用于孤立森林模型训练的运行时数据视图对象
 * @author zhexan
 * @since 2026-03-06
 */
@Data
public class ModelTrainingDataVO {
    /** 时间戳（毫秒） */
    private long timestamp;

    /** CPU 使用率 (0-1) */
    private double cpuUsage;

    /** 内存使用率 (0-1) */
    private double memoryUsage;

    /** 磁盘使用率 (0-1) */
    private double diskUsage;

    /** 网络上传速度 (KB/s) */
    private double networkUpload;

    /** 网络下载速度 (KB/s) */
    private double networkDownload;

    /** 磁盘读取速度 (KB/s) */
    private double diskRead;

    /** 磁盘写入速度 (KB/s) */
    private double diskWrite;
}

