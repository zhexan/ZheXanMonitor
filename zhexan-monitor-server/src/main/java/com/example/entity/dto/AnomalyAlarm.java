package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异常告警记录实体
 * @author zhexan
 * @since 2026-03-03
 */
@Data
@TableName("tb_anomaly_alarm")
public class AnomalyAlarm {
    
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    /**
     * 客户端 ID
     */
    private Integer clientId;
    
    /**
     * 异常分数
     */
    private Double anomalyScore;
    
    /**
     * 阈值
     */
    private Double threshold;
    
    /**
     * CPU 使用率
     */
    private Double cpuUsage;
    
    /**
     * 内存使用率
     */
    private Double memoryUsage;
    
    /**
     * 磁盘使用率
     */
    private Double diskUsage;
    
    /**
     * 网络上传 (KB/s)
     */
    private Double networkUpload;
    
    /**
     * 网络下载 (KB/s)
     */
    private Double networkDownload;
    
    /**
     * 磁盘读取 (MB/s)
     */
    private Double diskRead;
    
    /**
     * 磁盘写入 (MB/s)
     */
    private Double diskWrite;
    
    /**
     * 异常描述
     */
    private String description;
    
    /**
     * 是否已处理
     */
    private Boolean isHandled;
    
    /**
     * 是否已忽略
     */
    private Boolean isIgnored;
    
    /**
     * 告警时间
     */
    private LocalDateTime alarmTime;
    
    /**
     * 故障类型代码: 0=正常(异常非故障), 1=CPU过载, 2=内存泄漏, 3=磁盘满, 4=网络拥塞, 5=IO瓶颈, 6=复合故障, 7=检测到异常
     */
    private Integer faultTypeCode;
}
