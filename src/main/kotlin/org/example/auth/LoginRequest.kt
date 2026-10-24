package org.example.auth

import jakarta.validation.constraints.NotBlank

class LoginRequest(
    @field:NotBlank(message = "Заполните обязательное поле")
    val login: String = "",
    @field:NotBlank(message = "Заполните обязательное поле")
    val password: String = "",
)
