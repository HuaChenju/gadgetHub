package org.example.error

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import tools.jackson.core.JacksonException

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ApiError> {
        if (ex.status.is5xxServerError) {
            return internalError(ex)
        }
        return ResponseEntity.status(ex.status)
            .body(ApiError(ex.code, ex.message, ex.fieldErrors))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception): ResponseEntity<ApiError> = internalError(ex)

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val error = if (statusCode.is5xxServerError) {
            log.error("Ошибка обработки запроса", ex)
            internalErrorBody()
        } else {
            ApiError(
                code = ErrorCode.VALIDATION_ERROR,
                message = when (ex) {
                    is HttpMessageNotReadableException -> "Некорректное тело запроса"
                    is MethodArgumentNotValidException -> "Проверьте заполненные поля"
                    else -> "Проверьте параметры запроса"
                },
                fieldErrors = fieldErrors(ex),
            )
        }

        return super.handleExceptionInternal(ex, error, headers, statusCode, request)
    }

    private fun fieldErrors(ex: Exception): Map<String, String> = when (ex) {
        is MethodArgumentNotValidException -> ex.bindingResult.fieldErrors
            .groupBy { it.field }
            .mapValues { (_, errors) -> errors.first().defaultMessage ?: "Некорректное значение" }

        is HandlerMethodValidationException -> ex.parameterValidationResults.associate { result ->
            val parameter = result.methodParameter
            val requestParam = parameter.getParameterAnnotation(RequestParam::class.java)
            val pathVariable = parameter.getParameterAnnotation(PathVariable::class.java)
            val name = requestParam?.name?.takeIf { it.isNotEmpty() }
                ?: requestParam?.value?.takeIf { it.isNotEmpty() }
                ?: pathVariable?.name?.takeIf { it.isNotEmpty() }
                ?: pathVariable?.value?.takeIf { it.isNotEmpty() }
                ?: parameter.parameterName
                ?: "request"
            name to (result.resolvableErrors.firstOrNull()?.defaultMessage ?: "Некорректное значение")
        }

        is MethodArgumentTypeMismatchException -> mapOf(ex.name to "Некорректный тип значения")
        is MissingServletRequestParameterException -> mapOf(ex.parameterName to "Заполните обязательное поле")
        is HttpMessageNotReadableException -> jsonFieldErrors(ex)
        else -> emptyMap()
    }

    private fun jsonFieldErrors(ex: HttpMessageNotReadableException): Map<String, String> {
        val cause = generateSequence(ex.cause) { it.cause }
            .filterIsInstance<JacksonException>()
            .firstOrNull() ?: return emptyMap()
        val path = buildString {
            for (reference in cause.path) {
                val property = reference.propertyName
                if (property != null) {
                    if (isNotEmpty()) append('.')
                    append(property)
                } else if (reference.index >= 0) {
                    append('[').append(reference.index).append(']')
                }
            }
        }
        return if (path.isEmpty()) emptyMap() else mapOf(path to "Поле отсутствует или имеет некорректное значение")
    }

    private fun internalError(ex: Exception): ResponseEntity<ApiError> {
        log.error("Ошибка обработки запроса", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(internalErrorBody())
    }

    private fun internalErrorBody() = ApiError(
        ErrorCode.INTERNAL_ERROR,
        "Не удалось выполнить действие. Попробуйте позже",
    )
}
