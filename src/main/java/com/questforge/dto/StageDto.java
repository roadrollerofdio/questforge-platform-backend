package com.questforge.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 核心流转 DTO 对象
 */
public class StageDto {

    /**
     * 前端答题心跳上报请求
     */
    @Data
    public static class HeartbeatReq {
        private Long stageId;
        private Long questionId;
        private String userAnswer;
    }

    /**
     * 关卡提交请求
     */
    @Data
    public static class SubmitReq {
        private Long stageId;
        private Boolean forceSubmit;
    }

    /**
     * 投递至 RabbitMQ 的异步结算消息体
     */
    @Data
    public static class MqSubmitMessage implements Serializable {
        private Long progressId;
        private Long userId;
        private Long stageId;
        private Long projectId;
        private Boolean forceSubmit;
        private Long submitTimestamp;
    }
}