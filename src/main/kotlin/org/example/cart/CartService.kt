package org.example.cart

import org.example.error.ApiException
import org.example.error.ErrorCode
import org.example.product.ProductRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class CartService(private val productRepository: ProductRepository) {
    fun calculate(items: List<CartItemInput?>): CartResponse {
        val seen = mutableSetOf<Int>()
        try {
            val calculated = items.mapIndexed { index, item ->
                val position = item ?: throw invalid("items[$index]", "Укажите товар и количество")
                if (position.productId <= 0) {
                    throw invalid("items[$index].productId", "ID товара должен быть положительным")
                }
                if (position.quantity <= 0) {
                    throw invalid("items[$index].quantity", "Количество должно быть положительным целым числом")
                }
                if (!seen.add(position.productId)) {
                    throw invalid("items[$index].productId", "Товар не должен повторяться в корзине")
                }
                val product = productRepository.findById(position.productId)
                    ?: throw ApiException(
                        ErrorCode.PRODUCT_NOT_FOUND,
                        "Один из товаров не найден",
                        mapOf("items[$index].productId" to "Товар не найден"),
                        HttpStatus.BAD_REQUEST,
                    )
                CartItemResponse(product, position.quantity, Math.multiplyExact(product.price, position.quantity.toLong()))
            }
            return CartResponse(
                items = calculated,
                totalQuantity = calculated.sumOf { it.quantity.toLong() },
                totalPrice = calculated.fold(0L) { total, item -> Math.addExact(total, item.totalPrice) },
            )
        } catch (ex: ArithmeticException) {
            throw invalid("items", "Сумма корзины слишком велика")
        }
    }

    private fun invalid(field: String, message: String) = ApiException(
        ErrorCode.VALIDATION_ERROR,
        "Проверьте состав корзины",
        mapOf(field to message),
    )
}
