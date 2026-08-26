package com.cyberflow.admin.newsite.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** One row of a single or batch new-site creation request. */
@Data
public class NewSiteCreateRequest {

    @NotBlank(message = "自定义分类不能为空")
    private String customCategory;

    @NotEmpty(message = "至少选择一个主产品分类")
    private List<@NotBlank(message = "主产品分类不能为空") String> mainProductCategories = new ArrayList<>();

    @NotEmpty(message = "至少选择一个副产品分类")
    private List<@NotBlank(message = "副产品分类不能为空") String> supplementProductCategories = new ArrayList<>();

    @NotEmpty(message = "至少选择一个源站点")
    private List<@NotBlank(message = "源站点不能为空") String> sourceDomains = new ArrayList<>();
}
