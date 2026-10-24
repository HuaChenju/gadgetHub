package org.example.error

import org.springframework.http.HttpStatus

data class ApiError(
    val code: ErrorCode,
    val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
)

enum class ErrorCode(val status: HttpStatus) {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
}
