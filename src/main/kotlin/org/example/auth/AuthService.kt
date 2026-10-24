package org.example.auth

import org.example.error.ApiException
import org.example.error.ErrorCode
import org.springframework.stereotype.Service

@Service
class AuthService {
    private val user = UserResponse(id = 1, login = "student")
    private val password = "student"

    fun authenticate(login: String, password: String): UserResponse {
        if (login.trim() != user.login || password != this.password) {
            throw ApiException(
                ErrorCode.INVALID_CREDENTIALS,
                "Такого пользователя нет, возможно неправильный логин или пароль — проверьте данные",
            )
        }
        return user
    }
}
