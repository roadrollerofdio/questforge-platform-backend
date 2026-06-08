package com.questforge.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 数据分析与报表 DTO
 */
public class AnalysisDto {

    @Data
    public static class PendingPaperResp {
        private String paperId;
        private String title;
        private Integer durationMins;
        private Integer totalScore;
    }

    @Data
    public static class ReportResp {
        private Integer totalScore;
        private String beatPercentage;
        private List<WrongQuestionDetail> wrongQuestions;

        @Data
        public static class WrongQuestionDetail {
            private String questionId;
            private String content;
            private String userAnswer;
            private String standardAnswer;
            private String analysis;
        }
    }

    @Data
    public static class LeaderboardResp {
        private Integer rank;
        private String userId;
        private String realName;
        private Integer score;
    }

    @Data
    public static class DashboardResp {
        private Integer paperStatus; // 0-草稿, 1-考试中, 2-已结束
        private Integer totalParticipants;
        private Double averageScore;
        private Integer highestScore;
        private String passRate;
        private Map<String, Integer> scoreDistribution;
    }
}