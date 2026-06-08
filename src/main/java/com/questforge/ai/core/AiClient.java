package com.questforge.ai.core;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AI 大模型通用请求客户端
 */
@Slf4j
@Component
public class AiClient {

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai.model:deepseek-chat}")
    private String model;

    /**
     * 发送 ChatCompletion 请求
     * @param systemPrompt 系统角色预设
     * @param userMessage 用户输入内容
     * @return AI 回复的文本
     */
    public String callChatApi(String systemPrompt, String userMessage) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("AI 接口未配置 apiKey，跳过真实请求。");
            return null;
        }

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model);
        requestBody.set("temperature", 0.7);

        JSONArray messages = new JSONArray();
        messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));
        messages.add(new JSONObject().set("role", "user").set("content", userMessage));
        requestBody.set("messages", messages);

        try (HttpResponse response = HttpRequest.post(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(30000)
                .execute()) {

            if (response.isOk()) {
                JSONObject resJson = JSONUtil.parseObj(response.body());
                JSONArray choices = resJson.getJSONArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    return choices.getJSONObject(0).getJSONObject("message").getStr("content");
                }
            } else {
                log.error("AI 接口调用状态异常: HTTP {}, Body: {}", response.getStatus(), response.body());
            }
        } catch (Exception e) {
            log.error("AI 接口请求发送失败", e);
        }
        return null;
    }
}