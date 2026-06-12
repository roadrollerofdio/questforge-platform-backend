package com.questforge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 管理端核心 DTO 聚合
 */
public class AdminDto {

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
        private Integer type;
        @NotBlank(message = "题干内容不能为空")
        private String content;
        private Object options;
        @NotBlank(message = "标准答案不能为空")
        private String answer;
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
    public static class SubjectReq {
        private Long id;
        private Long parentId;
        @NotBlank(message = "科目名称不能为空")
        private String name;
    }

    @Data
    public static class SubjectTreeResp {
        private Long id;
        private Long parentId;
        private String name;
        private List<SubjectTreeResp> children;
    }

    @Data
    public static class DashboardSummaryResp {
        private Long totalQuestions;
        private Long totalPapers;
        private Long totalExams;
        private Long activeUsers;
    }

    @Data
    public static class SystemSettingsReq {
        @NotBlank(message = "AI 模型不能为空")
        private String aiModel;
        private String aiApiUrl;
        private String aiApiKey;
        @NotNull(message = "MQ 延迟不能为空")
        private Integer mqDelay;
        @NotNull(message = "Redis 缓存开关不能为空")
        private Boolean enableRedisCache;
    }

    @Data
    public static class SystemSettingsResp {
        private String aiModel;
        private String aiApiUrl;
        private String aiApiKey;
        private Integer mqDelay;
        private Boolean enableRedisCache;
    }

    @Data
    public static class StageItemResp {
        private Long refId;
        private Long itemId;
        private Integer itemType;
        private Integer scoreWeight;
        private Integer sortNum;
        private String content;
        private Integer questionType;
    }
}