package com.example.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.FaultTrainingData;
import com.example.entity.enums.FaultType;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;
import com.example.entity.vo.response.FaultClassificationResultVO;
import com.example.entity.vo.response.RootCauseAnalysisVO;
import com.example.mapper.FaultTrainingDataMapper;
import com.example.service.AIModelTrainingService;
import com.example.service.FaultClassificationService;
import com.example.service.RuleFallbackService;
import com.example.utils.FeatureNormalizer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import weka.classifiers.trees.RandomForest;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 故障分类服务实现 - 负责训练数据的采集和标注
 * @author zhexan
 * @since 2026-03-14
 */
@Slf4j
@Service
public class FaultClassificationServiceImpl extends ServiceImpl<FaultTrainingDataMapper, FaultTrainingData> implements FaultClassificationService {
    
    @Resource
    private FaultTrainingDataMapper faultTrainingDataMapper;
    
    @Resource
    private FeatureNormalizer featureNormalizer;
    
    @Resource
    private AIModelTrainingService aiModelTrainingService;
    
    @Resource
    private RuleFallbackService ruleFallbackService;
    
    // 存储每个客户端的随机森林模型（定制模型）
    private final Map<Integer, RandomForest> modelMap = new ConcurrentHashMap<>();
    
    // 存储每个客户端的新增训练数据缓冲区
    private final Map<Integer, List<FaultTrainingData>> newDataBufferMap = new ConcurrentHashMap<>();
    
    // 最大缓冲数据量（达到此数量自动触发模型更新）
    private static final int MAX_BUFFER_SIZE = 30;
    
    // 最小训练数据量（第一阶段：提高到 500）
    private static final int MIN_TRAINING_SIZE = 500;
    
    // 理想训练数据量
    private static final int IDEAL_TRAINING_SIZE = 1000;

    // 定制模型门槛（数据量超过此值才训练定制模型）
    private static final int CUSTOM_MODEL_THRESHOLD = 1000;
    
    /**
     * 服务启动时加载已训练的模型
     */
    @PostConstruct
    public void loadModels() {
        log.info("开始加载故障分类模型...");
        try {
            // 从数据库加载所有训练数据
            List<FaultTrainingData> allData = faultTrainingDataMapper.selectList(null);
            log.info("从数据库加载了 {} 条训练数据", allData.size());
            
            // 按客户端分组，训练定制模型
            Map<Integer, List<FaultTrainingData>> dataByClient = allData.stream()
                    .collect(Collectors.groupingBy(FaultTrainingData::getClientId));
            
            // 为每个客户端训练定制模型（如果数据足够）
            for (Map.Entry<Integer, List<FaultTrainingData>> entry : dataByClient.entrySet()) {
                Integer clientId = entry.getKey();
                List<FaultTrainingData> clientData = entry.getValue();
                
                if (clientData.size() >= CUSTOM_MODEL_THRESHOLD) {
                    try {
                        // 使用 AIModelTrainingService 训练定制模型
                        RandomForest randomForest = aiModelTrainingService.trainModel(clientData);
                        modelMap.put(clientId, randomForest);
                        log.info("客户端 {} 的定制模型加载并训练完成，数据量：{}", clientId, clientData.size());
                    } catch (Exception e) {
                        log.error("加载客户端 {} 的定制模型失败", clientId, e);
                    }
                } else if (clientData.size() >= MIN_TRAINING_SIZE) {
                    log.info("客户端 {} 的训练数据不足 ({} < {})，暂不训练定制模型", 
                             clientId, clientData.size(), CUSTOM_MODEL_THRESHOLD);
                }
            }
            
            log.info("模型和数据加载完成 - 定制模型：{} 个", modelMap.size());
        } catch (Exception e) {
            log.error("加载模型和数据时发生错误", e);
        }
    }
    
