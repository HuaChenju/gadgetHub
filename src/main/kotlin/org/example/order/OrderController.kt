package org.example.order

import jakarta.servlet.http.HttpServletRequest
import org.example.auth.AuthController
import org.example.auth.UserResponse
import org.example.error.ApiException
import org.example.error.ErrorCode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(private val orderService: OrderService) {
    @GetMapping
    fun history(request: HttpServletRequest): List<OrderSummary> = orderService.history(currentUserId(request))

    @PostMapping
    fun create(@RequestBody body: OrderRequest, request: HttpServletRequest): ResponseEntity<OrderSummary> {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(currentUserId(request), body))
    }

    private fun currentUserId(request: HttpServletRequest): Int {
        val user = request.getSession(false)?.getAttribute(AuthController.SESSION_USER) as? UserResponse
            ?: throw ApiException(ErrorCode.UNAUTHORIZED, "Для выполнения действия войдите в систему")
        return user.id
    }
}
