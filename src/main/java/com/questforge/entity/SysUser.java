package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体: sys_user (系统用户表)
 */
@Data
@TableName("sys_user")
public class SysUser implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;
    private String password;
    private String realName;
    private String roleCode; // ADMIN / USER
    private Integer status;  // 1-正常, 0-禁用

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}