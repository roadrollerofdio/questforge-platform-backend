package com.questforge.common;

/**
 * Redis Key 规范枚举与常量
 */
public class RedisConsts {
    // 试卷缓存 (Type: String/JSON) TTL: 考试时长 + 30m
    public static final String PAPER_INFO_PREFIX = "exam:paper:info:";

    // ====== 新增：关卡数据预热缓存（修复 UserStageServiceImpl 报错） ======
    public static final String STAGE_INFO_PREFIX = "exam:stage:info:";

    // 用户答题会话 (Type: Hash) 考完主动删，兜底 12h
    public static final String SESSION_ANS_PREFIX = "exam:session:ans:";

    // 排行榜 (Type: ZSet)
    public static final String LEADERBOARD_PREFIX = "exam:leaderboard:";

    // 防重复交卷分布式锁 (Type: String)
    public static final String SUBMIT_LOCK_PREFIX = "lock:exam:submit:";

    // JWT 黑名单 (Type: String)
    public static final String TOKEN_BLACKLIST_PREFIX = "auth:token:blacklist:";

    public static String getSessionKey(Long paperId, Long userId) {
        return SESSION_ANS_PREFIX + paperId + ":" + userId;
    }
}