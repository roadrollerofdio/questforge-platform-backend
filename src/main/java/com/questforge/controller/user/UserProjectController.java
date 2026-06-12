package com.questforge.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.questforge.common.Result;
import com.questforge.entity.LearningProject;
import com.questforge.entity.Stage;
import com.questforge.entity.UserStageProgress;
import com.questforge.mapper.LearningProjectMapper;
import com.questforge.mapper.StageMapper;
import com.questforge.mapper.UserStageProgressMapper;
import com.questforge.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户端：可参与的学习路线图列表
 */
@RestController
@RequestMapping("/user/project")
@RequiredArgsConstructor
public class UserProjectController {

    private final LearningProjectMapper projectMapper;
    private final StageMapper stageMapper;
    private final UserStageProgressMapper progressMapper;

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getSysUser().getId();
        }
        throw new IllegalStateException("未登录或会话已失效");
    }

    @GetMapping("/page")
    public Result<Page<LearningProject>> pagePublishedProjects(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {

        LambdaQueryWrapper<LearningProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(LearningProject::getStatus, 1, 2)
                .orderByDesc(LearningProject::getCreateTime);
        return Result.success(projectMapper.selectPage(new Page<>(pageNo, pageSize), wrapper));
    }

    /**
     * 发布中项目列表 + 我的完成进度(项目选择页)
     */
    @GetMapping("/list-with-progress")
    public Result<List<Map<String, Object>>> listWithProgress() {
        Long userId = getCurrentUserId();

        List<LearningProject> projects = projectMapper.selectList(new LambdaQueryWrapper<LearningProject>()
                .in(LearningProject::getStatus, 1, 2)
                .orderByDesc(LearningProject::getCreateTime));

        List<Map<String, Object>> result = new ArrayList<>();
        for (LearningProject project : projects) {
            Long totalStages = stageMapper.selectCount(new LambdaQueryWrapper<Stage>()
                    .eq(Stage::getProjectId, project.getId()));
            Long passedStages = progressMapper.selectCount(new LambdaQueryWrapper<UserStageProgress>()
                    .eq(UserStageProgress::getUserId, userId)
                    .eq(UserStageProgress::getProjectId, project.getId())
                    .eq(UserStageProgress::getStatus, 4));

            Map<String, Object> row = new HashMap<>();
            row.put("projectId", project.getId().toString());
            row.put("title", project.getTitle());
            row.put("startTime", project.getStartTime());
            row.put("endTime", project.getEndTime());
            row.put("totalStages", totalStages);
            row.put("passedStages", passedStages);
            row.put("progressPercent", totalStages == 0 ? 0 : Math.round(passedStages * 100.0 / totalStages));
            result.add(row);
        }
        return Result.success(result);
    }
}
