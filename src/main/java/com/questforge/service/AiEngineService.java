package com.questforge.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiEngineService {
    void streamAiTutorAnalysis(Long questionId, String wrongUserAnswer, SseEmitter emitter);
    void streamGenerateQuestions(String documentText, SseEmitter emitter);
}