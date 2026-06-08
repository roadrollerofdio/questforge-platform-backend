package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学习路线图/考核项目主表
 */
@Data
@TableName("learning_project")
public class LearningProject {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // 防作弊配置
    private Integer allowSwitchScreen;
    private Integer allowQuit;

    // 状态: 0-草稿, 1-已发布, 2-已下线
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}