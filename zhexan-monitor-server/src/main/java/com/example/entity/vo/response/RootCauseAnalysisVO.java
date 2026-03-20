package com.example.entity.vo.response;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 根因分析结果 VO
 * @author zhexan
 * @since 2026-03-14
 */
@Data
public class RootCauseAnalysisVO {
    
    /**
     * 客户端 ID
     */
    private Integer clientId;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 各指标对异常的贡献度 (0-1)
     * key: "cpuUsage", "memoryUsage", etc.
     */
    private Map<String, Double> contributorScores = new HashMap<>();
    
    /**
     * 主要异常指标
     */
    private String topContributor;
    
    /**
     * 次要异常指标
     */
    private String secondContributor;
    
    /**
     * 根因描述
     */
    private String rootCauseDescription;
    
    /**
     * 是否已确认（用于人工审核）
     */
    private Boolean isConfirmed = false;
    
    /**
     * 添加贡献度分数
     */
    public void addContributor(String metricName, Double score) {
        contributorScores.put(metricName, score);
    }
}
