package com.example.myprojectbackend.service.Impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.myprojectbackend.entity.dto.Client;
import com.example.myprojectbackend.entity.dto.ClientDetail;
import com.example.myprojectbackend.entity.vo.request.ClientDetailVO;
import com.example.myprojectbackend.entity.vo.request.RenameClientVO;
import com.example.myprojectbackend.entity.vo.request.RenameNodeVO;
import com.example.myprojectbackend.entity.vo.request.RuntimeDetailVO;
import com.example.myprojectbackend.entity.vo.response.ClientDetailsVO;
import com.example.myprojectbackend.entity.vo.response.ClientPreviewVO;
import com.example.myprojectbackend.entity.vo.response.ClientSimpleVO;
import com.example.myprojectbackend.entity.vo.response.RuntimeHistoryVO;
import com.example.myprojectbackend.mapper.ClientDetailMapper;
import com.example.myprojectbackend.mapper.ClientMapper;
import com.example.myprojectbackend.service.ClientService;
import com.example.myprojectbackend.utils.InfluxDBUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ClientServiceImpl extends ServiceImpl<ClientMapper, Client> implements ClientService {

    private String registerToken = this.generateNewToken();
    private final Map<Integer, Client> clientIDCache = new ConcurrentHashMap<>();
    private final Map<String, Client> clientTokenCache = new ConcurrentHashMap<>();

    @Resource
    ClientDetailMapper detailMapper;
    @Resource
    InfluxDBUtils influx;

    @PostConstruct
    public void initClientCache() {
        clientTokenCache.clear();
        clientIDCache.clear();
        this.list().forEach(this::addClientCache);
    }

    /**
     * 向前端返回Token
     *
     * @return registerToken
     */
    @Override
    public String registerToken() {
        return registerToken;
    }

    /**
     * 注册
     *
     * @param token 前端传来的token
     * @return 返回是否注册成功
     */
    public boolean verifyAndRegister(String token) {
        if (this.registerToken.equals(token)) {
            int id = this.randomClientId();
            Client client = new Client(id, "未命名主机", token, "cn", "未命名节点", new Date());
            if (this.save(client)) {
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

    /**
     * 更新客户端上报的基础数据
     *
     * @param vo     客户端发来的数据
     * @param client 哪一个客户端
     * @since 2026-01-21
     */
    @Override
    public void updateClientDetail(ClientDetailVO vo, Client client) {
        ClientDetail detail = new ClientDetail();
        BeanUtils.copyProperties(vo, detail);
        detail.setId(client.getId());
        if (Objects.nonNull(detailMapper.selectById(client.getId()))) {
            detailMapper.updateById(detail);
        } else {
            detailMapper.insert(detail);
        }
    }

    private final Map<Integer, RuntimeDetailVO> currentRuntime = new ConcurrentHashMap<>();

    /**
     * 更新客户端上报的实时数据
     *
     * @param vo     客户端发送的实时数据
     * @param client 客户端信息，确认是哪一个客户端
     * @since 2026-01-24,2026-01-27
     */
    @Override
    public void updateRuntimeDetail(RuntimeDetailVO vo, Client client) {
        currentRuntime.put(client.getId(), vo);
        influx.writeRuntimeData(client.getId(), vo);

    }

    @Override
    public List<ClientPreviewVO> listClients() {
        return clientIDCache.values().stream().map(client -> {
            ClientPreviewVO vo = client.asViewObject(ClientPreviewVO.class);
            BeanUtils.copyProperties(detailMapper.selectById(vo.getId()), vo);
            RuntimeDetailVO runtime = currentRuntime.get(client.getId());
            if (this.isOnline(runtime)) {
                BeanUtils.copyProperties(runtime, vo);
                vo.setOnline(true);
            }
            return vo;
        }).toList();

    }

    /**
     *
     * @param vo
     * @since 2026-02-07
     */
    @Override
    public void renameClient(RenameClientVO vo) {
        this.update(Wrappers.<Client>update().eq("id", vo.getId()).set("name", vo.getName()));
        this.initClientCache();
    }

    @Override
    public void renameNode(RenameNodeVO vo) {
        this.update(Wrappers.<Client>update().eq("id", vo.getId()).set("node", vo.getNode()).set("location", vo.getLocation()));
        this.initClientCache();
    }

    /**
     *
     * @param id 客户端ID
     * @return
     * @@since 2026-02-08
     */
    @Override
    public ClientDetailsVO clientDetails(int id) {
        ClientDetailsVO vo = this.clientIDCache.get(id).asViewObject(ClientDetailsVO.class);
        BeanUtils.copyProperties(detailMapper.selectById(id), vo);
        vo.setOnline(this.isOnline(currentRuntime.get(id)));
        return vo;
    }

    /**
     * 获取指定客户端的历史运行时详细信息
     * @param clientId 客户端ID
     * @return 包含历史运行时数据和客户端详细信息的RuntimeHistoryVO对象
     * @since 2026-02-11
     */
    @Override
    public RuntimeHistoryVO clientRuntimeDetailsHistory(int clientId) {
        RuntimeHistoryVO vo = influx.readRuntimeData(clientId);
        ClientDetail detail = detailMapper.selectById(clientId);
        BeanUtils.copyProperties(detail, vo);
        return vo;
    }

    /**
     * 获取指定客户端的当前运行时详细信息
     * @param clientId 客户端ID
     * @return 当前运行时详细信息，如果客户端不在线则返回null
     * @since 2026-02-11
     */
    @Override
    public RuntimeDetailVO clientRuntimeDetailsNow(int clientId) {
        return currentRuntime.get(clientId);
    }
    private boolean isOnline(RuntimeDetailVO runtime) {
        return runtime != null && System.currentTimeMillis() - runtime.getTimestamp() < 60 * 1000;
    }

    @Override
    public void deleteClient(int clientId) {
        this.removeById(clientId);
        detailMapper.deleteById(clientId);
        this.initClientCache();
        currentRuntime.remove(clientId);
    }

    @Override
    public List<ClientSimpleVO> listSimpleClientList() {
        return clientIDCache.values().stream().map(client -> {
            ClientSimpleVO vo = client.asViewObject(ClientSimpleVO.class);
            BeanUtils.copyProperties(detailMapper.selectById(vo.getId()), vo);
            return vo;
        }).toList();
    }

    private void addClientCache(Client client) {
        clientIDCache.put(client.getId(), client);
        clientTokenCache.put(client.getToken(), client);
    }

    private int randomClientId() {
        return new Random().nextInt(99999999) + 10000000;
    }

    private String generateNewToken() {
        String CHARACTERS = "abcdefghijklmnuporstuvwxyzABCDEFGHIJKLMNUPORSTUVWXYZ123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        System.out.println(sb);
        return sb.toString();
    }
}

