package com.example.service;

import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;
import com.example.entity.vo.response.FaultClassificationResultVO;
import com.example.entity.vo.response.RootCauseAnalysisVO;

/**
 * 规则降级服务接口
 * 当模型不可用时，使用基于规则的分类
 * 
 * @author zhexan
 * @since 2026-03-14
 */
public interface RuleFallbackService {
    
    /**
     * 基于规则的分类
     * 
     * @param runtimeData 运行时数据
     * @param anomalyScore ML 模型检测到的异常分数（如果有）
     * @param rcaResult 根因分析结果（可选，用于优化分类）
     * @return 故障分类结果
     */
    FaultClassificationResultVO classifyByRules(RuntimeDetailVO runtimeData, Double anomalyScore, RootCauseAnalysisVO rcaResult);

}