    @Override
    public void trainClientModel(int clientId) {
        try {
            // 从数据库加载该客户端的训练数据
            List<FaultTrainingData> trainingData = loadTrainingDataByClientId(clientId);
            
            if (trainingData == null || trainingData.size() < MIN_TRAINING_SIZE) {
                throw new IllegalArgumentException("训练数据不足，至少需要 " + MIN_TRAINING_SIZE + " 条数据，当前：" + 
                    (trainingData == null ? 0 : trainingData.size()));
            }
            
            log.info("开始为客户端 {} 训练随机森林模型，数据量：{}", clientId, trainingData.size());
            
            // 打印归一化参数信息
            featureNormalizer.printNormalizationInfo();
            
            // 如果数据量小于理想值但大于最小值，启用数据增强
            List<FaultTrainingData> actualTrainingData = trainingData;
            if (trainingData.size() < IDEAL_TRAINING_SIZE && trainingData.size() >= MIN_TRAINING_SIZE) {
                log.info("训练数据量在 [{}, {}) 之间，启用数据增强", MIN_TRAINING_SIZE, IDEAL_TRAINING_SIZE);
                actualTrainingData = aiModelTrainingService.augmentTrainingData(trainingData);
                log.info("数据增强后总数据量：{}", actualTrainingData.size());
            }
            
            // 使用 AIModelTrainingService 训练模型
            RandomForest randomForest = aiModelTrainingService.trainModel(actualTrainingData);
            
            // 保存模型到内存
            modelMap.put(clientId, randomForest);
            
            log.info("客户端 {} 的模型训练完成并保存到内存", clientId);
            
        } catch (Exception e) {
            log.error("训练随机森林模型时发生错误，客户端 ID: {}", clientId, e);
            throw new RuntimeException("模型训练失败：" + e.getMessage(), e);
        }
    }
    
    @Override
    public FaultClassificationResultVO classify(int clientId, RuntimeDetailVO runtimeData) {
        return classify(clientId, runtimeData, null);
    }
    
    @Override
    public FaultClassificationResultVO classify(int clientId, RuntimeDetailVO runtimeData, Double anomalyScore) {
        return classify(clientId, runtimeData, anomalyScore, null);
    }
    
    @Override
    public FaultClassificationResultVO classify(int clientId, RuntimeDetailVO runtimeData, Double anomalyScore, RootCauseAnalysisVO rcaResult) {
        RandomForest model = null;
        
        // 优先使用定制模型
        if (modelMap.containsKey(clientId)) {
            model = modelMap.get(clientId);
            log.debug("客户端 {} 使用定制模型", clientId);
        }
        
        // 如果没有定制模型，降级到规则分类
        if (model == null) {
            log.debug("客户端 {} 无可用模型，使用规则分类", clientId);
            return ruleFallbackService.classifyByRules(runtimeData, anomalyScore, rcaResult);
        }
        
        try {
            // 创建用于预测的数据数组
            double[] features = new double[] {
                    runtimeData.getCpuUsage(),
                    runtimeData.getMemoryUsage(),
                    runtimeData.getDiskUsage(),
                    runtimeData.getNetworkUpload(),
                    runtimeData.getNetworkDownload(),
                    runtimeData.getDiskRead(),
                    runtimeData.getDiskWrite()
            };
            
            // 预测
            int predictedLabel = aiModelTrainingService.predict(model, features);
            
            // 计算各类别的概率
            Map<String, Double> probabilities = calculateProbabilities(model, features);
            
            // 构建结果
            FaultClassificationResultVO result = new FaultClassificationResultVO();
            result.setCpuUsage(runtimeData.getCpuUsage());
            result.setMemoryUsage(runtimeData.getMemoryUsage());
            result.setDiskUsage(runtimeData.getDiskUsage());
            result.setNetworkUpload(runtimeData.getNetworkUpload());
            result.setNetworkDownload(runtimeData.getNetworkDownload());
            result.setDiskRead(runtimeData.getDiskRead());
            result.setDiskWrite(runtimeData.getDiskWrite());
            
            FaultType faultType = getFaultTypeByCode(predictedLabel);
            result.setFaultType(faultType);
            result.setFault(predictedLabel != FaultType.NORMAL.getCode());
            result.setConfidence(probabilities.getOrDefault(faultType.name(), 0.0));
            result.setProbabilities(probabilities);
            result.setDescription(generateDescription(faultType, result.getConfidence()));
            result.setRecommendation(generateRecommendation(faultType));
            
            return result;
            
        } catch (Exception e) {
            log.error("故障分类时发生错误，客户端 ID: {}", clientId, e);
            // 降级到基于规则的分类
            return ruleFallbackService.classifyByRules(runtimeData, anomalyScore, rcaResult);
        }
    }
    
