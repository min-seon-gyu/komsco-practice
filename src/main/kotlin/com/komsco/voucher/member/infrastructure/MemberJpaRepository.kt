package com.komsco.voucher.member.infrastructure

import com.komsco.voucher.member.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<Member, Long> {
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
}
