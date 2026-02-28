package com.example.entity.vo.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class RenameClientVO {
    @NotNull
    int id;
    @Length(min = 1, max = 30)
    String name;
}
