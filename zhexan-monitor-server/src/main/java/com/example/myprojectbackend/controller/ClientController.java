package com.example.myprojectbackend.controller;

import com.example.myprojectbackend.entity.RestBean;
import com.example.myprojectbackend.entity.dto.Client;
import com.example.myprojectbackend.entity.vo.request.ClientDetailVO;
import com.example.myprojectbackend.service.ClientService;
import com.example.myprojectbackend.utils.Const;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/monitor")
public class ClientController {

    @Resource
    private ClientService clientService;
    @GetMapping("/register")
    public RestBean<Client> registerClient(@RequestHeader("Authorization") String token) {
        log.info("execute registerClient");
        return clientService.verifyAndRegister(token) ? RestBean.success() : RestBean.failure(401,"客户机注册失败");
    }

    /**
     *
     * @param client
     * @param vo
     * @return
     * @since 2026-1-21
     */

    @PostMapping("/detail")
    public RestBean<Void> updateClientDetails(@RequestAttribute(Const.ATTR_CLIENT) Client client,
                                              @RequestBody @Valid ClientDetailVO vo) {
        clientService.updateClientDetail(vo, client);
        return RestBean.success();
    }
}

