package com.questforge.service.strategy;

/**
 * 策略模式接口：组卷算法
 */
public interface PaperGenerationStrategy {

    /**
     * 执行组卷
     * @param paperId 试卷ID
     * @param subjectId 科目ID
     * @param ruleParams 组卷规则(如题型、数量，可扩展为复杂对象)
     */
    void generate(Long paperId, Long subjectId, Object ruleParams);
}