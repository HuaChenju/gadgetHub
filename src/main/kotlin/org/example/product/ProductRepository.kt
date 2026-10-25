package org.example.product

import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

@Repository
class ProductRepository(jsonMapper: JsonMapper) {
    private val products: List<Product>

    init {
        try {
            products = ClassPathResource("data/goods.json").inputStream.use { input ->
                jsonMapper.readValue(input, object : TypeReference<List<Product>>() {})
            }
            validate(products)
            LoggerFactory.getLogger(javaClass).info("Загружен каталог: {} товаров", products.size)
        } catch (ex: Exception) {
            throw IllegalStateException("Не удалось загрузить каталог data/goods.json: ${ex.message}", ex)
        }
    }

    fun findAll(): List<Product> = products

    fun findById(id: Int): Product? = products.firstOrNull { it.id == id }

    private fun validate(products: List<Product>) {
        require(products.isNotEmpty()) { "Каталог не должен быть пустым" }
        val duplicateIds = products.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Повторяющиеся ID товаров: $duplicateIds" }

        for (product in products) {
            val prefix = "Товар ${product.id}:"
            require(product.id > 0) { "$prefix ID должен быть положительным" }
            require(product.name.isNotBlank()) { "$prefix название не должно быть пустым" }
            require(product.price > 0) { "$prefix цена должна быть положительной" }
            require(product.type.isNotBlank()) { "$prefix тип не должен быть пустым" }
            require(product.color.isNotBlank()) { "$prefix цвет не должен быть пустым" }
            require(product.rating.isFinite() && product.rating in 0.0..5.0) { "$prefix рейтинг должен быть от 0 до 5" }
            require(product.description.isNotBlank()) { "$prefix описание не должно быть пустым" }
            require(product.popularity >= 0) { "$prefix популярность не должна быть отрицательной" }
            require(product.characteristics.all { it.name.isNotBlank() && it.value.isNotBlank() }) {
                "$prefix названия и значения характеристик не должны быть пустыми"
            }
            require(product.imageUrl.startsWith("/images/goods/") &&
                ClassPathResource("static${product.imageUrl}").exists()) {
                "$prefix изображение не найдено: ${product.imageUrl}"
            }
        }
    }
}
