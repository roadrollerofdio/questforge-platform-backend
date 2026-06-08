package com.questforge.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.questforge.common.RedisConsts;
import com.questforge.config.RabbitMQConfig;
import com.questforge.dto.StageDto;
import com.questforge.entity.LearningProject;
import com.questforge.entity.Stage;
import com.questforge.entity.UserStageProgress;
import com.questforge.mapper.LearningProjectMapper;
import com.questforge.mapper.StageMapper;
import com.questforge.mapper.UserStageProgressMapper;
import com.questforge.service.UserStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserStageServiceImpl implements UserStageService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserStageProgressMapper progressMapper;
    private final LearningProjectMapper projectMapper;
    private final StageMapper stageMapper;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 进入挑战关卡 (极速从 Redis 拉取预热结构)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> enterStage(Long stageId, Long userId) {
        Stage stage = stageMapper.selectById(stageId);
        if (stage == null) throw new RuntimeException("关卡不存在");

        LearningProject project = projectMapper.selectById(stage.getProjectId());
        LocalDateTime now = LocalDateTime.now();
        if (project.getStartTime() != null && now.isBefore(project.getStartTime())) {
            throw new RuntimeException("该学习计划尚未开放");
        }
        if (project.getEndTime() != null && now.isAfter(project.getEndTime())) {
            throw new RuntimeException("该学习计划已结束");
        }

        // 校验关卡解锁状态
        UserStageProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<UserStageProgress>()
                .eq(UserStageProgress::getUserId, userId).eq(UserStageProgress::getStageId, stageId));

        if (progress == null || progress.getStatus() == 0) {
            throw new RuntimeException("当前关卡尚未解锁，请先完成前置关卡！");
        }

        // 状态流转：1(已解锁) -> 2(进行中)
        if (progress.getStatus() == 1) {
            progress.setStatus(2);
            progress.setStartTime(now);
            progressMapper.updateById(progress);
        } else if (progress.getStatus() >= 3) {
            throw new RuntimeException("该关卡已结算，无法重复挑战");
        }
        // 如果是 2(进行中) 则为断线重连，直接放行

        // 从 Redis 拉取预热的关卡快照（不含标准答案）
        String stageCacheKey = RedisConsts.STAGE_INFO_PREFIX + stageId;
        Object stageJsonStr = redisTemplate.opsForValue().get(stageCacheKey);
        if (stageJsonStr == null) throw new RuntimeException("关卡数据缓存异常，请联系管理员");

        Map<String, Object> stageData = JSONUtil.parseObj((String) stageJsonStr);
        stageData.put("allowSwitchScreen", project.getAllowSwitchScreen() == 1);
        stageData.put("allowQuit", project.getAllowQuit() == 1);
        stageData.put("serverTime", System.currentTimeMillis() / 1000);

        return stageData;
    }

    /**
     * 高频心跳保存进度 (纯 Redis 操作，防掉线)
     */
    @Override
    public void saveHeartbeat(StageDto.HeartbeatReq req, Long userId) {
        String sessionKey = RedisConsts.getSessionKey(req.getStageId(), userId);
        if (req.getUserAnswer() == null || req.getUserAnswer().trim().isEmpty()) {
            redisTemplate.opsForHash().delete(sessionKey, req.getQuestionId().toString());
        } else {
            redisTemplate.opsForHash().put(sessionKey, req.getQuestionId().toString(), req.getUserAnswer());
        }
        redisTemplate.expire(sessionKey, 24, TimeUnit.HOURS);
    }

    /**
     * 发起强制交卷结算 (状态排他 + MQ削峰)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitStage(StageDto.SubmitReq req, Long userId) {
        Long stageId = req.getStageId();
        String lockKey = RedisConsts.SUBMIT_LOCK_PREFIX + stageId + ":" + userId;
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(isLocked)) throw new RuntimeException("数据正在上传中，请勿连击");

        try {
            UserStageProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<UserStageProgress>()
                    .eq(UserStageProgress::getUserId, userId).eq(UserStageProgress::getStageId, stageId));

            if (progress == null) throw new RuntimeException("未找到挑战记录");

            // 【核心防御】：乐观抢占状态机，确保绝对幂等。2(进行中) -> 3(结算中)
            int updated = progressMapper.update(null, new LambdaUpdateWrapper<UserStageProgress>()
                    .set(UserStageProgress::getStatus, 3)
                    .set(UserStageProgress::getCompleteTime, LocalDateTime.now())
                    .eq(UserStageProgress::getId, progress.getId())
                    .eq(UserStageProgress::getStatus, 2));

            if (updated == 0) throw new RuntimeException("挑战状态异常或已结算完毕");

            // 投递异步结算指令至 RabbitMQ
            StageDto.MqSubmitMessage mqMessage = new StageDto.MqSubmitMessage();
            mqMessage.setProgressId(progress.getId());
            mqMessage.setUserId(userId);
            mqMessage.setStageId(stageId);
            mqMessage.setProjectId(progress.getProjectId());
            mqMessage.setForceSubmit(req.getForceSubmit());
            mqMessage.setSubmitTimestamp(System.currentTimeMillis());

            rabbitTemplate.convertAndSend(RabbitMQConfig.EXAM_EXCHANGE, RabbitMQConfig.STAGE_SUBMIT_ROUTING_KEY, mqMessage);
            return progress.getId();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}