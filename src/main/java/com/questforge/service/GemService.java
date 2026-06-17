package com.questforge.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.questforge.entity.SysUser;
import com.questforge.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 宝石账户服务: 加/扣宝石
 */
@Service
public class GemService {

    private static final Logger log = LoggerFactory.getLogger(GemService.class);

    private final SysUserMapper sysUserMapper;

    public GemService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 发放宝石
     * 使用 REQUIRES_NEW 独立事务: 发奖失败不得回滚调用方(如关卡判分)的核心事务
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void addGems(Long userId, int amount) {
        if (amount <= 0) return;
        sysUserMapper.update(null, new UpdateWrapper<SysUser>()
                .setSql("gems = IFNULL(gems, 0) + " + amount)
                .eq("id", userId));
        log.info("【宝石发放】用户 {} 获得 {} 宝石", userId, amount);
    }

    /**
     * 扣减宝石(余额不足抛异常); 利用条件更新保证并发下不扣成负数
     */
    public void deductGems(Long userId, int amount) {
        if (amount <= 0) return;
        int updated = sysUserMapper.update(null, new UpdateWrapper<SysUser>()
                .setSql("gems = gems - " + amount)
                .eq("id", userId)
                .ge("gems", amount));
        if (updated == 0) {
            throw new RuntimeException("宝石余额不足");
        }
        log.info("【宝石扣减】用户 {} 消费 {} 宝石", userId, amount);
    }

    public int getBalance(Long userId) {
        return sysUserMapper.selectMaps(new QueryWrapper<SysUser>()
                        .select("gems")
                        .eq("id", userId))
                .stream()
                .findFirst()
                .map(row -> row.get("gems"))
                .map(v -> v instanceof Number n ? n.intValue() : 0)
                .orElse(0);
    }
}
