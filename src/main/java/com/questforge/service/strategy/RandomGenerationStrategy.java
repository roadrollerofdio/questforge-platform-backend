package com.questforge.service.strategy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 智能随机组卷策略实现 (对应需求文档：规则随机抽题算法)
 */
@Slf4j
@Component("randomGenerationStrategy")
@RequiredArgsConstructor
public class RandomGenerationStrategy implements PaperGenerationStrategy {

    private final ExamQuestionMapper examQuestionMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generate(Long paperId, Long subjectId, Object ruleParams) {
        log.info(">>>> 开始执行智能随机组卷, paperId: {}, subjectId: {}", paperId, subjectId);

        // 1. 根据规则从题库中捞取符合条件的题目
        // 这里简化为捞取该科目下的所有试题，实际业务中 ruleParams 会指定单/多选的具体数量和难度
        LambdaQueryWrapper<ExamQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamQuestion::getSubjectId, subjectId)
                .eq(ExamQuestion::getIsDeleted, 0);
        List<ExamQuestion> allQuestions = examQuestionMapper.selectList(wrapper);

        if (allQuestions == null || allQuestions.isEmpty()) {
            throw new RuntimeException("该科目下题库不足，无法完成组卷");
        }

        // 2. 核心随机算法 (洗牌打乱)
        Collections.shuffle(allQuestions);

        // 3. 假设规则要求抽取最多 20 题
        int limit = Math.min(20, allQuestions.size());

        // 4. 生成试卷-试题关联记录并批量落库
        for (int i = 0; i < limit; i++) {
            ExamQuestion question = allQuestions.get(i);
            ExamPaperQuestion paperQuestion = new ExamPaperQuestion();
            paperQuestion.setPaperId(paperId);
            paperQuestion.setQuestionId(question.getId());
            paperQuestion.setSortNum(i + 1);

            // 根据题型设置默认分值：单选5分，多选10分
            int score = (question.getQuestionType() == 1) ? 5 : 10;
            paperQuestion.setItemScore(score);

            examPaperQuestionMapper.insert(paperQuestion);
        }

        log.info(">>>> 智能随机组卷完成，共抽取 {} 题", limit);
    }
}