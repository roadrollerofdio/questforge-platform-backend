package com.questforge.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 核心工具类 (生成、解析、校验)
 */
@Slf4j
@Component
public class JwtUtils {

    // 增加配置读取容错：若未读取到，则使用与 application.properties 严格一致的缺省值
    @Value("${jwt.secret:Y29tcGxleF9hbmRfc2VjdXJlX2p3dF9zZWNyZXRfa2V5X2V4YW1fcGxhdGZvcm1fMjAyNg==}")
    private String secret;

    @Value("${jwt.expiration:86400}")
    private Long expiration;

    /**
     * 生成安全密钥
     */
    private SecretKey getSecretKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token
     * @param userId 用户ID
     * @param username 用户名
     * @param role 角色
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 从 Token 中获取用户ID (Subject)
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSecretKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException ex) {
            log.error("非法的 JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("JWT token 已过期");
        } catch (UnsupportedJwtException ex) {
            log.error("不支持的 JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims 字符串为空");
        }
        return false;
    }
}