package com.questforge.security;

import com.questforge.entity.SysUser;
import com.questforge.mapper.CoreMappers.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Spring Security 用户信息加载核心逻辑
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String userIdStr) throws UsernameNotFoundException {
        SysUser sysUser = sysUserMapper.selectById(Long.parseLong(userIdStr));

        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        if (sysUser.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        return new User(
                sysUser.getId().toString(), // 为了方便后续拿ID，将Subject设为ID
                sysUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + sysUser.getRoleCode()))
        );
    }
}