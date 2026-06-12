package com.questforge.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiEngineService {
    void streamAiTutorAnalysis(Long questionId, String wrongUserAnswer, SseEmitter emitter);
    void streamGenerateQuestions(String documentText, SseEmitter emitter);
    void streamLearningDataAnalysis(Long projectId, String question, SseEmitter emitter);

    /**
     * 关卡内 AI 教师: mode = hint(未作答给提示, 不泄露答案) / analysis(已作答给解析)
     */
    void streamStageTutor(Long questionId, String userAnswer, String mode, SseEmitter emitter);

    /**
     * AI 教师页自由引导式对话, historyJson 为 [{role, content}] 数组
     */
    void streamFreeChat(String historyJson, SseEmitter emitter);
}