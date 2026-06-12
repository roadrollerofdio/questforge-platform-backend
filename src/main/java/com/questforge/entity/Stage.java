package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 关卡定义表
 */
@Data
@TableName("stage")
public class Stage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long projectId;
    private String stageName;

    // 1-图文学习关卡, 2-考核挑战关卡
    private Integer stageType;

    // 关卡在路线图中的先后顺序
    private Integer sortOrder;

    // 考核及格门槛
    private Integer passScoreThreshold;
    private Integer durationMins;
    private Integer totalScore;

    // 通关奖励宝石数(管理员设置)
    private Integer gemReward;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}