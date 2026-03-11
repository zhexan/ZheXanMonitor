package com.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.entity.dto.AnomalyAlarm;

import java.util.List;

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
}
