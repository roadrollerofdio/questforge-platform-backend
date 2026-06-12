package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日任务进度
 */
@Data
@TableName("user_daily_task")
public class UserDailyTask {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private LocalDate planDate;
    private Long taskPoolId;

    private Integer progress;
    private Integer isCompleted;

    // 宝石是否已发放(防重复发奖)
    private Integer isRewarded;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
