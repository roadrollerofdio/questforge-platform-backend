package com.questforge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.Result;
import com.questforge.entity.DailyTaskPlan;
import com.questforge.entity.DailyTaskPool;
import com.questforge.mapper.DailyTaskPlanMapper;
import com.questforge.mapper.DailyTaskPoolMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理端：每日任务池维护 + 按日挑选任务
 */
@RestController
@RequestMapping("/admin/daily-task")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminDailyTaskController {

    private final DailyTaskPoolMapper poolMapper;
    private final DailyTaskPlanMapper planMapper;

    /**
     * 任务池列表
     */
    @GetMapping("/pool")
    public Result<List<DailyTaskPool>> listPool() {
        return Result.success(poolMapper.selectList(
                new LambdaQueryWrapper<DailyTaskPool>().orderByDesc(DailyTaskPool::getCreateTime)));
    }

    /**
     * 新增/更新任务池条目
     */
    @PostMapping("/pool/save")
    public Result<Long> savePoolTask(@RequestBody @Valid TaskSaveReq req) {
        DailyTaskPool task;
        if (req.getId() != null) {
            task = poolMapper.selectById(req.getId());
            if (task == null) {
                return Result.error(400, "任务不存在");
            }
        } else {
            task = new DailyTaskPool();
        }

        task.setTitle(req.getTitle());
        task.setTaskType(req.getTaskType());
        task.setTargetCount(req.getTargetCount() != null ? req.getTargetCount() : 1);
        task.setGemReward(req.getGemReward() != null ? req.getGemReward() : 10);
        task.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);

        if (task.getId() == null) {
            poolMapper.insert(task);
        } else {
            poolMapper.updateById(task);
        }
        return Result.success(task.getId(), "任务已保存");
    }

    /**
     * 删除任务池条目
     */
    @DeleteMapping("/pool/{id}")
    public Result<Void> deletePoolTask(@PathVariable Long id) {
        poolMapper.deleteById(id);
        return Result.success(null, "任务已删除");
    }

    /**
     * 查看某日已挑选的任务
     */
    @GetMapping("/plan")
    public Result<List<DailyTaskPool>> getPlan(@RequestParam String date) {
        LocalDate planDate = LocalDate.parse(date);
        List<DailyTaskPlan> plans = planMapper.selectList(new LambdaQueryWrapper<DailyTaskPlan>()
                .eq(DailyTaskPlan::getPlanDate, planDate));
        if (plans.isEmpty()) {
            return Result.success(List.of());
        }
        List<Long> poolIds = plans.stream().map(DailyTaskPlan::getTaskPoolId).collect(Collectors.toList());
        List<DailyTaskPool> tasks = poolMapper.selectBatchIds(poolIds);
        Map<Long, DailyTaskPool> taskMap = tasks.stream().collect(Collectors.toMap(DailyTaskPool::getId, t -> t));
        return Result.success(poolIds.stream().map(taskMap::get).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    /**
     * 为某日挑选 3 个任务(覆盖原有计划)
     */
    @PostMapping("/plan/save")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> savePlan(@RequestBody @Valid PlanSaveReq req) {
        if (req.getTaskPoolIds() == null || req.getTaskPoolIds().size() != 3) {
            return Result.error(400, "每日任务必须挑选 3 个");
        }
        if (req.getTaskPoolIds().stream().distinct().count() != 3) {
            return Result.error(400, "不能重复挑选同一个任务");
        }

        LocalDate planDate = LocalDate.parse(req.getDate());
        for (Long poolId : req.getTaskPoolIds()) {
            if (poolMapper.selectById(poolId) == null) {
                return Result.error(400, "任务池中不存在 ID 为 " + poolId + " 的任务");
            }
        }

        planMapper.delete(new LambdaQueryWrapper<DailyTaskPlan>().eq(DailyTaskPlan::getPlanDate, planDate));
        for (Long poolId : req.getTaskPoolIds()) {
            DailyTaskPlan plan = new DailyTaskPlan();
            plan.setPlanDate(planDate);
            plan.setTaskPoolId(poolId);
            planMapper.insert(plan);
        }
        return Result.success(null, "每日任务计划已保存");
    }

    @Data
    public static class TaskSaveReq {
        private Long id;
        @NotBlank(message = "任务标题不能为空")
        private String title;
        @NotBlank(message = "任务类型不能为空")
        private String taskType;
        private Integer targetCount;
        private Integer gemReward;
        private Integer enabled;
    }

    @Data
    public static class PlanSaveReq {
        @NotBlank(message = "日期不能为空")
        private String date;
        @NotNull(message = "任务列表不能为空")
        private List<Long> taskPoolIds;
    }
}
