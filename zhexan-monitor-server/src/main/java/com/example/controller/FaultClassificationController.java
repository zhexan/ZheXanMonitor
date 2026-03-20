package com.example.controller;

import com.example.entity.RestBean;
import com.example.entity.dto.FaultTrainingData;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.FaultClassificationResultVO;
import com.example.service.Impl.FaultClassificationServiceImpl;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 故障分类控制器
 * @author zhexan
 * @since 2026-03-14
 */
@Slf4j
@RestController
@RequestMapping("/api/fault")
public class FaultClassificationController {
    
    @Resource
    private FaultClassificationServiceImpl faultClassificationService;
    
    /**
     * 实时故障分类
     */
    @PostMapping("/classify")
    public RestBean<FaultClassificationResultVO> classify(
            @RequestBody @Valid RuntimeDetailVO vo,
            @RequestParam Integer clientId,
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {
        
        try {
            FaultClassificationResultVO result = 
                    faultClassificationService.classify(clientId, vo);
            return RestBean.success(result);
        } catch (Exception e) {
            log.error("故障分类失败", e);
            return RestBean.failure(500, "分类失败：" + e.getMessage());
        }
    }
    
    /**
     * 批量故障分类
     */
    @PostMapping("/classify-batch")
    public RestBean<List<FaultClassificationResultVO>> classifyBatch(
            @RequestBody List<RuntimeDetailVO> dataList,
            @RequestParam Integer clientId,
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {
        
        try {
            List<FaultClassificationResultVO> results = 
                    faultClassificationService.classifyBatch(clientId, dataList);
            return RestBean.success(results);
        } catch (Exception e) {
            log.error("批量故障分类失败", e);
            return RestBean.failure(500, "批量分类失败：" + e.getMessage());
        }
    }
    
    /**
     * 添加训练数据（手动标注）
     */
    @PostMapping("/add-training-data")
    public RestBean<Void> addTrainingData(
            @RequestBody Map<String, Object> params,
            @RequestParam Integer clientId,
            @RequestParam Integer faultTypeCode,
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {
        
        try {
            RuntimeDetailVO runtimeData = convertToRuntimeDetailVO(params);
            
            faultClassificationService.addManualTrainingData(clientId, faultTypeCode, runtimeData);
            return RestBean.success();
        } catch (Exception e) {
            log.error("添加训练数据失败", e);
            return RestBean.failure(500, "添加数据失败：" + e.getMessage());
        }
    }
    
    /**
     * 查询训练数据统计
     */
    @GetMapping("/stats")
    public RestBean<Map<String, Object>> getTrainingStats(
            @RequestParam Integer clientId,
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 加载该客户端的所有训练数据
            List<FaultTrainingData> trainingDataList = 
                    faultClassificationService.loadTrainingDataByClientId(clientId);
            
            // 统计各故障类型的数量
            Map<Integer, Long> faultTypeStats = trainingDataList.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            FaultTrainingData::getFaultTypeCode,
                            java.util.stream.Collectors.counting()));
            
            result.put("total", trainingDataList.size());
            result.put("faultTypeStats", faultTypeStats);
            result.put("isEnoughForTraining", trainingDataList.size() >= 100);
            
            return RestBean.success(result);
        } catch (Exception e) {
            log.error("查询训练数据统计失败", e);
            return RestBean.failure(500, "查询失败：" + e.getMessage());
        }
    }
    
    /**
     * 清除模型和数据
     */
    @PostMapping("/clear")
    public RestBean<Void> clearModel(
            @RequestParam Integer clientId,
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {
        
        try {
            faultClassificationService.clearModel(clientId);
            return RestBean.success();
        } catch (Exception e) {
            log.error("清除模型失败", e);
            return RestBean.failure(500, "清除失败：" + e.getMessage());
        }
    }
    
    /**
     * 查询待审核的训练数据
     */
    @GetMapping("/pending")
    public RestBean<List<FaultTrainingData>> getPendingTrainingData(
            @RequestParam(required = false) Integer clientId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {
        
        try {
            List<FaultTrainingData> list = faultClassificationService.getPendingTrainingData(clientId, limit);
            return RestBean.success(list);
        } catch (Exception e) {
            log.error("查询待审核数据失败", e);
            return RestBean.failure(500, "查询失败：" + e.getMessage());
        }
    }
    
    /**
     * 审核训练数据（确认或拒绝）
     */
    @PostMapping("/review")
    public RestBean<FaultTrainingData> reviewTrainingData(
            @RequestParam int id,
            @RequestParam int faultTypeCode,
            @RequestParam boolean approved,
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {
        
        try {
            FaultTrainingData result = faultClassificationService.reviewTrainingData(id, faultTypeCode, approved);
            return RestBean.success(result);
        } catch (Exception e) {
            log.error("审核训练数据失败", e);
            return RestBean.failure(500, "审核失败：" + e.getMessage());
        }
    }
    
    /**
     * 辅助方法：将 Map 转换为 RuntimeDetailVO
     */
    private RuntimeDetailVO convertToRuntimeDetailVO(Map<String, Object> params) {
        RuntimeDetailVO vo = new RuntimeDetailVO();
        vo.setTimestamp(System.currentTimeMillis());
        vo.setCpuUsage(getDoubleValue(params, "cpuUsage"));
        vo.setMemoryUsage(getDoubleValue(params, "memoryUsage"));
        vo.setDiskUsage(getDoubleValue(params, "diskUsage"));
        vo.setNetworkUpload(getDoubleValue(params, "networkUpload"));
        vo.setNetworkDownload(getDoubleValue(params, "networkDownload"));
        vo.setDiskRead(getDoubleValue(params, "diskRead"));
        vo.setDiskWrite(getDoubleValue(params, "diskWrite"));
        return vo;
    }
    
    /**
     * 辅助方法：从 Map 中获取 Double 值
     */
    private Double getDoubleValue(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}
