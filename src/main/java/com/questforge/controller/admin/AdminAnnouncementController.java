package com.questforge.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.Result;
import com.questforge.dto.MessageDto;
import com.questforge.entity.Announcement;
import com.questforge.entity.SysUser;
import com.questforge.mapper.AnnouncementMapper;
import com.questforge.mapper.SysUserMapper;
import com.questforge.security.UserDetailsImpl;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端：公告发布与管理
 */
@RestController
@RequestMapping("/admin/announcement")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AnnouncementMapper announcementMapper;
    private final SysUserMapper sysUserMapper;

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getSysUser().getId();
        }
        throw new IllegalStateException("未登录");
    }

    /**
     * 公告列表(全部)
     */
    @GetMapping("/list")
    public Result<List<Announcement>> list() {
        List<Announcement> list = announcementMapper.selectList(
                new LambdaQueryWrapper<Announcement>().orderByDesc(Announcement::getCreateTime));
        return Result.success(list);
    }

    /**
     * 发布 / 编辑公告
     */
    @PostMapping("/save")
    public Result<Long> save(@RequestBody SaveReq req) {
        if (!StringUtils.hasText(req.getTitle())) {
            return Result.error(400, "公告标题不能为空");
        }
        if (!StringUtils.hasText(req.getContent())) {
            return Result.error(400, "公告内容不能为空");
        }

        Announcement ann;
        if (req.getId() != null) {
            ann = announcementMapper.selectById(req.getId());
            if (ann == null) {
                return Result.error(400, "公告不存在");
            }
        } else {
            ann = new Announcement();
            ann.setPublisherId(getCurrentUserId());
        }

        ann.setTitle(req.getTitle().trim());
        ann.setContent(req.getContent().trim());
        ann.setStatus(req.getStatus() != null ? req.getStatus() : 1);

        if (ann.getId() == null) {
            announcementMapper.insert(ann);
        } else {
            announcementMapper.updateById(ann);
        }
        return Result.success(ann.getId(), req.getId() == null ? "公告已发布" : "公告已更新");
    }

    /**
     * 删除公告
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        announcementMapper.deleteById(id);
        return Result.success(null, "公告已删除");
    }

    /**
     * 切换上下架
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Announcement ann = new Announcement();
        ann.setId(id);
        ann.setStatus(status);
        announcementMapper.updateById(ann);
        return Result.success(null, status == 1 ? "公告已发布" : "公告已下线");
    }

    /**
     * 填充发布人姓名(内部工具)
     */
    public static Map<Long, String> buildPublisherNameMap(List<Announcement> list, SysUserMapper userMapper) {
        if (list == null || list.isEmpty()) return Map.of();
        List<Long> ids = list.stream().map(Announcement::getPublisherId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Map.of();
        List<SysUser> users = userMapper.selectBatchIds(ids);
        return users.stream().collect(Collectors.toMap(SysUser::getId, u -> u.getNickname() != null ? u.getNickname() : u.getRealName(), (a, b) -> a));
    }

    @Data
    public static class SaveReq {
        private Long id;
        @NotBlank(message = "标题不能为空")
        private String title;
        @NotBlank(message = "内容不能为空")
        private String content;
        private Integer status;
    }
}
