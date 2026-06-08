package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体: exam_question (试题主表)
 */
@Data
@TableName(value = "exam_question", autoResultMap = true)
public class ExamQuestion implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long subjectId;
    private Integer questionType; // 1:单选, 2:多选
    private String content;

    // 利用 MyBatis-Plus 的 TypeHandler 自动处理 JSON 转换
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Object optionsJson;

    private String standardAnswer;
    private Integer difficulty; // 1易 2中 3难
    private String analysis;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}