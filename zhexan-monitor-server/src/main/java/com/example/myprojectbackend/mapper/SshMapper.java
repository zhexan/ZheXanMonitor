package com.example.myprojectbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.myprojectbackend.entity.dto.ClientSsh;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SshMapper extends BaseMapper<ClientSsh> {
}
