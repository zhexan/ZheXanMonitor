package com.example.entity.vo.response;

import com.alibaba.fastjson2.JSONObject;
import com.example.entity.dto.RuntimeData;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

@Data
public class RuntimeHistoryVO {
    double disk;
    double memory;
    List<JSONObject> list = new LinkedList<>();
    // 为了兼容异常检测，添加运行时数据列表
    private List<RuntimeData> runtimeDataList;
}
