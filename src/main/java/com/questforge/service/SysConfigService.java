package com.questforge.service;

import com.questforge.dto.AdminDto;

public interface SysConfigService {

    AdminDto.SystemSettingsResp getSettings();

    void saveSettings(AdminDto.SystemSettingsReq req);

    String getAiApiKey();

    String getAiApiUrl();

    String getAiModel();

    int getMqDelaySeconds();

    boolean isRedisCacheEnabled();
}
