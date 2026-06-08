package com.questforge.service;

import java.util.Map;

/**
 * 考试核心业务层接口
 */
public interface ExamService {

    /**
     * 拉取试卷结构 (走 Redis 缓存，绝不返回标准答案)
     */
    Map<String, Object> getPaperDetail(Long paperId, Long userId);

    /**
     * 记录答题心跳 (存入 Redis Hash)
     */
    void saveHeartbeat(Long paperId, Long questionId, String userAnswer, Long userId);

    /**
     * 提交试卷 (发送至 MQ，不等待阅卷结果)
     */
    Long submitPaper(Long paperId, Long userId, boolean forceSubmit);
}