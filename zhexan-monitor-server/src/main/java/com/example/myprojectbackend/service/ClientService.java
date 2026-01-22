package com.example.myprojectbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.myprojectbackend.entity.dto.Client;
import com.example.myprojectbackend.entity.vo.request.ClientDetailVO;

public interface ClientService extends IService<Client> {
    String registerToken();
    boolean verifyAndRegister(String token);
    Client getClientById(int id);
    Client getClientByToken(String token);
    void updateClientDetail(ClientDetailVO vo, Client client);

}
