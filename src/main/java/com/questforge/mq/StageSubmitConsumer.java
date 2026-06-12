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

import java.util.*;
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

    @RabbitListener(queues = RabbitMQConfig.EXAM_SUBMIT_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void processStageSubmit(StageDto.MqSubmitMessage msg) {
        log.info("【关卡结算引擎】开始处理挑战数据: ProgressID = {}", msg.getProgressId());

        UserStageProgress progress = progressMapper.selectById(msg.getProgressId());
        if (progress == null || progress.getStatus() != 3) return;

        String sessionKey = RedisConsts.getSessionKey(msg.getStageId(), msg.getUserId());
        Map<Object, Object> userAnswersMap = redisTemplate.opsForHash().entries(sessionKey);

        List<StageItemRef> itemRefs = itemRefMapper.selectList(new LambdaQueryWrapper<StageItemRef>()
                .eq(StageItemRef::getStageId, msg.getStageId())
                .eq(StageItemRef::getItemType, 2));

        List<Long> qIds = itemRefs.stream().map(StageItemRef::getItemId).toList();
        Map<Long, QuestionBank> stdMap = qIds.isEmpty() ? new HashMap<>() :
                questionBankMapper.selectBatchIds(qIds).stream().collect(Collectors.toMap(QuestionBank::getId, q -> q));

        int finalScore = 0;
        List<UserAnswer> answersToInsert = new ArrayList<>();

        for (StageItemRef ref : itemRefs) {
            Long qId = ref.getItemId();
            QuestionBank stdQ = stdMap.get(qId);
            String uAns = (String) userAnswersMap.get(qId.toString());

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

        for (UserAnswer ans : answersToInsert) {
            userAnswerMapper.insert(ans);
        }

        Stage currentStage = stageMapper.selectById(msg.getStageId());
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

            // 通关奖励宝石: enterStage 限制同一关卡只能结算一次, 故此处不会重复发放
            gemsEarned = currentStage.getGemReward() != null ? currentStage.getGemReward() : 0;
            if (gemsEarned > 0) {
                gemService.addGems(msg.getUserId(), gemsEarned);
            }

            // 每日任务事件: 完成关卡 / 无错通关
            dailyTaskService.onEvent(msg.getUserId(), DailyTaskService.EVENT_STAGE_COMPLETE);
            boolean isPerfect = !answersToInsert.isEmpty()
                    && answersToInsert.stream().allMatch(a -> a.getIsCorrect() != null && a.getIsCorrect() == 1);
            if (isPerfect) {
                dailyTaskService.onEvent(msg.getUserId(), DailyTaskService.EVENT_STAGE_PERFECT);
            }
        }

        // 缓存本次结算获得的宝石, 供结算页展示 (24h)
        redisTemplate.opsForValue().set(RedisConsts.STAGE_GEMS_PREFIX + progress.getId(),
                String.valueOf(gemsEarned), 24, java.util.concurrent.TimeUnit.HOURS);

        redisTemplate.delete(sessionKey);
        String leaderboardKey = RedisConsts.LEADERBOARD_PREFIX + msg.getProjectId();
        redisTemplate.opsForZSet().incrementScore(leaderboardKey, msg.getUserId().toString(), finalScore);

        log.info("【关卡结算完成】ProgressID: {}, 得分: {}, 结果: {}", msg.getProgressId(), finalScore, nextStatus == 4 ? "通关" : "失败");
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