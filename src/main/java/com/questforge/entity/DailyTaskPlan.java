package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日任务计划: 某日选定的任务(每天 3 条)
 */
@Data
@TableName("daily_task_plan")
public class DailyTaskPlan {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private LocalDate planDate;
    private Long taskPoolId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
