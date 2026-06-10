package com.questforge.service;

import com.questforge.dto.StageDto;
import java.util.Map;

public interface UserStageService {
    Map<String, Object> enterStage(Long stageId, Long userId);
    void saveHeartbeat(StageDto.HeartbeatReq req, Long userId);
    Long submitStage(StageDto.SubmitReq req, Long userId);
}