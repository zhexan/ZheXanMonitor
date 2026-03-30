package com.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.dto.FaultTrainingData;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.FaultClassificationResultVO;
import com.example.service.AccountService;
import com.example.service.FaultClassificationService;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 故障分类控制器
 * 
 * @author zhexan
 * @since 2026-03-14
 */
@Slf4j
@RestController
@RequestMapping("/api/fault")
public class FaultClassificationController {

    @Resource
    private FaultClassificationService faultClassificationService;
    @Resource
    private AccountService accountService;

    /**
     * 实时故障分类
     */
    @PostMapping("/classify")
    public RestBean<FaultClassificationResultVO> classify(
            @RequestBody @Valid RuntimeDetailVO vo,
            @RequestParam Integer clientId,
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {

        try {
            FaultClassificationResultVO result = faultClassificationService.classify(clientId, vo);
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
            List<FaultClassificationResultVO> results = faultClassificationService.classifyBatch(clientId, dataList);
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
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {

        try {
            Integer clientId = (Integer) params.get("clientId");
            Integer faultTypeCode = (Integer) params.get("faultTypeCode");

            if (clientId == null || faultTypeCode == null) {
                return RestBean.failure(400, "缺少必要参数");
            }

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
            List<FaultTrainingData> trainingDataList = faultClassificationService.loadTrainingDataByClientId(clientId);

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
     * 查询待审核的训练数据（仅管理员可见，分页）
     */
    @GetMapping("/pending")
    public RestBean<Map<String, Object>> getPendingTrainingData(
            @RequestParam(required = false) Integer clientId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {

        if (!isAdminAccount(userRole)) {
            log.warn("非管理员用户 {} 无权访问待审核数据", userId);
            Map<String, Object> empty = new HashMap<>();
            empty.put("data", List.of());
            empty.put("total", 0);
            empty.put("hasMore", false);
            return RestBean.success(empty);
        }

        try {
            log.debug("管理员 {} 查询待审核数据, clientId: {}, offset: {}, limit: {}", userId, clientId, offset, limit);

            Map<String, Object> result = faultClassificationService.getPendingTrainingDataPaged(clientId, offset, limit);
            log.debug("查询到 {} 条待审核数据", ((List<?>)result.get("data")).size());

            return RestBean.success(result);
        } catch (Exception e) {
            log.error("查询待审核数据失败", e);
            return RestBean.failure(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 审核训练数据（仅管理员可操作，确认或拒绝，并可修改标签）
     */
    @PostMapping("/review")
    public RestBean<FaultTrainingData> reviewTrainingData(
            @RequestParam int id,
            @RequestParam int faultTypeCode,
            @RequestParam boolean approved,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {

        if (!isAdminAccount(userRole)) {
            log.warn("非管理员用户 {} 无权审核训练数据", userId);
            return RestBean.noPermission();
        }

        try {
            FaultTrainingData data = faultClassificationService.getPendingTrainingData(null, Integer.MAX_VALUE)
                    .stream().filter(d -> d.getId() == id).findFirst().orElse(null);
            if (data == null) {
                return RestBean.failure(404, "数据不存在或已审核");
            }

            FaultTrainingData result = faultClassificationService.reviewTrainingData(id, faultTypeCode, approved);
            log.info("管理员 {} 审核训练数据 ID: {}, 新标签: {}, 通过: {}", 
                    userId, id, faultTypeCode, approved);
            return RestBean.success(result);
        } catch (Exception e) {
            log.error("审核训练数据失败", e);
            return RestBean.failure(500, "审核失败：" + e.getMessage());
        }
    }

    /**
     * 获取故障记录列表（已审核的）
     */
    @GetMapping("/list")
    public RestBean<Map<String, Object>> getFaultRecords(
            @RequestParam(required = false) Integer clientId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {

        try {
            boolean isAdmin = isAdminAccount(userRole);
            List<Integer> accessibleClients = isAdmin ? null : getAccessibleClients(userId, userRole);

            LambdaQueryWrapper<FaultTrainingData> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FaultTrainingData::getStatus, 1)
                    .orderByDesc(FaultTrainingData::getDataTime)
                    .last("LIMIT " + limit);

            if (clientId != null) {
                if (accessibleClients != null && !accessibleClients.contains(clientId)) {
                    return RestBean.noPermission();
                }
                wrapper.eq(FaultTrainingData::getClientId, clientId);
            }

            List<FaultTrainingData> list = faultClassificationService.list(wrapper);

            if (!isAdmin) {
                list = list.stream()
                        .filter(data -> {
                            if (accessibleClients != null) {
                                return accessibleClients.contains(data.getClientId());
                            }
                            return false;
                        })
                        .collect(Collectors.toList());
            }

            Map<Integer, Long> stats = list.stream()
                    .collect(Collectors.groupingBy(
                            FaultTrainingData::getFaultTypeCode,
                            Collectors.counting()));

            Map<String, Object> result = new HashMap<>();
            result.put("records", list);
            result.put("total", list.size());
            result.put("stats", stats);

            return RestBean.success(result);
        } catch (Exception e) {
            log.error("获取故障记录失败", e);
            return RestBean.failure(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取全局故障统计
     */
    @GetMapping("/stats-all")
    public RestBean<Map<String, Object>> getAllStats(
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {

        try {
            boolean isAdmin = isAdminAccount(userRole);
            List<Integer> accessibleClients = isAdmin ? null : getAccessibleClients(userId, userRole);

            LambdaQueryWrapper<FaultTrainingData> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(FaultTrainingData::getStatus, 1);

            List<FaultTrainingData> list = faultClassificationService.list(wrapper);

            if (!isAdmin) {
                list = list.stream()
                        .filter(data -> {
                            if (accessibleClients != null) {
                                return accessibleClients.contains(data.getClientId());
                            }
                            return false;
                        })
                        .toList();
            }

            Map<Integer, Long> faultTypeStats = list.stream()
                    .collect(Collectors.groupingBy(
                            FaultTrainingData::getFaultTypeCode,
                            Collectors.counting()));

            Map<String, Object> result = new HashMap<>();
            result.put("total", list.size());
            result.put("faultTypeStats", faultTypeStats);

            return RestBean.success(result);
        } catch (Exception e) {
            log.error("获取全局统计失败", e);
            return RestBean.failure(500, "查询失败：" + e.getMessage());
        }
    }

    private List<Integer> getAccessibleClients(int userId, String userRole) {
        if (isAdminAccount(userRole)) {
            return null;
        }
        Account account = accountService.getById(userId);
        if (account == null) {
            return List.of();
        }
        return account.getClientList();
    }

    private boolean isAdminAccount(String role) {
        return role != null && role.startsWith("ROLE_") && Const.ROLE_ADMIN.equals(role.substring(5));
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
