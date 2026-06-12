package com.questforge.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.entity.SysUser;
import com.questforge.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 真实有效的 Spring Security 数据库验证核心服务
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user;
        // JWT 过滤器传入的是 userId 字符串，需按 ID 查询
        if (username.matches("\\d+")) {
            user = sysUserMapper.selectById(Long.parseLong(username));
        } else {
            user = sysUserMapper.selectOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
            );
        }

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        return new UserDetailsImpl(user);
    }
}