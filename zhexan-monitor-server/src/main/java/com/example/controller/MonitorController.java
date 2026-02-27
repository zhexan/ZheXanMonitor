package com.example.controller;


import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.RenameClientVO;
import com.example.entity.vo.request.RenameNodeVO;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.request.SshConnectionVO;
import com.example.entity.vo.response.*;
import com.example.myprojectbackend.entity.vo.response.*;
import com.example.service.AccountService;
import com.example.service.ClientService;
import com.example.utils.Const;
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
    @Resource
    AccountService accountService;

    @GetMapping("/list")
    public RestBean<List<ClientPreviewVO>> ListAllClient(@RequestAttribute(Const.ATTR_USER_ID) int userId,
                                                         @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        List<ClientPreviewVO> clients = clientService.listClients();
        if (this.isAdminAccount(userRole)) {
            return RestBean.success(clientService.listClients());
        } else {
            List<Integer> ids = this.accountAccessClients(userId);
            return RestBean.success(clients.stream()
                    .filter(vo -> ids.contains(vo.getId()))
                    .toList());
        }
    }

    /**
     * @param vo
     * @return
     * @since 2026-02-07
     */
    @PostMapping("/rename")
    public RestBean<Void> renameClient(@RequestBody @Valid RenameClientVO vo,
                                       @RequestAttribute(Const.ATTR_USER_ID) int userId,
                                       @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        if (this.permissionCheck(userId, userRole, vo.getId())) {
            clientService.renameClient(vo);
            return RestBean.success();
        } else {
            return RestBean.noPermission();
        }
    }

    @PostMapping("/node")
    public RestBean<Void> RenameClient(@RequestBody @Valid RenameNodeVO vo) {
        clientService.renameNode(vo);
        return RestBean.success();
    }

    @GetMapping("/details")
    public RestBean<ClientDetailsVO> details(int clientId,
                                             @RequestAttribute(Const.ATTR_USER_ID) int userId,
                                             @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        if(this.permissionCheck(userId, userRole, clientId)) {
            return RestBean.success(clientService.clientDetails(clientId));
        } else {
            return RestBean.noPermission();
        }
    }

    @GetMapping("/runtime-history")
    public RestBean<RuntimeHistoryVO> runtimeDetailsHistory(int clientId,
                                                            @RequestAttribute(Const.ATTR_USER_ID) int userId,
                                                            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        if(this.permissionCheck(userId, userRole, clientId)) {
            return RestBean.success(clientService.clientRuntimeDetailsHistory(clientId));
        } else {
            return RestBean.noPermission();
        }
    }

    @GetMapping("/runtime-now")
    public RestBean<RuntimeDetailVO> runtimeDetailsNow(int clientId,
                                                       @RequestAttribute(Const.ATTR_USER_ID) int userId,
                                                       @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        if(this.permissionCheck(userId, userRole, clientId)) {
            return RestBean.success(clientService.clientRuntimeDetailsNow(clientId));
        } else {
            return RestBean.noPermission();
        }
    }

    @GetMapping("/register")
    public RestBean<String> registerToken(@RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        if (this.isAdminAccount(userRole)) {
            return RestBean.success(clientService.registerToken());
        } else {
            return RestBean.noPermission();
        }
    }

    @GetMapping("/delete")
    public RestBean<String> deleteClient(int clientId,
                                         @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        if (this.isAdminAccount(userRole)) {
            clientService.deleteClient(clientId);
            return RestBean.success();
        } else {
            return RestBean.noPermission();
        }
    }
    @GetMapping("simple-list")
    public RestBean<List<ClientSimpleVO>> simpleClientList(@RequestAttribute(Const.ATTR_USER_ROLE) String role) {
        if (this.isAdminAccount(role)) {
            return RestBean.success(clientService.listSimpleClientList());
        } else {
            return RestBean.noPermission();
        }
    }
    @PostMapping("/ssh-save")
    public RestBean<Void> saveSshConnection(@RequestBody @Valid SshConnectionVO vo,
                                            @RequestAttribute(Const.ATTR_USER_ID) int userId,
                                            @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        if(this.permissionCheck(userId, userRole, vo.getId())) {
            clientService.saveClientSshConnection(vo);
            return RestBean.success();
        } else {
            return RestBean.noPermission();
        }
    }

    @GetMapping("/ssh")
    public RestBean<SshSettingsVO> sshSettings(int clientId,
                                               @RequestAttribute(Const.ATTR_USER_ID) int userId,
                                               @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
        if(this.permissionCheck(userId, userRole, clientId)) {
            return RestBean.success(clientService.sshSettings(clientId));
        } else {
            return RestBean.noPermission();
        }
    }

    private List<Integer> accountAccessClients(int userId) {
        Account account = accountService.getById(userId);
        return account.getClientList();
    }

    private boolean isAdminAccount(String role) {
        role = role.substring(5);
        return Const.ROLE_ADMIN.equals(role);
    }

    private boolean permissionCheck(int uid, String role, int clientId) {
        if (this.isAdminAccount(role)) return true;
        return this.accountAccessClients(uid).contains(clientId);
    }
}