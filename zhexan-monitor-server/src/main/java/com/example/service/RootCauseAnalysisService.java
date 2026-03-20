package com.example.service;

import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;
import com.example.entity.vo.response.RootCauseAnalysisVO;

/**
 * 根因分析服务接口
 */
public interface RootCauseAnalysisService {
    
    /**
     * 执行根因分析
     * @param anomalyResult 异常检测结果
     * @return 根因分析结果
     */
    RootCauseAnalysisVO analyze(AnomalyResultVO anomalyResult);
    
    /**
     * 执行根因分析（带运行时数据）
     * @param runtimeData 运行时数据
     * @param anomalyResult 异常检测结果
     * @return 根因分析结果
     */
    RootCauseAnalysisVO analyze(RuntimeDetailVO runtimeData, AnomalyResultVO anomalyResult);
}
