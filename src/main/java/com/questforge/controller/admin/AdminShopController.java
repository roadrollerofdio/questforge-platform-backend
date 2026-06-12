package com.questforge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.Result;
import com.questforge.entity.ShopItem;
import com.questforge.mapper.ShopItemMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端：宝石小店商品管理(上架/下架/定价)
 */
@RestController
@RequestMapping("/admin/shop")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminShopController {

    private final ShopItemMapper shopItemMapper;

    /**
     * 全部商品(含已下架)
     */
    @GetMapping("/items")
    public Result<List<ShopItem>> listItems() {
        return Result.success(shopItemMapper.selectList(
                new LambdaQueryWrapper<ShopItem>().orderByAsc(ShopItem::getSlot).orderByAsc(ShopItem::getPrice)));
    }

    /**
     * 新增/编辑商品(svgKey 必须取自前端内置装扮部件库)
     */
    @PostMapping("/items/save")
    public Result<Long> saveItem(@RequestBody @Valid ItemSaveReq req) {
        ShopItem item;
        if (req.getId() != null) {
            item = shopItemMapper.selectById(req.getId());
            if (item == null) {
                return Result.error(400, "商品不存在");
            }
        } else {
            Long exists = shopItemMapper.selectCount(new LambdaQueryWrapper<ShopItem>()
                    .eq(ShopItem::getSvgKey, req.getSvgKey()));
            if (exists > 0) {
                return Result.error(400, "该装扮部件已上架过商品");
            }
            item = new ShopItem();
        }

        item.setName(req.getName());
        item.setSlot(req.getSlot());
        item.setSvgKey(req.getSvgKey());
        item.setPrice(req.getPrice());
        if (req.getStatus() != null) {
            item.setStatus(req.getStatus());
        } else if (item.getStatus() == null) {
            item.setStatus(1);
        }

        if (item.getId() == null) {
            shopItemMapper.insert(item);
        } else {
            shopItemMapper.updateById(item);
        }
        return Result.success(item.getId(), "商品已保存");
    }

    /**
     * 上架/下架
     */
    @PutMapping("/items/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        ShopItem item = shopItemMapper.selectById(id);
        if (item == null) {
            return Result.error(400, "商品不存在");
        }
        item.setStatus(status);
        shopItemMapper.updateById(item);
        return Result.success(null, status == 1 ? "商品已上架" : "商品已下架");
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/items/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        shopItemMapper.deleteById(id);
        return Result.success(null, "商品已删除");
    }

    @Data
    public static class ItemSaveReq {
        private Long id;
        @NotBlank(message = "商品名称不能为空")
        private String name;
        @NotBlank(message = "装扮部位不能为空")
        private String slot;
        @NotBlank(message = "装扮部件不能为空")
        private String svgKey;
        @NotNull(message = "价格不能为空")
        private Integer price;
        private Integer status;
    }
}
