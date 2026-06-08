package com.questforge.controller.common;

import com.questforge.service.AiEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 统一大模型驱动网关
 * 提供管理端的 AI 自动出题 和 用户端的 AI 私教伴读 功能
 */
@RestController
@RequestMapping("/api/ai-engine")
@RequiredArgsConstructor
public class AiEngineController {

    private final AiEngineService aiEngineService;

    /**
     * 用户端：呼叫 AI 伴学导师进行错题指导 (SSE流式响应)
     * 采用打字机效果，不阻塞主线程池，提升感官体验
     */
    @GetMapping(value = "/tutor/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter callAiTutor(
            @RequestParam Long questionId,
            @RequestParam String wrongUserAnswer) {

        // 设置 60 秒超时
        SseEmitter emitter = new SseEmitter(60000L);
        aiEngineService.streamAiTutorAnalysis(questionId, wrongUserAnswer, emitter);
        return emitter;
    }

    /**
     * 管理端：提供知识库材料，一键生成结构化题库 (SSE流式响应/或异步Webhook)
     */
    @PostMapping(value = "/admin/generate-questions", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter generateQuestionsByMaterial(@RequestBody String documentText) {
        SseEmitter emitter = new SseEmitter(120000L);
        aiEngineService.streamGenerateQuestions(documentText, emitter);
        return emitter;
    }
}