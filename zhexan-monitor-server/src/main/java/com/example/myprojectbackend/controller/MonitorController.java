package com.example.myprojectbackend.controller;


import com.example.myprojectbackend.entity.RestBean;
import com.example.myprojectbackend.entity.dto.Account;
import com.example.myprojectbackend.entity.vo.request.RenameClientVO;
import com.example.myprojectbackend.entity.vo.request.RenameNodeVO;
import com.example.myprojectbackend.entity.vo.request.RuntimeDetailVO;
import com.example.myprojectbackend.entity.vo.response.ClientDetailsVO;
import com.example.myprojectbackend.entity.vo.response.ClientPreviewVO;
import com.example.myprojectbackend.entity.vo.response.RuntimeHistoryVO;
import com.example.myprojectbackend.service.ClientService;
import com.example.myprojectbackend.utils.Const;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @since 2026-02-02
 */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    @Resource
    ClientService clientService;

    @GetMapping("/list")
    public RestBean<List<ClientPreviewVO>> ListAllClient() {
        return RestBean.success(clientService.listClients());
    }

    /**
     * @param vo
     * @return
     * @since 2026-02-07
     */
    @PostMapping("/rename")
    public RestBean<Void> RenameClient(@RequestBody @Valid RenameClientVO vo) {
        clientService.renameClient(vo);
        return RestBean.success();
    }

    @PostMapping("/node")
    public RestBean<Void> RenameClient(@RequestBody @Valid RenameNodeVO vo) {
        clientService.renameNode(vo);
        return RestBean.success();
    }

    @GetMapping("/details")
    public RestBean<ClientDetailsVO> returnDetails(int clientId) {
        return RestBean.success(clientService.clientDetails(clientId));
    }

    @GetMapping("/runtime-history")
    public RestBean<RuntimeHistoryVO> runtimeDetailsHistory(int clientId) {
            return RestBean.success(clientService.clientRuntimeDetailsHistory(clientId));
    }
    @GetMapping("/runtime-now")
    public RestBean<RuntimeDetailVO> runtimeDetailsNow(int clientId) {
        return RestBean.success(clientService.clientRuntimeDetailsNow(clientId));
    }
    @GetMapping("/register")
    public RestBean<String> registerToken() {
        return RestBean.success(clientService.registerToken());
    }
    @GetMapping("/delete")
    public RestBean<Void> deleteClient(int clientId) {
        clientService.deleteClient(clientId);
        return RestBean.success();
    }
}