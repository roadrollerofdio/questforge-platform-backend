package com.questforge.ai.service;

import com.questforge.ai.core.AiClient;
import com.questforge.ai.prompt.PromptManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 *  AI 智能助教与出题服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionService {

    private final AiClient aiClient;

    /**
     * @param documentText 培训文档/知识点文本
     * @param questionType 题型需求 (例如: "3道单选题和2道多选题")
     * @return 返回 JSON 格式的试题集合
     */
    public String generateQuestionsFromText(String documentText, String questionType) {
        log.info(">>>> 正在调用 AI 大模型接口生成考题...");
        log.info(">>>> 提取文本素材长度: {} 字符", documentText.length());

        // 1. 组装 Prompt
        String systemPrompt = String.format(PromptManager.GENERATE_QUESTION_SYSTEM, questionType);
        String userMessage = "请根据以下文本内容出题：\n" + documentText;

        // 2. 调用核心客户端获取结果
        String aiResult = aiClient.callChatApi(systemPrompt, userMessage);

        if (StringUtils.hasText(aiResult)) {
            log.info(">>>> AI 真实接口生成试题成功。");
            return aiResult.trim();
        }

        // 3. 兜底策略返回精准备用数据
        log.warn(">>>> AI 接口调用失败或未配置 apiKey，将使用 Mock 数据兜底返回。");
        return """
            [
              {
                "content": "<p>微服务架构中，熔断器的主要作用是什么？</p>",
                "optionsJson": [
                  {"key": "A", "val": "增加系统并发量"},
                  {"key": "B", "val": "防止雪崩效应，保护系统"},
                  {"key": "C", "val": "加快数据库查询速度"},
                  {"key": "D", "val": "替代负载均衡器"}
                ],
                "standardAnswer": "B",
                "analysis": "熔断器在下游服务不可用时，直接返回降级策略，从而防止雪崩效应。"
              }
            ]
            """;
    }


    public String diagnoseWrongAnswer(String questionContent, String standardAnswer, String wrongUserAnswer) {
        String systemPrompt = PromptManager.DIAGNOSE_WRONG_ANSWER_SYSTEM;
        String userMessage = PromptManager.buildDiagnoseUserMessage(questionContent, standardAnswer, wrongUserAnswer);

        String aiResult = aiClient.callChatApi(systemPrompt, userMessage);

        if (StringUtils.hasText(aiResult)) {
            return aiResult.trim();
        }

        return " AI老师点评：你选了 '" + wrongUserAnswer + "'，说明你对相关概念的边界还有些模糊。这道题考察的核心点在于特定场景下的最优解，建议重新回顾一下相关基础，加油！";
    }
}