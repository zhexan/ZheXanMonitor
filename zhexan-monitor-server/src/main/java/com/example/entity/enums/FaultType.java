package com.example.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 故障类型枚举
 * @author zhexan
 * @since 2026-03-14
 */
@Getter
@AllArgsConstructor
public enum FaultType {
    NORMAL(0, "正常"),
    CPU_OVERLOAD(1, "CPU 过载"),
    MEMORY_LEAK(2, "内存泄漏"),
    DISK_FULL(3, "磁盘已满"),
    NETWORK_CONGESTION(4, "网络拥塞"),
    IO_BOTTLENECK(5, "IO 瓶颈"),
    MULTIPLE_FAULTS(6, "复合故障"),
    ANOMALY_DETECTED(7, "检测到异常");
    
    private final Integer code;
    private final String description;
}
