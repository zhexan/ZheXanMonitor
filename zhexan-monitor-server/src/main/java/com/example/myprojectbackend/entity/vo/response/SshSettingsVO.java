package com.example.myprojectbackend.entity.vo.response;

import lombok.Data;

@Data
public class SshSettingsVO {
    String ip;
    int port = 22;
    String username;
    String password;
}
