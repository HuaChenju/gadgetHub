package org.example.cart

import org.example.product.Product

data class CartResponse(
    val items: List<CartItemResponse>,
    val totalQuantity: Long,
    val totalPrice: Long,
)

data class CartItemResponse(
    val product: Product,
    val quantity: Int,
    val totalPrice: Long,
)
