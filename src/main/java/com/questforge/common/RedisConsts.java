package com.questforge.common;

/**
 * Redis Key 规范枚举与常量
 */
public interface RedisConsts {
    // 试卷缓存 (Type: String/JSON) TTL: 考试时长 + 30m
    String PAPER_INFO_PREFIX = "exam:paper:info:";

    // 用户答题会话 (Type: Hash) 考完主动删，兜底 12h
    String SESSION_ANS_PREFIX = "exam:session:ans:";

    // 排行榜 (Type: ZSet)
    String LEADERBOARD_PREFIX = "exam:leaderboard:";

    // 防重复交卷分布式锁 (Type: String)
    String SUBMIT_LOCK_PREFIX = "lock:exam:submit:";

    // JWT 黑名单 (Type: String)
    String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";

    static String getSessionKey(Long paperId, Long userId) {
        return SESSION_ANS_PREFIX + paperId + ":" + userId;
    }
}