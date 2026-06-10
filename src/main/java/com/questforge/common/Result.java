package com.questforge.common;

import lombok.Data;
import java.io.Serializable;

/**
 * 接口统一响应结构
 */
@Data
public class Result<T> implements Serializable {
    private Integer code;
    private String message;
    private T data;
    private Long timestamp;

    private Result() {
        this.timestamp = System.currentTimeMillis() / 1000;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    // ====== 新增：处理带自定义 message 的成功响应，修复 Controller 层报错 ======
    public static <T> Result<T> success(T data, String message) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setData(null);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
}