package com.example.myprojectbackend.utils;

import com.example.myprojectbackend.entity.dto.RuntimeData;
import com.example.myprojectbackend.entity.vo.request.RuntimeDetailVO;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * influxdb的工具类
 * @author zhexan
 * @since 2026-01-27
 */
@Component
public class InfluxDBUtils {

    @Value("${spring.influx.url}")
    String url;
    @Value("${spring.influx.user}")
    String user;
    @Value("${spring.influx.password}")
    String password;

    private final String BUCKET = "zhexan_monitor";
    private final String ORG = "influxdb_zhexan";
    private InfluxDBClient client;
    /**
     *
     * @since 2026-01-27
     */
    @PostConstruct
    public void init() {
       client = InfluxDBClientFactory.create(url, user, password.toCharArray());
    }

    /**
     *
     * @param clientId 客户端ID
     * @param vo 客户端上报的实时数据
     * @since 2026-01-27
     */
    public void writeRuntimeData(int clientId, RuntimeDetailVO vo){
        RuntimeData data = new RuntimeData();
        BeanUtils.copyProperties(vo, data);
        data.setTimestamp(new Date(vo.getTimestamp()).toInstant());
        data.setClientId(clientId);
        WriteApiBlocking writeApi = client.getWriteApiBlocking();
        writeApi.writeMeasurement(BUCKET, ORG, WritePrecision.NS, data);
    }
}
