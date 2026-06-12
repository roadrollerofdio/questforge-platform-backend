package com.questforge.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.questforge.common.Result;
import com.questforge.entity.FriendRelation;
import com.questforge.entity.SysUser;
import com.questforge.mapper.FriendRelationMapper;
import com.questforge.mapper.SysUserMapper;
import com.questforge.security.UserDetailsImpl;
import com.questforge.service.UserProfileService;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户端：好友列表 / 好友申请 / 昵称搜索
 */
@RestController
@RequestMapping("/user/friend")
@RequiredArgsConstructor
public class UserFriendController {

    private final FriendRelationMapper friendMapper;
    private final SysUserMapper sysUserMapper;
    private final UserProfileService userProfileService;

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl userDetails) {
            return userDetails.getSysUser().getId();
        }
        throw new IllegalStateException("未登录或会话已失效");
    }

    /**
     * 我的好友列表(双向已接受)
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listFriends() {
        Long userId = getCurrentUserId();
        List<FriendRelation> relations = friendMapper.selectList(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getStatus, 1)
                .and(w -> w.eq(FriendRelation::getFromUserId, userId).or().eq(FriendRelation::getToUserId, userId)));

        List<Map<String, Object>> result = new ArrayList<>();
        for (FriendRelation rel : relations) {
            Long friendId = rel.getFromUserId().equals(userId) ? rel.getToUserId() : rel.getFromUserId();
            Map<String, Object> brief = userProfileService.buildBrief(friendId);
            brief.put("passedStages", userProfileService.getPassedStagesCount(friendId));
            brief.put("learnedToday", userProfileService.hasLearnedToday(friendId));
            result.add(brief);
        }
        return Result.success(result);
    }

    /**
     * 收到的待处理好友申请
     */
    @GetMapping("/requests")
    public Result<List<Map<String, Object>>> listRequests() {
        Long userId = getCurrentUserId();
        List<FriendRelation> requests = friendMapper.selectList(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getToUserId, userId)
                .eq(FriendRelation::getStatus, 0)
                .orderByDesc(FriendRelation::getCreateTime));

        List<Map<String, Object>> result = new ArrayList<>();
        for (FriendRelation rel : requests) {
            Map<String, Object> brief = userProfileService.buildBrief(rel.getFromUserId());
            brief.put("requestId", rel.getId().toString());
            result.add(brief);
        }
        return Result.success(result);
    }

    /**
     * 按昵称搜索用户(支持昵称/姓名/账号模糊匹配)
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> searchUsers(@RequestParam String keyword) {
        Long userId = getCurrentUserId();
        if (keyword == null || keyword.isBlank()) {
            return Result.success(new ArrayList<>());
        }

        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .ne(SysUser::getId, userId)
                .and(w -> w.like(SysUser::getNickname, keyword)
                        .or().like(SysUser::getRealName, keyword)
                        .or().like(SysUser::getUsername, keyword))
                .last("LIMIT 20"));

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser user : users) {
            Map<String, Object> brief = userProfileService.buildBrief(user);
            brief.put("relationStatus", getRelationStatus(userId, user.getId()));
            result.add(brief);
        }
        return Result.success(result);
    }

    /**
     * 发送好友申请
     */
    @PostMapping("/request")
    public Result<Void> sendRequest(@RequestBody FriendRequestReq req) {
        Long userId = getCurrentUserId();
        Long targetId = req.getToUserId();

        if (targetId == null || targetId.equals(userId)) {
            return Result.error(400, "无法添加该用户为好友");
        }
        if (sysUserMapper.selectById(targetId) == null) {
            return Result.error(400, "用户不存在");
        }

        String status = getRelationStatus(userId, targetId);
        if ("friend".equals(status)) {
            return Result.error(400, "你们已经是好友啦");
        }
        if ("pending".equals(status)) {
            return Result.error(400, "已有待处理的好友申请");
        }

        FriendRelation rel = new FriendRelation();
        rel.setFromUserId(userId);
        rel.setToUserId(targetId);
        rel.setStatus(0);
        friendMapper.insert(rel);
        return Result.success(null, "好友申请已发送");
    }

    /**
     * 处理好友申请(接受/拒绝)
     */
    @PostMapping("/handle")
    public Result<Void> handleRequest(@RequestBody FriendHandleReq req) {
        Long userId = getCurrentUserId();
        FriendRelation rel = friendMapper.selectById(req.getRequestId());

        if (rel == null || !rel.getToUserId().equals(userId) || rel.getStatus() != 0) {
            return Result.error(400, "申请不存在或已处理");
        }

        rel.setStatus(Boolean.TRUE.equals(req.getAccept()) ? 1 : 2);
        friendMapper.updateById(rel);
        return Result.success(null, rel.getStatus() == 1 ? "已添加好友" : "已拒绝申请");
    }

    /**
     * 双方关系: none / pending / friend
     */
    private String getRelationStatus(Long userId, Long otherId) {
        List<FriendRelation> relations = friendMapper.selectList(new LambdaQueryWrapper<FriendRelation>()
                .ne(FriendRelation::getStatus, 2)
                .and(w -> w
                        .and(x -> x.eq(FriendRelation::getFromUserId, userId).eq(FriendRelation::getToUserId, otherId))
                        .or(x -> x.eq(FriendRelation::getFromUserId, otherId).eq(FriendRelation::getToUserId, userId))));

        if (relations.stream().anyMatch(r -> r.getStatus() == 1)) return "friend";
        if (relations.stream().anyMatch(r -> r.getStatus() == 0)) return "pending";
        return "none";
    }

    @Data
    public static class FriendRequestReq {
        @NotNull
        private Long toUserId;
    }

    @Data
    public static class FriendHandleReq {
        @NotNull
        private Long requestId;
        private Boolean accept;
    }
}
