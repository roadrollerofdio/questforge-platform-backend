package com.questforge.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.RedisConsts;
import com.questforge.common.Result;
import com.questforge.dto.StageDto;
import com.questforge.entity.Stage;
import com.questforge.entity.UserAnswer;
import com.questforge.entity.UserStageProgress;
import com.questforge.mapper.StageMapper;
import com.questforge.mapper.UserAnswerMapper;
import com.questforge.mapper.UserStageProgressMapper;
import com.questforge.security.UserDetailsImpl;
import com.questforge.service.UserProfileService;
import com.questforge.service.UserStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户端：学习地图、沉浸式挑战与结算反馈
 */
@RestController
@RequestMapping("/user/stage")
@RequiredArgsConstructor
public class UserStageController {

    private final UserStageService userStageService;
    private final StageMapper stageMapper;
    private final UserStageProgressMapper progressMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final UserProfileService userProfileService;
    private final RedisTemplate<String, Object> redisTemplate;

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getSysUser().getId();
        }
        throw new IllegalStateException("未登录或会话已失效");
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

    /**
     * 核心接口 5：关卡结算页数据 (前端轮询直到判分完成)
     * 返回得分总结 + 获得宝石 + 项目排行榜(带 userId 供发好友申请)
     */
    @GetMapping("/result/{progressId}")
    public Result<Map<String, Object>> getStageResult(@PathVariable Long progressId) {
        Long userId = getCurrentUserId();

        UserStageProgress progress = progressMapper.selectById(progressId);
        if (progress == null || !progress.getUserId().equals(userId)) {
            return Result.error(400, "未找到挑战记录");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("progressId", progressId.toString());
        data.put("status", progress.getStatus()); // 3-结算中 4-通关 5-未通关

        if (progress.getStatus() == 3) {
            return Result.success(data); // 仍在判分, 前端继续轮询
        }

        Stage stage = stageMapper.selectById(progress.getStageId());
        data.put("stageName", stage != null ? stage.getStageName() : "未知关卡");
        data.put("projectId", progress.getProjectId().toString());
        data.put("score", progress.getCurrentScore() == null ? 0 : progress.getCurrentScore());
        data.put("totalScore", stage != null && stage.getTotalScore() != null ? stage.getTotalScore() : 0);
        data.put("passed", progress.getStatus() == 4);

        // 对错统计
        List<UserAnswer> answers = userAnswerMapper.selectList(new LambdaQueryWrapper<UserAnswer>()
                .eq(UserAnswer::getProgressId, progressId));
        long correctCount = answers.stream().filter(a -> a.getIsCorrect() != null && a.getIsCorrect() == 1).count();
        data.put("correctCount", correctCount);
        data.put("wrongCount", answers.size() - correctCount);
        data.put("totalQuestions", answers.size());

        // 本次结算获得的宝石(结算引擎写入 Redis)
        Object gems = redisTemplate.opsForValue().get(RedisConsts.STAGE_GEMS_PREFIX + progressId);
        data.put("gemsEarned", gems == null ? 0 : Integer.parseInt(gems.toString()));

        // 项目排行榜 Top 20 (带 userId / 昵称 / 形象, 供加好友与头像渲染)
        data.put("leaderboard", buildLeaderboard(progress.getProjectId(), userId));

        return Result.success(data);
    }

    private List<Map<String, Object>> buildLeaderboard(Long projectId, Long currentUserId) {
        String key = RedisConsts.LEADERBOARD_PREFIX + projectId;
        Set<ZSetOperations.TypedTuple<Object>> topUsers =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 19);

        List<Map<String, Object>> leaderboard = new ArrayList<>();
        if (topUsers == null) return leaderboard;

        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> tuple : topUsers) {
            Long uid = Long.valueOf(tuple.getValue().toString());
            Map<String, Object> row = userProfileService.buildBrief(uid);
            row.put("rank", rank++);
            row.put("score", tuple.getScore() != null ? tuple.getScore().intValue() : 0);
            row.put("isSelf", uid.equals(currentUserId));
            leaderboard.add(row);
        }
        return leaderboard;
    }
}