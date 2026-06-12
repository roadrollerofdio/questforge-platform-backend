package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("exam_record")
public class ExamRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long paperId;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private Integer totalScore;
    private Integer examStatus;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Integer isFavorited;
    @TableLogic
    private Integer isDeleted;
}
