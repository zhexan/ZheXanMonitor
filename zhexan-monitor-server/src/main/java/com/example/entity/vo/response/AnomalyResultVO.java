package com.example.entity.vo.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 异常检测结果VO
 * @author zhexan
 * @since 2026-03-02
 */
@Data
public class AnomalyResultVO {
    /**
     * 时间戳
     */
    private long timestamp;
    
    /**
     * 异常分数 (0-1之间，越接近1越异常)
     */
    private double anomalyScore;
    
    /**
     * 是否异常
     */
    private boolean anomaly;
    
    /**
     * 异常阈值
     */
    private double threshold = 0.6; // 默认阈值
    
    /**
     * CPU使用率
     */
    private double cpuUsage;
    
    /**
     * 内存使用率
     */
    private double memoryUsage;
    
    /**
     * 磁盘使用率
     */
    private double diskUsage;
    
    /**
     * 网络上传速度
     */
    private double networkUpload;
    
    /**
     * 网络下载速度
     */
    private double networkDownload;
    
    /**
     * 磁盘读取速度
     */
    private double diskRead;
    
    /**
     * 磁盘写入速度
     */
    private double diskWrite;
    
    /**
     * 异常描述
     */
    private String description;
}