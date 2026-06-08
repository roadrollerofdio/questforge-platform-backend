package com.questforge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.RedisConsts;
import com.questforge.common.Result;
import com.questforge.dto.AuthDto;
import com.questforge.entity.SysUser;
import com.questforge.mapper.CoreMappers.SysUserMapper;
import com.questforge.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证授权控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 1. 用户登录
     */
    @PostMapping("/login")
    public Result<AuthDto.LoginResp> login(@RequestBody @Valid AuthDto.LoginReq req) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername())
        );

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return Result.error(400, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            return Result.error(403, "账号已被禁用，请联系管理员");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRoleCode());

        AuthDto.LoginResp resp = new AuthDto.LoginResp();
        resp.setToken(token);
        resp.setUserId(user.getId().toString()); // 雪花算法ID给前端需转String防精度丢失
        resp.setRole("ROLE_" + user.getRoleCode());
        resp.setRealName(user.getRealName());

        return Result.success(resp);
    }

    /**
     * 用户注册 (新增功能)
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid AuthDto.RegisterReq req) {
        Long count = sysUserMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, req.getUsername())
        );
        if (count > 0) {
            return Result.error(400, "用户名已被占用，请更换");
        }

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword())); // BCrypt加密
        user.setRealName(req.getRealName());
        user.setRoleCode(req.getRoleCode());
        user.setStatus(1);
        sysUserMapper.insert(user);

        return Result.success();
    }

    /**
     * 2. 退出登录 (加入 Redis 黑名单)
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7);
            // 解析出剩余时间，并存入Redis黑名单
            try {
                long expirationTime = jwtUtils.getUserIdFromToken(jwt);
                // 将失效token存入Redis，存活时间24小时
                redisTemplate.opsForValue().set(
                        RedisConsts.TOKEN_BLACKLIST_PREFIX + jwt,
                        System.currentTimeMillis(),
                        24, TimeUnit.HOURS
                );
            } catch (Exception e) {
                //
            }
        }
        return Result.success();
    }

    /**
     * 3. 获取当前用户信息 (测试鉴权是否生效)
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getInfo(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        String jwt = bearerToken.substring(7);
        Long userId = jwtUtils.getUserIdFromToken(jwt);

        SysUser user = sysUserMapper.selectById(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId().toString());
        data.put("username", user.getUsername());
        data.put("role", "ROLE_" + user.getRoleCode());
        return Result.success(data);
    }
}