package com.example.controller;

import com.example.entity.dto.AnomalyAlarm;
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
    
    /**
     * 获取指定客户端的未处理告警列表
     */
    @GetMapping("/unhandled")
    public Map<String, Object> getUnhandledAlarms(
            @RequestParam Integer clientId,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        
        Map<String, Object> result = new HashMap<>();
        List<AnomalyAlarm> alarms = anomalyAlarmService.getUnhandledAlarms(clientId);
        result.put("alarms", alarms);
        result.put("count", alarms.size());
        
        return result;
    }
    
    /**
     * 获取指定客户端的告警历史
     */
    @GetMapping("/history")
    public Map<String, Object> getAlarmHistory(
            @RequestParam Integer clientId,
            @RequestParam(defaultValue = "100") Integer limit,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        
        Map<String, Object> result = new HashMap<>();
        List<AnomalyAlarm> alarms = anomalyAlarmService.getAlarmHistory(clientId, limit);
        result.put("alarms", alarms);
        result.put("total", alarms.size());
        
        return result;
    }
    
    /**
     * 标记告警为已处理
     */
    @PostMapping("/handle")
    public Map<String, Object> handleAlarm(
            @RequestBody Map<String, Integer> params,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        
        Integer alarmId = params.get("alarmId");
        if (alarmId == null) {
            throw new IllegalArgumentException("缺少 alarmId 参数");
        }
        
        anomalyAlarmService.handleAlarm(alarmId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "告警已标记为已处理");
        
        return result;
    }
    
    /**
     * 批量标记告警为已处理
     */
    @PostMapping("/batch-handle")
    public Map<String, Object> batchHandleAlarms(
            @RequestBody Map<String, Object> params,
            @RequestAttribute(Const.ATTR_USER_ID) int userId,
            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        
        List<Integer> alarmIds = (List<Integer>) params.get("alarmIds");
        if (alarmIds == null || alarmIds.isEmpty()) {
            throw new IllegalArgumentException("缺少 alarmIds 参数");
        }
        
        for (Integer alarmId : alarmIds) {
            anomalyAlarmService.handleAlarm(alarmId);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已成功标记 " + alarmIds.size() + " 条告警");
        
        return result;
    }
    
    /**
     * 测试手动推送告警消息（仅用于测试）
     */
    @PostMapping("/test-push")
    public Map<String, Object> testPushAlarm(
            @RequestBody Map<String, Object> params,
            @RequestAttribute(Const.ATTR_USER_ID) int userId) {
        
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "test_alarm");
            message.put("message", "这是一条测试告警消息");
            message.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = com.alibaba.fastjson2.JSON.toJSONString(message);
            alarmWebSocket.sendMessageToUser(userId, jsonMessage);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "测试消息已发送");
            
            return result;
            
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
