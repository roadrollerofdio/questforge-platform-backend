package com.questforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.dto.AnalysisDto;
import com.questforge.entity.SysUser;
import com.questforge.entity.UserStageProgress;
import com.questforge.mapper.SysUserMapper;
import com.questforge.mapper.UserStageProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import com.questforge.common.RedisConsts;

import java.util.*;

/**
 * 学习项目数据分析与报表服务 (真实有效版)
 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final UserStageProgressMapper progressMapper;
    private final SysUserMapper sysUserMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 真实获取单场学习计划的宏观统计大盘
     */
    public AnalysisDto.ProjectStatsResp getProjectStatistics(Long projectId) {
        // 1. 获取该项目下所有的关卡进度流转记录
        List<UserStageProgress> allProgress = progressMapper.selectList(
                new LambdaQueryWrapper<UserStageProgress>().eq(UserStageProgress::getProjectId, projectId)
        );

        // 2. 统计参与总人数 (依据 userId 去重)
        long totalUsers = allProgress.stream().map(UserStageProgress::getUserId).distinct().count();

        // 3. 计算平均分
        double avgScore = allProgress.stream().mapToInt(p -> p.getCurrentScore() == null ? 0 : p.getCurrentScore()).average().orElse(0.0);

        // 4. 计算通关率 (状态为 4-已通关 的人数)
        long passedCount = allProgress.stream().filter(p -> p.getStatus() == 4).map(UserStageProgress::getUserId).distinct().count();
        String passRate = totalUsers == 0 ? "0%" : String.format("%.1f%%", (double) passedCount / totalUsers * 100);

        // 5. 计算分数段分布情况
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("0-59", allProgress.stream().filter(p -> p.getCurrentScore() != null && p.getCurrentScore() < 60).count());
        distribution.put("60-79", allProgress.stream().filter(p -> p.getCurrentScore() != null && p.getCurrentScore() >= 60 && p.getCurrentScore() < 80).count());
        distribution.put("80-89", allProgress.stream().filter(p -> p.getCurrentScore() != null && p.getCurrentScore() >= 80 && p.getCurrentScore() < 90).count());
        distribution.put("90-100", allProgress.stream().filter(p -> p.getCurrentScore() != null && p.getCurrentScore() >= 90).count());

        AnalysisDto.ProjectStatsResp resp = new AnalysisDto.ProjectStatsResp();
        resp.setTotalParticipants(totalUsers);
        resp.setAverageScore(avgScore);
        resp.setPassRate(passRate);
        resp.setScoreDistribution(distribution);
        return resp;
    }

    /**
     * 真实获取单场学习计划的动态排行榜 (基于 Redis ZSet)
     */
    public List<AnalysisDto.LeaderboardResp> getLeaderboard(Long projectId) {
        String key = RedisConsts.LEADERBOARD_PREFIX + projectId;

        // 获取 Redis 中排名前 20 的分数集合 (分数从大到小)
        Set<ZSetOperations.TypedTuple<Object>> topUsers = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 19);
        List<AnalysisDto.LeaderboardResp> result = new ArrayList<>();

        if (topUsers == null || topUsers.isEmpty()) {
            return result;
        }

        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> tuple : topUsers) {
            Long userId = Long.valueOf(tuple.getValue().toString());
            SysUser user = sysUserMapper.selectById(userId);

            AnalysisDto.LeaderboardResp item = new AnalysisDto.LeaderboardResp();
            item.setRank(rank++);
            item.setUserId(userId);
            item.setUsername(user != null ? user.getUsername() : "未知账号");
            item.setRealName(user != null ? user.getRealName() : "匿名冒险者");
            item.setScore(tuple.getScore() != null ? tuple.getScore().intValue() : 0);
            result.add(item);
        }
        return result;
    }
}