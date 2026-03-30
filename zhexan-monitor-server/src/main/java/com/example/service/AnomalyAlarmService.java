package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.AnomalyAlarm;

import java.util.List;
import java.util.Map;

/**
 * 异常告警服务接口
 * @author zhexan
 * @since 2026-03-03
 */
public interface AnomalyAlarmService extends IService<AnomalyAlarm> {
    
    /**
     * 保存异常告警记录
     * @param alarm 告警信息
     */
    void saveAlarm(AnomalyAlarm alarm);
    
    /**
     * 获取指定客户端的未处理告警列表
     * @param clientId 客户端 ID
     * @return 未处理告警列表
     */
    List<AnomalyAlarm> getUnhandledAlarms(Integer clientId);
    
    /**
     * 获取指定客户端的所有告警历史
     * @param clientId 客户端 ID
     * @param limit 返回数量限制
     * @return 告警历史列表
     */
    List<AnomalyAlarm> getAlarmHistory(Integer clientId, Integer limit);
    
    /**
     * 标记告警为已处理
     * @param alarmId 告警 ID
     */
    void handleAlarm(Integer alarmId);
    
    /**
     * 标记告警为已忽略
     * @param alarmId 告警 ID
     */
    void ignoreAlarm(Integer alarmId);
    
    /**
     * 批量标记告警为已忽略
     * @param alarmIds 告警 ID 列表
     */
    void batchIgnoreAlarms(List<Integer> alarmIds);

    /**
     * 获取多个客户端的未处理告警列表
     * @param clientIds 客户端 ID 列表
     * @return 未处理告警列表
     */
    List<AnomalyAlarm> getUnhandledAlarmsByClientIds(List<Integer> clientIds);

    /**
     * 获取多个客户端的告警历史
     * @param clientIds 客户端 ID 列表
     * @param limit 返回数量限制
     * @return 告警历史列表
     */
    List<AnomalyAlarm> getAlarmHistoryByClientIds(List<Integer> clientIds, Integer limit);

    /**
     * 分页获取告警历史（单客户端）
     * @param clientId 客户端 ID（可选）
     * @param offset 起始偏移量
     * @param limit 每页数量
     * @return 分页结果，包含 alarts、total、hasMore
     */
    Map<String, Object> getAlarmHistoryPaged(Integer clientId, int offset, int limit);

    /**
     * 分页获取告警历史（多客户端）
     * @param clientIds 客户端 ID 列表
     * @param offset 起始偏移量
     * @param limit 每页数量
     * @return 分页结果，包含 alarms、total、hasMore
     */
    Map<String, Object> getAlarmHistoryPagedByClientIds(List<Integer> clientIds, int offset, int limit);

    /**
     * 分页获取未处理告警（单客户端）
     * @param clientId 客户端 ID（可选）
     * @param offset 起始偏移量
     * @param limit 每页数量
     * @return 分页结果，包含 alarms、total、hasMore
     */
    Map<String, Object> getUnhandledAlarmsPaged(Integer clientId, int offset, int limit);

    /**
     * 分页获取未处理告警（多客户端）
     * @param clientIds 客户端 ID 列表
     * @param offset 起始偏移量
     * @param limit 每页数量
     * @return 分页结果，包含 alarms、total、hasMore
     */
    Map<String, Object> getUnhandledAlarmsPagedByClientIds(List<Integer> clientIds, int offset, int limit);
}
