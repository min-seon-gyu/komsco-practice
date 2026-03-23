package com.komsco.voucher.common.idempotency

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
import java.time.Duration

@Component
class IdempotencyInterceptor(
    private val redissonClient: RedissonClient,
    private val idempotencyRepository: IdempotencyRepository,
) : HandlerInterceptor {

    private val log = LoggerFactory.getLogger(javaClass)
    private val REDIS_TTL = Duration.ofHours(24)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true
        if (!handler.hasMethodAnnotation(Idempotent::class.java)) return true

        val key = request.getHeader("Idempotency-Key") ?: return true

        // 1. Check Redis first
        val rBucket = redissonClient.getBucket<String>("idempotency:$key")
        val cached = rBucket.get()
        if (cached != null) {
            log.info("Idempotency hit (Redis): {}", key)
            writeCachedResponse(response, cached)
            return false
        }

        // 2. Check DB fallback
        val dbRecord = idempotencyRepository.findByIdempotencyKey(key)
        if (dbRecord != null) {
            log.info("Idempotency hit (DB): {}", key)
            rBucket.set(dbRecord.responseBody, REDIS_TTL)
            writeCachedResponse(response, dbRecord.responseBody)
            return false
        }

        return true
    }

    fun saveResult(key: String, responseBody: String, status: Int) {
        try {
            val rBucket = redissonClient.getBucket<String>("idempotency:$key")
            rBucket.set(responseBody, REDIS_TTL)

            idempotencyRepository.save(
                IdempotencyKey(
                    idempotencyKey = key,
                    responseBody = responseBody,
                    responseStatus = status,
                )
            )
        } catch (e: Exception) {
            log.error("Failed to save idempotency result for key {}: {}", key, e.message)
        }
    }

    private fun writeCachedResponse(response: HttpServletResponse, body: String) {
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        response.status = 200
        response.writer.write(body)
    }
}

/**
 * ResponseBodyAdvice: @Idempotent 메서드의 응답을 자동으로 캡처하여 멱등키 저장
 */
@ControllerAdvice
class IdempotencyResponseAdvice(
    private val idempotencyInterceptor: IdempotencyInterceptor,
    private val objectMapper: ObjectMapper,
) : ResponseBodyAdvice<Any> {

    override fun supports(returnType: MethodParameter, converterType: Class<out HttpMessageConverter<*>>): Boolean {
        return returnType.hasMethodAnnotation(Idempotent::class.java)
    }

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        val servletRequest = (request as? ServletServerHttpRequest)?.servletRequest ?: return body
        val key = servletRequest.getHeader("Idempotency-Key") ?: return body

        if (body != null) {
            val responseBody = objectMapper.writeValueAsString(body)
            idempotencyInterceptor.saveResult(key, responseBody, 200)
        }
        return body
    }
}

@Component
class IdempotencyWebConfig(
    private val idempotencyInterceptor: IdempotencyInterceptor
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(idempotencyInterceptor).addPathPatterns("/api/**")
    }
}
