package com.questforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.entity.DailyTaskPlan;
import com.questforge.entity.DailyTaskPool;
import com.questforge.entity.UserDailyTask;
import com.questforge.mapper.DailyTaskPlanMapper;
import com.questforge.mapper.DailyTaskPoolMapper;
import com.questforge.mapper.UserDailyTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 每日任务引擎:
 * 1. 当日计划缺失时自动从任务池随机挑选 3 个兜底
 * 2. onEvent 推进进度, 完成即自动发放宝石
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyTaskService {

    public static final String EVENT_STAGE_COMPLETE = "STAGE_COMPLETE";
    public static final String EVENT_STAGE_PERFECT = "STAGE_PERFECT";
    public static final String EVENT_ASK_AI = "ASK_AI";

    private static final int DAILY_TASK_COUNT = 3;

    private final DailyTaskPoolMapper poolMapper;
    private final DailyTaskPlanMapper planMapper;
    private final UserDailyTaskMapper userTaskMapper;
    private final GemService gemService;

    /**
     * 获取今日任务计划(无管理员配置则随机生成兜底)
     */
    @Transactional(rollbackFor = Exception.class)
    public List<DailyTaskPool> getTodayPlanTasks() {
        LocalDate today = LocalDate.now();
        List<DailyTaskPlan> plans = planMapper.selectList(new LambdaQueryWrapper<DailyTaskPlan>()
                .eq(DailyTaskPlan::getPlanDate, today));

        if (plans.isEmpty()) {
            plans = generateRandomPlan(today);
        }

        List<Long> poolIds = plans.stream().map(DailyTaskPlan::getTaskPoolId).collect(Collectors.toList());
        if (poolIds.isEmpty()) return Collections.emptyList();

        List<DailyTaskPool> tasks = poolMapper.selectBatchIds(poolIds);
        // 保持计划顺序
        Map<Long, DailyTaskPool> taskMap = tasks.stream().collect(Collectors.toMap(DailyTaskPool::getId, t -> t));
        return poolIds.stream().map(taskMap::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private List<DailyTaskPlan> generateRandomPlan(LocalDate date) {
        List<DailyTaskPool> enabledTasks = poolMapper.selectList(new LambdaQueryWrapper<DailyTaskPool>()
                .eq(DailyTaskPool::getEnabled, 1));
        if (enabledTasks.isEmpty()) return Collections.emptyList();

        Collections.shuffle(enabledTasks);
        List<DailyTaskPool> picked = enabledTasks.subList(0, Math.min(DAILY_TASK_COUNT, enabledTasks.size()));

        List<DailyTaskPlan> plans = new ArrayList<>();
        for (DailyTaskPool task : picked) {
            DailyTaskPlan plan = new DailyTaskPlan();
            plan.setPlanDate(date);
            plan.setTaskPoolId(task.getId());
            planMapper.insert(plan);
            plans.add(plan);
        }
        log.info("【每日任务】{} 无管理员配置, 已自动随机生成 {} 个任务", date, plans.size());
        return plans;
    }

    /**
     * 获取用户今日任务列表(含进度), 首次访问时初始化进度记录
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> getTodayTasksWithProgress(Long userId) {
        LocalDate today = LocalDate.now();
        List<DailyTaskPool> tasks = getTodayPlanTasks();
        if (tasks.isEmpty()) return Collections.emptyList();

        Map<Long, UserDailyTask> progressMap = loadUserTasks(userId, today).stream()
                .collect(Collectors.toMap(UserDailyTask::getTaskPoolId, t -> t, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (DailyTaskPool task : tasks) {
            UserDailyTask ut = progressMap.get(task.getId());
            if (ut == null) {
                ut = new UserDailyTask();
                ut.setUserId(userId);
                ut.setPlanDate(today);
                ut.setTaskPoolId(task.getId());
                ut.setProgress(0);
                ut.setIsCompleted(0);
                ut.setIsRewarded(0);
                userTaskMapper.insert(ut);
            }

            Map<String, Object> row = new HashMap<>();
            row.put("taskId", task.getId().toString());
            row.put("title", task.getTitle());
            row.put("taskType", task.getTaskType());
            row.put("targetCount", task.getTargetCount());
            row.put("gemReward", task.getGemReward());
            row.put("progress", ut.getProgress());
            row.put("isCompleted", ut.getIsCompleted());
            result.add(row);
        }
        return result;
    }

    /**
     * 任务事件埋点入口: 推进匹配类型任务的进度, 完成即发宝石
     * 使用 REQUIRES_NEW 独立事务: 任务推进/发奖失败不得回滚调用方(如关卡判分)的核心事务
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void onEvent(Long userId, String eventType) {
        try {
            LocalDate today = LocalDate.now();
            List<DailyTaskPool> tasks = getTodayPlanTasks();
            if (tasks.isEmpty()) return;

            Map<Long, UserDailyTask> progressMap = loadUserTasks(userId, today).stream()
                    .collect(Collectors.toMap(UserDailyTask::getTaskPoolId, t -> t, (a, b) -> a));

            for (DailyTaskPool task : tasks) {
                if (!eventType.equals(task.getTaskType())) continue;

                UserDailyTask ut = progressMap.get(task.getId());
                if (ut == null) {
                    ut = new UserDailyTask();
                    ut.setUserId(userId);
                    ut.setPlanDate(today);
                    ut.setTaskPoolId(task.getId());
                    ut.setProgress(0);
                    ut.setIsCompleted(0);
                    ut.setIsRewarded(0);
                    userTaskMapper.insert(ut);
                }

                if (ut.getIsCompleted() != null && ut.getIsCompleted() == 1) continue;

                int newProgress = (ut.getProgress() == null ? 0 : ut.getProgress()) + 1;
                ut.setProgress(newProgress);

                int target = task.getTargetCount() == null ? 1 : task.getTargetCount();
                if (newProgress >= target) {
                    ut.setIsCompleted(1);
                    if (ut.getIsRewarded() == null || ut.getIsRewarded() == 0) {
                        ut.setIsRewarded(1);
                        gemService.addGems(userId, task.getGemReward() == null ? 0 : task.getGemReward());
                        log.info("【每日任务完成】用户 {} 完成任务「{}」, 奖励 {} 宝石", userId, task.getTitle(), task.getGemReward());
                    }
                }
                userTaskMapper.updateById(ut);
            }
        } catch (Exception e) {
            // 任务推进失败不应阻断主业务流程
            log.error("每日任务事件处理异常: userId={}, event={}", userId, eventType, e);
        }
    }

    private List<UserDailyTask> loadUserTasks(Long userId, LocalDate date) {
        return userTaskMapper.selectList(new LambdaQueryWrapper<UserDailyTask>()
                .eq(UserDailyTask::getUserId, userId)
                .eq(UserDailyTask::getPlanDate, date));
    }
}
