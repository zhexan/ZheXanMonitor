package com.example.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.entity.RestBean;
import com.example.entity.dto.AnomalyAlarm;
import com.example.entity.dto.Account;
import com.example.service.AccountService;
import com.example.service.AnomalyAlarmService;
import com.example.utils.Const;
import com.example.websocket.AlarmWebSocket;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常告警管理 Controller
 * @author zhexan
 * @since 2026-03-03
 */
@Slf4j
@RestController
@RequestMapping("/api/alarm")
public class AlarmController {

    @Resource
    private AlarmWebSocket alarmWebSocket;
    @Resource
    private AnomalyAlarmService anomalyAlarmService;
    @Resource
    private AccountService accountService;

    /**
     * 获取未处理告警列表（分页）
     * 管理员可获取所有客户端的告警，子账户只能获取所管理客户端的告警
     */
    @GetMapping("/unhandled")
    public RestBean<Map<String, Object>> getUnhandledAlarms(
            @RequestParam(required = false) Integer clientId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {

        boolean isAdmin = isAdminAccount(userRole);
        List<Integer> accessibleClients = isAdmin ? null : getAccessibleClients(userId, userRole);

        Map<String, Object> result;
        if (clientId != null) {
            if (!isAdmin && accessibleClients != null && !accessibleClients.contains(clientId)) {
                return RestBean.noPermission();
            }
            result = anomalyAlarmService.getUnhandledAlarmsPaged(clientId, offset, limit);
        } else if (isAdmin) {
            result = anomalyAlarmService.getUnhandledAlarmsPaged(null, offset, limit);
        } else {
            result = anomalyAlarmService.getUnhandledAlarmsPagedByClientIds(accessibleClients, offset, limit);
        }

        return RestBean.success(result);
    }

    /**
     * 获取告警历史（分页）
     * 管理员可获取所有客户端的告警历史，子账户只能获取所管理客户端的告警历史
     */
    @GetMapping("/history")
    public RestBean<Map<String, Object>> getAlarmHistory(
            @RequestParam(required = false) Integer clientId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {

        boolean isAdmin = isAdminAccount(userRole);
        List<Integer> accessibleClients = isAdmin ? null : getAccessibleClients(userId, userRole);

        Map<String, Object> result;
        if (clientId != null) {
            if (!isAdmin && accessibleClients != null && !accessibleClients.contains(clientId)) {
                return RestBean.noPermission();
            }
            result = anomalyAlarmService.getAlarmHistoryPaged(clientId, offset, limit);
        } else if (isAdmin) {
            result = anomalyAlarmService.getAlarmHistoryPaged(null, offset, limit);
        } else {
            result = anomalyAlarmService.getAlarmHistoryPagedByClientIds(accessibleClients, offset, limit);
        }

        return RestBean.success(result);
    }

    /**
     * 标记告警为已处理
     * 只有管理该告警所属客户端的用户才能处理
     */
    @PostMapping("/handle")
    public RestBean<Void> handleAlarm(
            @RequestBody Map<String, Integer> params,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {

        Integer alarmId = params.get("alarmId");
        if (alarmId == null) {
            return RestBean.failure(400, "缺少 alarmId 参数");
        }

        AnomalyAlarm alarm = anomalyAlarmService.getById(alarmId);
        if (alarm == null) {
            return RestBean.failure(404, "告警不存在");
        }

        if (!isAdminAccount(userRole)) {
            List<Integer> accessibleClients = getAccessibleClients(userId, userRole);
            if (!accessibleClients.contains(alarm.getClientId())) {
                return RestBean.noPermission();
            }
        }

        anomalyAlarmService.handleAlarm(alarmId);
        return RestBean.success();
    }

    /**
     * 批量标记告警为已处理
     * 只有管理这些告警所属客户端的用户才能处理
     */
    @PostMapping("/batch-handle")
    public RestBean<Void> batchHandleAlarms(
            @RequestBody Map<String, Object> params,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {

        List<Integer> alarmIds = (List<Integer>) params.get("alarmIds");
        if (alarmIds == null || alarmIds.isEmpty()) {
            return RestBean.failure(400, "缺少 alarmIds 参数");
        }

        if (!isAdminAccount(userRole)) {
            List<AnomalyAlarm> alarms = anomalyAlarmService.listByIds(alarmIds);
            List<Integer> accessibleClients = getAccessibleClients(userId, userRole);

            for (AnomalyAlarm alarm : alarms) {
                if (!accessibleClients.contains(alarm.getClientId())) {
                    return RestBean.noPermission();
                }
            }
        }

        for (Integer alarmId : alarmIds) {
            anomalyAlarmService.handleAlarm(alarmId);
        }
        return RestBean.success();
    }
    
    /**
     * 忽略告警（不添加到训练数据）
     */
    @PostMapping("/ignore")
    public RestBean<Void> ignoreAlarm(
            @RequestBody Map<String, Integer> params,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        
        Integer alarmId = params.get("alarmId");
        if (alarmId == null) {
            return RestBean.failure(400, "缺少 alarmId 参数");
        }
        
        AnomalyAlarm alarm = anomalyAlarmService.getById(alarmId);
        if (alarm == null) {
            return RestBean.failure(404, "告警不存在");
        }
        
        if (!isAdminAccount(userRole)) {
            List<Integer> accessibleClients = getAccessibleClients(userId, userRole);
            if (!accessibleClients.contains(alarm.getClientId())) {
                return RestBean.noPermission();
            }
        }
        
        anomalyAlarmService.ignoreAlarm(alarmId);
        return RestBean.success();
    }
    
    /**
     * 批量忽略告警（不添加到训练数据）
     */
    @PostMapping("/batch-ignore")
    public RestBean<Void> batchIgnoreAlarms(
            @RequestBody Map<String, Object> params,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        
        List<Integer> alarmIds = (List<Integer>) params.get("alarmIds");
        if (alarmIds == null || alarmIds.isEmpty()) {
            return RestBean.failure(400, "缺少 alarmIds 参数");
        }
        
        if (!isAdminAccount(userRole)) {
            List<AnomalyAlarm> alarms = anomalyAlarmService.listByIds(alarmIds);
            List<Integer> accessibleClients = getAccessibleClients(userId, userRole);
            
            for (AnomalyAlarm alarm : alarms) {
                if (!accessibleClients.contains(alarm.getClientId())) {
                    return RestBean.noPermission();
                }
            }
        }
        
        anomalyAlarmService.batchIgnoreAlarms(alarmIds);
        return RestBean.success();
    }

    private List<Integer> getAccessibleClients(int userId, String userRole) {
        if (isAdminAccount(userRole)) {
            return List.of();
        }
        Account account = accountService.getById(userId);
        return account.getClientList();
    }

    private boolean isAdminAccount(String role) {
        role = role.substring(5);
        return Const.ROLE_ADMIN.equals(role);
    }
}