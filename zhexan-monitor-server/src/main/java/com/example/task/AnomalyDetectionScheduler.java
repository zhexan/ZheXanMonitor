package com.example.task;

import com.alibaba.fastjson2.JSON;
import com.example.entity.dto.Account;
import com.example.entity.dto.AnomalyAlarm;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.AnomalyResultVO;
import com.example.entity.vo.response.ClientPreviewVO;
import com.example.entity.vo.response.FaultClassificationResultVO;
import com.example.entity.vo.response.RootCauseAnalysisVO;
import com.example.entity.vo.response.RuntimeHistoryVO;
import com.example.service.AccountService;
import com.example.service.AnomalyAlarmService;
import com.example.service.AnomalyDetectionService;
import com.example.service.ClientService;
import com.example.service.FaultClassificationService;
import com.example.service.RootCauseAnalysisService;
import com.example.websocket.AlarmWebSocket;
import jakarta.annotation.Resource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常检测定时任务调度器
 * 自动执行异常检测并推送告警到前端
 * @author zhexan
 * @since 2026-03-03
 */
@Slf4j
@Component
public class AnomalyDetectionScheduler {
    
    @Resource
    private AnomalyDetectionService anomalyDetectionService;
    
    @Resource
    private ClientService clientService;
    
    @Resource
    private AnomalyAlarmService anomalyAlarmService;
    
    @Resource
    private AlarmWebSocket alarmWebSocket;
    
    @Resource
    private AccountService accountService;
    
    @Resource
    private RootCauseAnalysisService rootCauseAnalysisService;
    
    @Resource
    private FaultClassificationService faultClassificationService;


    /**
     * -- SETTER --
     *  设置置信度阈值，用于控制高置信度分类结果加入训练集
     */
    @Setter
    private double confidenceThreshold = 0.85;

