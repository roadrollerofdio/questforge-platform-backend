package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户已购装扮
 */
@Data
@TableName("user_item")
public class UserItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long itemId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
