package com.komsco.voucher.member.interfaces

import com.komsco.voucher.member.application.MemberService
import com.komsco.voucher.member.interfaces.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberService: MemberService
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterMemberRequest): MemberResponse =
        MemberResponse.from(memberService.register(request))

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse =
        memberService.login(request)

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): MemberResponse =
        MemberResponse.from(memberService.getById(id))
}
