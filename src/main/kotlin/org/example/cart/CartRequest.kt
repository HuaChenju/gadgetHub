package org.example.cart

data class CartRequest(val items: List<CartItemInput?>)

data class CartItemInput(
    val productId: Int,
    val quantity: Int,
)
