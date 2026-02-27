package org.example.config;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.ConnectConfig;
import org.example.utils.MonitorUtils;
import org.example.utils.NetUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

@Slf4j
@Configuration
public class ServerConfiguration implements ApplicationRunner {

    @Resource
    NetUtils net;
    @Resource
    MonitorUtils monitor;
    @Bean
    ConnectConfig connectConfig() {
       log.info("正在加载服务器端链接配置...");
       ConnectConfig config = this.readConnectConfigurationFormFile();
        if (config == null)
            config = registerToServer();
       log.info("服务端链接配置加载成功...");
        return config;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("正在向服务器端更新基本信息...");
        net.updateBaseDetails(monitor.monitorBaseDetail());
    }

    private ConnectConfig registerToServer() {
        Scanner scanner = new Scanner(System.in);
        String token, address, ifName;
        do {
            log.info("请输入需要注册的服务端访问地址，地址类似于 'http://192.168.0.22:8080' 这种写法:");
            address = scanner.nextLine();
            log.info("请输入服务端生成的用于注册客户端的Token秘钥:");
            token = scanner.nextLine();
            List<String> ifs = monitor.listNetworkInterfaceName();
            if(ifs.size() > 1) {
                log.info("检测到您的主机有多个网卡设备: {}", ifs);
                do {
                    log.info("请选择需要监控的设备名称:");
                    ifName = scanner.nextLine();
                } while (!ifs.contains(ifName));
            } else {
                ifName = ifs.get(0);
            }
        } while (!net.registerToServer(address, token));
        ConnectConfig config = new ConnectConfig(address, token, ifName);
        this.saveConnectConfig(config);
        return config;
    }
    private void saveConnectConfig(ConnectConfig config) {
        File dir = new File("config");
        if(!dir.exists() && dir.mkdir()) {
            log.info("创建保存服务器连接信息的目录创建完成");
            File file = new File("config/server.json");
            try(FileWriter writer = new FileWriter(file)) {
                writer.write(JSONObject.from(config).toString());
            } catch (IOException e) {
                log.error("保存配置文件失败", e);
            }
            log.info("保存配置文件成功");

        }

    }
    private ConnectConfig readConnectConfigurationFormFile() {
        File configurationFile = new File("config/server.json");
        if(configurationFile.exists()) {
            try(FileInputStream fileOutputStream = new FileInputStream(configurationFile)){
                String raw = new String(fileOutputStream.readAllBytes(), StandardCharsets.UTF_8);
                return JSONObject.parseObject(raw).to(ConnectConfig.class);
            } catch (IOException e) {
                log.error("读取配置文件时出错", e);
            }
        }
        return null;
    }

}
