package org.example.order

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Instant

@Repository
class OrderRepository(
    private val jsonMapper: JsonMapper,
    @Value("\${app.orders-file:data/orders.json}") fileName: String,
) {
    private val file = Path.of(fileName).toAbsolutePath().normalize()
    private var orders: List<Order>

    init {
        try {
            if (Files.notExists(file)) {
                writeOrders(emptyList())
            }
            orders = Files.newInputStream(file).use { input ->
                jsonMapper.readValue(input, object : TypeReference<List<Order>>() {})
            }
            require(orders.map { it.number }.distinct().size == orders.size) { "Номера заказов не должны повторяться" }
            orders.forEach { validate(it) }
            LoggerFactory.getLogger(javaClass).info("Загружено заказов: {}. Файл: {}", orders.size, file)
        } catch (ex: Exception) {
            throw IllegalStateException("Не удалось загрузить заказы из $file: ${ex.message}", ex)
        }
    }

    @Synchronized
    fun findByUserId(userId: Int): List<Order> = orders.filter { it.userId == userId }

    @Synchronized
    fun save(userId: Int, items: List<OrderItem>, details: OrderDetails): Order {
        val order = Order(
            number = Math.incrementExact(orders.maxOfOrNull { it.number } ?: 0L),
            createdAt = Instant.now(),
            userId = userId,
            items = items.toList(),
            details = details,
            totalQuantity = items.sumOf { it.quantity.toLong() },
            totalPrice = items.fold(0L) { total, item -> Math.addExact(total, item.totalPrice) },
        )
        validate(order)
        val updated = orders + order
        writeOrders(updated)
        orders = updated
        return order
    }

    private fun writeOrders(updated: List<Order>) {
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, "orders-", ".tmp")
        try {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), updated)
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun validate(order: Order) {
        require(order.number > 0 && order.userId > 0) { "Номер заказа и ID пользователя должны быть положительными" }
        require(order.items.isNotEmpty()) { "Заказ ${order.number}: список товаров пуст" }
        require(order.items.map { it.productId }.distinct().size == order.items.size) {
            "Заказ ${order.number}: товары не должны повторяться"
        }
        for (item in order.items) {
            require(item.productId > 0 && item.name.isNotBlank() && item.price > 0 && item.quantity > 0) {
                "Заказ ${order.number}: некорректная позиция товара"
            }
            require(item.totalPrice == Math.multiplyExact(item.price, item.quantity.toLong())) {
                "Заказ ${order.number}: неверная стоимость позиции"
            }
        }
        require(order.totalQuantity == order.items.sumOf { it.quantity.toLong() } &&
            order.totalPrice == order.items.fold(0L) { total, item -> Math.addExact(total, item.totalPrice) }) {
            "Заказ ${order.number}: неверные итоги"
        }
        require(order.details.phone.isNotBlank()) { "Заказ ${order.number}: телефон не заполнен" }
        require(order.details.deliveryMethod in setOf("pickup", "delivery")) {
            "Заказ ${order.number}: неизвестный способ получения"
        }
        require(order.details.deliveryMethod != "delivery" || !order.details.address.isNullOrBlank()) {
            "Заказ ${order.number}: адрес доставки не заполнен"
        }
        require(order.details.paymentMethod in setOf("card", "cash")) {
            "Заказ ${order.number}: неизвестный способ оплаты"
        }
    }
}
