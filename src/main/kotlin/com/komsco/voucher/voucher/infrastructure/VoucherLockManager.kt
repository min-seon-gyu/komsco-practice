package com.komsco.voucher.voucher.infrastructure

import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class VoucherLockManager(
    private val redissonClient: RedissonClient,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T> withVoucherLock(voucherId: Long, action: () -> T): T =
        withLock("voucher:$voucherId", action)

    fun <T> withMemberPurchaseLock(memberId: Long, action: () -> T): T =
        withLock("member:purchase:$memberId", action)

    private fun <T> withLock(key: String, action: () -> T): T {
        val lockType = key.substringBefore(':')

        // Redis 분산락 시도 — 실패 시 DB 비관적 락만으로 fallback
        var redisLock: org.redisson.api.RLock? = null
        try {
            val lock = redissonClient.getLock(key)
            val timer = Timer.start(meterRegistry)
            val acquired = lock.tryLock(5, 10, TimeUnit.SECONDS)
            timer.stop(meterRegistry.timer("lock.acquisition.duration", "key", lockType))

            if (!acquired) {
                meterRegistry.counter("lock.acquisition.timeout", "key", lockType).increment()
                throw BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED)
            }
            redisLock = lock
        } catch (e: BusinessException) {
            throw e // 락 타임아웃은 그대로 전파
        } catch (e: Exception) {
            // Redis 장애 — DB 비관적 락(SELECT FOR UPDATE)만으로 동시성 제어
            log.warn("Redis 분산락 획득 실패, DB 락으로 fallback: key={}, error={}", key, e.message)
            meterRegistry.counter("lock.redis.fallback", "key", lockType).increment()
        }

        try {
            return action()
        } finally {
            try {
                if (redisLock != null && redisLock.isHeldByCurrentThread) redisLock.unlock()
            } catch (e: Exception) {
                log.warn("Redis 분산락 해제 실패: key={}, error={}", key, e.message)
            }
        }
    }
}
