package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友关系与申请
 */
@Data
@TableName("friend_relation")
public class FriendRelation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long fromUserId;
    private Long toUserId;

    // 0-待处理 1-已接受 2-已拒绝
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
