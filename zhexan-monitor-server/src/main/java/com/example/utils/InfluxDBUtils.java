package com.example.utils;

import com.alibaba.fastjson2.JSONObject;
import com.example.entity.dto.RuntimeData;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.ModelTrainingDataVO;
import com.example.entity.vo.response.RuntimeHistoryVO;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * influxdb的工具类
 * @author zhexan
 * @since 2026-01-27
 */
@Slf4j
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

    /**
     *
     * @param clientId 客户端ID，用于查询指定客户端的历史运行时数据
     * @return RuntimeHistoryVO 包含历史运行时数据的视图对象
     * @since 2026-02-11
     */
    public RuntimeHistoryVO readRuntimeData(int clientId) {
        RuntimeHistoryVO vo = new RuntimeHistoryVO();
        String query = """
                from(bucket: "%s")
                |> range(start: %s)
                |> filter(fn: (r) => r["_measurement"] == "runtime")
                |> filter(fn: (r) => r["clientId"] == "%s")
                """;
        String format = String.format(query, BUCKET, "-1h", clientId);
        List<FluxTable> tables = client.getQueryApi().query(format, ORG);
        int size = tables.size();
        if (size == 0) return vo;
        List<FluxRecord> records = tables.get(0).getRecords();
        for (int i = 0; i < records.size(); i++) {
            JSONObject object = new JSONObject();
            object.put("timestamp", records.get(i).getTime());
            for (int j = 0; j < size; j++) {
                FluxRecord record = tables.get(j).getRecords().get(i);
                object.put(record.getField(), record.getValue());
            }
            vo.getList().add(object);
        }
        return vo;
    }

    /**
     * 获取指定客户端的历史运行时数据用于模型训练
     * @param clientId 客户端 ID
     * @param totalMemory 总内存容量 (GB)
     * @param totalDisk 总磁盘容量 (GB)
     * @return 用于模型训练的历史数据列表（内存和磁盘已转换为使用率）
     * @since 2026-03-06
     */
    public List<ModelTrainingDataVO> getTrainingData(int clientId, double totalMemory, double totalDisk) {
        String query = """
                from(bucket: "%s")
                |> range(start: %s)
                |> filter(fn: (r) => r["_measurement"] == "runtime")
                |> filter(fn: (r) => r["clientId"] == "%s")
                |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                |> sort(columns: ["_time"], desc: false)
                """;
        String format = String.format(query, BUCKET, "-24h", clientId);
        List<FluxTable> tables = client.getQueryApi().query(format, ORG);
        
        List<ModelTrainingDataVO> trainingData = new LinkedList<>();
        
        if (tables.isEmpty()) {
            return trainingData;
        }
        
        FluxTable pivotTable = tables.get(0);
        List<FluxRecord> records = pivotTable.getRecords();
        
        for (FluxRecord record : records) {
            try {
                ModelTrainingDataVO vo = new ModelTrainingDataVO();
                
                Object timeValue = record.getValueByKey("_time");
                if (timeValue != null) {
                    vo.setTimestamp(((java.time.Instant) timeValue).toEpochMilli());
                }
                
                Object cpuUsage = record.getValueByKey("cpuUsage");
                if (cpuUsage instanceof Number) {
                    vo.setCpuUsage(((Number) cpuUsage).doubleValue());
                }
                
                // 将内存使用量转换为使用率
                Object memoryUsage = record.getValueByKey("memoryUsage");
                if (memoryUsage instanceof Number && totalMemory > 0) {
                    vo.setMemoryUsage(((Number) memoryUsage).doubleValue() / totalMemory);
                }
                
                // 将磁盘使用量转换为使用率
                Object diskUsage = record.getValueByKey("diskUsage");
                if (diskUsage instanceof Number && totalDisk > 0) {
                    vo.setDiskUsage(((Number) diskUsage).doubleValue() / totalDisk);
                }
                
                Object networkUpload = record.getValueByKey("networkUpload");
                if (networkUpload instanceof Number) {
                    vo.setNetworkUpload(((Number) networkUpload).doubleValue());
                }
                
                Object networkDownload = record.getValueByKey("networkDownload");
                if (networkDownload instanceof Number) {
                    vo.setNetworkDownload(((Number) networkDownload).doubleValue());
                }
                
                Object diskRead = record.getValueByKey("diskRead");
                if (diskRead instanceof Number) {
                    vo.setDiskRead(((Number) diskRead).doubleValue());
                }
                
                Object diskWrite = record.getValueByKey("diskWrite");
                if (diskWrite instanceof Number) {
                    vo.setDiskWrite(((Number) diskWrite).doubleValue());
                }
                
                trainingData.add(vo);
                
            } catch (Exception e) {
                log.warn("解析记录时发生错误：{}", e.getMessage());
            }
        }
        
        log.info("从 InfluxDB 查询到客户端 {} 的 {} 条训练数据 (已转换为使用率)", clientId, trainingData.size());
        return trainingData;
    }
}
