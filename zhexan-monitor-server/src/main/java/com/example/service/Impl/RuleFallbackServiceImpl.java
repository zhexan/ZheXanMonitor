package com.example.service.Impl;

import com.example.entity.enums.FaultType;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;
import com.example.entity.vo.response.FaultClassificationResultVO;
import com.example.entity.vo.response.RootCauseAnalysisVO;
import com.example.service.RuleFallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 规则降级服务实现
 * 
 * @author zhexan
 * @since 2026-03-14
 */
@Slf4j
@Service
public class RuleFallbackServiceImpl implements RuleFallbackService {
    
    @Override
    public FaultClassificationResultVO classifyByRules(RuntimeDetailVO data, Double anomalyScore, RootCauseAnalysisVO rcaResult) {
        FaultClassificationResultVO result = new FaultClassificationResultVO();
        result.setCpuUsage(data.getCpuUsage());
        result.setMemoryUsage(data.getMemoryUsage());
        result.setDiskUsage(data.getDiskUsage());
        result.setNetworkUpload(data.getNetworkUpload());
        result.setNetworkDownload(data.getNetworkDownload());
        result.setDiskRead(data.getDiskRead());
        result.setDiskWrite(data.getDiskWrite());
        
        Integer faultTypeCode = autoLabelFaultType(createAnomalyResultFromData(data), rcaResult);
        boolean mlDetectedAnomaly = anomalyScore != null && anomalyScore > 0.5;
        
        if (mlDetectedAnomaly && faultTypeCode.equals(FaultType.NORMAL.getCode())) {
            faultTypeCode = FaultType.ANOMALY_DETECTED.getCode();
        }
        
        FaultType faultType = getFaultTypeByCode(faultTypeCode);
        
        result.setFaultType(faultType);
        result.setFault(!faultTypeCode.equals(FaultType.NORMAL.getCode()));
        
        double confidence = calculateConfidence(faultTypeCode, anomalyScore, rcaResult);
        result.setConfidence(confidence);
        result.setDescription(generateDescription(faultType, confidence));
        result.setRecommendation(generateRecommendation(faultType));
        
        return result;
    }
    
    private double calculateConfidence(Integer faultTypeCode, Double anomalyScore, RootCauseAnalysisVO rcaResult) {
        double baseConfidence = 0.8;
        
        if (anomalyScore != null && anomalyScore > 0.5) {
            if (faultTypeCode.equals(FaultType.ANOMALY_DETECTED.getCode())) {
                return Math.min(anomalyScore, 0.95);
            }
            baseConfidence = 0.7 + Math.min(anomalyScore * 0.25, 0.25);
        }
        
        if (rcaResult != null && rcaResult.getTopContributor() != null) {
            Double contributorScore = rcaResult.getContributorScores().get(rcaResult.getTopContributor());
            if (contributorScore != null && contributorScore > 0.3) {
                baseConfidence = Math.min(baseConfidence + contributorScore * 0.15, 0.95);
            }
        }
        
        return baseConfidence;
    }
    
    /**
     * 根据指标自动标注故障类型（结合 RCA 结果优化）
     */
    private Integer autoLabelFaultType(AnomalyResultVO result, RootCauseAnalysisVO rcaResult) {
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
        
        String rcaTopContributor = rcaResult != null ? rcaResult.getTopContributor() : null;
        Double rcaTopScore = rcaTopContributor != null && rcaResult != null ? 
            rcaResult.getContributorScores().get(rcaTopContributor) : null;
        boolean rcaSignificant = rcaTopScore != null && rcaTopScore > 0.3;
        
        double cpuThreshold = rcaSignificant && "cpuUsage".equals(rcaTopContributor) ? 0.70 : 0.85;
        double memoryThreshold = rcaSignificant && "memoryUsage".equals(rcaTopContributor) ? 0.70 : 0.85;
        double diskThreshold = rcaSignificant && "diskUsage".equals(rcaTopContributor) ? 0.80 : 0.90;
        double networkThresholdUpload = rcaSignificant && "networkUpload".equals(rcaTopContributor) ? 5000.0 : 10000.0;
        double networkThresholdDownload = rcaSignificant && "networkDownload".equals(rcaTopContributor) ? 5000.0 : 10000.0;
        
        if (cpuUsage > cpuThreshold) {
            return FaultType.CPU_OVERLOAD.getCode();
        }
        
        if (memoryUsage > memoryThreshold) {
            return FaultType.MEMORY_LEAK.getCode();
        }
        
        if (diskUsage > diskThreshold) {
            return FaultType.DISK_FULL.getCode();
        }
        
        if (networkUpload > networkThresholdUpload || networkDownload > networkThresholdDownload) {
            return FaultType.NETWORK_CONGESTION.getCode();
        }
        
        if (diskRead > 200 || diskWrite > 200) {
            return FaultType.IO_BOTTLENECK.getCode();
        }
        
        if (rcaSignificant && rcaTopContributor != null) {
            return mapContributorToFaultType(rcaTopContributor);
        }
        
        int abnormalCount = 0;
        if (cpuUsage > 0.7) abnormalCount++;
        if (memoryUsage > 0.7) abnormalCount++;
        if (diskUsage > 0.8) abnormalCount++;
        if (networkUpload > 5000 || networkDownload > 5000) abnormalCount++;
        
        if (abnormalCount >= 2) {
            return FaultType.MULTIPLE_FAULTS.getCode();
        }
        
        return FaultType.NORMAL.getCode();
    }
    
    private Integer mapContributorToFaultType(String contributor) {
        return switch (contributor) {
            case "cpuUsage" -> FaultType.CPU_OVERLOAD.getCode();
            case "memoryUsage" -> FaultType.MEMORY_LEAK.getCode();
            case "diskUsage" -> FaultType.DISK_FULL.getCode();
            case "networkUpload", "networkDownload" -> FaultType.NETWORK_CONGESTION.getCode();
            case "diskRead", "diskWrite" -> FaultType.IO_BOTTLENECK.getCode();
            default -> FaultType.ANOMALY_DETECTED.getCode();
        };
    }
    
    /**
     * 从 RuntimeDetailVO 创建 AnomalyResultVO
     */
    private AnomalyResultVO createAnomalyResultFromData(RuntimeDetailVO data) {
        AnomalyResultVO result = new AnomalyResultVO();
        result.setTimestamp(data.getTimestamp());
        result.setCpuUsage(data.getCpuUsage());
        result.setMemoryUsage(data.getMemoryUsage());
        result.setDiskUsage(data.getDiskUsage());
        result.setNetworkUpload(data.getNetworkUpload());
        result.setNetworkDownload(data.getNetworkDownload());
        result.setDiskRead(data.getDiskRead());
        result.setDiskWrite(data.getDiskWrite());
        return result;
    }
    
    /**
     * 获取故障类型
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
            case ANOMALY_DETECTED -> "ML 模型检测到异常但具体类型未知，建议收集更多数据进行分析";
            default -> "系统正常运行，无需特殊处理";
        };
    }
}
