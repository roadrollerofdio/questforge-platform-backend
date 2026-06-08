//package com.questforge.entity;
//
//import com.baomidou.mybatisplus.annotation.*;
//import lombok.Data;
//import java.io.Serializable;
//import java.time.LocalDateTime;
//
///**
// * 实体: exam_paper (试卷配置表)
// */
//@Data
//@TableName("exam_paper")
//public class ExamPaper implements Serializable {
//    @TableId(type = IdType.ASSIGN_ID)
//    private Long id;
//
//    private String title;
//    private Integer totalScore;
//    private Integer passScore;
//    private Integer durationMins;
//    private Integer paperStatus; // 0-草稿, 1-已发布, 2-已下线
//
//    // --- 新增字段 ---
//    private LocalDateTime examStartTime;
//    private LocalDateTime examEndTime;
//    private Integer allowSwitchScreen; // 1允许 0禁止
//    private Integer allowQuit;         // 1允许 0禁止
//
//    @TableField(fill = FieldFill.INSERT)
//    private LocalDateTime createTime;
//
//    @TableField(fill = FieldFill.INSERT_UPDATE)
//    private LocalDateTime updateTime;
//
//    @TableLogic
//    private Integer isDeleted;
//}