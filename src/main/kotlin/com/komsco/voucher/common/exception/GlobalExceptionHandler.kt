package com.komsco.voucher.common.exception

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    data class ErrorResponse(
        val code: String,
        val message: String
    )

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ErrorResponse> {
        log.warn("Business exception: {} - {}", e.errorCode, e.message)
        return ResponseEntity
            .status(e.errorCode.status)
            .body(ErrorResponse(e.errorCode.name, e.message))
    }
}
