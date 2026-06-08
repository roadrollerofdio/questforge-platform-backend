package com.questforge.controller.admin;

import com.questforge.common.Result;
import com.questforge.dto.AdminDto;
import com.questforge.dto.AnalysisDto;
import com.questforge.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 API: 考试数据大盘统计
 */
@RestController
@RequestMapping("/admin/analysis")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAnalysisController {

    private final AnalysisService analysisService;

    /**
     * 考试大盘数据统计
     */
    @GetMapping("/statistics/{paperId}")
    public Result<AnalysisDto.DashboardResp> getStatistics(@PathVariable Long paperId) {
        AnalysisDto.DashboardResp resp = analysisService.getExamStatistics(paperId);
        return Result.success(resp);
    }

    /**
     * 控制台首页全局汇总数据
     */
    @GetMapping("/dashboard-summary")
    public Result<AdminDto.DashboardSummaryResp> getDashboardSummary() {
        return Result.success(analysisService.getDashboardSummary());
    }
}