package org.example.order

import java.time.Instant

data class OrderSummary(
    val number: Long,
    val createdAt: Instant,
    val totalQuantity: Long,
    val totalPrice: Long,
)
