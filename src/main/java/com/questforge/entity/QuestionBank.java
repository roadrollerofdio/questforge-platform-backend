package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "question_bank", autoResultMap = true)
public class QuestionBank implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long subjectId;
    private Integer type; // 1-单选, 2-多选, 3-判断
    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object optionsJson;

    private String answer;
    private Integer difficulty;
    private String aiAnalysis; // AI伴学预置解析

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}