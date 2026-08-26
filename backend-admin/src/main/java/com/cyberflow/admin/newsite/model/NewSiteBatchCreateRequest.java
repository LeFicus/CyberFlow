package com.cyberflow.admin.newsite.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NewSiteBatchCreateRequest {

    @NotEmpty(message = "至少创建一个新站点")
    @Valid
    private List<NewSiteCreateRequest> sites = new ArrayList<>();
}
