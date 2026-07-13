package com.ahao.haochat.common.common.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class JwtUtils {

    @Value("${haochat.jwt.secret}")
    private String secret;

    private static final String UID_CLAIM = "uid";
    private static final String CREATE_TIME = "createTime";
    private static final String SESSION_ID = "sid";
    private static final String TOKEN_TYPE = "typ";
    private static final String JWT_ID = "jti";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    /**
     * Legacy token creation. New code should use createAccessToken.
     */
    public String createToken(Long uid) {
        return createAccessToken(uid, null, 5 * 24 * 60L);
    }

    public String createAccessToken(Long uid, Long sessionId, long expireMinutes) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMinutes * 60_000L);
        com.auth0.jwt.JWTCreator.Builder builder = JWT.create()
                .withClaim(UID_CLAIM, uid)
                .withClaim(TOKEN_TYPE, TYPE_ACCESS)
                .withClaim(CREATE_TIME, now)
                .withExpiresAt(expireAt);
        if (sessionId != null) {
            builder.withClaim(SESSION_ID, sessionId);
        }
        return builder.sign(Algorithm.HMAC256(secret));
    }

    public String createRefreshToken(Long uid, Long sessionId, String jwtId, long expireDays) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireDays * 24 * 60 * 60_000L);
        return JWT.create()
                .withClaim(UID_CLAIM, uid)
                .withClaim(SESSION_ID, sessionId)
                .withClaim(TOKEN_TYPE, TYPE_REFRESH)
                .withClaim(JWT_ID, jwtId)
                .withClaim(CREATE_TIME, now)
                .withExpiresAt(expireAt)
                .sign(Algorithm.HMAC256(secret));
    }

    public Map<String, Claim> verifyToken(String token) {
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret)).build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getClaims();
        } catch (Exception e) {
            log.error("decode error,token:{}", token, e);
        }
        return null;
    }

    public Long getUidOrNull(String token) {
        return Optional.ofNullable(verifyToken(token))
                .map(map -> map.get(UID_CLAIM))
                .map(Claim::asLong)
                .orElse(null);
    }

    public Long getSessionIdOrNull(String token) {
        return Optional.ofNullable(verifyToken(token))
                .map(map -> map.get(SESSION_ID))
                .map(Claim::asLong)
                .orElse(null);
    }

    public String getTokenTypeOrNull(String token) {
        return Optional.ofNullable(verifyToken(token))
                .map(map -> map.get(TOKEN_TYPE))
                .map(Claim::asString)
                .orElse(null);
    }

    public String getJwtIdOrNull(String token) {
        return Optional.ofNullable(verifyToken(token))
                .map(map -> map.get(JWT_ID))
                .map(Claim::asString)
                .orElse(null);
    }
}
