package com.example.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.AnomalyAlarm;
import com.example.mapper.AnomalyAlarmMapper;
import com.example.service.AnomalyAlarmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异常告警服务实现类
 * @author zhexan
 * @since 2026-03-03
 */
@Slf4j
@Service
public class AnomalyAlarmServiceImpl extends ServiceImpl<AnomalyAlarmMapper, AnomalyAlarm> implements AnomalyAlarmService {
    
    @Override
    public void saveAlarm(AnomalyAlarm alarm) {
        // 设置默认值
        if (alarm.getIsHandled() == null) {
            alarm.setIsHandled(false);
        }
        if (alarm.getAlarmTime() == null) {
            alarm.setAlarmTime(LocalDateTime.now());
        }
        
        // 保存到数据库
        this.save(alarm);
        log.info("保存异常告警记录：clientId={}, score={}, description={}", 
                alarm.getClientId(), alarm.getAnomalyScore(), alarm.getDescription());
    }
    
    @Override
    public List<AnomalyAlarm> getUnhandledAlarms(Integer clientId) {
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnomalyAlarm::getClientId, clientId)
               .eq(AnomalyAlarm::getIsHandled, false)
               .orderByDesc(AnomalyAlarm::getAlarmTime);
        return this.list(wrapper);
    }
    
    @Override
    public List<AnomalyAlarm> getAlarmHistory(Integer clientId, Integer limit) {
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnomalyAlarm::getClientId, clientId)
               .orderByDesc(AnomalyAlarm::getAlarmTime)
               .last("LIMIT " + (limit != null ? limit : 100));
        return this.list(wrapper);
    }
    
    @Override
    public void handleAlarm(Integer alarmId) {
        AnomalyAlarm alarm = this.getById(alarmId);
        if (alarm != null) {
            alarm.setIsHandled(true);
            this.updateById(alarm);
            log.info("标记告警为已处理：id={}", alarmId);
        } else {
            log.warn("未找到告警记录：id={}", alarmId);
        }
    }
}
