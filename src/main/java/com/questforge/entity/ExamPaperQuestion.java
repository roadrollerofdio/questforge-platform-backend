package com.questforge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

/**
 * 实体: exam_paper_question (试卷-试题关联明细表)
 */
@Data
@TableName("exam_paper_question")
public class ExamPaperQuestion implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long paperId;
    private Long questionId;
    private Integer sortNum;
    private Integer itemScore;
}