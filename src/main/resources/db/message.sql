-- =============================================================
-- 消息中心 迁移脚本
-- 包含: announcement 公告表 + chat_message 私聊消息表
-- =============================================================

-- 1. 系统公告
CREATE TABLE IF NOT EXISTS announcement (
    id           BIGINT       NOT NULL PRIMARY KEY,
    title        VARCHAR(128) NOT NULL COMMENT '公告标题',
    content      TEXT         NOT NULL COMMENT '公告内容',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '0-下线 1-发布中',
    publisher_id BIGINT       DEFAULT NULL COMMENT '发布人用户ID',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT      DEFAULT 0,
    KEY idx_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告';

-- 2. 好友私聊消息
CREATE TABLE IF NOT EXISTS chat_message (
    id           BIGINT   NOT NULL PRIMARY KEY,
    sender_id    BIGINT   NOT NULL COMMENT '发送方',
    receiver_id  BIGINT   NOT NULL COMMENT '接收方',
    content      VARCHAR(2000) NOT NULL COMMENT '消息内容',
    is_read      TINYINT  NOT NULL DEFAULT 0 COMMENT '0-未读 1-已读',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT  DEFAULT 0,
    KEY idx_sender_receiver (sender_id, receiver_id, create_time),
    KEY idx_receiver_sender (receiver_id, sender_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友私聊消息';
