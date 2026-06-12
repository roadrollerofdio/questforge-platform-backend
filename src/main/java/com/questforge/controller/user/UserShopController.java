package com.questforge.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.Result;
import com.questforge.entity.ShopItem;
import com.questforge.entity.UserItem;
import com.questforge.mapper.ShopItemMapper;
import com.questforge.mapper.UserItemMapper;
import com.questforge.security.UserDetailsImpl;
import com.questforge.service.GemService;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户端：宝石小店(浏览/购买装扮)
 */
@RestController
@RequestMapping("/user/shop")
@RequiredArgsConstructor
public class UserShopController {

    private final ShopItemMapper shopItemMapper;
    private final UserItemMapper userItemMapper;
    private final GemService gemService;

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getSysUser().getId();
        }
        throw new IllegalStateException("未登录或会话已失效");
    }

    /**
     * 在售商品列表(含是否已拥有标记)
     */
    @GetMapping("/items")
    public Result<List<Map<String, Object>>> listItems() {
        Long userId = getCurrentUserId();

        List<ShopItem> items = shopItemMapper.selectList(new LambdaQueryWrapper<ShopItem>()
                .eq(ShopItem::getStatus, 1)
                .orderByAsc(ShopItem::getPrice));

        Set<Long> ownedIds = userItemMapper.selectList(new LambdaQueryWrapper<UserItem>()
                        .eq(UserItem::getUserId, userId))
                .stream().map(UserItem::getItemId).collect(Collectors.toSet());

        List<Map<String, Object>> result = new ArrayList<>();
        for (ShopItem item : items) {
            result.add(toItemMap(item, ownedIds.contains(item.getId())));
        }
        return Result.success(result);
    }

    /**
     * 购买商品: 扣宝石 + 写入背包
     */
    @PostMapping("/buy")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> buyItem(@RequestBody BuyReq req) {
        Long userId = getCurrentUserId();

        ShopItem item = shopItemMapper.selectById(req.getItemId());
        if (item == null || item.getStatus() != 1) {
            return Result.error(400, "商品不存在或已下架");
        }

        Long owned = userItemMapper.selectCount(new LambdaQueryWrapper<UserItem>()
                .eq(UserItem::getUserId, userId)
                .eq(UserItem::getItemId, item.getId()));
        if (owned > 0) {
            return Result.error(400, "你已经拥有该装扮了");
        }

        gemService.deductGems(userId, item.getPrice());

        UserItem userItem = new UserItem();
        userItem.setUserId(userId);
        userItem.setItemId(item.getId());
        userItemMapper.insert(userItem);

        return Result.success(null, "购买成功，快去装扮你的形象吧！");
    }

    /**
     * 我的已购装扮
     */
    @GetMapping("/my-items")
    public Result<List<Map<String, Object>>> myItems() {
        Long userId = getCurrentUserId();

        List<UserItem> userItems = userItemMapper.selectList(new LambdaQueryWrapper<UserItem>()
                .eq(UserItem::getUserId, userId));
        if (userItems.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        List<Long> itemIds = userItems.stream().map(UserItem::getItemId).toList();
        List<ShopItem> items = shopItemMapper.selectBatchIds(itemIds);

        List<Map<String, Object>> result = new ArrayList<>();
        for (ShopItem item : items) {
            result.add(toItemMap(item, true));
        }
        return Result.success(result);
    }

    private Map<String, Object> toItemMap(ShopItem item, boolean owned) {
        Map<String, Object> map = new HashMap<>();
        map.put("itemId", item.getId().toString());
        map.put("name", item.getName());
        map.put("slot", item.getSlot());
        map.put("svgKey", item.getSvgKey());
        map.put("price", item.getPrice());
        map.put("owned", owned);
        return map;
    }

    @Data
    public static class BuyReq {
        @NotNull
        private Long itemId;
    }
}
