package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.dto.AnomalyAlarm;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常告警记录 Mapper
 * @author zhexan
 * @since 2026-03-03
 */
@Mapper
public interface AnomalyAlarmMapper extends BaseMapper<AnomalyAlarm> {
}
