//package com.questforge.entity;
//
//import com.baomidou.mybatisplus.annotation.*;
//import lombok.Data;
//import java.io.Serializable;
//import java.time.LocalDateTime;
//
///**
// * 实体: exam_record (考试记录主表 - 千万级)
// */
//@Data
//@TableName("exam_record")
//public class ExamRecord implements Serializable {
//    @TableId(type = IdType.ASSIGN_ID)
//    private Long id;
//
//    private Long userId;
//    private Long paperId;
//    private LocalDateTime startTime;
//    private LocalDateTime submitTime;
//    private Integer totalScore;
//    private Integer examStatus; // 0-考试中, 1-已交卷
//
//    // --- 新增：考生个人管理状态 ---
//    private Integer isFavorited; // 1-收藏 0-不收藏
//
//    @TableLogic
//    private Integer isDeleted; // 供用户端逻辑删除
//
//    @TableField(fill = FieldFill.INSERT)
//    private LocalDateTime createTime;
//}