package com.questforge.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息中心相关响应(公告 + 好友会话列表)
 */
public class MessageDto {

    /**
     * 用户端公告列表项
     */
    @Data
    public static class AnnouncementItem {
        private String id;
        private String title;
        private String content;
        private String publisherName;
        private LocalDateTime createTime;
    }

    /**
     * 会话列表项(每个好友一个会话)
     */
    @Data
    public static class ConversationItem {
        private String friendId;
        private String friendNickname;
        private String friendAvatarConfig;
        private String lastMessage;
        private LocalDateTime lastTime;
        private Integer unreadCount;
    }

    /**
     * 单条聊天消息
     */
    @Data
    public static class ChatMessageItem {
        private String id;
        private String senderId;
        private String receiverId;
        private String content;
        private Integer isRead;
        private LocalDateTime createTime;
        private Boolean mine; // 是否我发的(前端气泡用)
    }

    /**
     * 发送消息请求
     */
    @Data
    public static class SendMessageReq {
        private Long toUserId;
        private String content;
    }

    /**
     * 公告保存请求(管理端)
     */
    @Data
    public static class AnnouncementSaveReq {
        private Long id;
        private String title;
        private String content;
        private Integer status;
    }

    /**
     * 会话列表包装(用于一次性返回公告+会话+未读)
     */
    @Data
    public static class MessageOverviewResp {
        private List<AnnouncementItem> announcements;
        private List<ConversationItem> conversations;
        private Integer unreadMessageCount;
        private Integer unreadAnnouncementCount;
    }
}
