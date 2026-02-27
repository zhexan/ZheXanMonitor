package org.example.utils;

import lombok.extern.slf4j.Slf4j;
import oshi.SystemInfo;
import oshi.hardware.*;
import org.example.entity.BaseDetail;
import org.example.entity.ConnectConfig;
import org.example.entity.RuntimeDetail;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.io.IOException;

@Slf4j
@Component
public class MonitorUtils {
    @Lazy
    @Resource
    ConnectConfig config;
    private final SystemInfo info = new SystemInfo();
    private final Properties properties = System.getProperties();

    public BaseDetail monitorBaseDetail() {
        OperatingSystem os = info.getOperatingSystem();
        HardwareAbstractionLayer hardware = info.getHardware();
        double memory = hardware.getMemory().getTotal() / 1024.0 / 1024 /1024;
        double diskSize = Arrays.stream(File.listRoots()).mapToLong(File::getTotalSpace).sum() / 1024.0 / 1024 /1024;
        
        // 处理网络接口可能为空的情况
        NetworkIF networkInterface = this.findNetworkInterface(hardware);
        String ip = "unknown";
        if (networkInterface != null && networkInterface.getIPv4addr() != null && networkInterface.getIPv4addr().length > 0) {
            ip = networkInterface.getIPv4addr()[0];
        } else {
            log.warn("无法获取有效的IP地址，使用默认值");
        }
        
        return new BaseDetail()
                .setOsArch(properties.getProperty("os.arch"))
                .setOsName(os.getFamily())
                .setOsVersion(os.getVersionInfo().getVersion())
                .setOsBit(os.getBitness())
                .setCpuName(hardware.getProcessor().getProcessorIdentifier().getName())
                .setCpuCore(hardware.getProcessor().getLogicalProcessorCount())
                .setMemory(memory)
                .setDisk(diskSize)
                .setIp(ip);

    }

    /**
     * 获取运行时数据，因为oshi只支持获取一段时间内的数据取平均值，不支持获取某一时刻的数据，所以我们要自己计算。
     * @return 运行时数据
     * @author zhexan
     * @since 2026-01-20
     */
    public RuntimeDetail monitorRuntimeDetail() {
        double statisticTime = 0.5;
        try {
            HardwareAbstractionLayer hardware = info.getHardware();
            NetworkIF networkInterface = this.findNetworkInterface(hardware);
            if (networkInterface == null) {
                log.error("无法获取网络接口信息");
                return null;
            }
            CentralProcessor processor = hardware.getProcessor();
            // 读取网络上传和下载数据
            double upload = networkInterface.getBytesSent(),download = networkInterface.getBytesRecv();
            // 读取磁盘读取和写入数据
            double read = hardware.getDiskStores().stream().mapToLong(HWDiskStore::getReadBytes).sum();
            double write = hardware.getDiskStores().stream().mapToLong(HWDiskStore::getWriteBytes).sum();
            // 读取cpu信息
            long[] ticks = processor.getSystemCpuLoadTicks();
            // 线程休眠5秒
            Thread.sleep((long) (statisticTime *1000));
            // 读取5秒后的数据, 并进行计算
            networkInterface = this.findNetworkInterface(hardware);
            if (networkInterface == null) {
                log.error("无法获取网络接口信息");
                return null;
            }
            upload = (networkInterface.getBytesSent() - upload) / statisticTime;
            download = (networkInterface.getBytesRecv() - download) / statisticTime;
            read = (hardware.getDiskStores().stream().mapToLong(HWDiskStore::getReadBytes).sum() - read) / statisticTime;
            write = (hardware.getDiskStores().stream().mapToLong(HWDiskStore::getReadBytes).sum() - write) / statisticTime;
           double memory =  (hardware.getMemory().getTotal() - hardware.getMemory().getAvailable()) / 1024.0 / 1024 /1024;
           double disk = Arrays.stream(File.listRoots())
                   .mapToLong(file -> file.getTotalSpace() - file.getFreeSpace()).sum() / 1024.0 / 1024 / 1024;
           return new RuntimeDetail()
                   .setCpuUsage(this.calculateCpuUsage(processor, ticks))
                   .setMemoryUsage(memory)
                   .setDiskUsage(disk)
                   .setNetworkUpload(upload / 1024)
                   .setNetworkDownload(download / 1024)
                   .setDiskRead(read / 1024 / 1024)
                   .setDisKWrite(write / 1024 / 1024)
                   .setTimestamp(new Date().getTime());
        } catch(Exception e) {
            log.error("读取运行时数据失败", e);
        }
        return null;
    }

    /**
     * cv代码，内容复杂，需要学习
     * @param processor
     * @param prevTicks
     * @return
     * @author zhexan
     * @since 2026-01-20
     */
    private double calculateCpuUsage(CentralProcessor processor, long[] prevTicks) {
        long[] ticks = processor.getSystemCpuLoadTicks();
        long nice = ticks[CentralProcessor.TickType.NICE.getIndex()]
                - prevTicks[CentralProcessor.TickType.NICE.getIndex()];
        long irq = ticks[CentralProcessor.TickType.IRQ.getIndex()]
                - prevTicks[CentralProcessor.TickType.IRQ.getIndex()];
        long softIrq = ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()]
                - prevTicks[CentralProcessor.TickType.SOFTIRQ.getIndex()];
        long steal = ticks[CentralProcessor.TickType.STEAL.getIndex()]
                - prevTicks[CentralProcessor.TickType.STEAL.getIndex()];
        long cSys = ticks[CentralProcessor.TickType.SYSTEM.getIndex()]
                - prevTicks[CentralProcessor.TickType.SYSTEM.getIndex()];
        long cUser = ticks[CentralProcessor.TickType.USER.getIndex()]
                - prevTicks[CentralProcessor.TickType.USER.getIndex()];
        long ioWait = ticks[CentralProcessor.TickType.IOWAIT.getIndex()]
                - prevTicks[CentralProcessor.TickType.IOWAIT.getIndex()];
        long idle = ticks[CentralProcessor.TickType.IDLE.getIndex()]
                - prevTicks[CentralProcessor.TickType.IDLE.getIndex()];
        long totalCpu = cUser + nice + cSys + idle + ioWait + irq + softIrq + steal;
        return (cSys + cUser) * 1.0 / totalCpu;
    }

    public List<String> listNetworkInterfaceName() {
        HardwareAbstractionLayer hardware = info.getHardware();
        return hardware.getNetworkIFs()
                .stream()
                .map(NetworkIF::getName)
                .toList();
    }

    private NetworkIF findNetworkInterface(HardwareAbstractionLayer hardware) {
        try {
            // 检查config是否为null
            if (config == null) {
                log.warn("ConnectConfig未初始化，使用默认网络接口");
                List<NetworkIF> networkIFs = hardware.getNetworkIFs();
                if (!networkIFs.isEmpty()) {
                    return networkIFs.get(0);
                } else {
                    throw new IOException("未找到任何网络接口");
                }
            }
            
            String target = config.getNetworkInterface();
            List<NetworkIF> ifs = hardware.getNetworkIFs()
                    .stream()
                    .filter(inter -> inter.getName().equals(target))
                    .toList();
            if (!ifs.isEmpty()) {
                return ifs.get(0);
            } else {
                throw new IOException("网卡信息错误，找不到网卡: " + target);
            }
        } catch (IOException e) {
            log.error("读取网络接口信息时出错", e);
        }
        return null;
    }
}