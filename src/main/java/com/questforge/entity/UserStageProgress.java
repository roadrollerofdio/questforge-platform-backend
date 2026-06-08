package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户关卡进度生命周期表 (核心状态机底表)
 */
@Data
@TableName("user_stage_progress")
public class UserStageProgress {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long projectId;
    private Long stageId;

    // 核心状态机: 0-未解锁, 1-已解锁待考, 2-进行中, 3-结算中, 4-已通关, 5-未通关
    private Integer status;

    private Integer currentScore;
    private LocalDateTime startTime;
    private LocalDateTime completeTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}