    /**
     * 服务启动后延迟 10 秒执行初始化训练任务
     * 检查所有客户端的数据量，达到 100 条自动开始训练
     */
    @Scheduled(fixedDelay = 1000000, initialDelay = 10000)
    public void initializeModelTraining() {
        log.info("开始执行模型初始化训练检查");
        
        try {
            // 获取所有客户端 ID
            List<Integer> allClientIds = clientService.listClients().stream()
                    .map(ClientPreviewVO::getId)
                    .toList();
            
            if (allClientIds.isEmpty()) {
                log.info("当前没有注册的客户端，跳过模型训练");
                return;
            }
            
            int trainedCount = 0;
            int waitingCount = 0;
            
            // 对每个客户端检查数据量并决定是否训练
            for (Integer clientId : allClientIds) {
                try {
                    // 如果模型已经训练过，跳过
                    if (anomalyDetectionService.isModelTrained(clientId)) {
                        log.info("客户端 {} 的模型已训练，跳过", clientId);
                        trainedCount++;
                        continue;
                    }
                    
                    // 获取历史数据
                    RuntimeHistoryVO history = new RuntimeHistoryVO();
                    history.setRuntimeDataList(clientService.getModelTrainingData(clientId));
                    
                    if (history.getRuntimeDataList() == null) {
                        log.info("客户端 {} 暂无历史数据，等待数据累积", clientId);
                        waitingCount++;
                        continue;
                    }
                    
                    List<RuntimeDetailVO> historyData = history.getRuntimeDataList().stream()
                            .map(dto -> {
                                RuntimeDetailVO vo = new RuntimeDetailVO();
                                BeanUtils.copyProperties(dto, vo);
                                return vo;
                            })
                            .toList();

                    // 检查数据量是否达到训练要求
                    if (historyData.size() >= 50) {  // 降低到 50 条即可开始训练
                        log.info("客户端 {} 数据量达到 {} 条，开始初始化训练模型", clientId, historyData.size());
                        anomalyDetectionService.trainModel(clientId, historyData);
                        trainedCount++;
                        log.info("客户端 {} 模型初始化训练完成", clientId);
                    } else {
                        log.info("客户端 {} 当前数据量 {} 条，未达到训练门槛 (50 条)，继续等待", 
                                clientId, historyData.size());
                        waitingCount++;
                    }
                    
                } catch (Exception e) {
                    log.error("客户端 {} 模型初始化训练失败：{}", clientId, e.getMessage(), e);
                }
            }
            
            log.info("模型初始化训练检查完成，已训练：{}, 等待中：{}", trainedCount, waitingCount);
            
            // 如果所有客户端都已训练或没有客户端，停止此定时任务
            if (trainedCount == allClientIds.size()) {
                log.info("所有客户端模型已完成初始化，停止初始化检查任务");
                // 注意：这里不主动停止，因为可能有新客户端注册
            }
            
        } catch (Exception e) {
            log.error("执行模型初始化训练检查时发生错误：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 每10秒执行一次异常检测
     * 可根据实际需求调整频率
     */
    @Scheduled(fixedRate = 10000, initialDelay = 10000)
    public void executeAnomalyDetection() {
        log.info("开始执行定时异常检测任务");
        
        try {
            // 获取所有已训练模型的客户端 ID
            List<Integer> trainedClientIds = anomalyDetectionService.getTrainedClientIds();
            
            if (trainedClientIds.isEmpty()) {
                log.info("当前没有已训练的模型，跳过异常检测");
                return;
            }
            
            log.info("检测到 {} 个已训练的客户端模型", trainedClientIds.size());
            
            int anomalyCount = 0;
            
            // 对每个客户端进行异常检测
            for (Integer clientId : trainedClientIds) {
                try {
                    // 获取客户端当前的监控数据
                    RuntimeDetailVO currentData = clientService.clientRuntimeDetailsAnomalyDetect(clientId);
                    
                    if (currentData == null) {
                        log.warn("无法获取客户端 {} 的当前监控数据，跳过检测", clientId);
                        continue;
                    }
                    
                    // 执行异常检测
                    AnomalyResultVO result = anomalyDetectionService.detectAnomaly(clientId, currentData);
                    
                    // 如果检测到异常
                    if (result.isAnomaly()) {
                        anomalyCount++;
                        
                        log.warn("【步骤 1/3】检测到异常！clientId={}, score={}", 
                                clientId, result.getAnomalyScore());
                        
                        // === 步骤 2: 根因分析 ===
                        RootCauseAnalysisVO rcaResult = null;
                        try {
                            log.info("【步骤 2/3】执行根因分析...");
                            rcaResult = rootCauseAnalysisService.analyze(currentData, result);
                            log.info("根因分析完成 - 主要因素：{}, 贡献度：{}%",
                                    rcaResult.getTopContributor(),
                                    String.format("%.1f", rcaResult.getContributorScores().getOrDefault(rcaResult.getTopContributor(), 0.0) * 100));
                        } catch (Exception e) {
                            log.error("根因分析失败", e);
                        }
                        
                        // === 步骤 3: 故障分类 ===
                        FaultClassificationResultVO faultResult = null;
                        try {
                            // 先检查模型是否已训练
                            if (faultClassificationService.isModelTrained(clientId)) {
                                log.info("【步骤 3/3】执行故障分类...");
                                faultResult = faultClassificationService.classify(clientId, currentData, result.getAnomalyScore(), rcaResult);
                                log.info("故障分类完成 - 故障类型：{}, 置信度：{}%", 
                                        faultResult.getFaultType(), 
                                        Math.round(faultResult.getConfidence() * 1000) / 10.0);
                            } else {
                                log.info("【步骤 3/3】客户端 {} 的故障模型未训练，使用规则降级", clientId);
                                // 即使模型未训练，classify 方法内部会降级到规则分类
                                faultResult = faultClassificationService.classify(clientId, currentData, result.getAnomalyScore(), rcaResult);
                                log.info("使用规则分类 - 故障类型：{}, 置信度：{}%", 
                                        faultResult.getFaultType(), 
                                        Math.round(faultResult.getConfidence() * 1000) / 10.0);
                            }
                        } catch (Exception e) {
                            log.error("故障分类失败", e);
                        }
                        
                        // === 综合处理：保存告警并推送前端 ===
                        handleDetectionResults(clientId, result, rcaResult, faultResult, currentData);
                        
                        // 触发增量更新（将异常数据加入缓冲区）
                        updateModelWithAnomalyData(clientId, currentData);
                    }
                    
                } catch (Exception e) {
                    log.error("对客户端 {} 执行异常检测时发生错误：{}", clientId, e.getMessage(), e);
                }
            }
            
            log.info("定时异常检测任务完成，共检测到 {} 个异常", anomalyCount);
            
        } catch (Exception e) {
            log.error("执行定时异常检测任务时发生错误：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 每 5 分钟执行一次模型增量更新
     * 使用最近的数据重新训练模型，加快模型更新频率
     */
    @Scheduled(fixedRate = 300000)
    public void executeModelUpdate() {
        log.info("开始执行定时模型增量更新任务");
        
        try {
            List<Integer> trainedClientIds = anomalyDetectionService.getTrainedClientIds();
            
            if (trainedClientIds.isEmpty()) {
                log.info("当前没有已训练的模型，跳过增量更新");
                return;
            }
            
            int updateCount = 0;
            
            // 对每个客户端检查是否需要进行增量更新
            for (Integer clientId : trainedClientIds) {
                try {
                    // 获取该客户端最近的监控数据（最近10条）
                    RuntimeHistoryVO history = clientService.clientRuntimeDetailsHistory(clientId);
                    
                    if (history == null || history.getRuntimeDataList() == null || history.getRuntimeDataList().isEmpty()) {
                        log.info("客户端 {} 没有历史数据，跳过增量更新", clientId);
                        continue;
                    }
                    
                    // 转换 DTO 为 VO
                    List<RuntimeDetailVO> recentData = history.getRuntimeDataList().stream()
                            .limit(10)
                            .map(dto -> {
                                RuntimeDetailVO vo = new RuntimeDetailVO();
                                vo.setTimestamp(dto.getTimestamp());
                                vo.setCpuUsage(dto.getCpuUsage());
                                vo.setMemoryUsage(dto.getMemoryUsage());
                                vo.setDiskUsage(dto.getDiskUsage());
                                vo.setNetworkUpload(dto.getNetworkUpload());
                                vo.setNetworkDownload(dto.getNetworkDownload());
                                vo.setDiskRead(dto.getDiskRead());
                                vo.setDiskWrite(dto.getDiskWrite());
                                return vo;
                            })
                            .toList();
                    
                    // 将最新数据添加到模型缓冲区
                    anomalyDetectionService.updateModel(clientId, recentData);
                    updateCount++;
                    
                    log.debug("已将客户端 {} 的最近 {} 条数据添加到模型更新缓冲区", clientId, recentData.size());
                    
                } catch (Exception e) {
                    log.error("对客户端 {} 执行模型增量更新时发生错误：{}", clientId, e.getMessage(), e);
                }
            }
            
            log.info("定时模型增量更新任务完成，已更新 {} 个客户端", updateCount);
            
        } catch (Exception e) {
            log.error("执行定时模型增量更新任务时发生错误：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 综合处理检测结果
     */
    private void handleDetectionResults(
            Integer clientId,
            AnomalyResultVO anomalyResult,
            RootCauseAnalysisVO rcaResult,
            FaultClassificationResultVO faultResult,
            RuntimeDetailVO currentData) {
        
        try {
            // 保存告警记录到数据库
            AnomalyAlarm alarm = convertToAlarm(anomalyResult, clientId);
            
            // 设置故障类型代码
            Integer faultTypeCode = null;
            if (faultResult != null && faultResult.getFaultType() != null) {
                faultTypeCode = faultResult.getFaultType().getCode();
                alarm.setFaultTypeCode(faultTypeCode);
            }
            
            // 如果有 RCA 结果，添加到描述中
            if (rcaResult != null && rcaResult.getRootCauseDescription() != null) {
                String combinedDesc = anomalyResult.getDescription() + " | " + rcaResult.getRootCauseDescription();
                alarm.setDescription(combinedDesc);
            }
            
            anomalyAlarmService.saveAlarm(alarm);
            
            // 高置信度且是故障 → 加入训练集 (pending 状态)
            if (faultResult != null && faultResult.isFault() 
                    && faultResult.getConfidence() >= confidenceThreshold) {
                try {
                    faultClassificationService.addManualTrainingData(
                            clientId, 
                            faultTypeCode, 
                            currentData
                    );
                    log.debug("高置信度故障加入训练集 - clientId: {}, faultType: {}, confidence: {}", 
                            clientId, faultResult.getFaultType(), faultResult.getConfidence());
                } catch (Exception e) {
                    log.warn("添加训练数据失败，但不影响告警", e);
                }
            }
            
            // 通过 WebSocket 推送告警到前端（增强版）
            pushEnhancedAlarmToFrontend(clientId, anomalyResult, rcaResult, faultResult);
            
        } catch (Exception e) {
            log.error("处理检测结果失败", e);
        }
    }
    
    /**
     * 将异常检测结果转换为告警实体
     */
    private AnomalyAlarm convertToAlarm(AnomalyResultVO result, int clientId) {
        AnomalyAlarm alarm = new AnomalyAlarm();
        alarm.setClientId(clientId);
        alarm.setAnomalyScore(result.getAnomalyScore());
        alarm.setThreshold(result.getThreshold());
        alarm.setCpuUsage(result.getCpuUsage());
        alarm.setMemoryUsage(result.getMemoryUsage());
        alarm.setDiskUsage(result.getDiskUsage());
        alarm.setNetworkUpload(result.getNetworkUpload());
        alarm.setNetworkDownload(result.getNetworkDownload());
        alarm.setDiskRead(result.getDiskRead());
        alarm.setDiskWrite(result.getDiskWrite());
        alarm.setDescription(result.getDescription());
        alarm.setIsHandled(false);
        alarm.setAlarmTime(LocalDateTime.now());
        return alarm;
    }
    
    /**
     * 从结果中获取客户端 ID（需要从上下文获取）
     * 由于 AnomalyResultVO 中没有 clientId，需要通过其他方式获取
     * 这里简化处理，在实际调用时会传入
     */
    private Integer getClientIdFromResult(AnomalyResultVO result) {
        // 这个方法实际上不会被使用，因为在调用时会直接设置 clientId
        return 0;
    }
    
    /**
     * 通过 WebSocket 向前端推送告警消息（增强版）
     */
    private void pushEnhancedAlarmToFrontend(
            Integer clientId, 
            AnomalyResultVO anomalyResult,
            RootCauseAnalysisVO rcaResult,
            FaultClassificationResultVO faultResult) {
        try {
            // 构建告警消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "enhanced_alarm");
            message.put("clientId", clientId);
            message.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // 异常检测信息
            Map<String, Object> anomalyInfo = new HashMap<>();
            anomalyInfo.put("isAnomaly", anomalyResult.isAnomaly());
            anomalyInfo.put("anomalyScore", anomalyResult.getAnomalyScore());
            anomalyInfo.put("threshold", anomalyResult.getThreshold());
            anomalyInfo.put("description", anomalyResult.getDescription());
            message.put("anomaly", anomalyInfo);
            
            // 根因分析信息
            if (rcaResult != null) {
                Map<String, Object> rcaInfo = new HashMap<>();
                rcaInfo.put("topContributor", rcaResult.getTopContributor());
                rcaInfo.put("secondContributor", rcaResult.getSecondContributor());
                rcaInfo.put("rootCauseDescription", rcaResult.getRootCauseDescription());
                rcaInfo.put("contributorScores", rcaResult.getContributorScores());
                message.put("rootCause", rcaInfo);
            }
            
            // 故障分类信息
            if (faultResult != null) {
                Map<String, Object> faultInfo = new HashMap<>();
                faultInfo.put("faultType", faultResult.getFaultType() != null ? faultResult.getFaultType().name() : "UNKNOWN");
                faultInfo.put("isFault", faultResult.isFault());
                faultInfo.put("confidence", faultResult.getConfidence());
                faultInfo.put("recommendation", faultResult.getRecommendation());
                faultInfo.put("description", faultResult.getDescription());
                message.put("fault", faultInfo);
            }
            
            // 详细的监控数据
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("cpuUsage", anomalyResult.getCpuUsage());
            metrics.put("memoryUsage", anomalyResult.getMemoryUsage());
            metrics.put("diskUsage", anomalyResult.getDiskUsage());
            metrics.put("networkUpload", anomalyResult.getNetworkUpload());
            metrics.put("networkDownload", anomalyResult.getNetworkDownload());
            metrics.put("diskRead", anomalyResult.getDiskRead());
            metrics.put("diskWrite", anomalyResult.getDiskWrite());
            message.put("metrics", metrics);
            
            String jsonMessage = JSON.toJSONString(message);
            
            // 获取可以访问该客户端的所有用户 ID
            List<Integer> userIds = getUserIdsByClientId(clientId);
            
            for (Integer userId : userIds) {
                if (AlarmWebSocket.isUserOnline(userId)) {
                    alarmWebSocket.sendMessageToUser(userId, jsonMessage);
                    log.info("已向用户 {} 推送增强告警消息", userId);
                } else {
                    log.warn("用户 {} 不在线，无法推送实时告警", userId);
                }
            }
            
        } catch (Exception e) {
            log.error("推送增强告警到前端失败：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 通过 WebSocket 向前端推送告警消息（旧版本，保留用于兼容）
     */
    private void pushAlarmToFrontend(Integer clientId, AnomalyResultVO result) {
        try {
            // 构建告警消息
            Map<String, Object> message = new HashMap<>();
            message.put("type", "anomaly_alarm");
            message.put("clientId", clientId);
            message.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            message.put("anomalyScore", result.getAnomalyScore());
            message.put("threshold", result.getThreshold());
            message.put("isAnomaly", result.isAnomaly());
            message.put("description", result.getDescription());
            
            // 详细的监控数据
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("cpuUsage", result.getCpuUsage());
            metrics.put("memoryUsage", result.getMemoryUsage());
            metrics.put("diskUsage", result.getDiskUsage());
            metrics.put("networkUpload", result.getNetworkUpload());
            metrics.put("networkDownload", result.getNetworkDownload());
            metrics.put("diskRead", result.getDiskRead());
            metrics.put("diskWrite", result.getDiskWrite());
            message.put("metrics", metrics);
            
            String jsonMessage = JSON.toJSONString(message);
            
            // 获取可以访问该客户端的所有用户 ID
            List<Integer> userIds = getUserIdsByClientId(clientId);
            
            for (Integer userId : userIds) {
                if (AlarmWebSocket.isUserOnline(userId)) {
                    alarmWebSocket.sendMessageToUser(userId, jsonMessage);
                    log.info("已向用户 {} 推送异常告警消息", userId);
                } else {
                    log.warn("用户 {} 不在线，无法推送实时告警", userId);
                }
            }
            
        } catch (Exception e) {
            log.error("推送异常告警到前端失败：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 根据客户端 ID 获取关联的用户 ID 列表
     * 查询所有可以访问该客户端的用户
     */
    private List<Integer> getUserIdsByClientId(Integer clientId) {
        // 查询所有用户可以访问这个客户端的账户
        return accountService.list().stream()
                .filter(account -> account.getClientList() != null && 
                                   account.getClientList().contains(clientId))
                .map(Account::getId)
                .toList();
    }
    
    /**
     * 使用异常数据进行增量更新
     * 这样可以让模型学习到异常模式
     */
    private void updateModelWithAnomalyData(Integer clientId, RuntimeDetailVO data) {
        try {
            // 将异常数据添加到缓冲区，用于后续的模型更新
            List<RuntimeDetailVO> dataList = List.of(data);
            anomalyDetectionService.updateModel(clientId, dataList);
            log.info("已将异常数据添加到客户端 {} 的模型更新缓冲区", clientId);
        } catch (Exception e) {
            log.error("使用异常数据更新模型失败：{}", e.getMessage(), e);
        }
    }
}
