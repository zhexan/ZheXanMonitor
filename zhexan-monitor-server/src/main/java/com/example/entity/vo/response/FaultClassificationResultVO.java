package com.example.entity.vo.response;

import com.example.entity.enums.FaultType;
import lombok.Data;

import java.util.Map;

/**
 * 故障分类结果 VO
 * @author zhexan
 * @since 2026-03-14
 */
@Data
public class FaultClassificationResultVO {
    
    /**
     * 是否发生故障
     */
    private boolean isFault;
    
    /**
     * 故障类型
     */
    private FaultType faultType;
    
    /**
     * 置信度 (0-1)
     */
    private double confidence;
    
    /**
     * 各故障类型的概率分布
     */
    private Map<String, Double> probabilities;
    
    /**
     * 故障描述
     */
    private String description;
    
    /**
     * 建议处理措施
     */
    private String recommendation;
    
    // 原始指标数据
    private Double cpuUsage;
    private Double memoryUsage;
    private Double diskUsage;
    private Double networkUpload;
    private Double networkDownload;
    private Double diskRead;
    private Double diskWrite;
}
