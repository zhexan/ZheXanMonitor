package com.example.service.Impl;

import com.example.entity.dto.FaultTrainingData;
import com.example.entity.enums.FaultType;
import com.example.service.AIModelTrainingService;
import com.example.utils.FeatureNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import smile.classification.RandomForest;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructType;
import smile.data.formula.Formula;
import smile.data.vector.IntVector;
import smile.base.cart.SplitRule;

import jakarta.annotation.Resource;
import java.util.*;

/**
 * AI 模型训练服务实现
 * 
 * @author zhexan
 * @since 2026-03-14
 */
@Slf4j
@Service
public class AIModelTrainingServiceImpl implements AIModelTrainingService {
    
    @Resource
    private FeatureNormalizer featureNormalizer;
    
    private static final int FEATURE_COUNT = 7;
    private static final double TRAIN_RATIO = 0.8;
    private static final int MIN_TRAINING_SIZE = 500;
    private static final int IDEAL_TRAINING_SIZE = 1000;
    
    @Override
    public RandomForest trainModel(List<FaultTrainingData> trainingData) {
        if (trainingData == null || trainingData.size() < MIN_TRAINING_SIZE) {
            throw new IllegalArgumentException("训练数据不足，至少需要 " + MIN_TRAINING_SIZE + " 条");
        }
        
        try {
            // 打印归一化参数
            featureNormalizer.printNormalizationInfo();
            
            // 打乱数据
            List<FaultTrainingData> shuffledData = new ArrayList<>(trainingData);
            Collections.shuffle(shuffledData);
            
            // 80/20 划分训练集和测试集
            int splitIndex = (int)(shuffledData.size() * TRAIN_RATIO);
            List<FaultTrainingData> trainSet = shuffledData.subList(0, splitIndex);
            List<FaultTrainingData> testSet = shuffledData.subList(splitIndex, shuffledData.size());
            
            // 数据增强只在训练集上进行
            List<FaultTrainingData> actualTrainSet = trainSet;
            if (trainingData.size() < IDEAL_TRAINING_SIZE) {
                log.info("训练数据量在 [{}, {}) 之间，对训练集启用数据增强", MIN_TRAINING_SIZE, IDEAL_TRAINING_SIZE);
                actualTrainSet = augmentTrainingData(trainSet);
                log.info("数据增强后训练集数据量：{}", actualTrainSet.size());
            }
            
            log.info("训练集最终：{} 条，测试集：{} 条", actualTrainSet.size(), testSet.size());
            
            // 创建 DataFrame
            DataFrame df = createDataFrame(actualTrainSet);
            
            // 训练随机森林
            RandomForest model = RandomForest.fit(
                Formula.lhs("label"), 
                df,
                100,                        // ntrees
                (int) Math.sqrt(FEATURE_COUNT), // mtry
                SplitRule.GINI,             // 分裂规则
                20,                         // maxDepth
                Integer.MAX_VALUE,          // maxNodes
                2,                          // nodeSize
                1.0                         // subsample
            );
            
            // 评估模型
            ModelMetrics metrics = evaluateModel(model, testSet);
            log.info("模型评估结果 - {}", metrics);
            
            // 质量检查
            if (metrics.accuracy() < 0.7) {
                throw new RuntimeException("模型准确率低于阈值 (70%)：" + String.format("%.2f%%", metrics.accuracy() * 100));
            }
            
            return model;
            
        } catch (Exception e) {
            log.error("训练模型失败", e);
            throw new RuntimeException("模型训练失败：" + e.getMessage(), e);
        }
    }
    
    @Override
    public int predict(RandomForest model, double[] features) {
        if (model == null || features == null || features.length != FEATURE_COUNT) {
            throw new IllegalArgumentException("模型或特征数据无效");
        }
        
        try {
            StructType schema = model.schema();
            Tuple tuple = Tuple.of(features, schema);
            return model.predict(tuple);
        } catch (Exception e) {
            log.error("预测失败", e);
            throw new RuntimeException("预测失败：" + e.getMessage(), e);
        }
    }
    
