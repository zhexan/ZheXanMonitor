package com.example.myprojectbackend.entity.vo.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RuntimeDetailVO {
    @NotNull
    long timestamp;
    @NotNull
    double cupUsage;
    @NotNull
    double memoryUsage;
    @NotNull
    double diskUsage;
    @NotNull
    double networkUpload;
    @NotNull
    double networkDownload;
    @NotNull
    double diskRead;
    @NotNull
    double disKWrite;
}
