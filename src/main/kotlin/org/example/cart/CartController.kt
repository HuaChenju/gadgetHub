package org.example.cart

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/cart")
class CartController(private val cartService: CartService) {
    @PostMapping("/calculate")
    fun calculate(@RequestBody body: CartRequest): CartResponse = cartService.calculate(body.items)
}
