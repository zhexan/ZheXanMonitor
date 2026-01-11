package com.example.myprojectbackend.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.myprojectbackend.entity.dto.Client;
import com.example.myprojectbackend.mapper.ClientMapper;
import com.example.myprojectbackend.service.ClientService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClientServiceImpl extends ServiceImpl<ClientMapper, Client> implements ClientService {

    private String registerToken = this.generateNewToken();
    private final Map<Integer, Client> clientIDCache= new ConcurrentHashMap<>();
    private final Map<String, Client> clientTokenCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initClientCache() {
        this.list().forEach(this::addClientCache);
    }
    /**
     * 向前端返回Token
     * @return registerToken
     */
    public String registerToken() {
        return registerToken;
    }

    /**
     * 注册
     * @param token 前端传来的token
     * @return 返回是否注册成功
     */
    public boolean verifyAndRegister(String token) {
        if(this.registerToken.equals(token)) {
            int id = this.randomClientId();
            Client client = new Client(id, "未命名主机", token, new Date());
            if(this.save(client)){
                registerToken = this.generateNewToken();
                this.addClientCache(client);
                return true;
            }
        }
        return false;
    }

    @Override
    public Client getClientById(int id) {
        return clientIDCache.get(id);
    }

    @Override
    public Client getClientByToken(String token) {
        return clientTokenCache.get(token);
    }

    private void addClientCache(Client client) {
        clientIDCache.put(client.getId(),client);
        clientTokenCache.put(client.getToken(),client);
    }
    private int randomClientId() {
        return new Random().nextInt(99999999) + 10000000;
    }
    private String generateNewToken() {
        String CHARACTERS = "abcdefghijklmnuporstuvwxyzABCDEFGHIJKLMNUPORSTUVWXYZ123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0;i < 24; i++) {
           sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}

