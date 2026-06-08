package com.questforge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.questforge.common.Result;
import com.questforge.dto.AdminDto;
import com.questforge.entity.SysUser;
import com.questforge.mapper.CoreMappers.SysUserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 API: 系统用户权限管理
 */
@RestController
@RequestMapping("/admin/user")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserMapper sysUserMapper;

    /**
     * 分页查询系统用户
     */
    @GetMapping("/page")
    public Result<Page<SysUser>> pageUsers(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getIsDeleted, 0).orderByDesc(SysUser::getCreateTime);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getRealName, keyword));
        }

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        // 抹除密码下发给前端
        page.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(page);
    }

    /**
     * 更新用户状态 (封禁/解禁)
     */
    @PutMapping("/status")
    public Result<Void> updateUserStatus(@RequestBody @Valid AdminDto.UserStatusUpdateReq req) {
        SysUser user = new SysUser();
        user.setId(req.getId());
        user.setStatus(req.getStatus());
        sysUserMapper.updateById(user);
        return Result.success();
    }
}