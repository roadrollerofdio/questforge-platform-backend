package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学习路线图/考核项目主表
 */
@Data
@TableName("exam_paper")
public class LearningProject {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    @TableField("exam_start_time")
    private LocalDateTime startTime;

    @TableField("exam_end_time")
    private LocalDateTime endTime;

    @TableField("allow_switch_screen")
    private Integer allowSwitchScreen;

    @TableField("allow_quit")
    private Integer allowQuit;

    @TableField("paper_status")
    private Integer status;

    @TableField("total_score")
    private Integer totalScore = 100;

    @TableField("pass_score")
    private Integer passScore = 60;

    @TableField("duration_mins")
    private Integer durationMins = 120;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}