package org.example.error

import org.springframework.http.HttpStatus

/** Сообщение и ошибки полей предназначены для показа пользователю. */
class ApiException(
    val code: ErrorCode,
    override val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
    val status: HttpStatus = code.status,
) : RuntimeException(message)
