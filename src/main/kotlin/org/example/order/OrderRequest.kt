package org.example.order

import jakarta.validation.constraints.Email
import org.example.cart.CartItemInput

data class OrderRequest(
    val items: List<CartItemInput?>,
    val phone: String = "",
    @field:Email(message = "Укажите корректный e-mail")
    val email: String? = null,
    val deliveryMethod: String = "",
    val address: String? = null,
    val paymentMethod: String = "",
    val packaging: Boolean = false,
)