    @Override
    public List<FaultClassificationResultVO> classifyBatch(int clientId, List<RuntimeDetailVO> dataList) {
        return dataList.stream()
                .map(data -> classify(clientId, data))
                .toList();
    }
    
    @Override
    public boolean isModelTrained(int clientId) {
        return modelMap.containsKey(clientId);
    }
    
    @Override
    public void clearModel(int clientId) {
        modelMap.remove(clientId);
        newDataBufferMap.remove(clientId);
        log.info("已清除客户端 {} 的故障分类模型", clientId);
    }
    
    @Override
    public List<Integer> getTrainedClientIds() {
        return new ArrayList<>(modelMap.keySet());
    }
    
    /**
     * 核心方法：根据异常检测结果自动标注并保存训练数据
     * @param clientId 客户端 ID
     * @param anomalyResult 异常检测结果
     * @param runtimeData 原始运行时数据
     */
    public void collectAndLabelTrainingData(Integer clientId, AnomalyResultVO anomalyResult, RuntimeDetailVO runtimeData) {
        try {
            // 自动标注故障类型
            Integer faultTypeCode = autoLabelFaultType(anomalyResult);
            
            // NORMAL 数据不存入训练数据库
            if (faultTypeCode.equals(FaultType.NORMAL.getCode())) {
                log.debug("NORMAL 数据不存入训练数据库 - 客户端：{}, 异常分数：{}",
                        clientId, anomalyResult.getAnomalyScore());
                return;
            }
            
            // 所有自动标注的数据都需要审核，状态=待审核(0)
            int status = 0;
            
            // 创建训练数据
            FaultTrainingData trainingData = createTrainingData(clientId, faultTypeCode, runtimeData, status);
            
            // 保存到数据库
            saveToDatabase(trainingData);
            
            // 待审核的数据不添加到内存缓冲区，等待管理员审核通过后再添加
            
            log.debug("已采集训练数据 - 客户端：{}, 故障类型：{}, 状态：{}, 异常分数：{}",
                    clientId, getFaultTypeName(faultTypeCode), 
                    status == 1 ? "已确认" : "待审核",
                    anomalyResult.getAnomalyScore());
                    
        } catch (Exception e) {
            log.error("采集训练数据失败，客户端 ID: {}", clientId, e);
        }
    }
    
    /**
     * 根据异常检测结果自动标注故障类型
     */
    public Integer autoLabelFaultType(AnomalyResultVO result) {
        // 如果不是异常，直接返回正常
        if (!result.isAnomaly()) {
            return FaultType.NORMAL.getCode();
        }

        double cpuUsage = result.getCpuUsage();
        double memoryUsage = result.getMemoryUsage();
        double diskUsage = result.getDiskUsage();
        double networkUpload = result.getNetworkUpload();
        double networkDownload = result.getNetworkDownload();
        double diskRead = result.getDiskRead();
        double diskWrite = result.getDiskWrite();

        // 判断 CPU 过载
        if (cpuUsage > 0.85) {
            return FaultType.CPU_OVERLOAD.getCode();
        }

        // 判断内存泄漏
        if (memoryUsage > 0.85) {
            return FaultType.MEMORY_LEAK.getCode();
        }

        // 判断磁盘已满
        if (diskUsage > 0.90) {
            return FaultType.DISK_FULL.getCode();
        }

        // 判断网络拥塞
        if (networkUpload > 10000 || networkDownload > 10000) {
            return FaultType.NETWORK_CONGESTION.getCode();
        }

        // 判断 IO 瓶颈
        if (diskRead > 200 || diskWrite > 200) {
            return FaultType.IO_BOTTLENECK.getCode();
        }

        // 检查是否为复合故障（多个指标同时异常）
        int abnormalCount = 0;
        if (cpuUsage > 0.7) abnormalCount++;
        if (memoryUsage > 0.7) abnormalCount++;
        if (diskUsage > 0.8) abnormalCount++;
        if (networkUpload > 5000 || networkDownload > 5000) abnormalCount++;

        if (abnormalCount >= 2) {
            return FaultType.MULTIPLE_FAULTS.getCode();
        }

        // 如果无法明确判断，但被孤立森林标记为异常，返回 ANOMALY_DETECTED（需人工确认）
        return FaultType.ANOMALY_DETECTED.getCode();
    }
    
