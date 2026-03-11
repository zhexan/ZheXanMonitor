package com.example.service.Impl;

import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 孤立森林异常检测服务测试类
 * @author zhexan
 * @since 2026-03-02
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "websocket.enabled=false"
})
public class AnomalyDetectionServiceTest {

    private final AnomalyDetectionServiceImpl anomalyDetectionService = new AnomalyDetectionServiceImpl();

    @Test
    public void testModelTrainingAndDetection() {
        try {
            // 生成测试数据
            List<RuntimeDetailVO> normalData = generateNormalData(100);
            List<RuntimeDetailVO> anomalyData = generateAnomalyData(10);

            log.info("=== 孤立森林异常检测测试开始 ===");
            log.info("正常数据量: {}, 异常数据量: {}", normalData.size(), anomalyData.size());

            // 训练模型
            int clientId = 1;
            log.info("开始训练模型...");
            anomalyDetectionService.trainModel(clientId, normalData);
            log.info("模型训练完成");

            // 检查模型是否训练成功
            boolean isTrained = anomalyDetectionService.isModelTrained(clientId);
            log.info("模型训练状态: {}", isTrained ? "成功" : "失败");

            if (!isTrained) {
                log.error("模型训练失败，无法继续测试");
                return;
            }


            // 测试批量检测
            log.info("\n=== 批量检测测试 ===");
            List<AnomalyResultVO> batchResults = anomalyDetectionService.detectAnomalies(clientId, anomalyData);
            log.info("批量检测完成，结果数量: {}", batchResults.size());

            // 测试异常数据的检测结果
            log.info("\n=== 异常数据检测结果 ===");
            int anomalyDetected = 0;
            for (int i = 0; i < anomalyData.size(); i++) {
                RuntimeDetailVO data = anomalyData.get(i);
                AnomalyResultVO result = anomalyDetectionService.detectAnomaly(clientId, data);
                log.info("异常数据 {}: 异常分数={}, 是否异常={}, CPU={}, 内存={}",
                        i+1, String.format("%.4f", result.getAnomalyScore()), result.isAnomaly(),
                        data.getCpuUsage(), data.getMemoryUsage());
                if (result.isAnomaly()) {
                    anomalyDetected++;
                }
            }

            // 测试正常数据的检测结果
            log.info("\n=== 正常数据检测结果 ===");
            int normalDetected = 0;
            for (int i = 0; i < normalData.size(); i++) {
                RuntimeDetailVO data = normalData.get(i);
                AnomalyResultVO result = anomalyDetectionService.detectAnomaly(clientId, data);
//                log.info("数据 {}: 异常分数={}, 是否异常={}",
//                        i+1, String.format("%.4f", result.getAnomalyScore()), result.isAnomaly());
                if (result.isAnomaly()) {
                    normalDetected++;
                }
            }
            // 统计结果
            log.info("\n=== 测试结果统计 ===");
            log.info("正常数据误报率: {}/{} ({}%)",
                    normalDetected, normalData.size(),
                    String.format("%.2f",((double)normalDetected/normalData.size())*100));
            log.info("异常数据检出率: {}/{} ({}%)",
                    anomalyDetected, anomalyData.size(),
                    String.format("%.2f",(double)anomalyDetected/anomalyData.size()*100));





            // 清理测试数据
            anomalyDetectionService.clearModel(clientId);
            log.info("测试完成，模型已清理");

        } catch (Exception e) {
            log.error("测试过程中发生错误", e);
        }
    }

    /**
     * 生成正常监控数据
     * @param count 数据条数
     * @return 正常数据列表
     */
    private List<RuntimeDetailVO> generateNormalData(int count) {
        List<RuntimeDetailVO> dataList = new ArrayList<>();
        Random random = new Random(42); // 固定种子保证可重现

        for (int i = 0; i < count; i++) {
            RuntimeDetailVO data = new RuntimeDetailVO();
            data.setTimestamp(System.currentTimeMillis() - (count - i) * 60000L); // 每分钟一条

            // 正常范围内的随机值
            data.setCpuUsage(0.1 + random.nextDouble() * 0.6); // 10%-70%
            data.setMemoryUsage(0.2 + random.nextDouble() * 0.5); // 20%-70%
            data.setDiskUsage(0.3 + random.nextDouble() * 0.4); // 30%-70%
            data.setNetworkUpload(random.nextDouble() * 5000); // 0-5000 KB/s
            data.setNetworkDownload(random.nextDouble() * 5000); // 0-5000 KB/s
            data.setDiskRead(random.nextDouble() * 1000); // 0-1000 MB/s
            data.setDiskWrite(random.nextDouble() * 1000); // 0-1000 MB/s

            dataList.add(data);
        }

        return dataList;
    }

