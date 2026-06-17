package com.questforge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.RedisConsts;
import com.questforge.common.Result;
import com.questforge.dto.AuthDto;
import com.questforge.entity.SysUser;
import com.questforge.mapper.SysUserMapper;
import com.questforge.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 系统统一身份认证中心
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/login")
    public Result<AuthDto.LoginResp> login(@RequestBody @Valid AuthDto.LoginReq req) {
        // 查库并校验密码
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername())
        );

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return Result.error(400, "用户名或密码错误");
        }
        if (user.getStatus() == 0) {
            return Result.error(403, "账号已被禁用，请联系管理员");
        }

        // 签发 JWT Token
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRoleCode());

        AuthDto.LoginResp resp = new AuthDto.LoginResp();
        resp.setToken(token);

        resp.setUserId(user.getId());

        resp.setRole("ROLE_" + user.getRoleCode());
        resp.setRealName(user.getRealName());

        return Result.success(resp);
    }

    /**
     * 注册请求载荷
     */
    @Data
    public static class RegisterReq {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        @NotBlank(message = "真实姓名不能为空")
        private String realName;
        private String roleCode = "USER";
    }

    /**
     * 注册功能
     */
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterReq req) {
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername())
        );
        if (count > 0) {
            return Result.error(400, "用户名已被占用，请更换");
        }

        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword())); // BCrypt强哈希加密
        user.setRealName(req.getRealName());
        user.setRoleCode(req.getRoleCode());
        user.setStatus(1);
        user.setNickname(req.getRealName());
        user.setGems(0);
        sysUserMapper.insert(user);

        return Result.success();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getInfo(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
            return Result.error(401, "无效的 Token");
        }

        String jwt = bearerToken.substring(7);
        Long userId = jwtUtils.getUserIdFromToken(jwt);

        SysUser user = sysUserMapper.selectById(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId().toString()); // 此处返回给前端转String防JS精度丢失
        data.put("username", user.getUsername());
        data.put("role", "ROLE_" + user.getRoleCode());
        data.put("realName", user.getRealName());
        return Result.success(data);
    }

    /**
     * 安全注销
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7);
            try {
                // 将失效的 token 存入 Redis 黑名单，有效拦截未过期的注销 token
                redisTemplate.opsForValue().set(
                        RedisConsts.TOKEN_BLACKLIST_PREFIX + jwt,
                        System.currentTimeMillis(),
                        24, TimeUnit.HOURS
                );
            } catch (Exception e) {
            }
        }
        return Result.success();
    }
}