    /**
     * 创建训练数据对象
     */
    private FaultTrainingData createTrainingData(Integer clientId, Integer faultTypeCode, RuntimeDetailVO data, int status) {
        FaultTrainingData trainingData = new FaultTrainingData();
        trainingData.setClientId(clientId);
        trainingData.setCpuUsage(data.getCpuUsage());
        trainingData.setMemoryUsage(data.getMemoryUsage());
        trainingData.setDiskUsage(data.getDiskUsage());
        trainingData.setNetworkUpload(data.getNetworkUpload());
        trainingData.setNetworkDownload(data.getNetworkDownload());
        trainingData.setDiskRead(data.getDiskRead());
        trainingData.setDiskWrite(data.getDiskWrite());
        trainingData.setFaultTypeCode(faultTypeCode);
        trainingData.setStatus(status);
        trainingData.setDataTime(LocalDateTime.now());
        trainingData.setCreateTime(LocalDateTime.now());
        return trainingData;
    }
    
    /**
     * 保存到数据库
     */
    private void saveToDatabase(FaultTrainingData trainingData) {
        try {
            faultTrainingDataMapper.insert(trainingData);
        } catch (Exception e) {
            log.error("保存训练数据到数据库失败", e);
            throw new RuntimeException("保存训练数据失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 添加到内存缓冲区
     */
    private void addToBuffer(Integer clientId, FaultTrainingData trainingData) {
        List<FaultTrainingData> buffer = newDataBufferMap.computeIfAbsent(clientId, k -> new ArrayList<>());
        buffer.add(trainingData);
        
        // 检查是否需要处理（比如触发模型更新通知）
        if (buffer.size() >= MAX_BUFFER_SIZE) {
            log.info("客户端 {} 的训练数据缓冲区达到阈值 ({})，触发模型更新检查", 
                    clientId, buffer.size());
            triggerModelTraining(clientId);
            buffer.clear();
        }
    }
    
    /**
     * 触发模型训练
     */
    private void triggerModelTraining(int clientId) {
        try {
            List<FaultTrainingData> allData = loadTrainingDataByClientId(clientId);
            log.info("客户端 {} 当前共有 {} 条已确认训练数据", clientId, allData.size());
            
            if (allData.size() >= MIN_TRAINING_SIZE) {
                log.info("开始为客户端 {} 自动训练模型", clientId);
                trainClientModel(clientId);
                log.info("客户端 {} 模型自动训练完成", clientId);
            } else {
                log.info("训练数据不足 ({}/{})，跳过模型训练", allData.size(), MIN_TRAINING_SIZE);
            }
        } catch (Exception e) {
            log.error("自动触发模型训练失败，客户端 ID: {}", clientId, e);
        }
    }
    
    /**
     * 获取故障类型名称
     */
    private String getFaultTypeName(Integer code) {
        for (FaultType type : FaultType.values()) {
            if (type.getCode().equals(code)) {
                return type.getDescription();
            }
        }
        return "未知";
    }
    
    /**
     * 从数据库加载指定客户端的所有训练数据
     */
    public List<FaultTrainingData> loadTrainingDataByClientId(Integer clientId) {
        LambdaQueryWrapper<FaultTrainingData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaultTrainingData::getClientId, clientId)
               .eq(FaultTrainingData::getStatus, 1)
               .orderByDesc(FaultTrainingData::getDataTime);
        return faultTrainingDataMapper.selectList(wrapper);
    }
    
    /**
     * 查询待审核的训练数据
     */
    @Override
    public List<FaultTrainingData> getPendingTrainingData(Integer clientId, int limit) {
        LambdaQueryWrapper<FaultTrainingData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaultTrainingData::getStatus, 0)
               .orderByAsc(FaultTrainingData::getDataTime);
        if (clientId != null) {
            wrapper.eq(FaultTrainingData::getClientId, clientId);
        }
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return faultTrainingDataMapper.selectList(wrapper);
    }

    /**
     * 分页查询待审核的训练数据
     */
    @Override
    public Map<String, Object> getPendingTrainingDataPaged(Integer clientId, int offset, int limit) {
        int pageNum = offset / limit + 1;
        Page<FaultTrainingData> page = new Page<>(pageNum, limit);
        
        LambdaQueryWrapper<FaultTrainingData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FaultTrainingData::getStatus, 0)
               .orderByAsc(FaultTrainingData::getDataTime);
        if (clientId != null) {
            wrapper.eq(FaultTrainingData::getClientId, clientId);
        }
        
        Page<FaultTrainingData> result = faultTrainingDataMapper.selectPage(page, wrapper);
        
        Map<String, Object> map = new HashMap<>();
        map.put("data", result.getRecords());
        map.put("total", result.getTotal());
        map.put("hasMore", result.getRecords().size() == limit);
        return map;
    }
    