    /**
     * 生成异常监控数据
     * @param count 数据条数
     * @return 异常数据列表
     */
    private List<RuntimeDetailVO> generateAnomalyData(int count) {
        List<RuntimeDetailVO> dataList = new ArrayList<>();
        Random random = new Random(123); // 不同种子

        for (int i = 0; i < count; i++) {
            RuntimeDetailVO data = new RuntimeDetailVO();
            data.setTimestamp(System.currentTimeMillis() - i * 60000L);

            // 异常值生成策略
            int anomalyType = i % 4;
            switch (anomalyType) {
                case 0: // CPU异常高
                    data.setCpuUsage(0.9 + random.nextDouble() * 0.1); // 90%-100%
                    data.setMemoryUsage(0.2 + random.nextDouble() * 0.5);
                    break;
                case 1: // 内存异常高
                    data.setCpuUsage(0.1 + random.nextDouble() * 0.6);
                    data.setMemoryUsage(0.9 + random.nextDouble() * 0.1); // 90%-100%
                    break;
                case 2: // 网络流量异常
                    data.setCpuUsage(0.1 + random.nextDouble() * 0.6);
                    data.setMemoryUsage(0.2 + random.nextDouble() * 0.5);
                    data.setNetworkUpload(15000 + random.nextDouble() * 10000); // 15000-25000 KB/s
                    data.setNetworkDownload(15000 + random.nextDouble() * 10000); // 15000-25000 KB/s
                    break;
                case 3: // 综合异常
                    data.setCpuUsage(0.8 + random.nextDouble() * 0.2);
                    data.setMemoryUsage(0.8 + random.nextDouble() * 0.2);
                    data.setDiskUsage(0.9 + random.nextDouble() * 0.1);
                    break;
            }

            // 其他字段设置为正常范围
            if (data.getDiskUsage() == 0) {
                data.setDiskUsage(0.4 + random.nextDouble() * 0.3);
            }
            if (data.getNetworkUpload() == 0) {
                data.setNetworkUpload(random.nextDouble() * 3000);
            }
            if (data.getNetworkDownload() == 0) {
                data.setNetworkDownload(random.nextDouble() * 3000);
            }
            data.setDiskRead(random.nextDouble() * 500);
            data.setDiskWrite(random.nextDouble() * 500);

            dataList.add(data);
        }

        return dataList;
    }

    /**
     * 性能测试
     */
    @Test
    public void testPerformance() {
        try {
            log.info("=== 性能测试开始 ===");

            // 生成大量数据
            List<RuntimeDetailVO> trainingData = generateNormalData(500);
            List<RuntimeDetailVO> testData = generateNormalData(1000);

            int clientId = 2;

            // 训练时间测试
            long startTime = System.currentTimeMillis();
            anomalyDetectionService.trainModel(clientId, trainingData);
            long trainTime = System.currentTimeMillis() - startTime;
            log.info("训练500条数据耗时: {} ms", trainTime);

            // 检测时间测试
            startTime = System.currentTimeMillis();
            AnomalyResultVO result = anomalyDetectionService.detectAnomaly(clientId, testData.get(0));
            long detectTime = System.currentTimeMillis() - startTime;
            log.info("单次检测耗时: {} ms", detectTime);

            // 批量检测时间测试
            startTime = System.currentTimeMillis();
            List<AnomalyResultVO> results = anomalyDetectionService.detectAnomalies(clientId, testData);
            long batchTime = System.currentTimeMillis() - startTime;
            log.info("批量检测1000条数据耗时: {} ms", batchTime);
            log.info("平均每条检测耗时: {} ms", String.format("%.2f",(double)batchTime/testData.size()));

            anomalyDetectionService.clearModel(clientId);
            log.info("性能测试完成");

        } catch (Exception e) {
            log.error("性能测试过程中发生错误", e);
        }
    }
}

