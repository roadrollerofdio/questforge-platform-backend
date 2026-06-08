package com.questforge.controller;

import com.questforge.common.Result;
import com.questforge.dto.StageDto;
import com.questforge.service.ExamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户端端控制器: 核心考试流转接口
 */
@RestController
@RequestMapping("/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /**
     * 获取当前登录用户 ID 工具方法
     */
    private Long getCurrentUserId() {
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getName();
        return Long.parseLong(userIdStr);
    }

    /**
     * 1. 考生进入考场，拉取完整试卷内容 (万人并发瞬间)
     */
    @GetMapping("/paper/detail/{paperId}")
    public Result<Map<String, Object>> getPaperDetail(@PathVariable Long paperId) {
        Long userId = getCurrentUserId();
        Map<String, Object> paperData = examService.getPaperDetail(paperId, userId);
        return Result.success(paperData);
    }

    /**
     * 2. 答题心跳与会话保存 (每隔 10 秒 / 切换选项时触发)
     */
    @PostMapping("/session/heartbeat")
    public Result<Void> heartbeat(@RequestBody @Valid ExamDto.HeartbeatReq req) {
        Long userId = getCurrentUserId();
        examService.saveHeartbeat(req.getPaperId(), req.getQuestionId(), req.getUserAnswer(), userId);
        return Result.success();
    }

    /**
     * 3. 提交试卷
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submitPaper(@RequestBody @Valid ExamDto.SubmitReq req) {
        Long userId = getCurrentUserId();

        Long recordId = examService.submitPaper(req.getPaperId(), userId, req.getForceSubmit());

        return Result.success(Map.of("recordId", recordId.toString(), "msg", "交卷成功，正在等待系统自动阅卷"));
    }
}