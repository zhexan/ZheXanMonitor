package com.example.service.Impl;

import com.example.entity.dto.FaultTrainingData;
import com.example.entity.enums.FaultType;
import com.example.service.AIModelTrainingService;
import com.example.utils.FeatureNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import weka.classifiers.trees.RandomForest;
import weka.core.*;

import jakarta.annotation.Resource;
import java.util.*;

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
            featureNormalizer.printNormalizationInfo();
            
            List<FaultTrainingData> shuffledData = new ArrayList<>(trainingData);
            Collections.shuffle(shuffledData);
            
            int splitIndex = (int)(shuffledData.size() * TRAIN_RATIO);
            List<FaultTrainingData> trainSet = shuffledData.subList(0, splitIndex);
            List<FaultTrainingData> testSet = shuffledData.subList(splitIndex, shuffledData.size());
            
            List<FaultTrainingData> actualTrainSet = trainSet;
            if (trainingData.size() < IDEAL_TRAINING_SIZE) {
                log.info("训练数据量在 [{}, {}) 之间，对训练集启用数据增强", MIN_TRAINING_SIZE, IDEAL_TRAINING_SIZE);
                actualTrainSet = augmentTrainingData(trainSet);
                log.info("数据增强后训练集数据量：{}", actualTrainSet.size());
            }
            
            log.info("训练集最终：{} 条，测试集：{} 条", actualTrainSet.size(), testSet.size());
            
            Instances trainData = createWekaInstances(actualTrainSet);
            Instances testDataTemplate = createWekaInstances(testSet);
            
            RandomForest rf = new RandomForest();
            String[] options = weka.core.Utils.splitOptions("-I 100 -K 0 -S 1");
            rf.setOptions(options);
            rf.buildClassifier(trainData);
            
            ModelMetrics metrics = evaluateModel(rf, testDataTemplate, testSet);
            log.info("模型评估结果 - {}", metrics);
            
            if (metrics.accuracy() < 0.7) {
                throw new RuntimeException("模型准确率低于阈值 (70%)：" + String.format("%.2f%%", metrics.accuracy() * 100));
            }
            
            return rf;
            
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
            ArrayList<Attribute> attributes = new ArrayList<>();
            for (int i = 0; i < FEATURE_COUNT; i++) {
                attributes.add(new Attribute("attr" + i));
            }
            ArrayList<String> classLabels = new ArrayList<>();
            for (FaultType type : FaultType.values()) {
                classLabels.add(type.getCode().toString());
            }
            attributes.add(new Attribute("label", classLabels));
            
            Instances emptyData = new Instances("PredictData", attributes, 1);
            emptyData.setClassIndex(FEATURE_COUNT);
            
            double[] instanceValues = new double[FEATURE_COUNT + 1];
            System.arraycopy(features, 0, instanceValues, 0, FEATURE_COUNT);
            
            DenseInstance instance = new DenseInstance(1.0, instanceValues);
            instance.setDataset(emptyData);
            
            return (int) model.classifyInstance(instance);
        } catch (Exception e) {
            log.error("预测失败", e);
            throw new RuntimeException("预测失败：" + e.getMessage(), e);
        }
    }
    
    @Override
    public ModelMetrics evaluateModel(RandomForest model, List<FaultTrainingData> testData) {
        try {
            Instances testDataWeka = createWekaInstances(testData);
            return evaluateModel(model, testDataWeka, testData);
        } catch (Exception e) {
            log.error("评估模型失败", e);
            throw new RuntimeException("模型评估失败：" + e.getMessage(), e);
        }
    }
    
    private ModelMetrics evaluateModel(RandomForest model, Instances testData, List<FaultTrainingData> testDataList) {
        int correct = 0;
        int total = testDataList.size();
        
        Map<Integer, Integer> truePositives = new HashMap<>();
        Map<Integer, Integer> predictedPositives = new HashMap<>();
        Map<Integer, Integer> actualCounts = new HashMap<>();
        
        try {
            for (int i = 0; i < testData.numInstances(); i++) {
                Instance instance = testData.instance(i);
                int predicted = (int) model.classifyInstance(instance);
                int actual = testDataList.get(i).getFaultTypeCode();
                
                if (predicted == actual) {
                    correct++;
                }
                
                truePositives.merge(actual, (predicted == actual) ? 1 : 0, Integer::sum);
                predictedPositives.merge(predicted, 1, Integer::sum);
                actualCounts.merge(actual, 1, Integer::sum);
            }
        } catch (Exception e) {
            log.error("评估过程出错", e);
            throw new RuntimeException("评估过程出错：" + e.getMessage(), e);
        }
        
        double accuracy = (double) correct / total;
        
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
    
    private Instances createWekaInstances(List<FaultTrainingData> dataList) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        
        attributes.add(new Attribute("cpu"));
        attributes.add(new Attribute("memory"));
        attributes.add(new Attribute("disk"));
        attributes.add(new Attribute("networkUpload"));
        attributes.add(new Attribute("networkDownload"));
        attributes.add(new Attribute("diskRead"));
        attributes.add(new Attribute("diskWrite"));
        
        ArrayList<String> classLabels = new ArrayList<>();
        for (FaultType type : FaultType.values()) {
            classLabels.add(type.getCode().toString());
        }
        attributes.add(new Attribute("label", classLabels));
        
        Instances data = new Instances("FaultData", attributes, dataList.size());
        data.setClassIndex(attributes.size() - 1);
        
        for (FaultTrainingData faultData : dataList) {
            double[] values = new double[attributes.size()];
            values[0] = faultData.getCpuUsage();
            values[1] = faultData.getMemoryUsage();
            values[2] = faultData.getDiskUsage();
            values[3] = faultData.getNetworkUpload();
            values[4] = faultData.getNetworkDownload();
            values[5] = faultData.getDiskRead();
            values[6] = faultData.getDiskWrite();
            values[7] = faultData.getFaultTypeCode();
            
            data.add(new DenseInstance(1.0, values));
        }
        
        return data;
    }
    
    public List<FaultTrainingData> augmentTrainingData(List<FaultTrainingData> originalData) {
        List<FaultTrainingData> augmented = new ArrayList<>();
        
        for (FaultTrainingData data : originalData) {
            augmented.add(data);
            augmented.add(addGaussianNoise(data, 0.02));
            augmented.add(addGaussianNoise(data, 0.05));
        }
        
        return augmented;
    }
    
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
    
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
