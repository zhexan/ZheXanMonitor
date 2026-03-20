package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.entity.dto.FaultTrainingData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 故障分类训练数据 Mapper
 * @author zhexan
 * @since 2026-03-14
 */
@Mapper
public interface FaultTrainingDataMapper extends BaseMapper<FaultTrainingData> {
}
