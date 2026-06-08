package com.questforge.dto;

import lombok.Data;
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
}