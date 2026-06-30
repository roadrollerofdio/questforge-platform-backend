package com.questforge.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.questforge.controller.admin.AdminAnnouncementController;
import com.questforge.common.Result;
import com.questforge.dto.MessageDto;
import com.questforge.entity.Announcement;
import com.questforge.entity.ChatMessage;
import com.questforge.entity.FriendRelation;
import com.questforge.entity.SysUser;
import com.questforge.mapper.AnnouncementMapper;
import com.questforge.mapper.ChatMessageMapper;
import com.questforge.mapper.FriendRelationMapper;
import com.questforge.mapper.SysUserMapper;
import com.questforge.security.UserDetailsImpl;
import com.questforge.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户端：消息中心
 * - 公告列表
 * - 好友会话列表(含未读数)
 * - 与某好友的聊天记录
 * - 发送消息(自动标记对方已发给我的未读为已读)
 */
@RestController
@RequestMapping("/user/message")
@RequiredArgsConstructor
public class UserMessageController {

    private final AnnouncementMapper announcementMapper;
    private final ChatMessageMapper chatMessageMapper;
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
     * 消息中心首页数据(公告列表 + 好友会话 + 未读统计)
     */
    @GetMapping("/overview")
    public Result<MessageDto.MessageOverviewResp> overview() {
        Long userId = getCurrentUserId();

        // 1. 已发布公告(按时间倒序, 最多 20 条)
        List<Announcement> anns = announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, 1)
                .orderByDesc(Announcement::getCreateTime)
                .last("LIMIT 20"));
        Map<Long, String> publisherMap = AdminAnnouncementController.buildPublisherNameMap(anns, sysUserMapper);
        List<MessageDto.AnnouncementItem> annItems = anns.stream().map(a -> {
            MessageDto.AnnouncementItem it = new MessageDto.AnnouncementItem();
            it.setId(a.getId().toString());
            it.setTitle(a.getTitle());
            it.setContent(a.getContent());
            it.setPublisherName(publisherMap.getOrDefault(a.getPublisherId(), "管理员"));
            it.setCreateTime(a.getCreateTime());
            return it;
        }).collect(Collectors.toList());

        // 2. 好友会话(基于 friend_relation 状态=1 的所有好友)
        List<FriendRelation> relations = friendMapper.selectList(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getStatus, 1)
                .and(w -> w.eq(FriendRelation::getFromUserId, userId).or().eq(FriendRelation::getToUserId, userId)));
        Set<Long> friendIds = relations.stream()
                .map(r -> r.getFromUserId().equals(userId) ? r.getToUserId() : r.getFromUserId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<MessageDto.ConversationItem> convItems = new ArrayList<>();
        int totalUnread = 0;
        for (Long friendId : friendIds) {
            MessageDto.ConversationItem item = buildConversationItem(userId, friendId);
            convItems.add(item);
            if (item.getUnreadCount() != null) totalUnread += item.getUnreadCount();
        }
        // 按最后消息时间倒序
        convItems.sort((a, b) -> {
            if (a.getLastTime() == null && b.getLastTime() == null) return 0;
            if (a.getLastTime() == null) return 1;
            if (b.getLastTime() == null) return -1;
            return b.getLastTime().compareTo(a.getLastTime());
        });

        MessageDto.MessageOverviewResp resp = new MessageDto.MessageOverviewResp();
        resp.setAnnouncements(annItems);
        resp.setConversations(convItems);
        resp.setUnreadMessageCount(totalUnread);
        resp.setUnreadAnnouncementCount(annItems.size());
        return Result.success(resp);
    }

    /**
     * 公告列表(用户端独立接口)
     */
    @GetMapping("/announcements")
    public Result<List<MessageDto.AnnouncementItem>> announcements() {
        List<Announcement> anns = announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, 1)
                .orderByDesc(Announcement::getCreateTime)
                .last("LIMIT 50"));
        Map<Long, String> publisherMap = AdminAnnouncementController.buildPublisherNameMap(anns, sysUserMapper);
        List<MessageDto.AnnouncementItem> list = anns.stream().map(a -> {
            MessageDto.AnnouncementItem it = new MessageDto.AnnouncementItem();
            it.setId(a.getId().toString());
            it.setTitle(a.getTitle());
            it.setContent(a.getContent());
            it.setPublisherName(publisherMap.getOrDefault(a.getPublisherId(), "管理员"));
            it.setCreateTime(a.getCreateTime());
            return it;
        }).collect(Collectors.toList());
        return Result.success(list);
    }

    /**
     * 公告详情(同时将本次浏览视为已读: 不在 DB 中记录, 由前端传 id 集合判断)
     */
    @GetMapping("/announcement/{id}")
    public Result<MessageDto.AnnouncementItem> announcementDetail(@PathVariable Long id) {
        Announcement a = announcementMapper.selectById(id);
        if (a == null || a.getStatus() != 1) {
            return Result.error(400, "公告不存在或已下线");
        }
        MessageDto.AnnouncementItem it = new MessageDto.AnnouncementItem();
        it.setId(a.getId().toString());
        it.setTitle(a.getTitle());
        it.setContent(a.getContent());
        SysUser pub = a.getPublisherId() != null ? sysUserMapper.selectById(a.getPublisherId()) : null;
        it.setPublisherName(pub != null ? (pub.getNickname() != null ? pub.getNickname() : pub.getRealName()) : "管理员");
        it.setCreateTime(a.getCreateTime());
        return Result.success(it);
    }

    /**
     * 与某好友的聊天记录
     */
    @GetMapping("/chat/{friendId}")
    public Result<List<MessageDto.ChatMessageItem>> chatHistory(@PathVariable Long friendId) {
        Long userId = getCurrentUserId();
        if (!isFriend(userId, friendId)) {
            return Result.error(400, "只能与好友聊天");
        }

        List<ChatMessage> messages = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .and(w -> w
                        .and(x -> x.eq(ChatMessage::getSenderId, userId).eq(ChatMessage::getReceiverId, friendId))
                        .or(x -> x.eq(ChatMessage::getSenderId, friendId).eq(ChatMessage::getReceiverId, userId)))
                .orderByAsc(ChatMessage::getCreateTime)
                .last("LIMIT 500"));

        // 进入会话时, 把对方发给我的消息标记为已读
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getSenderId, friendId)
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getIsRead, 0)
                .set(ChatMessage::getIsRead, 1));

        List<MessageDto.ChatMessageItem> items = messages.stream().map(m -> {
            MessageDto.ChatMessageItem it = new MessageDto.ChatMessageItem();
            it.setId(m.getId().toString());
            it.setSenderId(m.getSenderId().toString());
            it.setReceiverId(m.getReceiverId().toString());
            it.setContent(m.getContent());
            it.setIsRead(m.getIsRead());
            it.setCreateTime(m.getCreateTime());
            it.setMine(m.getSenderId().equals(userId));
            return it;
        }).collect(Collectors.toList());
        return Result.success(items);
    }

    /**
     * 发送消息给好友
     */
    @PostMapping("/send")
    public Result<MessageDto.ChatMessageItem> send(@RequestBody MessageDto.SendMessageReq req) {
        Long userId = getCurrentUserId();
        Long toUserId = req.getToUserId();
        if (toUserId == null || toUserId.equals(userId)) {
            return Result.error(400, "消息接收方无效");
        }
        String content = req.getContent() == null ? "" : req.getContent().trim();
        if (!StringUtils.hasText(content)) {
            return Result.error(400, "消息内容不能为空");
        }
        if (content.length() > 1000) {
            return Result.error(400, "消息内容不能超过 1000 字");
        }
        if (!isFriend(userId, toUserId)) {
            return Result.error(400, "只能与好友聊天");
        }

        ChatMessage msg = new ChatMessage();
        msg.setSenderId(userId);
        msg.setReceiverId(toUserId);
        msg.setContent(content);
        msg.setIsRead(0);
        chatMessageMapper.insert(msg);

        MessageDto.ChatMessageItem it = new MessageDto.ChatMessageItem();
        it.setId(msg.getId().toString());
        it.setSenderId(userId.toString());
        it.setReceiverId(toUserId.toString());
        it.setContent(content);
        it.setIsRead(0);
        it.setCreateTime(msg.getCreateTime());
        it.setMine(true);
        return Result.success(it, "已发送");
    }

    /**
     * 标记与某好友的聊天记录为已读(前端进入会话时也可单独调用)
     */
    @PostMapping("/chat/{friendId}/read")
    public Result<Void> markRead(@PathVariable Long friendId) {
        Long userId = getCurrentUserId();
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getSenderId, friendId)
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getIsRead, 0)
                .set(ChatMessage::getIsRead, 1));
        return Result.success(null, "已标记已读");
    }

    // ===== 工具方法 =====

    private boolean isFriend(Long userId, Long otherId) {
        List<FriendRelation> relations = friendMapper.selectList(new LambdaQueryWrapper<FriendRelation>()
                .eq(FriendRelation::getStatus, 1)
                .and(w -> w
                        .and(x -> x.eq(FriendRelation::getFromUserId, userId).eq(FriendRelation::getToUserId, otherId))
                        .or(x -> x.eq(FriendRelation::getFromUserId, otherId).eq(FriendRelation::getToUserId, userId))));
        return !relations.isEmpty();
    }

    private MessageDto.ConversationItem buildConversationItem(Long userId, Long friendId) {
        SysUser friend = sysUserMapper.selectById(friendId);
        MessageDto.ConversationItem item = new MessageDto.ConversationItem();
        item.setFriendId(friendId.toString());
        if (friend != null) {
            item.setFriendNickname(friend.getNickname() != null ? friend.getNickname() : friend.getRealName());
            item.setFriendAvatarConfig(friend.getAvatarConfig());
        } else {
            item.setFriendNickname("已注销用户");
        }

        // 最新一条消息(双向)
        List<ChatMessage> latest = chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .and(w -> w
                        .and(x -> x.eq(ChatMessage::getSenderId, userId).eq(ChatMessage::getReceiverId, friendId))
                        .or(x -> x.eq(ChatMessage::getSenderId, friendId).eq(ChatMessage::getReceiverId, userId)))
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT 1"));
        if (!latest.isEmpty()) {
            ChatMessage m = latest.get(0);
            item.setLastMessage(m.getContent());
            item.setLastTime(m.getCreateTime());
        }

        // 对方发来的未读数
        Long unread = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSenderId, friendId)
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getIsRead, 0));
        item.setUnreadCount(unread.intValue());
        return item;
    }
}
