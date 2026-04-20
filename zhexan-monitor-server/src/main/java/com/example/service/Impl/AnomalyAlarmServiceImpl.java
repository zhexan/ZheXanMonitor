package com.example.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.AnomalyAlarm;
import com.example.mapper.AnomalyAlarmMapper;
import com.example.service.AnomalyAlarmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    @Override
    public void ignoreAlarm(Integer alarmId) {
        AnomalyAlarm alarm = this.getById(alarmId);
        if (alarm != null) {
            alarm.setIsIgnored(true);
            alarm.setIsHandled(true);
            this.updateById(alarm);
            log.info("标记告警为已忽略：id={}", alarmId);
        } else {
            log.warn("未找到告警记录：id={}", alarmId);
        }
    }
    
    @Override
    public void batchIgnoreAlarms(List<Integer> alarmIds) {
        for (Integer alarmId : alarmIds) {
            ignoreAlarm(alarmId);
        }
        log.info("批量忽略告警：共 {} 条", alarmIds.size());
    }

    @Override
    public Map<String, Object> getAlarmHistoryPaged(Integer clientId, int offset, int limit) {
        int pageNum = offset / limit + 1;
        Page<AnomalyAlarm> page = new Page<>(pageNum, limit);
        
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(clientId != null, AnomalyAlarm::getClientId, clientId)
               .orderByDesc(AnomalyAlarm::getAlarmTime);
        
        Page<AnomalyAlarm> result = this.page(page, wrapper);
        
        Map<String, Object> map = new HashMap<>();
        map.put("alarms", result.getRecords());
        map.put("total", result.getTotal());
        map.put("hasMore", result.getRecords().size() == limit);
        return map;
    }

    @Override
    public Map<String, Object> getAlarmHistoryPagedByClientIds(List<Integer> clientIds, int offset, int limit) {
        if (clientIds == null || clientIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("alarms", List.of());
            empty.put("total", 0L);
            empty.put("hasMore", false);
            return empty;
        }
        
        int pageNum = offset / limit + 1;
        Page<AnomalyAlarm> page = new Page<>(pageNum, limit);
        
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AnomalyAlarm::getClientId, clientIds)
               .orderByDesc(AnomalyAlarm::getAlarmTime);
        
        Page<AnomalyAlarm> result = this.page(page, wrapper);
        
        Map<String, Object> map = new HashMap<>();
        map.put("alarms", result.getRecords());
        map.put("total", result.getTotal());
        map.put("hasMore", result.getRecords().size() == limit);
        return map;
    }

    @Override
    public Map<String, Object> getUnhandledAlarmsPaged(Integer clientId, int offset, int limit) {
        int pageNum = offset / limit + 1;
        Page<AnomalyAlarm> page = new Page<>(pageNum, limit);
        
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(clientId != null, AnomalyAlarm::getClientId, clientId)
               .eq(AnomalyAlarm::getIsHandled, false)
               .orderByDesc(AnomalyAlarm::getAlarmTime);
        
        Page<AnomalyAlarm> result = this.page(page, wrapper);
        
        Map<String, Object> map = new HashMap<>();
        map.put("alarms", result.getRecords());
        map.put("total", result.getTotal());
        map.put("hasMore", result.getRecords().size() == limit);
        return map;
    }

    @Override
    public Map<String, Object> getUnhandledAlarmsPagedByClientIds(List<Integer> clientIds, int offset, int limit) {
        if (clientIds == null || clientIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("alarms", List.of());
            empty.put("total", 0L);
            empty.put("hasMore", false);
            return empty;
        }
        
        int pageNum = offset / limit + 1;
        Page<AnomalyAlarm> page = new Page<>(pageNum, limit);
        
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AnomalyAlarm::getClientId, clientIds)
               .eq(AnomalyAlarm::getIsHandled, false)
               .orderByDesc(AnomalyAlarm::getAlarmTime);
        
        Page<AnomalyAlarm> result = this.page(page, wrapper);
        
        Map<String, Object> map = new HashMap<>();
        map.put("alarms", result.getRecords());
        map.put("total", result.getTotal());
        map.put("hasMore", result.getRecords().size() == limit);
        return map;
    }
    
    @Override
    public Map<String, Object> getFaultsPaged(Integer clientId, int offset, int limit) {
        int pageNum = offset / limit + 1;
        Page<AnomalyAlarm> page = new Page<>(pageNum, limit);
        
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(AnomalyAlarm::getFaultTypeCode)
               .ne(AnomalyAlarm::getFaultTypeCode, 0)
               .orderByDesc(AnomalyAlarm::getAlarmTime);
        
        if (clientId != null) {
            wrapper.eq(AnomalyAlarm::getClientId, clientId);
        }
        
        Page<AnomalyAlarm> result = this.page(page, wrapper);
        
        Map<String, Object> map = new HashMap<>();
        map.put("alarms", result.getRecords());
        map.put("total", result.getTotal());
        map.put("hasMore", result.getRecords().size() == limit);
        return map;
    }
    
    @Override
    public Map<String, Object> getFaultsPagedByClientIds(List<Integer> clientIds, int offset, int limit) {
        if (clientIds == null || clientIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("alarms", List.of());
            empty.put("total", 0L);
            empty.put("hasMore", false);
            return empty;
        }
        
        int pageNum = offset / limit + 1;
        Page<AnomalyAlarm> page = new Page<>(pageNum, limit);
        
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AnomalyAlarm::getClientId, clientIds)
               .isNotNull(AnomalyAlarm::getFaultTypeCode)
               .ne(AnomalyAlarm::getFaultTypeCode, 0)
               .orderByDesc(AnomalyAlarm::getAlarmTime);
        
        Page<AnomalyAlarm> result = this.page(page, wrapper);
        
        Map<String, Object> map = new HashMap<>();
        map.put("alarms", result.getRecords());
        map.put("total", result.getTotal());
        map.put("hasMore", result.getRecords().size() == limit);
        return map;
    }
    
    @Override
    public Map<String, Object> getAnomaliesPaged(Integer clientId, int offset, int limit) {
        int pageNum = offset / limit + 1;
        Page<AnomalyAlarm> page = new Page<>(pageNum, limit);
        
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(clientId != null, AnomalyAlarm::getClientId, clientId)
               .eq(AnomalyAlarm::getIsHandled, false)
               .and(w -> w.eq(AnomalyAlarm::getFaultTypeCode, 0)
                        .or()
                        .eq(AnomalyAlarm::getFaultTypeCode, 7))
               .orderByDesc(AnomalyAlarm::getAlarmTime);
        
        Page<AnomalyAlarm> result = this.page(page, wrapper);
        
        Map<String, Object> map = new HashMap<>();
        map.put("alarms", result.getRecords());
        map.put("total", result.getTotal());
        map.put("hasMore", result.getRecords().size() == limit);
        return map;
    }
    
    @Override
    public Map<String, Object> getAnomaliesPagedByClientIds(List<Integer> clientIds, int offset, int limit) {
        if (clientIds == null || clientIds.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("alarms", List.of());
            empty.put("total", 0L);
            empty.put("hasMore", false);
            return empty;
        }
        
        int pageNum = offset / limit + 1;
        Page<AnomalyAlarm> page = new Page<>(pageNum, limit);
        
        LambdaQueryWrapper<AnomalyAlarm> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AnomalyAlarm::getClientId, clientIds)
               .eq(AnomalyAlarm::getIsHandled, false)
               .and(w -> w.eq(AnomalyAlarm::getFaultTypeCode, 0)
                        .or()
                        .eq(AnomalyAlarm::getFaultTypeCode, 7))
               .orderByDesc(AnomalyAlarm::getAlarmTime);
        
        Page<AnomalyAlarm> result = this.page(page, wrapper);
        
        Map<String, Object> map = new HashMap<>();
        map.put("alarms", result.getRecords());
        map.put("total", result.getTotal());
        map.put("hasMore", result.getRecords().size() == limit);
        return map;
    }
}
