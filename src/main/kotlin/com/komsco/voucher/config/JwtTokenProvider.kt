package com.komsco.voucher.config

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret:default-secret-key-for-voucher-system-minimum-256-bits-long!!}") private val secret: String,
    @Value("\${jwt.expiration:86400000}") private val expirationMs: Long,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

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
        return claims["role"] as String
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
