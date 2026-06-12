package com.questforge.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.questforge.entity.QuestionBank;
import com.questforge.mapper.QuestionBankMapper;
import com.questforge.service.AiEngineService;
import com.questforge.service.AnalysisService;
import com.questforge.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * 核心大模型(LLM)集成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiEngineServiceImpl implements AiEngineService {

    private final QuestionBankMapper questionBankMapper;
    private final WebClient.Builder webClientBuilder;
    private final SysConfigService sysConfigService;
    private final AnalysisService analysisService;

    /**
     * 利用 WebFlux 接收模型数据流，并透传给客户端 SseEmitter
     */
    @Override
    @Async("aiExecutor")
    public void streamAiTutorAnalysis(Long questionId, String wrongUserAnswer, SseEmitter emitter) {
        try {
            QuestionBank q = questionBankMapper.selectById(questionId);
            if (q == null) {
                emitter.send(SseEmitter.event().data("【系统异常】未找到题库数据。"));
                emitter.complete();
                return;
            }

            String systemPrompt = "你现在是 QuestForge 平台的金牌私教导师。学员做错了题目。请用循循善诱的口吻，不要直接给出标准答案，而是指出他的逻辑误区，引导他思考。";
            String userContent = String.format("题干：【%s】\n标准答案是：【%s】\n但我选了：【%s】\n请指导我。",
                    q.getContent(), q.getAnswer(), wrongUserAnswer);

            streamLlmChat(systemPrompt, userContent, emitter);
        } catch (Exception e) {
            log.error("AI 伴学环境初始化异常", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 管理端 AI 分析员：基于项目学情数据回答管理员的分析问题
     */
    @Override
    @Async("aiExecutor")
    public void streamLearningDataAnalysis(Long projectId, String question, SseEmitter emitter) {
        try {
            String dataContext = analysisService.buildAiAnalysisContext(projectId);

            String systemPrompt = "你是 QuestForge 平台的资深教学数据分析师，服务对象是平台管理员。"
                    + "下面提供了某个学习项目的真实学情数据（整体指标、关卡序列、每位学员的各关卡得分与错题摘要）。"
                    + "请基于这些数据进行专业、客观的分析：指出整体掌握情况、薄弱关卡与高频错题知识点、需要重点关注的学员，"
                    + "并给出可落地的教学改进建议。回答使用简洁的中文，可适当使用条目化结构。\n\n"
                    + "=== 学情数据 ===\n" + dataContext;

            String userContent = (question == null || question.isBlank())
                    ? "请对该项目的整体学习情况做一次全面分析。"
                    : question;

            streamLlmChat(systemPrompt, userContent, emitter);
        } catch (Exception e) {
            log.error("AI 学情分析初始化异常", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 关卡内 AI 教师双模式: 未作答给提示(不泄露答案), 已作答给完整解析
     */
    @Override
    @Async("aiExecutor")
    public void streamStageTutor(Long questionId, String userAnswer, String mode, SseEmitter emitter) {
        try {
            QuestionBank q = questionBankMapper.selectById(questionId);
            if (q == null) {
                emitter.send(SseEmitter.event().data("【系统异常】未找到题库数据。"));
                emitter.complete();
                return;
            }

            String systemPrompt;
            String userContent;

            if ("hint".equals(mode)) {
                systemPrompt = "你是一位多邻国风格的友善 AI 教师。学员正在做题但还没有作答。"
                        + "请针对题目给出一条启发式提示：点拨思考方向、回顾相关知识点，"
                        + "但【绝对不能】直接或间接透露正确答案及选项。提示控制在 100 字以内，语气轻松鼓励。";
                userContent = String.format("题干：【%s】\n选项：【%s】\n请给我一点提示。",
                        q.getContent(), q.getOptionsJson() == null ? "无" : q.getOptionsJson());
            } else {
                systemPrompt = "你是一位多邻国风格的友善 AI 教师。学员已经作答完毕。"
                        + "请给出这道题的完整解析：先指出学员答案是否正确，再讲清楚正确答案背后的原理与解题思路，"
                        + "最后用一句话鼓励学员。语气轻松友好，使用简洁中文。";
                userContent = String.format("题干：【%s】\n选项：【%s】\n标准答案：【%s】\n我的作答：【%s】\n请为我解析这道题。",
                        q.getContent(),
                        q.getOptionsJson() == null ? "无" : q.getOptionsJson(),
                        q.getAnswer(),
                        userAnswer == null || userAnswer.isBlank() ? "(空)" : userAnswer);
            }

            streamLlmChat(systemPrompt, userContent, emitter);
        } catch (Exception e) {
            log.error("关卡 AI 教师初始化异常", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * AI 教师页: 自由引导式学习对话(带上下文历史)
     */
    @Override
    @Async("aiExecutor")
    public void streamFreeChat(String historyJson, SseEmitter emitter) {
        try {
            String systemPrompt = "你是 QuestForge 学习平台的专属 AI 教师，风格类似多邻国的吉祥物老师：热情、幽默、循循善诱。"
                    + "你的职责是通过对话引导学员学习：根据学员的提问讲解知识点、出小练习巩固、用类比让概念更易懂，"
                    + "并在学员答对时给予称赞、答错时温柔纠正。每次回复保持简洁(200字以内)，"
                    + "并在结尾用一个问题或小挑战引导学员继续学习。始终使用中文。";

            JSONArray history = JSONUtil.parseArray(historyJson == null || historyJson.isBlank() ? "[]" : historyJson);

            JSONArray messages = new JSONArray();
            messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));
            // 仅保留最近 20 条历史防止上下文超长
            int start = Math.max(0, history.size() - 20);
            for (int i = start; i < history.size(); i++) {
                JSONObject msg = history.getJSONObject(i);
                String role = "assistant".equals(msg.getStr("role")) ? "assistant" : "user";
                messages.add(new JSONObject().set("role", role).set("content", msg.getStr("content")));
            }

            streamLlmMessages(messages, emitter);
        } catch (Exception e) {
            log.error("AI 教师自由对话初始化异常", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 通用 LLM 流式对话：组装 OpenAI 规范 Payload，经 WebClient 订阅并透传给 SseEmitter
     */
    private void streamLlmChat(String systemPrompt, String userContent, SseEmitter emitter) {
        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));
        messages.add(new JSONObject().set("role", "user").set("content", userContent));
        streamLlmMessages(messages, emitter);
    }

    /**
     * 支持多轮消息的 LLM 流式请求
     */
    private void streamLlmMessages(JSONArray messages, SseEmitter emitter) {
        String apiKey = sysConfigService.getAiApiKey();
        String apiUrl = sysConfigService.getAiApiUrl();
        String modelName = sysConfigService.getAiModel();

        JSONObject payload = new JSONObject();
        payload.set("model", modelName);
        payload.set("stream", true);
        payload.set("temperature", 0.7);
        payload.set("messages", messages);

        log.info("发起 LLM 真实请求: {}", apiUrl);

        WebClient webClient = webClientBuilder.baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Flux<ServerSentEvent<String>> eventStream = webClient.post()
                .uri("/chat/completions")
                .bodyValue(payload.toString())
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

        eventStream.subscribe(
                content -> {
                    try {
                        String data = content.data();
                        if ("[DONE]".equals(data)) return;

                        // 解析 OpenAI 格式的流式 Chunk
                        JSONObject chunk = JSONUtil.parseObj(data);
                        JSONArray choices = chunk.getJSONArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                            String textContent = delta.getStr("content");
                            if (textContent != null) {
                                emitter.send(SseEmitter.event().data(textContent));
                            }
                        }
                    } catch (Exception e) {
                        log.error("解析流式Chunk异常", e);
                    }
                },
                error -> {
                    log.error("AI WebClient 请求发生异常", error);
                    try {
                        emitter.send(SseEmitter.event().data("\n[AI 暂时离开了，请检查网络或 API 配置]"));
                    } catch (IOException e) {
                        // ignore
                    }
                    emitter.completeWithError(error);
                },
                () -> {
                    log.info("AI 流式输出正常结束");
                    emitter.complete();
                }
        );
    }

    @Override
    @Async("aiExecutor")
    public void streamGenerateQuestions(String documentText, SseEmitter emitter) {
        // 真实业务中同样使用 WebClient 请求，为了文档简洁，不重复列出相同的 WebClient 代码块。
        try {
            emitter.send(SseEmitter.event().data("功能建设中，核心实现参考 Tutor 方法的 WebClient 代码..."));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}