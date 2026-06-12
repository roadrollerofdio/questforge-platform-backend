package com.questforge.controller.common;

import com.questforge.security.UserDetailsImpl;
import com.questforge.service.AiEngineService;
import com.questforge.service.DailyTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 统一大模型驱动网关
 * 提供管理端的 AI 自动出题 和 用户端的 AI 私教伴读 功能
 */
@RestController
@RequestMapping("/ai-engine")
@RequiredArgsConstructor
public class AiEngineController {

    private final AiEngineService aiEngineService;
    private final DailyTaskService dailyTaskService;

    private Long getCurrentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl userDetails) {
            return userDetails.getSysUser().getId();
        }
        return null;
    }

    private void triggerAskAiEvent() {
        Long userId = getCurrentUserIdOrNull();
        if (userId != null) {
            dailyTaskService.onEvent(userId, DailyTaskService.EVENT_ASK_AI);
        }
    }

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
        triggerAskAiEvent();
        aiEngineService.streamAiTutorAnalysis(questionId, wrongUserAnswer, emitter);
        return emitter;
    }

    /**
     * 用户端：关卡内 AI 教师悬浮窗 (SSE)
     * mode = hint(未作答给提示) / analysis(已作答给解析)
     */
    @GetMapping(value = "/tutor/stage-chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter stageTutorChat(
            @RequestParam Long questionId,
            @RequestParam(required = false) String userAnswer,
            @RequestParam(defaultValue = "hint") String mode) {

        SseEmitter emitter = new SseEmitter(60000L);
        triggerAskAiEvent();
        aiEngineService.streamStageTutor(questionId, userAnswer, mode, emitter);
        return emitter;
    }

    /**
     * 用户端：AI 教师页自由引导式对话 (SSE)
     * body: { "history": "[{role, content}...]" } 或直接传 JSON 数组字符串
     */
    @PostMapping(value = "/tutor/free-chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter freeChat(@RequestBody Map<String, String> body) {
        SseEmitter emitter = new SseEmitter(120000L);
        triggerAskAiEvent();
        aiEngineService.streamFreeChat(body.get("history"), emitter);
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

    /**
     * 管理端：AI 学情分析员，基于项目学情数据回答管理员的分析问题 (SSE流式响应)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/admin/analysis-chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter analysisChat(@RequestParam Long projectId,
                                   @RequestParam(required = false) String question) {
        SseEmitter emitter = new SseEmitter(120000L);
        aiEngineService.streamLearningDataAnalysis(projectId, question, emitter);
        return emitter;
    }
}