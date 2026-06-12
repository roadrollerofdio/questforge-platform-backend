package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 宝石小店商品(引用前端内置 SVG 装扮部件)
 */
@Data
@TableName("shop_item")
public class ShopItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    // 装扮部位: hat / glasses / outfit / background
    private String slot;

    // 内置 SVG 部件 key, 前端按 key 渲染
    private String svgKey;

    private Integer price;

    // 1-上架 0-下架
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