    /**
     * 确认或修改训练数据标签
     */
    @Override
    public FaultTrainingData reviewTrainingData(int id, int faultTypeCode, boolean approved) {
        FaultTrainingData trainingData = faultTrainingDataMapper.selectById(id);
        if (trainingData == null) {
            throw new RuntimeException("训练数据不存在，ID: " + id);
        }
        
        trainingData.setFaultTypeCode(faultTypeCode);
        trainingData.setStatus(approved ? 1 : 2);
        
        faultTrainingDataMapper.updateById(trainingData);
        
        if (approved) {
            addToBuffer(trainingData.getClientId(), trainingData);
            log.info("训练数据已确认 - ID: {}, 故障类型: {}", id, getFaultTypeName(faultTypeCode));
        } else {
            log.info("训练数据已拒绝 - ID: {}", id);
        }
        
        return trainingData;
    }
    
    /**
     * 手动添加训练数据（用于人工标注）
     */
    public void addManualTrainingData(Integer clientId, Integer faultTypeCode, RuntimeDetailVO runtimeData) {
        FaultTrainingData trainingData = createTrainingData(clientId, faultTypeCode, runtimeData, 0);
        saveToDatabase(trainingData);
        addToBuffer(clientId, trainingData);
        log.info("手动添加训练数据 - 客户端：{}, 故障类型：{}", 
                clientId, getFaultTypeName(faultTypeCode));
    }

    /**
     * 计算各故障类型的概率（使用 Weka）
     */
    private Map<String, Double> calculateProbabilities(RandomForest model, double[] features) {
        Map<String, Double> probabilities = new HashMap<>();
        
        try {
            // 获取所有故障类型
            FaultType[] types = FaultType.values();
            
            // 创建 Weka 实例
            ArrayList<weka.core.Attribute> attributes = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                attributes.add(new weka.core.Attribute("attr" + i));
            }
            ArrayList<String> classLabels = new ArrayList<>();
            for (FaultType type : types) {
                classLabels.add(type.getCode().toString());
            }
            attributes.add(new weka.core.Attribute("label", classLabels));
            
            weka.core.Instances instances = new weka.core.Instances("ProbData", attributes, 1);
            instances.setClassIndex(7);
            
            double[] instanceValues = new double[8];
            System.arraycopy(features, 0, instanceValues, 0, 7);
            
            weka.core.DenseInstance instance = new weka.core.DenseInstance(1.0, instanceValues);
            instance.setDataset(instances);
            
            // 使用 Weka 的 distributionForInstance 获取概率分布
            double[] probArray = model.distributionForInstance(instance);
            
            // 将概率数组转换为 Map
            for (int i = 0; i < Math.min(types.length, probArray.length); i++) {
                probabilities.put(types[i].name(), probArray[i]);
            }
        } catch (Exception e) {
            log.warn("Weka 概率计算失败，使用均匀分布", e);
            // 降级到均匀分布
            for (FaultType type : FaultType.values()) {
                probabilities.put(type.name(), 1.0 / FaultType.values().length);
            }
        }
        
