package com.example.myprojectbackend.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class JWTUtils {
    @Value("${spring.security.jwt.key}")
    String key;
    @Value("${spring.security.jwt.expire}")
    int expire;
    @Resource
   StringRedisTemplate template;

    /**
     * 创建JWT
     *
     * @param details
     * @param id
     * @param username
     * @return
     */
    public String createJWT(UserDetails details, int id, String username) {
        Algorithm algorithm = Algorithm.HMAC256(key);
        Date expire = this.expireTime();
        return JWT.create()
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("id", id)
                .withClaim("name", username)
                .withClaim("authorities", details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .withExpiresAt(expire)
                .withIssuedAt(new Date())
                .sign(algorithm);
    }

    /**
     * 解析JWT
     *
     * @param headerToken
     * @return
     */
    public DecodedJWT resolveJWT(String headerToken) {
        String token = convertToken(headerToken);
        if (token == null) {
            return null;
        }
        Algorithm algorithm = Algorithm.HMAC256(key);
        JWTVerifier verifier = JWT.require(algorithm).build();
        try {
            DecodedJWT decodedJWT = verifier.verify(token);
            if(this.isInvalidJwt(decodedJWT.getId())) return null;
            Date expiresAt = decodedJWT.getExpiresAt();
            return new Date().after(expiresAt) ? null : decodedJWT;
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    /**
     * 失效Jwt
     * 采用黑名单的方式使Jwt失效
     * @param headerToken
     * @return
     */

    public boolean invalidateJwt(String headerToken) {
        String token = convertToken(headerToken);
        if (token == null) return false;
        Algorithm algorithm = Algorithm.HMAC256(key);
        JWTVerifier verifier = JWT.require(algorithm).build();
        try {
            DecodedJWT decodedJWT = verifier.verify(token);
            String id = decodedJWT.getId();
            return deleteJwt(id, decodedJWT.getExpiresAt());
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    /**
     * 生成过期日期
     *
     * @return
     */
    public Date expireTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, expire * 24);
        return calendar.getTime();
    }

    public int toId(DecodedJWT jwt) {
        Map<String, Claim> claims = jwt.getClaims();
        return claims.get("id").asInt();
    }

    /**
     * 解析用户
     *
     * @return
     */
    public UserDetails toUser(DecodedJWT jwt) {
        Map<String, Claim> claims = jwt.getClaims();
        return User.withUsername(claims.get("name").asString())
                .password("********")
                .authorities(claims.get("authorities").asArray(String.class))
                .build();
    }

    /**
     * 验证token
     *
     * @param headerToken
     * @return
     */
    private String convertToken(String headerToken) {
        if (headerToken == null || !headerToken.startsWith("Bearer "))
            return null;
        return headerToken.substring(7);
    }

   /**
    * 将Jwt放入黑名单中，并设置过期时间
    * 黑名单用Redis存储
    * @param uuid
    * @param time
    * @return
    */
    private  boolean deleteJwt(String uuid, Date time) {
      if(this.isInvalidJwt(uuid)) return false;
      Date now = new Date();
      long expire = Math.max(time.getTime() - now.getTime(), 0);
      template.opsForValue().setIfAbsent(Const.JWT_BLACK_LIST + uuid, "", expire, TimeUnit.MILLISECONDS);
      return true;
    }

    /**
     * 验证Jwt是否失效
     * @param uuid
     * @return
     */
    private boolean isInvalidJwt(String uuid) {
      return Boolean.TRUE.equals(template.hasKey(Const.JWT_BLACK_LIST + uuid));
    }


}
