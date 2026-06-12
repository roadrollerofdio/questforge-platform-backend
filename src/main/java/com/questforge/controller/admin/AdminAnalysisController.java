package com.questforge.controller.admin;

import com.questforge.common.Result;
import com.questforge.dto.AnalysisDto;
import com.questforge.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端：学习路线宏观大盘与排行榜控制器
 */
@RestController
@RequestMapping("/admin/analysis")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/dashboard-summary")
    public Result<com.questforge.dto.AdminDto.DashboardSummaryResp> getDashboardSummary() {
        return Result.success(analysisService.getDashboardSummary());
    }

    @GetMapping("/project/{projectId}")
    public Result<AnalysisDto.ProjectStatsResp> getProjectStats(@PathVariable Long projectId) {
        return Result.success(analysisService.getProjectStatistics(projectId));
    }

    @GetMapping("/statistics/{projectId}")
    public Result<AnalysisDto.ProjectStatsResp> getProjectStatistics(@PathVariable Long projectId) {
        return Result.success(analysisService.getProjectStatistics(projectId));
    }

    @GetMapping("/leaderboard/{projectId}")
    public Result<List<AnalysisDto.LeaderboardResp>> getLeaderboard(@PathVariable Long projectId) {
        return Result.success(analysisService.getLeaderboard(projectId));
    }

    /**
     * 学员学习情况明细：每个学员各关卡得分、错题清单、平均分等
     */
    @GetMapping("/learning-detail/{projectId}")
    public Result<AnalysisDto.ProjectLearningDetailResp> getLearningDetail(@PathVariable Long projectId) {
        return Result.success(analysisService.getProjectLearningDetail(projectId));
    }
}