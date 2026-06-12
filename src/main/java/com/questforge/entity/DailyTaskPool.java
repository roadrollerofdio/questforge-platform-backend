package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 每日任务池
 */
@Data
@TableName("daily_task_pool")
public class DailyTaskPool {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    // STAGE_COMPLETE-完成关卡 / STAGE_PERFECT-无错通关 / ASK_AI-询问AI教师
    private String taskType;

    private Integer targetCount;
    private Integer gemReward;

    // 1-启用 0-停用
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