    @Override
    public ModelMetrics evaluateModel(RandomForest model, List<FaultTrainingData> testData) {
        int correct = 0;
        int total = testData.size();
        
        Map<Integer, Integer> truePositives = new HashMap<>();
        Map<Integer, Integer> predictedPositives = new HashMap<>();
        
        // 统计各个类别的真实样本数
        Map<Integer, Integer> actualCounts = new HashMap<>();
        
        for (FaultTrainingData data : testData) {
            double[] features = extractFeatures(data);
            int predicted = predict(model, features);
            int actual = data.getFaultTypeCode();
            
            if (predicted == actual) {
                correct++;
            }
            
            truePositives.merge(actual, (predicted == actual) ? 1 : 0, Integer::sum);
            predictedPositives.merge(predicted, 1, Integer::sum);
            actualCounts.merge(actual, 1, Integer::sum);
        }
        
        double accuracy = (double) correct / total;
        
        // 计算宏平均精确率和召回率
        double precisionSum = 0.0;
        double recallSum = 0.0;
        
        for (FaultType type : FaultType.values()) {
            int tp = truePositives.getOrDefault(type.getCode(), 0);
            int pp = predictedPositives.getOrDefault(type.getCode(), 0);
            int actualCount = actualCounts.getOrDefault(type.getCode(), 0);
            
            if (pp > 0) {
                precisionSum += (double) tp / pp;
            }
            if (actualCount > 0) {
                recallSum += (double) tp / actualCount;
            }
        }
        
        int classCount = FaultType.values().length;
        double precision = precisionSum / classCount;
        double recall = recallSum / classCount;
        double f1 = (precision + recall > 0) ? (2 * precision * recall) / (precision + recall) : 0;
        
        return new ModelMetrics(accuracy, precision, recall, f1);
    }
    
    /**
     * 创建 DataFrame
     */
    private DataFrame createDataFrame(List<FaultTrainingData> dataList) {
        int n = dataList.size();
        double[][] data = new double[n][FEATURE_COUNT + 1];
        
        for (int i = 0; i < n; i++) {
            FaultTrainingData faultData = dataList.get(i);
            data[i][0] = faultData.getCpuUsage();
            data[i][1] = faultData.getMemoryUsage();
            data[i][2] = faultData.getDiskUsage();
            data[i][3] = faultData.getNetworkUpload();
            data[i][4] = faultData.getNetworkDownload();
            data[i][5] = faultData.getDiskRead();
            data[i][6] = faultData.getDiskWrite();
            data[i][7] = faultData.getFaultTypeCode();
        }
        
        return DataFrame.of(data, 
                "cpu", "memory", "disk", "net_upload", "net_download", "disk_read", "disk_write", "label");
    }
    
    /**
     * 提取特征
     */
    private double[] extractFeatures(FaultTrainingData data) {
        return new double[] {
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
     * 数据增强：通过添加高斯噪声生成新的训练样本
     */
    public List<FaultTrainingData> augmentTrainingData(List<FaultTrainingData> originalData) {
        List<FaultTrainingData> augmented = new ArrayList<>();
        
        for (FaultTrainingData data : originalData) {
            augmented.add(data);
            augmented.add(addGaussianNoise(data, 0.02));
            augmented.add(addGaussianNoise(data, 0.05));
        }
        
        return augmented;
    }
    
    /**
     * 为训练数据添加高斯噪声
     */
    private FaultTrainingData addGaussianNoise(FaultTrainingData data, double sigma) {
        FaultTrainingData noisyData = new FaultTrainingData();
        noisyData.setClientId(data.getClientId());
        noisyData.setFaultTypeCode(data.getFaultTypeCode());
        noisyData.setDataTime(java.time.LocalDateTime.now());
        
        Random random = new Random();
        
        noisyData.setCpuUsage(clamp(data.getCpuUsage() + random.nextGaussian() * sigma, 0, 1));
        noisyData.setMemoryUsage(clamp(data.getMemoryUsage() + random.nextGaussian() * sigma, 0, 1));
        noisyData.setDiskUsage(clamp(data.getDiskUsage() + random.nextGaussian() * sigma, 0, 1));
        noisyData.setNetworkUpload(clamp(data.getNetworkUpload() + random.nextGaussian() * sigma * 10000, 0, 10000));
        noisyData.setNetworkDownload(clamp(data.getNetworkDownload() + random.nextGaussian() * sigma * 10000, 0, 10000));
        noisyData.setDiskRead(clamp(data.getDiskRead() + random.nextGaussian() * sigma * 500, 0, 500));
        noisyData.setDiskWrite(clamp(data.getDiskWrite() + random.nextGaussian() * sigma * 500, 0, 500));
        
        return noisyData;
    }
    
    /**
     * 截断值到指定范围
     */
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
