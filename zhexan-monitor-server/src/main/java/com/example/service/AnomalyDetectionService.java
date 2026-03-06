package com.example.service;

import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;

import java.util.List;

/**
 * 孤立森林异常检测服务接口
 * @author zhexan
 * @since 2026-03-02
 */
public interface AnomalyDetectionService {
    
    /**
     * 使用历史数据训练孤立森林模型
     * @param clientId 客户端ID
     * @param historyData 历史监控数据
     */
    void trainModel(int clientId, List<RuntimeDetailVO> historyData);
    
    /**
     * 检测单个数据点是否异常
     * @param clientId 客户端ID
     * @param runtimeData 实时监控数据
     * @return 异常检测结果
     */
    AnomalyResultVO detectAnomaly(int clientId, RuntimeDetailVO runtimeData);
    
    /**
     * 批量检测异常
     * @param clientId 客户端ID
     * @param dataList 监控数据列表
     * @return 异常检测结果列表
     */
    List<AnomalyResultVO> detectAnomalies(int clientId, List<RuntimeDetailVO> dataList);
    
    /**
     * 判断模型是否已训练
     * @param clientId 客户端ID
     * @return 是否已训练
     */
    boolean isModelTrained(int clientId);
    
    /**
     * 清除指定客户端的模型
     * @param clientId 客户端ID
     */
    void clearModel(int clientId);
    
    /**
     * 获取所有已训练的客户端ID
     * @return 客户端ID列表
     */
    List<Integer> getTrainedClientIds();
    
    /**
     * 增量更新模型（添加新数据并重新训练）
     * @param clientId 客户端 ID
     * @param newData 新增的监控数据
     */
    void updateModel(int clientId, List<RuntimeDetailVO> newData);
}