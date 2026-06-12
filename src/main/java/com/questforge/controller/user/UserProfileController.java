package com.questforge.controller.user;

import com.questforge.common.Result;
import com.questforge.entity.SysUser;
import com.questforge.mapper.SysUserMapper;
import com.questforge.security.UserDetailsImpl;
import com.questforge.service.UserProfileService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户端：个人资料(昵称/宝石/虚拟形象/通关数)
 */
@RestController
@RequestMapping("/user/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final SysUserMapper sysUserMapper;
    private final UserProfileService userProfileService;

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getSysUser().getId();
        }
        throw new IllegalStateException("未登录或会话已失效");
    }

    /**
     * 本人资料
     */
    @GetMapping
    public Result<Map<String, Object>> getMyProfile() {
        Long userId = getCurrentUserId();
        SysUser user = sysUserMapper.selectById(userId);

        Map<String, Object> data = userProfileService.buildBrief(user);
        data.put("username", user.getUsername());
        data.put("gems", user.getGems() == null ? 0 : user.getGems());
        data.put("passedStages", userProfileService.getPassedStagesCount(userId));
        data.put("learnedToday", userProfileService.hasLearnedToday(userId));
        return Result.success(data);
    }

    /**
     * 修改昵称与虚拟形象
     */
    @PutMapping
    public Result<Void> updateMyProfile(@RequestBody ProfileUpdateReq req) {
        Long userId = getCurrentUserId();
        SysUser user = sysUserMapper.selectById(userId);

        if (StringUtils.hasText(req.getNickname())) {
            if (req.getNickname().length() > 20) {
                return Result.error(400, "昵称不能超过 20 个字符");
            }
            user.setNickname(req.getNickname().trim());
        }
        if (req.getAvatarConfig() != null) {
            user.setAvatarConfig(req.getAvatarConfig());
        }
        sysUserMapper.updateById(user);
        return Result.success(null, "个人资料已更新");
    }

    /**
     * 查看他人资料卡(形象、通关数、今日是否学习)
     */
    @GetMapping("/{userId}")
    public Result<Map<String, Object>> getUserProfile(@PathVariable Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return Result.error(400, "用户不存在");
        }
        Map<String, Object> data = userProfileService.buildBrief(user);
        data.put("passedStages", userProfileService.getPassedStagesCount(userId));
        data.put("learnedToday", userProfileService.hasLearnedToday(userId));
        return Result.success(data);
    }

    @Data
    public static class ProfileUpdateReq {
        private String nickname;
        private String avatarConfig;
    }
}
