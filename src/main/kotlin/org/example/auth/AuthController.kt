package org.example.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.example.error.ApiException
import org.example.error.ErrorCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody body: LoginRequest, request: HttpServletRequest): UserResponse {
        val user = authService.authenticate(body.login, body.password)
        request.getSession(false)?.invalidate()
        request.session.setAttribute(SESSION_USER, user)
        return user
    }

    @GetMapping("/me")
    fun me(request: HttpServletRequest): UserResponse {
        return request.getSession(false)?.getAttribute(SESSION_USER) as? UserResponse
            ?: throw ApiException(ErrorCode.UNAUTHORIZED, "Для выполнения действия войдите в систему")
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        request.getSession(false)?.invalidate()
        return ResponseEntity.noContent().build()
    }

    companion object {
        const val SESSION_USER = "user"
    }
}
