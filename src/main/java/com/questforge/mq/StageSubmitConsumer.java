package com.questforge.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.questforge.common.RedisConsts;
import com.questforge.config.RabbitMQConfig;
import com.questforge.dto.StageDto;
import com.questforge.entity.*;
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

    @RabbitListener(queues = RabbitMQConfig.STAGE_SUBMIT_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void processStageSubmit(StageDto.MqSubmitMessage msg) {
        log.info("【关卡结算引擎】开始处理挑战数据: ProgressID = {}", msg.getProgressId());

        UserStageProgress progress = progressMapper.selectById(msg.getProgressId());
        // 幂等拦截：仅处理状态为 3 (结算中) 的数据
        if (progress == null || progress.getStatus() != 3) return;

        // 1. 获取 Redis 中高频心跳暂存的答案
        String sessionKey = RedisConsts.getSessionKey(msg.getStageId(), msg.getUserId());
        Map<Object, Object> userAnswersMap = redisTemplate.opsForHash().entries(sessionKey);

        // 2. 获取本关卡试题清单与标准答案
        List<StageItemRef> itemRefs = itemRefMapper.selectList(new LambdaQueryWrapper<StageItemRef>()
                .eq(StageItemRef::getStageId, msg.getStageId())
                .eq(StageItemRef::getItemType, 2)); // 2-考核试题

        List<Long> qIds = itemRefs.stream().map(StageItemRef::getItemId).toList();
        Map<Long, QuestionBank> stdMap = qIds.isEmpty() ? new HashMap<>() :
                questionBankMapper.selectBatchIds(qIds).stream().collect(Collectors.toMap(QuestionBank::getId, q -> q));

        int finalScore = 0;
        List<UserAnswer> answersToInsert = new ArrayList<>();

        // 3. 开始批阅计算
        for (StageItemRef ref : itemRefs) {
            Long qId = ref.getItemId();
            QuestionBank stdQ = stdMap.get(qId);
            String uAns = (String) userAnswersMap.get(qId.toString());

            int actualScore = 0;
            int isCorrect = 0;

            if (stdQ != null && uAns != null && !uAns.isBlank()) {
                String stdAns = stdQ.getAnswer() != null ? stdQ.getAnswer().trim() : "";
                uAns = uAns.trim();

                // 兼容多选题乱序提交 (如标准答案 A,B 用户提交 B,A)
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

            // 组装答题明细底表
            UserAnswer detail = new UserAnswer();
            detail.setProgressId(progress.getId());
            detail.setQuestionId(qId);
            detail.setUserAnswer(uAns);
            detail.setIsCorrect(isCorrect);
            detail.setActualScore(actualScore);
            answersToInsert.add(detail);
        }

        // 批量落库明细
        for (UserAnswer ans : answersToInsert) {
            userAnswerMapper.insert(ans);
        }

        // 4. 判断是否达到及格门槛
        Stage currentStage = stageMapper.selectById(msg.getStageId());
        int nextStatus = 5; // 默认 5-未及格
        if (currentStage.getPassScoreThreshold() == null || finalScore >= currentStage.getPassScoreThreshold()) {
            nextStatus = 4; // 4-已通关
        }

        // 5. 推进当前关卡状态机
        progressMapper.update(null, new LambdaUpdateWrapper<UserStageProgress>()
                .set(UserStageProgress::getStatus, nextStatus)
                .set(UserStageProgress::getCurrentScore, finalScore)
                .eq(UserStageProgress::getId, progress.getId()));

        // 6. 若成功通关，自动解锁下一关卡！
        if (nextStatus == 4) {
            unlockNextStage(currentStage, msg.getUserId(), msg.getProjectId());
        }

        // 7. 清理临时会话，累加排行榜经验值
        redisTemplate.delete(sessionKey);
        String leaderboardKey = RedisConsts.LEADERBOARD_PREFIX + msg.getProjectId();
        redisTemplate.opsForZSet().incrementScore(leaderboardKey, msg.getUserId().toString(), finalScore);

        log.info("【关卡结算完成】ProgressID: {}, 得分: {}, 结果: {}", msg.getProgressId(), finalScore, nextStatus == 4 ? "通关" : "失败");
    }

    /**
     * 解锁下一关卡逻辑
     */
    private void unlockNextStage(Stage currentStage, Long userId, Long projectId) {
        Stage nextStage = stageMapper.selectOne(new LambdaQueryWrapper<Stage>()
                .eq(Stage::getProjectId, projectId)
                .gt(Stage::getSortOrder, currentStage.getSortOrder())
                .orderByAsc(Stage::getSortOrder)
                .last("LIMIT 1"));

        if (nextStage != null) {
            UserStageProgress nextProgress = progressMapper.selectOne(new LambdaQueryWrapper<UserStageProgress>()
                    .eq(UserStageProgress::getUserId, userId).eq(UserStageProgress::getStageId, nextStage.getId()));

            if (nextProgress != null && nextProgress.getStatus() == 0) {
                // 将下一关状态从 0(未解锁) 更新为 1(已解锁待考)
                nextProgress.setStatus(1);
                progressMapper.updateById(nextProgress);
            }
        }
    }
}