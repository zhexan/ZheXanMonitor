package com.example.myprojectbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.myprojectbackend.entity.dto.Client;
import com.example.myprojectbackend.entity.vo.request.*;
import com.example.myprojectbackend.entity.vo.response.*;

import java.util.List;

public interface ClientService extends IService<Client> {
    String registerToken();
    boolean verifyAndRegister(String token);
    Client getClientById(int id);
    Client getClientByToken(String token);
    void updateClientDetail(ClientDetailVO vo, Client client);
    void updateRuntimeDetail(RuntimeDetailVO vo, Client client);
    List<ClientPreviewVO> listClients();
    void renameClient(RenameClientVO vo);
    void renameNode(RenameNodeVO vo);
    ClientDetailsVO clientDetails(int id);
    RuntimeHistoryVO clientRuntimeDetailsHistory(int clientId);
    RuntimeDetailVO clientRuntimeDetailsNow(int clientId);
    void deleteClient(int clientId);
    List<ClientSimpleVO> listSimpleClientList();
    void saveClientSshConnection(SshConnectionVO vo);
    SshSettingsVO sshSettings(int clientId);
}
