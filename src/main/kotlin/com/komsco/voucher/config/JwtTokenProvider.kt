package com.komsco.voucher.config

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret:#{null}}") secret: String?,
    @Value("\${jwt.expiration:86400000}") private val expirationMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val key: SecretKey

    init {
        if (secret == null) {
            log.warn("jwt.secret 미설정 — 랜덤 키 생성됨 (재시작 시 기존 토큰 무효화). 프로덕션에서는 반드시 jwt.secret을 설정하세요.")
            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            key = Keys.hmacShaKeyFor(randomBytes)
        } else {
            key = Keys.hmacShaKeyFor(secret.toByteArray())
        }
    }

    fun generateToken(memberId: Long, role: String): String {
        val now = Date()
        return Jwts.builder()
            .subject(memberId.toString())
            .claim("role", role)
            .issuedAt(now)
            .expiration(Date(now.time + expirationMs))
            .signWith(key)
            .compact()
    }

    fun getMemberIdFromToken(token: String): Long {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        return claims.subject.toLong()
    }

    fun getRoleFromToken(token: String): String {
        val claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
        return claims.get("role", String::class.java) ?: "USER"
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
            true
        } catch (e: Exception) {
            false
        }
    }
}
