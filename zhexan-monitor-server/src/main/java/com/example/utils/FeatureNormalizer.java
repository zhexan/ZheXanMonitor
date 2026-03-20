package com.example.utils;

import com.example.entity.dto.FaultTrainingData;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 特征归一化工具类
 * 将不同量纲的特征缩放到 [0,1] 区间
 * 
 * @author zhexan
 * @since 2026-03-14
 */
@Component
public class FeatureNormalizer {
    
    /**
     * 各特征的最大值（根据实际业务场景设定）
     */
    private static final Map<String, Double> MAX_VALUES = Map.ofEntries(
        Map.entry("cpuUsage", 1.0),           // CPU 使用率：0-1
        Map.entry("memoryUsage", 1.0),        // 内存使用率：0-1
        Map.entry("diskUsage", 1.0),          // 磁盘使用率：0-1
        Map.entry("networkUpload", 10000.0),  // 网络上传：0-10000 KB/s
        Map.entry("networkDownload", 10000.0),// 网络下载：0-10000 KB/s
        Map.entry("diskRead", 500.0),         // 磁盘读取：0-500 MB/s
        Map.entry("diskWrite", 500.0)         // 磁盘写入：0-500 MB/s
    );
    
    /**
     * 特征名称数组（顺序必须与下方 normalize 方法一致）
     */
    private static final String[] FEATURE_NAMES = {
        "cpuUsage", "memoryUsage", "diskUsage", 
        "networkUpload", "networkDownload", "diskRead", "diskWrite"
    };
    
    /**
     * 对 FaultTrainingData 进行归一化
     * 
     * @param data 原始训练数据
     * @return 归一化后的特征数组
     */
    public double[] normalize(FaultTrainingData data) {
        double[] features = new double[FEATURE_NAMES.length];
        
        features[0] = normalizeValue(data.getCpuUsage(), "cpuUsage");
        features[1] = normalizeValue(data.getMemoryUsage(), "memoryUsage");
        features[2] = normalizeValue(data.getDiskUsage(), "diskUsage");
        features[3] = normalizeValue(data.getNetworkUpload(), "networkUpload");
        features[4] = normalizeValue(data.getNetworkDownload(), "networkDownload");
        features[5] = normalizeValue(data.getDiskRead(), "diskRead");
        features[6] = normalizeValue(data.getDiskWrite(), "diskWrite");
        
        return features;
    }
    
    /**
     * 对 RuntimeDetailVO 进行归一化
     * 
     * @param runtimeData 运行时数据
     * @return 归一化后的特征数组
     */
    public double[] normalize(Object runtimeData) {
        // 通过反射获取属性值（兼容 RuntimeDetailVO）
        try {
            double[] features = new double[FEATURE_NAMES.length];
            
            for (int i = 0; i < FEATURE_NAMES.length; i++) {
                String fieldName = FEATURE_NAMES[i];
                java.lang.reflect.Method getter = runtimeData.getClass()
                    .getMethod("get" + capitalize(fieldName));
                Object value = getter.invoke(runtimeData);
                
                if (value instanceof Number) {
                    features[i] = normalizeValue(((Number) value).doubleValue(), fieldName);
                } else {
                    features[i] = 0.0;
                }
            }
            
            return features;
        } catch (Exception e) {
            throw new RuntimeException("归一化失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 归一化单个特征值
     * 
     * @param value 原始值
     * @param featureName 特征名称
     * @return 归一化后的值 [0,1]
     */
    private double normalizeValue(double value, String featureName) {
        Double maxValue = MAX_VALUES.get(featureName);
        if (maxValue == null || maxValue <= 0) {
            // 如果没有找到最大值定义，返回原值
            return value;
        }
        
        // 截断到 [0, max] 范围
        double clampedValue = Math.max(0, Math.min(value, maxValue));
        
        // 归一化到 [0, 1]
        return clampedValue / maxValue;
    }
    
    /**
     * 反归一化（将归一化后的值还原）
     * 
     * @param normalizedFeatures 归一化后的特征数组
     * @return 原始特征值的描述
     */
    public String denormalizeToString(double[] normalizedFeatures) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FEATURE_NAMES.length && i < normalizedFeatures.length; i++) {
            double normalized = normalizedFeatures[i];
            double original = normalized * MAX_VALUES.getOrDefault(FEATURE_NAMES[i], 1.0);
            sb.append(FEATURE_NAMES[i]).append(": ").append(String.format("%.2f", original));
            if (i < FEATURE_NAMES.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
    
    /**
     * 字符串首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    
    /**
     * 获取所有特征名称
     */
    public static String[] getFeatureNames() {
        return FEATURE_NAMES.clone();
    }
    
    /**
     * 打印归一化参数信息
     */
    public void printNormalizationInfo() {
        System.out.println("=== 特征归一化参数 ===");
        for (Map.Entry<String, Double> entry : MAX_VALUES.entrySet()) {
            System.out.printf("%-15s: Max = %10.2f%n", entry.getKey(), entry.getValue());
        }
        System.out.println("====================");
    }
}
