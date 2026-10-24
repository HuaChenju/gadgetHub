package org.example.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.example.error.ApiException
import org.example.error.ErrorCode
import org.springframework.web.cors.CorsUtils
import org.springframework.web.servlet.HandlerInterceptor

class SessionAuthInterceptor : HandlerInterceptor {
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true
        }
        if (request.getSession(false)?.getAttribute(AuthController.SESSION_USER) !is UserResponse) {
            throw ApiException(ErrorCode.UNAUTHORIZED, "Для выполнения действия войдите в систему")
        }
        return true
    }
}
