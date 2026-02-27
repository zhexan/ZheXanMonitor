package com.example.service.Impl;

import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.Account;
import com.example.entity.vo.request.ConfirmResetVO;
import com.example.entity.vo.request.CreateSubAccountVO;
import com.example.entity.vo.request.EmailResetVO;
import com.example.entity.vo.request.ModifyEmailVO;
import com.example.myprojectbackend.entity.vo.request.*;
import com.example.entity.vo.response.SubAccountVO;
import com.example.mapper.AccountMapper;
import com.example.service.AccountService;
import com.example.utils.Const;
import com.example.utils.FlowUtils;
import jakarta.annotation.Resource;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {
    @Resource
    FlowUtils flowUtils;
    @Resource
    AmqpTemplate amqpTemplate;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    PasswordEncoder encoder;
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = this.findAccountByUsernameOrEmail(username);
        if (account == null)
            throw new UsernameNotFoundException("用户不存在");
        return User.withUsername(username)
                .password(account.getPassword())
                .roles(account.getRole())
                .build();

    }

    public Account findAccountByUsernameOrEmail(String usernameOrEmail) {
        return this.query()
                .eq("username", usernameOrEmail)
                .or()
                .eq("email", usernameOrEmail)
                .one();
    }

    @Override
    public String registerEmailVerifyCode(String type, String email, String ip) {
        synchronized (ip.intern()) {
            if(!this.verifyLimit(ip)){
                return "请求频繁，稍后再试";
            }
            Random random = new Random();
            int code = random.nextInt(899999) +100000;
            Map<String, Object> data = Map.of("type",type,"email",email,"code",code);
            amqpTemplate.convertAndSend("mail", data);
            stringRedisTemplate.opsForValue()
                    .set(Const.VERIFY_EMAIL_DATA + email,String.valueOf(code), 3, TimeUnit.MINUTES);
            return null;
        }
    }

    @Override
    public String resetConfirm(ConfirmResetVO vo){
        String email = vo.getEmail();
        String code = this.getEmailVerifyCode(email);
        if(code == null) {
            return "请先获取验证码";
        }
        if(!code.equals(vo.getCode())) {
            return "验证码输入错误";
        }
        return null;
    }
    @Override
    public String resetEmailAccountPassword(EmailResetVO vo) {
        String email = vo.getEmail();
        String verify = this.resetConfirm(new ConfirmResetVO(email,vo.getCode()));
        if(verify != null) return verify;
        String password = encoder.encode(vo.getPassword());
        boolean update =this.update().eq("email", email).set("password", password).update();
        if(update) {
            this.deleteEmailVerifyCode(email);
        }
        return update ? null : "内部错误";

    }

    @Override
    public boolean changePassword(int id, String oldPass, String newPass) {
        Account account = this.getById(id);
        String password = account.getPassword();
        if(!passwordEncoder.matches(oldPass, password))
            return false;
        this.update(Wrappers.<Account>update().eq("id", id).set("password", passwordEncoder.encode(newPass)));
        return true;
    }
    @Override
    public Map<Boolean, String> createSubAccount(CreateSubAccountVO vo) {
        Account account = this.findAccountByUsernameOrEmail(vo.getEmail());
        if(account != null)
            return Map.of(false, "该邮箱已被注册");
        account = this.findAccountByUsernameOrEmail(vo.getUsername());
        if(account != null)
            return Map.of(false, "改用户名已被注册");
        account = new Account(null, vo.getUsername(), passwordEncoder.encode(vo.getPassword()),
                vo.getEmail(), Const.ROLE_NORMAL, new Date(), JSONArray.copyOf(vo.getClients()).toJSONString());
        this.save(account);
        return Map.of(true, "改邮箱注册成功");
    }

    @Override
    public void deleteSubAccount(int uid) {
        this.removeById(uid);

    }

    @Override
    public List<SubAccountVO> listSubAccount() {
        return this.list(Wrappers.<Account>query().eq("role", Const.ROLE_NORMAL))
                .stream().map(account -> {
                    SubAccountVO vo = account.asViewObject(SubAccountVO.class);
                    vo.setClientList(JSONArray.parse(account.getClients()));
                    return vo;
                }).toList();
    }
    @Override
    public String modifyEmail(int id, ModifyEmailVO vo) {
        String code = getEmailVerifyCode(vo.getEmail());
        if (code == null) return "请先获取验证码";
        if(!code.equals(vo.getCode())) return "验证码错误，请重新输入";
        this.deleteEmailVerifyCode(vo.getEmail());
        Account account = this.findAccountByUsernameOrEmail(vo.getEmail());
        if(account != null && account.getId() != id) return "该邮箱账号已经被其他账号绑定，无法完成操作";
        this.update()
                .set("email", vo.getEmail())
                .eq("id", id)
                .update();
        return null;
    }


    private boolean verifyLimit(String ip) {
        String key = Const.VERIFY_EMAIL_LIMIT + ip;
        return flowUtils.limitOnceCheck(key, 60);
    }

    private void deleteEmailVerifyCode(String email){
        String key = Const.VERIFY_EMAIL_DATA + email;
        stringRedisTemplate.delete(key);
    }

    private String getEmailVerifyCode(String email){
        String key = Const.VERIFY_EMAIL_DATA + email;
        return stringRedisTemplate.opsForValue().get(key);
    }
}
