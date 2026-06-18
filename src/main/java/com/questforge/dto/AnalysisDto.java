package com.questforge.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 学习路线与关卡数据分析 DTO
 */
public class AnalysisDto {

    /**
     * 学习项目整体宏观指标
     */
    @Data
    public static class ProjectStatsResp {
        private Long totalParticipants;
        private Double averageScore;
        private String passRate;
        private Map<String, Long> scoreDistribution; // 分数段分布
    }

    /**
     * 学习排行榜条目
     */
    @Data
    public static class LeaderboardResp {
        private Integer rank;
        private Long userId;
        private String username;
        private String realName;
        private Integer score;
    }

    /**
     * 项目学员学习情况总览 (含每个学员的关卡明细)
     */
    @Data
    public static class ProjectLearningDetailResp {
        private Long projectId;
        private String projectTitle;
        private Long totalParticipants;
        private Double averageScore;
        private List<StageMetaResp> stages;          // 项目关卡序列 (表头用)
        private List<StudentLearningResp> students;  // 每个学员的学习明细
    }

    /**
     * 关卡元信息
     */
    @Data
    public static class StageMetaResp {
        private Long stageId;
        private String stageName;
        private Integer sortOrder;
        private Integer stageType;
        private Integer passScoreThreshold;
    }

    /**
     * 单个学员在某项目中的学习情况
     */
    @Data
    public static class StudentLearningResp {
        private Long userId;
        private String username;
        private String realName;
        private Double averageScore;     // 该学员各关卡平均分
        private Integer passedStages;    // 已通关数
        private Integer totalStages;     // 项目关卡总数
        private Integer totalWrongCount; // 累计错题数
        private List<StudentStageResp> stages;
    }

    /**
     * 学员在某个关卡的成绩与错题
     */
    @Data
    public static class StudentStageResp {
        private Long stageId;
        private String stageName;
        private Integer status; // 0-未解锁,1-已解锁待考,2-进行中,3-结算中,4-已通关,5-未通关
        private Integer score;
        private List<WrongQuestionResp> wrongQuestions;
    }

    /**
     * 错题明细
     */
    @Data
    public static class WrongQuestionResp {
        private Long questionId;
        private String content;
        private String userAnswer;
        private String standardAnswer;
        private String analysis;
    }
}