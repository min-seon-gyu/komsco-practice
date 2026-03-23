package com.komsco.voucher.voucher.infrastructure

import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class VoucherLockManager(private val redissonClient: RedissonClient) {

    fun <T> withVoucherLock(voucherId: Long, action: () -> T): T =
        withLock("voucher:$voucherId", action)

    fun <T> withMemberPurchaseLock(memberId: Long, action: () -> T): T =
        withLock("member:purchase:$memberId", action)

    private fun <T> withLock(key: String, action: () -> T): T {
        val lock = redissonClient.getLock(key)
        val acquired = lock.tryLock(5, 10, TimeUnit.SECONDS)
        if (!acquired) throw BusinessException(ErrorCode.LOCK_ACQUISITION_FAILED)
        try {
            return action()
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
    }
}
