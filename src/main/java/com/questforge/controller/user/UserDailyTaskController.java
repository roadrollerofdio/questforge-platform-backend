package com.questforge.controller.user;

import com.questforge.common.Result;
import com.questforge.security.UserDetailsImpl;
import com.questforge.service.DailyTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户端：每日任务
 */
@RestController
@RequestMapping("/user/daily-task")
@RequiredArgsConstructor
public class UserDailyTaskController {

    private final DailyTaskService dailyTaskService;

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getSysUser().getId();
        }
        throw new IllegalStateException("未登录或会话已失效");
    }

    /**
     * 今日 3 个任务 + 我的进度
     */
    @GetMapping("/today")
    public Result<List<Map<String, Object>>> getTodayTasks() {
        return Result.success(dailyTaskService.getTodayTasksWithProgress(getCurrentUserId()));
    }
}
