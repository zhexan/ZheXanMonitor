package org.example.utils;

import lombok.extern.slf4j.Slf4j;
import org.example.entity.BaseDetail;
import org.example.entity.RuntimeDetail;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HWDiskStore;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Component
public class MonitorUtils {
    private final SystemInfo info = new SystemInfo();
    private final Properties properties = System.getProperties();

    public BaseDetail monitorBaseDetail() {
        OperatingSystem os = info.getOperatingSystem();
        HardwareAbstractionLayer hardware = info.getHardware();
        double memory = hardware.getMemory().getTotal() / 1024.0 / 1024 /1024;
        double diskSize = Arrays.stream(File.listRoots()).mapToLong(File::getTotalSpace).sum() / 1024.0 / 1024 /1024;
        String ip = Objects.requireNonNull(this.findNetworkInterface(hardware)).getIPv4addr()[0];
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
            NetworkIF networkInterface = Objects.requireNonNull(this.findNetworkInterface(hardware));
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
            networkInterface = Objects.requireNonNull(this.findNetworkInterface(hardware));
            upload = (networkInterface.getBytesSent() - upload) / statisticTime;
            download = (networkInterface.getBytesRecv() - download) / statisticTime;
            read = (hardware.getDiskStores().stream().mapToLong(HWDiskStore::getReadBytes).sum() - read) / statisticTime;
            write = (hardware.getDiskStores().stream().mapToLong(HWDiskStore::getReadBytes).sum() - write) / statisticTime;
           double memory =  (hardware.getMemory().getTotal() - hardware.getMemory().getAvailable()) / 1024.0 / 1024 /1024;
           double disk = Arrays.stream(File.listRoots())
                   .mapToLong(file -> file.getTotalSpace() - file.getFreeSpace()).sum() / 1024.0 / 1024 / 1024;
           return new RuntimeDetail()
                   .setCupUsage(this.calculateCpuUsage(processor, ticks))
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

    private NetworkIF findNetworkInterface(HardwareAbstractionLayer hardware) {
            try{
               for (NetworkIF network : hardware.getNetworkIFs()) {
                   String[] ipv4Addr = network.getIPv4addr();
                   NetworkInterface ni = network.queryNetworkInterface();
                   if(!ni.isLoopback() && !ni.isPointToPoint() && !ni.isVirtual() && ni.isUp()
                           && (ni.getName().startsWith("eth") || ni.getName().startsWith("eh"))
                           && ipv4Addr.length > 0) {
                       return network;
                   }
               }
            } catch (Exception e) {
                log.error("获取网络接口信息出错", e);
            }
            return null;
    }
}
