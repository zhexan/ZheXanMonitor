package com.example.service;

import com.example.entity.dto.FaultTrainingData;
import lombok.Getter;
import smile.classification.RandomForest;

import java.util.List;

/**
 * AI 模型训练服务接口
 * 负责纯粹的机器学习逻辑：训练、预测、评估
 */
public interface AIModelTrainingService {

    /**
     * 训练随机森林模型
     *
     * @param trainingData 训练数据
     * @return 训练好的模型
     */
    RandomForest trainModel(List<FaultTrainingData> trainingData);

    /**
     * 使用模型进行预测
     *
     * @param model    模型
     * @param features 特征数据
     * @return 预测的故障类型代码
     */
    int predict(RandomForest model, double[] features);

    /**
     * 评估模型性能
     *
     * @param model    模型
     * @param testData 测试数据
     * @return 模型评估指标
     */
    ModelMetrics evaluateModel(RandomForest model, List<FaultTrainingData> testData);

    /**
     * 数据增强：通过添加高斯噪声生成新的训练样本
     */
    List<FaultTrainingData> augmentTrainingData(List<FaultTrainingData> originalData);

    /**
     * 模型评估指标
     *
     * @param accuracy  准确率
     * @param precision 精确率
     * @param recall    召回率
     * @param f1        F1 分数
     */
    record ModelMetrics(double accuracy, double precision, double recall, double f1) {

        @Override
        public String toString() {
            return String.format("准确率：%.2f%%, 精确率：%.2f%%, 召回率：%.2f%%, F1: %.2f%%",
                    accuracy * 100, precision * 100, recall * 100, f1 * 100);
        }
    }
}
