package com.example.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ModelPersistenceUtil {

    private static final String MODEL_KEY_PREFIX = "anomaly:model:";
    private static final String META_KEY_PREFIX = "anomaly:meta:";
    private static final long DEFAULT_MODEL_TTL = 30 * 24 * 3600; // 30 days

    @Resource
    private StringRedisTemplate template;

    public void saveModel(int clientId, byte[] modelData, double[] minValues, double[] maxValues, double threshold) {
        String modelKey = MODEL_KEY_PREFIX + clientId;
        String metaKey = META_KEY_PREFIX + clientId;

        try {
            // 使用 Base64 编码将字节数组转换为字符串
            String modelDataStr = Base64.getEncoder().encodeToString(modelData);
            template.opsForValue().set(modelKey, modelDataStr, DEFAULT_MODEL_TTL, TimeUnit.SECONDS);

            String metaBuilder = "threshold=" + threshold +
                    ";minValues=" + arrayToString(minValues) +
                    ";maxValues=" + arrayToString(maxValues);
            template.opsForValue().set(metaKey, metaBuilder, DEFAULT_MODEL_TTL, TimeUnit.SECONDS);

            log.info("客户端 {} 的模型已保存到 Redis", clientId);
        } catch (Exception e) {
            log.error("保存模型到 Redis 失败，客户端 ID: {}", clientId, e);
            throw new RuntimeException("模型保存失败", e);
        }
    }

    public ModelData loadModel(int clientId) {
        String modelKey = MODEL_KEY_PREFIX + clientId;
        String metaKey = META_KEY_PREFIX + clientId;

        try {
            String modelDataStr = template.opsForValue().get(modelKey);
            String metaData = template.opsForValue().get(metaKey);

            if (modelDataStr == null || metaData == null) {
                log.info("客户端 {} 在 Redis 中未找到模型", clientId);
                return null;
            }

            // 使用 Base64 解码
            byte[] modelData = Base64.getDecoder().decode(modelDataStr);

            double threshold = 0.6;
            double[] minValues = null;
            double[] maxValues = null;

            String[] metaParts = metaData.split(";");
            for (String part : metaParts) {
                String[] kv = part.split("=");
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    switch (key) {
                        case "threshold" -> threshold = Double.parseDouble(value);
                        case "minValues" -> minValues = stringToArray(value);
                        case "maxValues" -> maxValues = stringToArray(value);
                    }
                }
            }

            log.info("客户端 {} 的模型已从 Redis 加载", clientId);
            return new ModelData(modelData, minValues, maxValues, threshold);

        } catch (Exception e) {
            log.error("从 Redis 加载模型失败，客户端 ID: {}", clientId, e);
            return null;
        }
    }

    public List<Integer> getSavedClientIds() {
        List<Integer> clientIds = new ArrayList<>();
        try {
            var keys = template.keys(MODEL_KEY_PREFIX + "*");
            for (var key : keys) {
                if (key.startsWith(MODEL_KEY_PREFIX)) {
                    String idStr = key.substring(MODEL_KEY_PREFIX.length());
                    clientIds.add(Integer.parseInt(idStr));
                }
            }
        } catch (Exception e) {
            log.error("获取已保存的客户端 ID 列表失败", e);
        }
        return clientIds;
    }

    public void deleteModel(int clientId) {
        String modelKey = MODEL_KEY_PREFIX + clientId;
        String metaKey = META_KEY_PREFIX + clientId;
        try {
            template.delete(modelKey);
            template.delete(metaKey);
            log.info("客户端 {} 的模型已从 Redis 删除", clientId);
        } catch (Exception e) {
            log.error("从 Redis 删除模型失败，客户端 ID: {}", clientId, e);
        }
    }

    public byte[] serializeObject(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        }
    }

    public Object deserializeObject(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        }
    }

    private String arrayToString(double[] arr) {
        if (arr == null || arr.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private double[] stringToArray(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split(",");
        double[] arr = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            arr[i] = Double.parseDouble(parts[i].trim());
        }
        return arr;
    }
        public record ModelData(byte[] modelData, double[] minValues, double[] maxValues, double threshold) {

    }
}
