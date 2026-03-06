package com.example.service.Impl;

import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;
import com.example.service.AnomalyDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.anomaly.IsolationForest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 孤立森林异常检测服务实现 (SMILE 3.1.1 版本)
 * @author zhexan
 * @since 2026-03-02
 */
@Slf4j
@Service
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    // 存储每个客户端的孤立森林模型
    private final Map<Integer, IsolationForest> modelMap = new ConcurrentHashMap<>();

    // 存储训练数据用于模型更新
    private final Map<Integer, List<RuntimeDetailVO>> trainingDataMap = new ConcurrentHashMap<>();

    // 存储每个客户端的动态阈值
    private final Map<Integer, Double> thresholdMap = new ConcurrentHashMap<>();

    // 存储每个客户端的归一化参数 (最小值和最大值)
    private final Map<Integer, double[]> minValuesMap = new ConcurrentHashMap<>();
    private final Map<Integer, double[]> maxValuesMap = new ConcurrentHashMap<>();
    
    // 存储每个客户端的新增数据缓冲区
    private final Map<Integer, List<RuntimeDetailVO>> newDataBufferMap = new ConcurrentHashMap<>();
    
    // 最大缓冲数据量（达到此数量自动触发重训练）
    private static final int MAX_BUFFER_SIZE = 50;
    
    // 重训练的最小数据量（保证新旧数据比例合理）
    private static final int MIN_RETRAIN_SIZE = 150;

    // 特征数量（不包含时间戳）
    private static final int FEATURE_COUNT = 7;

    // 特征权重（用于放大 CPU 和内存的重要性）
    private static final double[] FEATURE_WEIGHTS = {
            2.0,  // CPU 使用率 - 提高权重
            2.0,  // 内存使用率 - 提高权重
            1.0,  // 磁盘使用率
            0.8,  // 网络上传 - 降低权重
            0.8,  // 网络下载 - 降低权重
            1.0,  // 磁盘读取
            1.0   // 磁盘写入
    };

    // 默认异常阈值
    private static final double DEFAULT_THRESHOLD = 0.6;

    @Override
    public void trainModel(int clientId, List<RuntimeDetailVO> historyData) {
        if (historyData == null || historyData.size() < 100) {
            throw new IllegalArgumentException("训练数据不足，至少需要 100 条历史数据");
        }

        try {
            log.info("开始为客户端 {} 训练孤立森林模型，数据量：{}", clientId, historyData.size());

            // 数据质量检查
            if (isDataQualityPoor(historyData)) {
                throw new IllegalArgumentException("数据质量不佳，无法训练有效模型");
            }

            // 转换数据格式为 double[][]
            double[][] trainingArray = convertToDoubleArray(historyData);
            
            // 计算归一化参数
            double[] minValues = new double[FEATURE_COUNT];
            double[] maxValues = new double[FEATURE_COUNT];
            computeNormalizationParams(trainingArray, minValues, maxValues);
            
            // 应用归一化和加权
            double[][] normalizedArray = applyNormalizationAndWeight(trainingArray, minValues, maxValues);

            // 训练孤立森林模型
            IsolationForest isolationForest = IsolationForest.fit(
                    normalizedArray,
                    100,
                    (int) Math.log(normalizedArray.length) + 3,
                    0.7,
                    FEATURE_COUNT - 1
            );

            // 保存模型
            modelMap.put(clientId, isolationForest);
            trainingDataMap.put(clientId, historyData);
            minValuesMap.put(clientId, minValues);
            maxValuesMap.put(clientId, maxValues);

            // 计算并保存动态阈值
            double dynamicThreshold = calculateDynamicThreshold(normalizedArray, isolationForest);
            thresholdMap.put(clientId, dynamicThreshold);

            log.info("客户端 {} 的孤立森林模型训练完成，动态阈值：{}", clientId, String.format("%.4f", dynamicThreshold));
            log.info("归一化参数 - CPU: [{}, {}], 内存：[{}, {}]",
                    minValues[0], maxValues[0], minValues[1], maxValues[1]);
        } catch (Exception e) {
            log.error("训练孤立森林模型时发生错误，客户端 ID: {}", clientId, e);
            throw new RuntimeException("模型训练失败：" + e.getMessage(), e);
        }
    }

    @Override
    public AnomalyResultVO detectAnomaly(int clientId, RuntimeDetailVO runtimeData) {
        if (!isModelTrained(clientId)) {
            throw new IllegalStateException("客户端 " + clientId + " 的模型尚未训练");
        }

        try {
            // 转换单个数据点为 double[]
            double[] testData = convertToDoubleArraySingle(runtimeData);
            
            // 获取归一化参数
            double[] minValues = minValuesMap.get(clientId);
            double[] maxValues = maxValuesMap.get(clientId);
            
            if (minValues == null || maxValues == null) {
                throw new IllegalStateException("未找到客户端 " + clientId + " 的归一化参数");
            }
            
            // 应用归一化和加权
            double[] normalizedData = applyNormalizationAndWeightSingle(testData, minValues, maxValues);

            // 获取模型并计算异常得分
            IsolationForest model = modelMap.get(clientId);
            double anomalyScore = model.score(normalizedData);

            // 使用动态阈值，如果没有则使用默认阈值
            double threshold = thresholdMap.getOrDefault(clientId, DEFAULT_THRESHOLD);

            // 基于异常分数判断是否异常
            boolean isAnomaly = anomalyScore > threshold;

            // 构建结果
            AnomalyResultVO result = new AnomalyResultVO();
            result.setTimestamp(runtimeData.getTimestamp());
            result.setAnomalyScore(anomalyScore);
            result.setAnomaly(isAnomaly);
            result.setThreshold(threshold);
            result.setCpuUsage(runtimeData.getCpuUsage());
            result.setMemoryUsage(runtimeData.getMemoryUsage());
            result.setDiskUsage(runtimeData.getDiskUsage());
            result.setNetworkUpload(runtimeData.getNetworkUpload());
            result.setNetworkDownload(runtimeData.getNetworkDownload());
            result.setDiskRead(runtimeData.getDiskRead());
            result.setDiskWrite(runtimeData.getDiskWrite());

            // 设置异常描述
            if (isAnomaly) {
                result.setDescription(generateAnomalyDescription(runtimeData, anomalyScore));
            }

            return result;
        } catch (Exception e) {
            log.error("异常检测时发生错误，客户端 ID: {}", clientId, e);
            throw new RuntimeException("异常检测失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<AnomalyResultVO> detectAnomalies(int clientId, List<RuntimeDetailVO> dataList) {
        return dataList.stream()
                .map(data -> detectAnomaly(clientId, data))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isModelTrained(int clientId) {
        return modelMap.containsKey(clientId);
    }

    @Override
    public void updateModel(int clientId, List<RuntimeDetailVO> newData) {
        if (newData == null || newData.isEmpty()) {
            throw new IllegalArgumentException("新增数据不能为空");
        }
        
        if (!isModelTrained(clientId)) {
            log.warn("客户端 {} 的模型尚未训练，无法进行增量更新", clientId);
            throw new IllegalStateException("模型未训练");
        }
        
        try {
            // 将新数据添加到缓冲区
            newDataBufferMap.computeIfAbsent(clientId, k -> new ArrayList<>()).addAll(newData);
            List<RuntimeDetailVO> buffer = newDataBufferMap.get(clientId);
            
            log.info("客户端 {} 新增 {} 条数据，当前缓冲区数据量：{}", 
                    clientId, newData.size(), buffer.size());
            
            // 检查是否需要触发重训练
            if (buffer.size() >= MAX_BUFFER_SIZE) {
                log.info("客户端 {} 的缓冲区数据量达到阈值，触发增量重训练", clientId);
                retrainWithIncrementalData(clientId);
            }
            
        } catch (Exception e) {
            log.error("增量更新模型时发生错误，客户端 ID: {}", clientId, e);
            throw new RuntimeException("增量更新失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 使用增量数据重新训练模型
     */
    private void retrainWithIncrementalData(int clientId) {
        List<RuntimeDetailVO> originalData = trainingDataMap.get(clientId);
        List<RuntimeDetailVO> newDataBuffer = newDataBufferMap.get(clientId);
        
        if (originalData == null || newDataBuffer == null) {
            log.warn("客户端 {} 的训练数据或新数据缓冲区为空", clientId);
            return;
        }
        
        // 合并旧数据和新数据
        List<RuntimeDetailVO> combinedData = new ArrayList<>(originalData);
        combinedData.addAll(newDataBuffer);
        
        // 如果总数据量过大，采用滑动窗口策略，保留最新的 N 条数据
        if (combinedData.size() > MIN_RETRAIN_SIZE) {
            // 按时间戳排序，保留最新的数据
            combinedData.sort(Comparator.comparingLong(RuntimeDetailVO::getTimestamp).reversed());
            int retainSize = Math.max(MIN_RETRAIN_SIZE, (int)(originalData.size() * 1.5));
            combinedData = combinedData.subList(0, Math.min(retainSize, combinedData.size()));
            log.info("客户端 {} 合并后数据量过大，采用滑动窗口保留最新 {} 条数据", 
                    clientId, combinedData.size());
        }
        
        // 清空缓冲区
        newDataBuffer.clear();
        
        // 使用合并后的数据重新训练
        log.info("客户端 {} 开始增量重训练，总数据量：{}", clientId, combinedData.size());
        trainModel(clientId, combinedData);
        
        log.info("客户端 {} 增量重训练完成", clientId);
    }

    @Override
    public void clearModel(int clientId) {
        modelMap.remove(clientId);
        trainingDataMap.remove(clientId);
        thresholdMap.remove(clientId);
        minValuesMap.remove(clientId);
        maxValuesMap.remove(clientId);
        newDataBufferMap.remove(clientId);
        log.info("已清除客户端 {} 的孤立森林模型", clientId);
    }

    @Override
    public List<Integer> getTrainedClientIds() {
        return new ArrayList<>(modelMap.keySet());
    }

    /**
     * 将监控数据列表转换为 double[][]数组
     */
    private double[][] convertToDoubleArray(List<RuntimeDetailVO> dataList) {
        if (dataList.isEmpty()) return new double[0][0];

        int size = dataList.size();
        double[][] result = new double[size][FEATURE_COUNT];

        for (int i = 0; i < size; i++) {
            RuntimeDetailVO data = dataList.get(i);
            result[i][0] = data.getCpuUsage();
            result[i][1] = data.getMemoryUsage();
            result[i][2] = data.getDiskUsage();
            result[i][3] = data.getNetworkUpload();
            result[i][4] = data.getNetworkDownload();
            result[i][5] = data.getDiskRead();
            result[i][6] = data.getDiskWrite();
        }

        return result;
    }

    /**
     * 转换单个监控数据点为 double[]数组
     */
    private double[] convertToDoubleArraySingle(RuntimeDetailVO data) {
        return new double[]{
                data.getCpuUsage(),
                data.getMemoryUsage(),
                data.getDiskUsage(),
                data.getNetworkUpload(),
                data.getNetworkDownload(),
                data.getDiskRead(),
                data.getDiskWrite()
        };
    }

    /**
     * 计算归一化参数（最小值和最大值）
     */
    private void computeNormalizationParams(double[][] data, double[] minValues, double[] maxValues) {
        int featureCount = data[0].length;
        
        // 初始化
        for (int j = 0; j < featureCount; j++) {
            minValues[j] = Double.MAX_VALUE;
            maxValues[j] = Double.MIN_VALUE;
        }
        
        // 找出每个特征的最小值和最大值
        for (double[] row : data) {
            for (int j = 0; j < featureCount; j++) {
                if (row[j] < minValues[j]) minValues[j] = row[j];
                if (row[j] > maxValues[j]) maxValues[j] = row[j];
            }
        }
        
        // 打印归一化参数
        log.debug("归一化参数计算完成：CPU=[{}, {}], 内存=[{}, {}], 网络上传=[{}, {}]",
                minValues[0], maxValues[0], minValues[1], maxValues[1], minValues[3], maxValues[3]);
    }

    /**
     * 应用归一化和加权（批量数据）
     */
    private double[][] applyNormalizationAndWeight(double[][] data, double[] minValues, double[] maxValues) {
        double[][] normalized = new double[data.length][data[0].length];
        
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[0].length; j++) {
                double range = maxValues[j] - minValues[j];
                if (range > 0) {
                    // Min-Max 归一化到 [0, 1]
                    double normalizedValue = (data[i][j] - minValues[j]) / range;
                    // 应用权重
                    normalized[i][j] = normalizedValue * FEATURE_WEIGHTS[j];
                } else {
                    normalized[i][j] = 0.5 * FEATURE_WEIGHTS[j];
                }
            }
        }
        return normalized;
    }

    /**
     * 应用归一化和加权（单个数据点）
     */
    private double[] applyNormalizationAndWeightSingle(double[] data, double[] minValues, double[] maxValues) {
        double[] normalized = new double[data.length];
        
        for (int j = 0; j < data.length; j++) {
            double range = maxValues[j] - minValues[j];
            if (range > 0) {
                // Min-Max 归一化到 [0, 1]
                double normalizedValue = (data[j] - minValues[j]) / range;
                // 应用权重
                normalized[j] = normalizedValue * FEATURE_WEIGHTS[j];
            } else {
                normalized[j] = 0.5 * FEATURE_WEIGHTS[j];
            }
        }
        return normalized;
    }

    /**
     * 计算动态阈值（基于训练数据的分位数）
     */
    private double calculateDynamicThreshold(double[][] trainingData, IsolationForest model) {
        // 计算所有训练数据的异常分数
        double[] scores = new double[trainingData.length];
        for (int i = 0; i < trainingData.length; i++) {
            scores[i] = model.score(trainingData[i]);
        }

        // 排序
        Arrays.sort(scores);

        // 取上侧 15% 分位数作为阈值（即 85% 的训练数据分数低于此值）
        int thresholdIndex = (int) (scores.length * 0.94);
        return scores[Math.min(thresholdIndex, scores.length - 1)];
    }

    /**
     * 根据异常分数生成描述
     */
    private String generateAnomalyDescription(RuntimeDetailVO data, double score) {
        StringBuilder description = new StringBuilder();
        description.append(String.format("检测到异常行为 (异常分数：%.3f)", score));

        // 检查各项指标是否超出正常范围
        if (data.getCpuUsage() > 0.8) {
            description.append(", CPU 使用率过高");
        }
        if (data.getMemoryUsage() > 0.8) {
            description.append(", 内存使用率过高");
        }
        if (data.getDiskUsage() > 0.9) {
            description.append(", 磁盘使用率过高");
        }
        if (data.getNetworkUpload() > 10000 || data.getNetworkDownload() > 10000) {
            description.append(", 网络流量异常");
        }

        return description.toString();
    }

    /**
     * 检查数据质量
     */
    private boolean isDataQualityPoor(List<RuntimeDetailVO> data) {
        // 检查是否有足够的变化
        double[] cpuValues = data.stream().mapToDouble(RuntimeDetailVO::getCpuUsage).toArray();
        double[] memoryValues = data.stream().mapToDouble(RuntimeDetailVO::getMemoryUsage).toArray();

        double cpuVariance = calculateVariance(cpuValues);
        double memoryVariance = calculateVariance(memoryValues);

        // 如果所有指标变化都很小，说明数据质量不佳
        return cpuVariance < 0.01 && memoryVariance < 0.01;
    }

    /**
     * 计算方差
     */
    private double calculateVariance(double[] values) {
        if (values.length == 0) return 0;
        double mean = Arrays.stream(values).average().orElse(0);
        return Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0);
    }
}
