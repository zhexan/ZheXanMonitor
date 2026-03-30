package com.example.entity.vo.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateSubAccountVO {
    @NotNull
    int id;

    @NotNull
    @Size(min = 1)
    List<Integer> clients;
}
