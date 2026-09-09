package com.cyberflow.admin.newsite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("new_site_asset")
public class NewSiteAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long siteId;
    private String generationGroup;
    private String assetType;
    private String variant;
    @JsonIgnore
    private String storageKey;
    private String mimeType;
    private Integer width;
    private Integer height;
    private String provider;
    private String model;
    private String prompt;
    private String status;
    private Integer isSelected;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Stable authenticated endpoint used instead of exposing a filesystem path. */
    @TableField(exist = false)
    private String contentUrl;
}
