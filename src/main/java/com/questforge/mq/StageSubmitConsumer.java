package com.questforge.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.questforge.common.RedisConsts;
import com.questforge.config.RabbitMQConfig;
import com.questforge.dto.StageDto;
import com.questforge.entity.*;
import com.questforge.mapper.QuestionBankMapper;
import com.questforge.mapper.StageItemRefMapper;
import com.questforge.mapper.StageMapper;
import com.questforge.mapper.UserAnswerMapper;
import com.questforge.mapper.UserStageProgressMapper;
import com.questforge.service.DailyTaskService;
import com.questforge.service.GemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class StageSubmitConsumer {

    private final UserStageProgressMapper progressMapper;
    private final StageItemRefMapper itemRefMapper;
    private final QuestionBankMapper questionBankMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final StageMapper stageMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final GemService gemService;
    private final DailyTaskService dailyTaskService;

    /**
     * 关键: acknowledgeMode=AUTO + setTransactionManager(RabbitTransactionManager)
     * - AUTO 模式下, Spring AMQP 会在 onMessage 正常返回时自动 ack, 抛出异常时自动 nack
     * - RabbitTransactionManager 会把整个监听处理包在 Rabbit channel tx + DB 事务中
     * - 业务代码只需抛/不抛异常, 完全不需要手动 basicAck/basicNack (避免重复 ack 导致 PRECONDITION_FAILED)
     * - 一旦方法返回或抛异常, 消息 ack/nack 与事务提交/回滚由 Spring 统一协调
     */
    @RabbitListener(queues = RabbitMQConfig.EXAM_SUBMIT_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void processStageSubmit(StageDto.MqSubmitMessage msg) {
        log.info("【关卡结算引擎】开始处理挑战数据: ProgressID = {}", msg.getProgressId());

        UserStageProgress progress = progressMapper.selectById(msg.getProgressId());
        if (progress == null || progress.getStatus() != 3) {
            log.info("【关卡结算】进度状态非结算中(可能已处理), 跳过: ProgressID={}, status={}",
                    msg.getProgressId(), progress == null ? "null" : progress.getStatus());
            return;
        }

        String sessionKey = RedisConsts.getSessionKey(msg.getStageId(), msg.getUserId());
        Map<Object, Object> userAnswersMap = redisTemplate.opsForHash().entries(sessionKey);

        List<StageItemRef> itemRefs = itemRefMapper.selectList(new LambdaQueryWrapper<StageItemRef>()
                .eq(StageItemRef::getStageId, msg.getStageId())
                .eq(StageItemRef::getItemType, 2));

        List<Long> qIds = itemRefs.stream().map(StageItemRef::getItemId).toList();
        Map<Long, QuestionBank> stdMap = qIds.isEmpty() ? new HashMap<>() :
                questionBankMapper.selectBatchIds(qIds).stream()
                        .collect(Collectors.toMap(QuestionBank::getId, q -> q));

        int finalScore = 0;
        List<UserAnswer> answersToInsert = new ArrayList<>();

        for (StageItemRef ref : itemRefs) {
            Long qId = ref.getItemId();
            QuestionBank stdQ = stdMap.get(qId);
            String uAns = extractAnswer(userAnswersMap.get(qId.toString()));

            int actualScore = 0;
            int isCorrect = 0;

            if (stdQ != null && uAns != null && !uAns.isBlank()) {
                String stdAns = stdQ.getAnswer() != null ? stdQ.getAnswer().trim() : "";
                uAns = uAns.trim();

                if (stdQ.getType() == 2) {
                    Set<String> stdSet = new HashSet<>(Arrays.asList(stdAns.toUpperCase().split(",")));
                    Set<String> uSet = new HashSet<>(Arrays.asList(uAns.toUpperCase().split(",")));
                    if (stdSet.equals(uSet)) {
                        actualScore = ref.getScoreWeight();
                        isCorrect = 1;
                    }
                } else {
                    if (stdAns.equalsIgnoreCase(uAns)) {
                        actualScore = ref.getScoreWeight();
                        isCorrect = 1;
                    }
                }
            }
            finalScore += actualScore;

            UserAnswer detail = new UserAnswer();
            detail.setProgressId(progress.getId());
            detail.setQuestionId(qId);
            detail.setUserAnswer(uAns);
            detail.setIsCorrect(isCorrect);
            detail.setActualScore(actualScore);
            answersToInsert.add(detail);
        }

        // 答案明细写入加容错: 个别题目落库失败不阻断核心判分流程
        int savedCount = 0;
        for (UserAnswer ans : answersToInsert) {
            try {
                userAnswerMapper.insert(ans);
                savedCount++;
            } catch (Exception e) {
                log.warn("【关卡结算】答题明细写入失败(不影响判分): progressId={}, questionId={}, err={}",
                        progress.getId(), ans.getQuestionId(), e.getMessage());
            }
        }
        log.info("【关卡结算】答题明细写入: 总数={}, 成功={}", answersToInsert.size(), savedCount);

        Stage currentStage = stageMapper.selectById(msg.getStageId());
        if (currentStage == null) {
            progressMapper.update(null, new LambdaUpdateWrapper<UserStageProgress>()
                    .set(UserStageProgress::getStatus, 5)
                    .set(UserStageProgress::getCurrentScore, finalScore)
                    .eq(UserStageProgress::getId, progress.getId()));
            registerRedisOpsAfterCommit(sessionKey, progress.getId(), 0, 5,
                    msg.getProjectId(), msg.getUserId(), finalScore);
            log.warn("【关卡结算】关卡已不存在, 仅记录得分并结束: StageID = {}", msg.getStageId());
            return;
        }

        int nextStatus = 5;
        if (currentStage.getPassScoreThreshold() == null || finalScore >= currentStage.getPassScoreThreshold()) {
            nextStatus = 4;
        }

        progressMapper.update(null, new LambdaUpdateWrapper<UserStageProgress>()
                .set(UserStageProgress::getStatus, nextStatus)
                .set(UserStageProgress::getCurrentScore, finalScore)
                .eq(UserStageProgress::getId, progress.getId()));

        int gemsEarned = 0;
        if (nextStatus == 4) {
            unlockNextStage(currentStage, msg.getUserId(), msg.getProjectId());

            gemsEarned = currentStage.getGemReward() != null ? currentStage.getGemReward() : 0;

            try {
                if (gemsEarned > 0) {
                    gemService.addGems(msg.getUserId(), gemsEarned);
                }

                dailyTaskService.onEvent(msg.getUserId(), DailyTaskService.EVENT_STAGE_COMPLETE);
                boolean isPerfect = !answersToInsert.isEmpty()
                        && answersToInsert.stream().allMatch(a -> a.getIsCorrect() != null && a.getIsCorrect() == 1);
                if (isPerfect) {
                    dailyTaskService.onEvent(msg.getUserId(), DailyTaskService.EVENT_STAGE_PERFECT);
                }
            } catch (Exception e) {
                log.error("【关卡结算】发放宝石或推进每日任务失败(不影响判分结果): userId={}, stageId={}",
                        msg.getUserId(), msg.getStageId(), e);
            }
        }

        // Redis 操作延迟到事务提交后执行, 避免 Redis 故障导致判分事务回滚
        registerRedisOpsAfterCommit(sessionKey, progress.getId(), gemsEarned, nextStatus,
                msg.getProjectId(), msg.getUserId(), finalScore);

        log.info("【关卡结算完成】ProgressID: {}, 得分: {}, 结果: {}",
                msg.getProgressId(), finalScore, nextStatus == 4 ? "通关" : "失败");
    }

    /**
     * 安全提取 Redis 中保存的用户答案: 兼容 JSON 反序列化产生的各种类型
     */
    private String extractAnswer(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        if (value instanceof Number n) return n.toString();
        if (value instanceof Map) return String.valueOf(((Map<?, ?>) value).get("value"));
        return value.toString();
    }

    /**
     * 将 Redis 后处理操作注册为事务同步回调, 确保 DB 事务提交成功后才执行
     * 这样即使 Redis 故障也不会回滚已完成的判分结果
     */
    private void registerRedisOpsAfterCommit(String sessionKey, Long progressId, int gemsEarned,
                                            int status, Long projectId, Long userId, int finalScore) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            applyRedisOps(sessionKey, progressId, gemsEarned, status, projectId, userId, finalScore);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                applyRedisOps(sessionKey, progressId, gemsEarned, status, projectId, userId, finalScore);
            }
        });
    }

    private void applyRedisOps(String sessionKey, Long progressId, int gemsEarned, int status,
                              Long projectId, Long userId, int finalScore) {
        try {
            if (gemsEarned > 0 || status == 4) {
                redisTemplate.opsForValue().set(RedisConsts.STAGE_GEMS_PREFIX + progressId,
                        String.valueOf(gemsEarned), 24, TimeUnit.HOURS);
            }
            redisTemplate.delete(sessionKey);
            String leaderboardKey = RedisConsts.LEADERBOARD_PREFIX + projectId;
            redisTemplate.opsForZSet().incrementScore(leaderboardKey, userId.toString(), finalScore);
        } catch (Exception e) {
            log.error("【关卡结算】Redis 后处理失败(判分已落库, 不影响结果): progressId={}", progressId, e);
        }
    }

    private void unlockNextStage(Stage currentStage, Long userId, Long projectId) {
        Stage nextStage = stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getProjectId, projectId)
                .gt(Stage::getSortOrder, currentStage.getSortOrder())
                .orderByAsc(Stage::getSortOrder)
                .last("LIMIT 1"));

        if (nextStage != null) {
            UserStageProgress nextProgress = progressMapper.selectOne(new LambdaQueryWrapper<UserStageProgress>()
                    .eq(UserStageProgress::getUserId, userId).eq(UserStageProgress::getStageId, nextStage.getId()));

            if (nextProgress == null) {
                nextProgress = new UserStageProgress();
                nextProgress.setUserId(userId);
                nextProgress.setProjectId(projectId);
                nextProgress.setStageId(nextStage.getId());
                nextProgress.setStatus(1);
                progressMapper.insert(nextProgress);
            } else if (nextProgress.getStatus() == 0) {
                nextProgress.setStatus(1);
                progressMapper.updateById(nextProgress);
            }
        }
    }
}
