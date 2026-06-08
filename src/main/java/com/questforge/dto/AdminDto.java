package com.questforge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理端核心 DTO 聚合
 */
public class AdminDto {

    /**
     * 1. 手动组卷创建请求体
     */
    @Data
    public static class PaperCreateReq {
        @NotBlank(message = "试卷标题不能为空")
        private String title;
        @NotNull(message = "考试时长不能为空")
        private Integer durationMins;
        @NotNull(message = "及格分数不能为空")
        private Integer passScore;

        @NotNull(message = "考试开放起始时间不能为空")
        private LocalDateTime examStartTime;
        @NotNull(message = "考试开放结束时间不能为空")
        private LocalDateTime examEndTime;
        private Boolean allowSwitchScreen = true;
        private Boolean allowQuit = true;

        private List<PaperQuestionItem> questionList;

        @Data
        public static class PaperQuestionItem {
            private Long questionId;
            private Integer itemScore;
            private Integer sortNum;
        }
    }

    /**
     * 2. 规则智能抽题组卷请求体
     */
    @Data
    public static class PaperRandomCreateReq {
        @NotBlank(message = "试卷标题不能为空")
        private String title;
        @NotNull(message = "考试时长不能为空")
        private Integer durationMins;
        @NotNull(message = "及格分数不能为空")
        private Integer passScore;
        @NotNull(message = "所属科目ID不能为空")
        private Long subjectId;

        @NotNull(message = "考试开放起始时间不能为空")
        private LocalDateTime examStartTime;
        @NotNull(message = "考试开放结束时间不能为空")
        private LocalDateTime examEndTime;
        private Boolean allowSwitchScreen = true;
        private Boolean allowQuit = true;

        private Map<String, Object> ruleParams;
    }

    @Data
    public static class DashboardSummaryResp {
        private Long totalQuestions;
        private Long totalPapers;
        private Long totalExams;
        private Long activeUsers;
    }

    @Data
    public static class UserStatusUpdateReq {
        @NotNull(message = "用户ID不能为空")
        private Long id;
        @NotNull(message = "状态不能为空")
        private Integer status;
    }

    @Data
    public static class QuestionReq {
        private Long id;
        @NotNull(message = "所属科目不能为空")
        private Long subjectId;
        @NotNull(message = "题目类型不能为空")
        private Integer questionType;
        @NotBlank(message = "题干内容不能为空")
        private String content;
        @NotNull(message = "选项配置不能为空")
        private Object optionsJson;
        @NotBlank(message = "标准答案不能为空")
        private String standardAnswer;
        private Integer difficulty = 2;
        private String analysis;
    }

    @Data
    public static class AiGenerateReq {
        @NotBlank(message = "指令描述不能为空")
        private String questionTypeDesc;
        @NotBlank(message = "上下文知识点文本不能为空")
        private String documentText;
    }

    @Data
    public static class PaperPublishReq {
        @NotNull(message = "试卷ID不能为空")
        private Long id;
        private Boolean enableAntiCheat = true;
    }
}