package com.questforge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.dto.AdminDto;
import com.questforge.entity.SysConfig;
import com.questforge.mapper.SysConfigMapper;
import com.questforge.service.SysConfigService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private static final String KEY_AI_MODEL = "ai_model";
    private static final String KEY_AI_API_URL = "ai_api_url";
    private static final String KEY_AI_API_KEY = "ai_api_key";
    private static final String KEY_MQ_DELAY = "mq_delay";
    private static final String KEY_REDIS_CACHE = "enable_redis_cache";
    private static final String MASKED_KEY = "********";

    private final SysConfigMapper sysConfigMapper;

    @Value("${ai.api-key:}")
    private String defaultApiKey;

    @Value("${ai.api-url:https://api.deepseek.com/v1}")
    private String defaultApiUrl;

    @Value("${ai.model:deepseek-chat}")
    private String defaultModel;

    private volatile Map<String, String> cache = new HashMap<>();

    @PostConstruct
    public void init() {
        reloadCache();
    }

    private void reloadCache() {
        Map<String, String> map = new HashMap<>();
        map.put(KEY_AI_MODEL, defaultModel);
        map.put(KEY_AI_API_URL, defaultApiUrl);
        map.put(KEY_AI_API_KEY, defaultApiKey);
        map.put(KEY_MQ_DELAY, "10");
        map.put(KEY_REDIS_CACHE, "true");

        try {
            List<SysConfig> rows = sysConfigMapper.selectList(null);
            for (SysConfig row : rows) {
                if (StringUtils.hasText(row.getConfigKey()) && row.getConfigValue() != null) {
                    map.put(row.getConfigKey(), row.getConfigValue());
                }
            }
        } catch (Exception e) {
            log.warn("加载 sys_config 失败，使用 application.properties 默认值: {}", e.getMessage());
        }
        cache = map;
    }

    private void upsert(String key, String value) {
        SysConfig existing = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (existing != null) {
            existing.setConfigValue(value);
            sysConfigMapper.updateById(existing);
        } else {
            SysConfig cfg = new SysConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            sysConfigMapper.insert(cfg);
        }
    }

    @Override
    public AdminDto.SystemSettingsResp getSettings() {
        AdminDto.SystemSettingsResp resp = new AdminDto.SystemSettingsResp();
        resp.setAiModel(cache.getOrDefault(KEY_AI_MODEL, defaultModel));
        resp.setAiApiUrl(cache.getOrDefault(KEY_AI_API_URL, defaultApiUrl));
        String key = cache.getOrDefault(KEY_AI_API_KEY, defaultApiKey);
        resp.setAiApiKey(StringUtils.hasText(key) ? MASKED_KEY : "");
        resp.setMqDelay(Integer.parseInt(cache.getOrDefault(KEY_MQ_DELAY, "10")));
        resp.setEnableRedisCache(Boolean.parseBoolean(cache.getOrDefault(KEY_REDIS_CACHE, "true")));
        return resp;
    }

    @Override
    public void saveSettings(AdminDto.SystemSettingsReq req) {
        upsert(KEY_AI_MODEL, req.getAiModel());
        upsert(KEY_AI_API_URL, req.getAiApiUrl());
        if (StringUtils.hasText(req.getAiApiKey()) && !MASKED_KEY.equals(req.getAiApiKey())) {
            upsert(KEY_AI_API_KEY, req.getAiApiKey());
        }
        upsert(KEY_MQ_DELAY, String.valueOf(req.getMqDelay()));
        upsert(KEY_REDIS_CACHE, String.valueOf(req.getEnableRedisCache()));
        reloadCache();
        log.info("系统配置已更新");
    }

    @Override
    public String getAiApiKey() {
        return cache.getOrDefault(KEY_AI_API_KEY, defaultApiKey);
    }

    @Override
    public String getAiApiUrl() {
        return cache.getOrDefault(KEY_AI_API_URL, defaultApiUrl);
    }

    @Override
    public String getAiModel() {
        return cache.getOrDefault(KEY_AI_MODEL, defaultModel);
    }

    @Override
    public int getMqDelaySeconds() {
        try {
            return Integer.parseInt(cache.getOrDefault(KEY_MQ_DELAY, "10"));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    @Override
    public boolean isRedisCacheEnabled() {
        return Boolean.parseBoolean(cache.getOrDefault(KEY_REDIS_CACHE, "true"));
    }
}