        return probabilities;
    }
    
    /**
     * 根据代码获取故障类型
     */
    private FaultType getFaultTypeByCode(int code) {
        for (FaultType type : FaultType.values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return FaultType.NORMAL;
    }
    
    /**
     * 生成故障描述
     */
    private String generateDescription(FaultType faultType, double confidence) {
        if (faultType == FaultType.NORMAL) {
            return String.format("系统运行正常 (%.1f%%)", confidence * 100);
        }
        return String.format("检测到 %s (置信度：%.1f%%)", 
                faultType.getDescription(), confidence * 100);
    }
    
    /**
     * 生成处理建议
     */
    private String generateRecommendation(FaultType faultType) {
        return switch (faultType) {
            case CPU_OVERLOAD -> "建议检查高 CPU 占用进程，考虑优化代码或增加 CPU 资源";
            case MEMORY_LEAK -> "建议检查内存泄漏，分析堆内存使用情况，重启应用释放内存";
            case DISK_FULL -> "建议清理磁盘空间，删除不必要的文件或扩容磁盘";
            case NETWORK_CONGESTION -> "建议检查网络连接，分析流量来源，优化网络配置";
            case IO_BOTTLENECK -> "建议检查磁盘 IO 性能，优化读写操作或升级存储设备";
            case MULTIPLE_FAULTS -> "检测到多种故障，建议进行全面系统检查和性能分析";
            default -> "系统正常运行，无需特殊处理";
        };
    }

    /**
     * 模型评估指标
     *
     * @param accuracy  准确率
     * @param precision 精确率
     * @param recall    召回率
     * @param f1        F1 分数
     */
        private record ModelMetrics(double accuracy, double precision, double recall, double f1) {
    }
    
    /**
     * 为训练数据添加高斯噪声
     * @param data 原始数据
     * @param sigma 标准差（噪声强度）
     * @return 添加噪声后的数据
     */
    private FaultTrainingData addGaussianNoise(FaultTrainingData data, double sigma) {
        FaultTrainingData noisyData = new FaultTrainingData();
        
        // 复制基本属性
        noisyData.setClientId(data.getClientId());
        noisyData.setFaultTypeCode(data.getFaultTypeCode());
        noisyData.setDataTime(LocalDateTime.now());
        
        // 为数值特征添加噪声
        Random random = new Random();
        
        double cpuNoisy = clamp(data.getCpuUsage() + random.nextGaussian() * sigma, 0, 1);
        double memoryNoisy = clamp(data.getMemoryUsage() + random.nextGaussian() * sigma, 0, 1);
        double diskNoisy = clamp(data.getDiskUsage() + random.nextGaussian() * sigma, 0, 1);
        double networkUploadNoisy = clamp(data.getNetworkUpload() + random.nextGaussian() * sigma * 10000, 0, 10000);
        double networkDownloadNoisy = clamp(data.getNetworkDownload() + random.nextGaussian() * sigma * 10000, 0, 10000);
        double diskReadNoisy = clamp(data.getDiskRead() + random.nextGaussian() * sigma * 500, 0, 500);
        double diskWriteNoisy = clamp(data.getDiskWrite() + random.nextGaussian() * sigma * 500, 0, 500);
        
        noisyData.setCpuUsage(cpuNoisy);
        noisyData.setMemoryUsage(memoryNoisy);
        noisyData.setDiskUsage(diskNoisy);
        noisyData.setNetworkUpload(networkUploadNoisy);
        noisyData.setNetworkDownload(networkDownloadNoisy);
        noisyData.setDiskRead(diskReadNoisy);
        noisyData.setDiskWrite(diskWriteNoisy);
        
        return noisyData;
    }
    
    /**
     * 截断值到指定范围
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
