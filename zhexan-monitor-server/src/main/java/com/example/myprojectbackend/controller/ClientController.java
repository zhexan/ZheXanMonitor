package com.example.myprojectbackend.controller;

import com.example.myprojectbackend.entity.RestBean;
import com.example.myprojectbackend.entity.dto.Client;
import com.example.myprojectbackend.service.ClientService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController("/monitor")
public class ClientController {

    @Resource
    private ClientService clientService;
    @GetMapping("/register")
    public RestBean<Client> registerClient(@RequestHeader("Authorization") String token) {
        return clientService.verifyAndRegister(token) ? RestBean.success() : RestBean.failure(401,"客户机注册失败");
    }
}
