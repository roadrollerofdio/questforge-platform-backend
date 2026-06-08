package com.questforge.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.questforge.entity.QuestionBank;
import com.questforge.mapper.QuestionBankMapper;
import com.questforge.service.AiEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    // 在 application.yml 中配置真实的 API_KEY 和 URL (如 https://api.deepseek.com/v1)
    @Value("${ai.api-key:sk-your-default-key}")
    private String apiKey;

    @Value("${ai.api-url:https://api.openai.com/v1}")
    private String apiUrl;

    @Value("${ai.model:gpt-3.5-turbo}")
    private String modelName;

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

            // 1. 组装符合 OpenAI 规范的请求 Payload
            JSONObject payload = new JSONObject();
            payload.set("model", modelName);
            payload.set("stream", true); // 开启流式输出
            payload.set("temperature", 0.7);

            JSONArray messages = new JSONArray();
            messages.add(new JSONObject()
                    .set("role", "system")
                    .set("content", "你现在是 QuestForge 平台的金牌私教导师。学员做错了题目。请用循循善诱的口吻，不要直接给出标准答案，而是指出他的逻辑误区，引导他思考。"));

            String userContent = String.format("题干：【%s】\n标准答案是：【%s】\n但我选了：【%s】\n请指导我。",
                    q.getContent(), q.getAnswer(), wrongUserAnswer);
            messages.add(new JSONObject().set("role", "user").set("content", userContent));

            payload.set("messages", messages);

            log.info("发起 LLM 真实请求: {}", apiUrl);

            // 2. 采用 WebClient 发起真实的非阻塞 HTTP 请求
            WebClient webClient = webClientBuilder.baseUrl(apiUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Flux<ServerSentEvent<String>> eventStream = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(payload.toString())
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

            // 3. 订阅大模型流，并转发给前端 SseEmitter
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
                                    // 转发内容片段给前端
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
                            emitter.send(SseEmitter.event().data("\n[AI 导师暂时离开了，请检查网络或 API 配置]"));
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

        } catch (Exception e) {
            log.error("AI 伴学环境初始化异常", e);
            emitter.completeWithError(e);
        }
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