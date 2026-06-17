package com.questforge.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.questforge.common.RedisConsts;
import com.questforge.config.RabbitMQConfig;
import com.questforge.dto.StageDto;
import com.questforge.entity.LearningProject;
import com.questforge.entity.QuestionBank;
import com.questforge.entity.Stage;
import com.questforge.entity.StageItemRef;
import com.questforge.entity.UserAnswer;
import com.questforge.entity.UserStageProgress;
import com.questforge.mapper.LearningProjectMapper;
import com.questforge.mapper.QuestionBankMapper;
import com.questforge.mapper.StageItemRefMapper;
import com.questforge.mapper.StageMapper;
import com.questforge.mapper.UserAnswerMapper;
import com.questforge.mapper.UserStageProgressMapper;
import com.questforge.service.UserStageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserStageServiceImpl implements UserStageService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserStageProgressMapper progressMapper;
    private final LearningProjectMapper projectMapper;
    private final StageMapper stageMapper;
    private final StageItemRefMapper stageItemRefMapper;
    private final QuestionBankMapper questionBankMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final RabbitTemplate rabbitTemplate;

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

        UserStageProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<UserStageProgress>()
                .eq(UserStageProgress::getUserId, userId).eq(UserStageProgress::getStageId, stageId));

        // 首次进入: 若为可解锁状态(第一关 或 前置关卡均已通关), 自动初始化进度记录
        if (progress == null) {
            if (!isStageUnlockable(stage, userId)) {
                throw new RuntimeException("当前关卡尚未解锁，请先完成前置关卡！");
            }
            progress = new UserStageProgress();
            progress.setUserId(userId);
            progress.setProjectId(stage.getProjectId());
            progress.setStageId(stageId);
            progress.setStatus(1);
            progressMapper.insert(progress);
        }

        if (progress.getStatus() == 0) {
            throw new RuntimeException("当前关卡尚未解锁，请先完成前置关卡！");
        }

        if (progress.getStatus() == 1) {
            progress.setStatus(2);
            progress.setStartTime(now);
            progressMapper.updateById(progress);
        } else if (progress.getStatus() == 5) {
            // 未通关允许重新挑战: 清理旧答题明细并重置状态
            userAnswerMapper.delete(new LambdaQueryWrapper<UserAnswer>()
                    .eq(UserAnswer::getProgressId, progress.getId()));
            progress.setStatus(2);
            progress.setStartTime(now);
            progress.setCurrentScore(0);
            progressMapper.updateById(progress);
        } else if (progress.getStatus() == 3) {
            // 超时兜底: 消费者异常未处理时, 进度会永久卡在"判分中"(status=3)
            // 若距 completeTime 已超过 5 分钟, 说明结算已失败, 视为未通关并允许重新挑战
            if (progress.getCompleteTime() != null) {
                long stuckMinutes = java.time.Duration.between(progress.getCompleteTime(), now).toMinutes();
                if (stuckMinutes >= 5) {
                    userAnswerMapper.delete(new LambdaQueryWrapper<UserAnswer>()
                            .eq(UserAnswer::getProgressId, progress.getId()));
                    progress.setStatus(2);
                    progress.setStartTime(now);
                    progress.setCurrentScore(0);
                    progressMapper.updateById(progress);
                    log.warn("【关卡恢复】进度卡在结算中超过 {} 分钟, 已自动重置为重新挑战: progressId={}", stuckMinutes, progress.getId());
                } else {
                    throw new RuntimeException("该关卡正在结算中，请稍后查看结果");
                }
            } else {
                throw new RuntimeException("该关卡正在结算中，请稍后查看结果");
            }
        } else if (progress.getStatus() == 4) {
            throw new RuntimeException("该关卡已通关，无法重复挑战");
        }

        String stageCacheKey = RedisConsts.STAGE_INFO_PREFIX + stageId;
        Object stageJsonStr = redisTemplate.opsForValue().get(stageCacheKey);
        Map<String, Object> stageData;
        if (stageJsonStr == null) {
            // 缓存缺失: 从数据库构建关卡快照并预热(不含标准答案)
            stageData = buildStageSnapshot(stage);
            redisTemplate.opsForValue().set(stageCacheKey, JSONUtil.toJsonStr(stageData), 12, TimeUnit.HOURS);
        } else {
            stageData = JSONUtil.parseObj((String) stageJsonStr);
        }

        // 选项字段归一化: 兼容历史 {key,val} 与管理端 {value,text}, 统一输出前端期望的 value/text
        normalizeStageOptions(stageData);
        stageData.put("allowSwitchScreen", project.getAllowSwitchScreen() == 1);
        stageData.put("allowQuit", project.getAllowQuit() == 1);
        stageData.put("serverTime", System.currentTimeMillis() / 1000);

        return stageData;
    }

    /**
     * 判断关卡是否可解锁: 第一关 或 所有前置关卡均已通关
     */
    private boolean isStageUnlockable(Stage stage, Long userId) {
        List<Stage> prevStages = stageMapper.selectList(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getProjectId, stage.getProjectId())
                .lt(Stage::getSortOrder, stage.getSortOrder()));
        if (prevStages.isEmpty()) return true;

        List<Long> prevIds = prevStages.stream().map(Stage::getId).collect(Collectors.toList());
        Long passedCount = progressMapper.selectCount(new LambdaQueryWrapper<UserStageProgress>()
                .eq(UserStageProgress::getUserId, userId)
                .in(UserStageProgress::getStageId, prevIds)
                .eq(UserStageProgress::getStatus, 4));
        return passedCount >= prevStages.size();
    }

    /**
     * 从数据库构建关卡快照(题目不携带标准答案)
     */
    private Map<String, Object> buildStageSnapshot(Stage stage) {
        List<StageItemRef> refs = stageItemRefMapper.selectList(new LambdaQueryWrapper<StageItemRef>()
                .eq(StageItemRef::getStageId, stage.getId())
                .eq(StageItemRef::getItemType, 2)
                .orderByAsc(StageItemRef::getSortNum));

        List<Map<String, Object>> questions = new ArrayList<>();
        if (!refs.isEmpty()) {
            List<Long> qIds = refs.stream().map(StageItemRef::getItemId).collect(Collectors.toList());
            Map<Long, QuestionBank> qMap = questionBankMapper.selectBatchIds(qIds).stream()
                    .collect(Collectors.toMap(QuestionBank::getId, q -> q));

            for (StageItemRef ref : refs) {
                QuestionBank q = qMap.get(ref.getItemId());
                if (q == null) continue;
                Map<String, Object> qData = new HashMap<>();
                qData.put("id", q.getId().toString());
                qData.put("type", q.getType());
                qData.put("content", q.getContent());
                qData.put("options", parseOptions(q.getOptionsJson()));
                qData.put("scoreWeight", ref.getScoreWeight());
                questions.add(qData);
            }
        }

        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("stageId", stage.getId().toString());
        snapshot.put("stageName", stage.getStageName());
        snapshot.put("stageType", stage.getStageType());
        snapshot.put("duration", stage.getDurationMins());
        snapshot.put("totalScore", stage.getTotalScore());
        snapshot.put("gemReward", stage.getGemReward());
        snapshot.put("questions", questions);
        return snapshot;
    }

    /**
     * 将关卡快照中每道题的选项统一为 {value, text} 结构
     * 兼容历史种子数据的 {key, val} 以及管理端录入的 {value, text}
     */
    @SuppressWarnings("unchecked")
    private void normalizeStageOptions(Map<String, Object> stageData) {
        Object questionsObj = stageData.get("questions");
        if (!(questionsObj instanceof java.util.List<?> questions)) return;

        for (Object qObj : questions) {
            if (!(qObj instanceof Map)) continue;
            Map<String, Object> q = (Map<String, Object>) qObj;
            Object optionsObj = q.get("options");
            if (!(optionsObj instanceof java.util.List<?> options)) continue;

            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object optObj : options) {
                if (!(optObj instanceof Map)) continue;
                Map<String, Object> opt = (Map<String, Object>) optObj;
                Object value = opt.get("value") != null ? opt.get("value") : opt.get("key");
                Object text = opt.get("text") != null ? opt.get("text") : opt.get("val");
                Map<String, Object> n = new HashMap<>();
                n.put("value", value);
                n.put("text", text);
                normalized.add(n);
            }
            q.put("options", normalized);
        }
    }

    /**
     * optionsJson 兼容: 可能为 JSON 字符串或已被 JacksonTypeHandler 反序列化的对象
     */
    private Object parseOptions(Object optionsJson) {
        if (optionsJson == null) return new ArrayList<>();
        if (optionsJson instanceof String str) {
            try {
                return JSONUtil.parseArray(str);
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
        return optionsJson;
    }

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

            int updated = progressMapper.update(null, new LambdaUpdateWrapper<UserStageProgress>()
                    .set(UserStageProgress::getStatus, 3)
                    .set(UserStageProgress::getCompleteTime, LocalDateTime.now())
                    .eq(UserStageProgress::getId, progress.getId())
                    .eq(UserStageProgress::getStatus, 2));

            if (updated == 0) throw new RuntimeException("挑战状态异常或已结算完毕");

            StageDto.MqSubmitMessage mqMessage = new StageDto.MqSubmitMessage();
            mqMessage.setProgressId(progress.getId());
            mqMessage.setUserId(userId);
            mqMessage.setStageId(stageId);
            mqMessage.setProjectId(progress.getProjectId());
            mqMessage.setForceSubmit(req.getForceSubmit());
            mqMessage.setSubmitTimestamp(System.currentTimeMillis());

            rabbitTemplate.convertAndSend(RabbitMQConfig.EXAM_EXCHANGE, RabbitMQConfig.EXAM_SUBMIT_ROUTING_KEY, mqMessage);
            return progress.getId();
        } finally {
            redisTemplate.delete(lockKey);
        }
    }
}