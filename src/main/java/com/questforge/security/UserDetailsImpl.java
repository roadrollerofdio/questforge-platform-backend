package com.questforge.security;

import com.questforge.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security 用户核心实现类
 */
@Getter
public class UserDetailsImpl implements UserDetails {

    private final SysUser sysUser;

    public UserDetailsImpl(SysUser sysUser) {
        this.sysUser = sysUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 动态授予角色权限，格式如: ROLE_ADMIN, ROLE_USER
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + sysUser.getRoleCode()));
    }

    @Override
    public String getPassword() {
        return sysUser.getPassword();
    }

    @Override
    public String getUsername() {
        return sysUser.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // 状态 1-正常, 0-封禁。若为0则触发锁定异常
        return sysUser.getStatus() == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return sysUser.getStatus() == 1;
    }
}