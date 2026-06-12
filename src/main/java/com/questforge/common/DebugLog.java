package com.questforge.common;

// #region agent log
/**
 * 临时调试日志工具(调试会话 03a64a 用, 调试结束后删除)
 */
public final class DebugLog {
    private static final String LOG_PATH = "C:/Users/27273/IdeaProjects/exam-platform-backend/debug-03a64a.log";

    public static void log(String hypothesisId, String location, String message, String dataJson) {
        try {
            String safeData = (dataJson == null || dataJson.isBlank()) ? "{}" : dataJson;
            String line = String.format(
                    "{\"sessionId\":\"03a64a\",\"hypothesisId\":\"%s\",\"location\":\"%s\",\"message\":\"%s\",\"data\":%s,\"timestamp\":%d}%n",
                    hypothesisId, location, message, safeData, System.currentTimeMillis());
            java.nio.file.Files.write(java.nio.file.Paths.get(LOG_PATH),
                    line.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
// #endregion
