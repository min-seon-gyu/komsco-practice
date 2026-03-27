package com.komsco.voucher.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                // 공개 엔드포인트
                it.requestMatchers("/api/v1/members/register", "/api/v1/members/login").permitAll()
                it.requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // 관리자 전용
                it.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                it.requestMatchers("/api/v1/regions/**").hasRole("ADMIN")
                it.requestMatchers("/api/v1/members/{id}/suspend", "/api/v1/members/{id}/unsuspend", "/api/v1/members/{id}/withdraw").hasRole("ADMIN")
                it.requestMatchers("/api/v1/merchants/{id}/approve", "/api/v1/merchants/{id}/reject").hasRole("ADMIN")
                it.requestMatchers("/api/v1/merchants/{id}/suspend", "/api/v1/merchants/{id}/unsuspend", "/api/v1/merchants/{id}/terminate").hasRole("ADMIN")
                it.requestMatchers("/api/v1/settlements/calculate", "/api/v1/settlements/{id}/confirm").hasRole("ADMIN")

                // 인증된 사용자
                it.anyRequest().authenticated()
            }

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
