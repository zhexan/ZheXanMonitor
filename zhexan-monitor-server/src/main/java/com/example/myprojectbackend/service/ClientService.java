package com.example.myprojectbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.myprojectbackend.entity.dto.Client;
import com.example.myprojectbackend.entity.vo.request.ClientDetailVO;
import com.example.myprojectbackend.entity.vo.request.RenameClientVO;
import com.example.myprojectbackend.entity.vo.request.RenameNodeVO;
import com.example.myprojectbackend.entity.vo.request.RuntimeDetailVO;
import com.example.myprojectbackend.entity.vo.response.ClientDetailsVO;
import com.example.myprojectbackend.entity.vo.response.ClientPreviewVO;
import com.example.myprojectbackend.entity.vo.response.RuntimeHistoryVO;

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
}
