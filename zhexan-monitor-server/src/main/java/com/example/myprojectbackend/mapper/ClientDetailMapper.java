package com.example.myprojectbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.myprojectbackend.entity.dto.ClientDetail;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClientDetailMapper extends BaseMapper<ClientDetail> {
}
