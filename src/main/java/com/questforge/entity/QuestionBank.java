package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName(value = "exam_question", autoResultMap = true)
public class QuestionBank implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long subjectId;

    @TableField("question_type")
    private Integer type;

    private String content;

    @TableField(value = "options_json", typeHandler = JacksonTypeHandler.class)
    @com.fasterxml.jackson.annotation.JsonProperty("options")
    private Object optionsJson;

    @TableField("standard_answer")
    private String answer;

    private Integer difficulty;

    @TableField("analysis")
    private String aiAnalysis;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}