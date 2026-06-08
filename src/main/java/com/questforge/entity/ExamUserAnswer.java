package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

/**
 * 实体: exam_user_answer (考生答题明细表 - 十亿级)
 */
@Data
@TableName("exam_user_answer")
public class ExamUserAnswer implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long recordId;
    private Long questionId;
    private String userAnswer;
    private Integer isCorrect; // 0-错, 1-对, 2-半对
    private Integer actualScore;
}