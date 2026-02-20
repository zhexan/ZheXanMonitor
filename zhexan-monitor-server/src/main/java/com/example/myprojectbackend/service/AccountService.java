package com.example.myprojectbackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.myprojectbackend.entity.dto.Account;
import com.example.myprojectbackend.entity.vo.request.ConfirmResetVO;
import com.example.myprojectbackend.entity.vo.request.CreateSubAccountVO;
import com.example.myprojectbackend.entity.vo.request.EmailRegisterVO;
import com.example.myprojectbackend.entity.vo.request.EmailResetVO;
import com.example.myprojectbackend.entity.vo.response.SubAccountVO;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;


public interface AccountService extends IService<Account>, UserDetailsService {
    Account findAccountByUsernameOrEmail(String usernameOrEmail);
     String registerEmailVerifyCode(String type, String email, String ip);
    String resetConfirm(ConfirmResetVO vo);
    String resetEmailAccountPassword(EmailResetVO vo);
    boolean changePassword(int id, String oldPass, String newPass);
    void createSubAccount(CreateSubAccountVO vo);
    void deleteSubAccount(int uid);
    List<SubAccountVO> listSubAccount();
}
