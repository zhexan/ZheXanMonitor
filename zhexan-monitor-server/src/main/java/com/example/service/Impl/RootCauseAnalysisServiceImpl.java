package com.example.service.Impl;

import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;
import com.example.entity.vo.response.RootCauseAnalysisVO;
import com.example.service.RootCauseAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 根因分析服务实现
 * 基于偏差分析和贡献度计算
 * 
 * @author zhexan
 * @since 2026-03-14
 */
@Slf4j
@Service
public class RootCauseAnalysisServiceImpl implements RootCauseAnalysisService {
    
    // 各指标的正常运行区间（可以从历史数据统计得出）
    private static final Map<String, double[]> NORMAL_RANGES = new HashMap<>();
    
    static {
        NORMAL_RANGES.put("cpuUsage", new double[]{0.0, 0.70});
        NORMAL_RANGES.put("memoryUsage", new double[]{0.0, 0.75});
        NORMAL_RANGES.put("diskUsage", new double[]{0.0, 0.80});
        NORMAL_RANGES.put("networkUpload", new double[]{0.0, 8000.0});
        NORMAL_RANGES.put("networkDownload", new double[]{0.0, 12000.0});
        NORMAL_RANGES.put("diskRead", new double[]{0.0, 400.0});
        NORMAL_RANGES.put("diskWrite", new double[]{0.0, 300.0});
    }
    
    // 指标中文名称映射
    private static final Map<String, String> METRIC_NAMES = new HashMap<>();
    
    static {
        METRIC_NAMES.put("cpuUsage", "CPU 使用率");
        METRIC_NAMES.put("memoryUsage", "内存使用率");
        METRIC_NAMES.put("diskUsage", "磁盘使用率");
        METRIC_NAMES.put("networkUpload", "网络上传");
        METRIC_NAMES.put("networkDownload", "网络下载");
        METRIC_NAMES.put("diskRead", "磁盘读取");
        METRIC_NAMES.put("diskWrite", "磁盘写入");
    }
    
    @Override
    public RootCauseAnalysisVO analyze(AnomalyResultVO anomalyResult) {
        return analyze(null, anomalyResult);
    }
    
    @Override
    public RootCauseAnalysisVO analyze(RuntimeDetailVO runtimeData, AnomalyResultVO anomalyResult) {
        RootCauseAnalysisVO result = new RootCauseAnalysisVO();
        result.setTimestamp(System.currentTimeMillis());
        
        // 如果不是异常，直接返回
        if (!anomalyResult.isAnomaly()) {
            result.setRootCauseDescription("系统运行正常，无需根因分析");
            return result;
        }
        
        // 计算各指标的偏差分数
        calculateDeviationScores(result, anomalyResult);
        
        // 找出贡献度最高的前两个指标
        identifyTopContributors(result);
        
        // 生成根因描述
        generateRootCauseDescription(result);
        
        log.debug("根因分析完成 - 主要因素：{}, 次要因素：{}", 
                 result.getTopContributor(), result.getSecondContributor());
        
        return result;
    }
    
    /**
     * 计算各指标的偏差分数
     */
    private void calculateDeviationScores(RootCauseAnalysisVO result, AnomalyResultVO anomalyResult) {
        // CPU 使用率
        double cpuScore = calculateDeviation(
            anomalyResult.getCpuUsage(), 
            NORMAL_RANGES.get("cpuUsage")
        );
        result.addContributor("cpuUsage", cpuScore);
        
        // 内存使用率
        double memoryScore = calculateDeviation(
            anomalyResult.getMemoryUsage(), 
            NORMAL_RANGES.get("memoryUsage")
        );
        result.addContributor("memoryUsage", memoryScore);
        
        // 磁盘使用率
        double diskScore = calculateDeviation(
            anomalyResult.getDiskUsage(), 
            NORMAL_RANGES.get("diskUsage")
        );
        result.addContributor("diskUsage", diskScore);
        
        // 网络上传
        double netUploadScore = calculateDeviation(
            anomalyResult.getNetworkUpload(), 
            NORMAL_RANGES.get("networkUpload")
        );
        result.addContributor("networkUpload", netUploadScore);
        
        // 网络下载
        double netDownloadScore = calculateDeviation(
            anomalyResult.getNetworkDownload(), 
            NORMAL_RANGES.get("networkDownload")
        );
        result.addContributor("networkDownload", netDownloadScore);
        
        // 磁盘读取
        double diskReadScore = calculateDeviation(
            anomalyResult.getDiskRead(), 
            NORMAL_RANGES.get("diskRead")
        );
        result.addContributor("diskRead", diskReadScore);
        
        // 磁盘写入
        double diskWriteScore = calculateDeviation(
            anomalyResult.getDiskWrite(), 
            NORMAL_RANGES.get("diskWrite")
        );
        result.addContributor("diskWrite", diskWriteScore);
    }
    
    /**
     * 计算单个指标的偏差分数
     * @param value 当前值
     * @param normalRange 正常范围 [min, max]
     * @return 偏差分数 (0-1)，0 表示正常，1 表示严重偏离
     */
    private double calculateDeviation(double value, double[] normalRange) {
        if (value < normalRange[0]) {
            // 低于最小值
            return Math.min(1.0, (normalRange[0] - value) / Math.max(normalRange[0], 0.01));
        } else if (value > normalRange[1]) {
            // 高于最大值
            double deviation = (value - normalRange[1]);
            double range = 1.0 - normalRange[1];
            if (range <= 0) {
                range = normalRange[1];
            }
            return Math.min(1.0, deviation / Math.max(range, 0.01));
        }
        // 在正常范围内
        return 0.0;
    }
    
    /**
     * 找出贡献度最高的前两个指标
     */
    private void identifyTopContributors(RootCauseAnalysisVO result) {
        Map<String, Double> scores = result.getContributorScores();
        
        // 按分数降序排序
        TreeMap<String, Double> sorted = new TreeMap<>((a, b) -> 
            Double.compare(scores.get(b), scores.get(a))
        );
        sorted.putAll(scores);
        
        // 获取前两个贡献者
        int i = 0;
        for (String metric : sorted.keySet()) {
            if (sorted.get(metric) > 0.01) { // 忽略微小偏差
                if (i == 0) {
                    result.setTopContributor(metric);
                } else if (i == 1) {
                    result.setSecondContributor(metric);
                }
                i++;
                if (i >= 2) break;
            }
        }
        
        // 如果没有找到显著贡献者，使用分数最高的
        if (result.getTopContributor() == null && !sorted.isEmpty()) {
            result.setTopContributor(sorted.firstKey());
        }
    }
    
    /**
     * 生成根因描述
     */
    private void generateRootCauseDescription(RootCauseAnalysisVO result) {
        StringBuilder desc = new StringBuilder();
        
        String topMetric = result.getTopContributor();
        String secondMetric = result.getSecondContributor();
        
        if (topMetric != null) {
            String topName = METRIC_NAMES.getOrDefault(topMetric, topMetric);
            Double topScore = result.getContributorScores().get(topMetric);
            
            desc.append(String.format("主要异常指标：%s (贡献度：%.1f%%)", 
                                     topName, topScore * 100));
            
            if (secondMetric != null) {
                String secondName = METRIC_NAMES.getOrDefault(secondMetric, secondMetric);
                Double secondScore = result.getContributorScores().get(secondMetric);
                
                desc.append(String.format("，次要异常指标：%s (贡献度：%.1f%%)", 
                                         secondName, secondScore * 100));
            }
        } else {
            desc.append("未检测到显著异常指标");
        }
        
        result.setRootCauseDescription(desc.toString());
    }
}
