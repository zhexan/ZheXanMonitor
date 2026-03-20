package com.example.entity.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 故障分类训练数据实体
 * @author zhexan
 * @since 2026-03-14
 */
@Data
@TableName("tb_fault_training_data")
public class FaultTrainingData {
    
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    /**
     * 客户端 ID
     */
    private Integer clientId;
    
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
     * 故障类型标签
     */
    private Integer faultTypeCode;
    
    /**
     * 审核状态: 0=待审核, 1=已确认, 2=已拒绝
     */
    private Integer status = 0;
    
    /**
     * 数据时间戳
     */
    private LocalDateTime dataTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
