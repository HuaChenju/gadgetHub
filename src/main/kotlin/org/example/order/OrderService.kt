package org.example.order

import jakarta.validation.Validator
import org.example.cart.CartService
import org.example.error.ApiException
import org.example.error.ErrorCode
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val cartService: CartService,
    private val orderRepository: OrderRepository,
    private val validator: Validator,
) {
    fun history(userId: Int): List<OrderSummary> = orderRepository.findByUserId(userId)
        .sortedWith(compareByDescending<Order> { it.createdAt }.thenByDescending { it.number })
        .map { summary(it) }

    fun create(userId: Int, body: OrderRequest): OrderSummary {
        val phone = body.phone.trim()
        val email = body.email?.trim()?.takeIf { it.isNotEmpty() }
        val address = if (body.deliveryMethod == "delivery") body.address?.trim() else null
        val errors = linkedMapOf<String, String>()

        if (body.items.isEmpty()) errors["items"] = "Корзина не должна быть пустой"
        if (phone.isEmpty()) errors["phone"] = "Заполните обязательное поле"
        if (validator.validateValue(OrderRequest::class.java, "email", email).isNotEmpty()) {
            errors["email"] = "Укажите корректный e-mail"
        }
        if (body.deliveryMethod !in setOf("pickup", "delivery")) {
            errors["deliveryMethod"] = "Выберите способ получения: самовывоз или доставка"
        }
        if (body.deliveryMethod == "delivery" && address.isNullOrEmpty()) {
            errors["address"] = "Укажите адрес доставки"
        }
        if (body.paymentMethod !in setOf("card", "cash")) {
            errors["paymentMethod"] = "Выберите способ оплаты"
        }
        if (errors.isNotEmpty()) {
            throw ApiException(ErrorCode.VALIDATION_ERROR, "Проверьте заполненные поля", errors)
        }

        val cart = cartService.calculate(body.items)
        val order = orderRepository.save(
            userId = userId,
            items = cart.items.map { item ->
                OrderItem(item.product.id, item.product.name, item.product.price, item.quantity, item.totalPrice)
            },
            details = OrderDetails(phone, email, body.deliveryMethod, address, body.paymentMethod, body.packaging),
        )
        return summary(order)
    }

    private fun summary(order: Order) = OrderSummary(order.number, order.createdAt, order.totalQuantity, order.totalPrice)
}
