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
        // 严谨的单条查询，防止脏数据导致报错
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)
        );

        if (user == null) {
            throw new UsernameNotFoundException("用户名不存在: " + username);
        }

        return new UserDetailsImpl(user);
    }
}