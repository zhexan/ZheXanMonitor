package com.example.myprojectbackend.controller;

import com.example.myprojectbackend.entity.RestBean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {
    @RequestMapping("/hello")
    public RestBean<String> hello() {
        return RestBean.success("Hello World");

    }
}
