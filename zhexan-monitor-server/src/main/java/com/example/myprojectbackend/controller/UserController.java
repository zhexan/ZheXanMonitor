package com.example.myprojectbackend.controller;

import com.example.myprojectbackend.entity.RestBean;
import com.example.myprojectbackend.entity.vo.request.ChangePasswordVO;
import com.example.myprojectbackend.entity.vo.request.CreateSubAccountVO;
import com.example.myprojectbackend.entity.vo.response.SubAccountVO;
import com.example.myprojectbackend.service.AccountService;
import com.example.myprojectbackend.utils.Const;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    AccountService service;

    public RestBean<Void>  changePassword(@RequestBody @Valid ChangePasswordVO vo,
                                          @RequestAttribute(Const.ATTR_USER_ID) int userId) {
       return service.changePassword(userId, vo.getPassword(), vo.getNew_password()) ?
               RestBean.success() : RestBean.failure(401, "原密码输入错误");
    }
    @PostMapping("/sub/create")
    public RestBean<Void> createSubAccount(@RequestBody @Valid CreateSubAccountVO vo) {
        service.createSubAccount(vo);
        return RestBean.success();
    }

    @GetMapping("/sub/delete")
    public RestBean<Void> deleteSubAccount(int uid,
                                           @RequestAttribute(Const.ATTR_USER_ID) int userId) {
        if(uid == userId)
            return RestBean.failure(401, "非法参数");
        service.deleteSubAccount(uid);
        return RestBean.success();
    }

    @GetMapping("/sub/list")
    public RestBean<List<SubAccountVO>> subAccountList() {
        return RestBean.success(service.listSubAccount());
    }
}
