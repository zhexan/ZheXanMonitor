package com.example.myprojectbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.myprojectbackend.entity.dto.Client;

public interface ClientService extends IService<Client> {
    boolean verifyAndRegister(String token);
    Client getClientById(int id);
    Client getClientByToken(String token);
}
