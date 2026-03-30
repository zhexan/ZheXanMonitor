package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.FaultTrainingData;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;
import com.example.entity.vo.response.FaultClassificationResultVO;
import com.example.entity.vo.response.RootCauseAnalysisVO;

import java.util.List;
import java.util.Map;

/**
 * 故障分类服务接口
 * @author zhexan
 * @since 2026-03-14
 */
public interface FaultClassificationService extends IService<FaultTrainingData> {
    
    /**
     * 手动触发模型训练（从数据库加载数据并训练）
     * @param clientId 客户端 ID
     */
    void trainClientModel(int clientId);
    
    /**
     * 对单个数据点进行故障分类
     * @param clientId 客户端 ID
     * @param runtimeData 实时监控数据
     * @return 故障分类结果
     */
    FaultClassificationResultVO classify(int clientId, RuntimeDetailVO runtimeData);
    
    /**
     * 对单个数据点进行故障分类（带异常分数）
     * @param clientId 客户端 ID
     * @param runtimeData 实时监控数据
     * @param anomalyScore ML 模型检测到的异常分数
     * @return 故障分类结果
     */
    FaultClassificationResultVO classify(int clientId, RuntimeDetailVO runtimeData, Double anomalyScore);
    
    /**
     * 对单个数据点进行故障分类（带异常分数和根因分析结果）
     * @param clientId 客户端 ID
     * @param runtimeData 实时监控数据
     * @param anomalyScore ML 模型检测到的异常分数
     * @param rcaResult 根因分析结果
     * @return 故障分类结果
     */
    FaultClassificationResultVO classify(int clientId, RuntimeDetailVO runtimeData, Double anomalyScore, RootCauseAnalysisVO rcaResult);
    
    /**
     * 批量故障分类
     * @param clientId 客户端 ID
     * @param dataList 监控数据列表
     * @return 故障分类结果列表
     */
    List<FaultClassificationResultVO> classifyBatch(int clientId, List<RuntimeDetailVO> dataList);
    
    /**
     * 判断模型是否已训练
     * @param clientId 客户端 ID
     * @return 是否已训练
     */
    boolean isModelTrained(int clientId);
    
    /**
     * 清除指定客户端的模型
     * @param clientId 客户端 ID
     */
    void clearModel(int clientId);
    
    /**
     * 获取所有已训练的客户端 ID
     * @return 客户端 ID 列表
     */
    List<Integer> getTrainedClientIds();
    
    /**
     * 查询待审核的训练数据
     * @param clientId 客户端 ID（可选，为空则查询所有）
     * @param limit 返回数量限制
     * @return 待审核的训练数据列表
     */
    List<FaultTrainingData> getPendingTrainingData(Integer clientId, int limit);

    /**
     * 分页查询待审核的训练数据
     * @param clientId 客户端 ID（可选，为空则查询所有）
     * @param offset 起始偏移量
     * @param limit 每页数量
     * @return 分页结果，包含 data、total、hasMore
     */
    Map<String, Object> getPendingTrainingDataPaged(Integer clientId, int offset, int limit);
    
    /**
     * 确认或修改训练数据标签
     * @param id 训练数据 ID
     * @param faultTypeCode 故障类型代码
     * @param approved 是否确认（true=确认，false=拒绝）
     * @return 更新后的训练数据
     */
    FaultTrainingData reviewTrainingData(int id, int faultTypeCode, boolean approved);

    Integer autoLabelFaultType(AnomalyResultVO result);

    void addManualTrainingData(Integer clientId, Integer faultTypeCode, RuntimeDetailVO runtimeData);

    List<FaultTrainingData> loadTrainingDataByClientId(Integer clientId);
}
