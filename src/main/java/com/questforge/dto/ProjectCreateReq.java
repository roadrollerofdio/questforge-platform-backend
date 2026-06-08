package com.questforge.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习计划级联创建请求参数 DTO
 */
@Data
public class ProjectCreateReq {
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer allowSwitchScreen;
    private Integer allowQuit;

    // 嵌套的关卡列表
    private List<StageReq> stages;

    @Data
    public static class StageReq {
        private String stageName;
        private Integer stageType; // 1-学习 2-考核
        private Integer sortOrder;
        private Integer passScoreThreshold;
        private Integer durationMins;

        // 关卡下挂载的试题/资料列表
        private List<ItemReq> items;
    }

    @Data
    public static class ItemReq {
        private Long itemId;
        private Integer itemType; // 1-资料 2-试题
        private Integer scoreWeight; // 若为试题，该题的分值
        private Integer sortNum;
    }
}