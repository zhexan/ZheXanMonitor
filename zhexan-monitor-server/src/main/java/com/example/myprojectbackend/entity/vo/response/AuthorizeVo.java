package com.example.myprojectbackend.entity.vo.response;

import lombok.Data;

import java.util.Date;

@Data
public class AuthorizeVo {
    private Date expire;
    private String role;
    private String token;
    private String username;
}
