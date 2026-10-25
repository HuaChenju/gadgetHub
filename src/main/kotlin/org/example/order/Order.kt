package org.example.order

import java.time.Instant

data class Order(
    val number: Long,
    val createdAt: Instant,
    val userId: Int,
    val items: List<OrderItem>,
    val details: OrderDetails,
    val totalQuantity: Long,
    val totalPrice: Long,
)

data class OrderItem(
    val productId: Int,
    val name: String,
    val price: Long,
    val quantity: Int,
    val totalPrice: Long,
)

data class OrderDetails(
    val phone: String,
    val email: String?,
    val deliveryMethod: String,
    val address: String?,
    val paymentMethod: String,
    val packaging: Boolean,
)
