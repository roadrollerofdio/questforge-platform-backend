package com.questforge.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.questforge.entity.SysUser;
import com.questforge.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 宝石账户服务: 加/扣宝石
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GemService {

    private final SysUserMapper sysUserMapper;

    /**
     * 发放宝石
     */
    public void addGems(Long userId, int amount) {
        if (amount <= 0) return;
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .setSql("gems = IFNULL(gems, 0) + " + amount)
                .eq(SysUser::getId, userId));
        log.info("【宝石发放】用户 {} 获得 {} 宝石", userId, amount);
    }

    /**
     * 扣减宝石(余额不足抛异常); 利用条件更新保证并发下不扣成负数
     */
    public void deductGems(Long userId, int amount) {
        if (amount <= 0) return;
        int updated = sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .setSql("gems = gems - " + amount)
                .eq(SysUser::getId, userId)
                .ge(SysUser::getGems, amount));
        if (updated == 0) {
            throw new RuntimeException("宝石余额不足");
        }
        log.info("【宝石扣减】用户 {} 消费 {} 宝石", userId, amount);
    }

    public int getBalance(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        return user != null && user.getGems() != null ? user.getGems() : 0;
    }
}
