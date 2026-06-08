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
@RequestMapping("/api/admin/analysis")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAnalysisController {

    private final AnalysisService analysisService;

    @GetMapping("/project/{projectId}")
    public Result<AnalysisDto.ProjectStatsResp> getProjectStats(@PathVariable Long projectId) {
        return Result.success(analysisService.getProjectStatistics(projectId));
    }

    @GetMapping("/leaderboard/{projectId}")
    public Result<List<AnalysisDto.LeaderboardResp>> getLeaderboard(@PathVariable Long projectId) {
        return Result.success(analysisService.getLeaderboard(projectId));
    }
}