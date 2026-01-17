package org.example.entity;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConnectConfig {
    String address;
    String token;
}
