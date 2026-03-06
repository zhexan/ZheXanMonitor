package com.example.controller;


import com.example.entity.RestBean;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.RenameClientVO;
import com.example.entity.vo.request.RenameNodeVO;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.request.SshConnectionVO;
import com.example.entity.vo.response.*;
import com.example.service.AccountService;
import com.example.service.ClientService;
import com.example.service.AnomalyDetectionService;
import com.example.utils.Const;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
    @Resource
    AnomalyDetectionService anomalyDetectionService;

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

    private boolean permissionCheck(int uid, String role, int clientId) {
        if (this.isAdminAccount(role)) return true;
        return this.accountAccessClients(uid).contains(clientId);
    }
    private boolean isAdminAccount(String role) {
        role = role.substring(5);
        return Const.ROLE_ADMIN.equals(role);
    }
    
//    /**
//     * 获取异常检测模型状态
//     * @param clientId 客户端 ID
//     * @param userId 用户 ID
//     * @param userRole 用户角色
//     * @return 模型状态信息
//     */
//    @GetMapping("/anomaly-status")
//    public RestBean<Map<String, Object>> getAnomalyStatus(@RequestParam int clientId,
//                                                         @RequestAttribute(Const.ATTR_USER_ID) int userId,
//                                                         @RequestAttribute(Const.ATTR_USER_ROLE) String userRole) {
//        if(this.permissionCheck(userId, userRole, clientId)) {
//            boolean isTrained = anomalyDetectionService.isModelTrained(clientId);
//            Map<String, Object> status = new HashMap<>();
//            status.put("trained", isTrained);
//            status.put("clientId", clientId);
//
//            if (isTrained) {
//                // 获取历史数据量
//                RuntimeHistoryVO history = clientService.clientRuntimeDetailsHistory(clientId);
//                int dataCount = (history != null && history.getRuntimeDataList() != null)
//                               ? history.getRuntimeDataList().size() : 0;
//
//                status.put("dataCount", dataCount);
//                status.put("status", "ready");
//                status.put("message", "模型已就绪，正在执行异常检测");
//            } else {
//                // 获取当前数据量
//                RuntimeHistoryVO history = clientService.clientRuntimeDetailsHistory(clientId);
//                int dataCount = (history != null && history.getRuntimeDataList() != null)
//                               ? history.getRuntimeDataList().size() : 0;
//
//                status.put("dataCount", dataCount);
//                status.put("status", dataCount >= 100 ? "training" : "waiting");
//                status.put("message", dataCount >= 100
//                                   ? "数据量已达标，正在初始化训练模型"
//                                   : "等待数据累积，需要至少 100 条历史数据");
//                status.put("requiredDataCount", 100);
//                status.put("remainingData", Math.max(0, 100 - dataCount));
//            }
//
//            return RestBean.success(status);
//        } else {
//            return RestBean.noPermission();
//        }
//    }
}