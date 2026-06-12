package com.questforge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.entity.SysUser;
import com.questforge.entity.UserStageProgress;
import com.questforge.mapper.SysUserMapper;
import com.questforge.mapper.UserStageProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户资料聚合服务: 通关数 / 今日是否学习 / 资料卡片组装
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final SysUserMapper sysUserMapper;
    private final UserStageProgressMapper progressMapper;

    /**
     * 累计通关数 (status=4)
     */
    public long getPassedStagesCount(Long userId) {
        return progressMapper.selectCount(new LambdaQueryWrapper<UserStageProgress>()
                .eq(UserStageProgress::getUserId, userId)
                .eq(UserStageProgress::getStatus, 4));
    }

    /**
     * 今日是否学习过 (当天有提交结算的关卡)
     */
    public boolean hasLearnedToday(Long userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return progressMapper.selectCount(new LambdaQueryWrapper<UserStageProgress>()
                .eq(UserStageProgress::getUserId, userId)
                .ge(UserStageProgress::getCompleteTime, startOfDay)) > 0;
    }

    public String displayName(SysUser user) {
        if (user == null) return "匿名冒险者";
        if (user.getNickname() != null && !user.getNickname().isBlank()) return user.getNickname();
        if (user.getRealName() != null && !user.getRealName().isBlank()) return user.getRealName();
        return user.getUsername();
    }

    /**
     * 用户简要卡片(头像渲染用): userId / nickname / avatarConfig
     */
    public Map<String, Object> buildBrief(Long userId) {
        return buildBrief(sysUserMapper.selectById(userId));
    }

    public Map<String, Object> buildBrief(SysUser user) {
        Map<String, Object> brief = new HashMap<>();
        if (user == null) {
            brief.put("userId", null);
            brief.put("nickname", "匿名冒险者");
            brief.put("avatarConfig", null);
            return brief;
        }
        brief.put("userId", user.getId().toString());
        brief.put("nickname", displayName(user));
        brief.put("avatarConfig", user.getAvatarConfig());
        return brief;
    }
}
