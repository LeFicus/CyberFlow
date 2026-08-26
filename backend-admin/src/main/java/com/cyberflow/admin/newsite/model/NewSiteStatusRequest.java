package com.cyberflow.admin.newsite.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NewSiteStatusRequest {

    @NotBlank(message = "站点使用状态不能为空")
    private String status;
}
