package com.questforge.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.Result;
import com.questforge.dto.StageDto;
import com.questforge.entity.Stage;
import com.questforge.entity.UserStageProgress;
import com.questforge.mapper.StageMapper;
import com.questforge.mapper.UserStageProgressMapper;
import com.questforge.service.UserStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户端：学习地图、沉浸式挑战与结算反馈
 */
@RestController
@RequestMapping("/api/user/stage")
@RequiredArgsConstructor
public class UserStageController {

    private final UserStageService userStageService;
    private final StageMapper stageMapper;
    private final UserStageProgressMapper progressMapper;

    private Long getCurrentUserId() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    /**
     * 核心接口 1：渲染学习大厅路线图 (Learning Path)
     */
    @GetMapping("/path/{projectId}")
    public Result<List<Map<String, Object>>> getLearningPath(@PathVariable Long projectId) {
        Long userId = getCurrentUserId();

        // 拉取该项目下的所有关卡定义
        List<Stage> stages = stageMapper.selectList(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getProjectId, projectId)
                .orderByAsc(Stage::getSortOrder));

        // 拉取当前用户在这些关卡上的生命周期进度
        List<UserStageProgress> progresses = progressMapper.selectList(new LambdaQueryWrapper<UserStageProgress>()
                .eq(UserStageProgress::getProjectId, projectId)
                .eq(UserStageProgress::getUserId, userId));

        Map<Long, UserStageProgress> progressMap = new HashMap<>();
        progresses.forEach(p -> progressMap.put(p.getStageId(), p));

        List<Map<String, Object>> resultList = new ArrayList<>();

        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i);
            UserStageProgress progress = progressMap.get(stage.getId());

            // 若进度表无记录，根据是否为第一关初始化状态
            int status = (progress != null) ? progress.getStatus() : (i == 0 ? 1 : 0);

            Map<String, Object> node = new HashMap<>();
            node.put("stageId", stage.getId().toString());
            node.put("stageName", stage.getStageName());
            node.put("stageType", stage.getStageType()); // 1-图文 2-考核
            node.put("sortOrder", stage.getSortOrder());
            node.put("passScoreThreshold", stage.getPassScoreThreshold());
            node.put("status", status); // 决定前端节点颜色与是否解锁
            node.put("currentScore", progress != null ? progress.getCurrentScore() : 0);

            resultList.add(node);
        }

        return Result.success(resultList);
    }

    /**
     * 核心接口 2：进入挑战关卡 (极速命中 Redis 预热快照)
     */
    @PostMapping("/{stageId}/enter")
    public Result<Map<String, Object>> enterStage(@PathVariable Long stageId) {
        Map<String, Object> stageData = userStageService.enterStage(stageId, getCurrentUserId());
        return Result.success(stageData);
    }

    /**
     * 核心接口 3：高频答题心跳防丢 (写入 Redis Hash)
     */
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody StageDto.HeartbeatReq req) {
        userStageService.saveHeartbeat(req, getCurrentUserId());
        return Result.success(null);
    }

    /**
     * 核心接口 4：发起强制结算 (触发行级锁排他 + MQ 异步判卷)
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submitStage(@RequestBody StageDto.SubmitReq req) {
        Long progressId = userStageService.submitStage(req, getCurrentUserId());
        Map<String, Object> res = new HashMap<>();
        res.put("progressId", progressId.toString());
        res.put("status", "结算中");
        return Result.success(res, "数据已同步至计算中心");
    }
}