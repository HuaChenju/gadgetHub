package org.example.product

import org.example.error.ApiException
import org.example.error.ErrorCode
import org.springframework.stereotype.Service

@Service
class ProductService(private val productRepository: ProductRepository) {
    fun showcase(): ShowcaseResponse {
        val products = productRepository.findAll()
        return ShowcaseResponse(
            newItems = products.filter { it.isNew }
                .sortedWith(compareByDescending<Product> { it.addedAt }.thenBy { it.id }),
            hitItems = products.filter { it.isHit }
                .sortedWith(compareByDescending<Product> { it.popularity }.thenBy { it.id }),
        )
    }

    fun findAll(
        sort: String,
        direction: String,
        page: Int,
        minPrice: Long? = null,
        maxPrice: Long? = null,
        types: List<String> = emptyList(),
        colors: List<String> = emptyList(),
    ): ProductPage {
        val products = productRepository.findAll()
        val selectedTypes = types.filter { it.isNotEmpty() }.toSet()
        val selectedColors = colors.filter { it.isNotEmpty() }.toSet()
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw invalidParameter("maxPrice", "Максимальная цена не должна быть меньше минимальной")
        }
        if (!products.map { it.type }.toSet().containsAll(selectedTypes)) {
            throw invalidParameter("types", "Выберите типы товаров из доступного списка")
        }
        if (!products.map { it.color }.toSet().containsAll(selectedColors)) {
            throw invalidParameter("colors", "Выберите цвета из доступного списка")
        }
        val filtered = products.filter { product ->
            (minPrice == null || product.price >= minPrice) &&
                (maxPrice == null || product.price <= maxPrice) &&
                (selectedTypes.isEmpty() || product.type in selectedTypes) &&
                (selectedColors.isEmpty() || product.color in selectedColors)
        }
        val comparator: Comparator<Product> = when (sort) {
            "newness" -> compareBy { it.addedAt }
            "popularity" -> compareBy { it.popularity }
            "price" -> compareBy { it.price }
            else -> throw invalidParameter("sort", "Выберите сортировку: newness, popularity или price")
        }
        val directedComparator = when (direction) {
            "asc" -> comparator
            "desc" -> comparator.reversed()
            else -> throw invalidParameter("direction", "Выберите направление: asc или desc")
        }
        val sorted = filtered.sortedWith(directedComparator.thenBy { it.id })
        val offset = (page.toLong() - 1) * PAGE_SIZE
        val items = if (offset >= sorted.size) emptyList() else sorted.drop(offset.toInt()).take(PAGE_SIZE)

        return ProductPage(
            items = items,
            page = page,
            pageSize = PAGE_SIZE,
            totalItems = sorted.size,
            totalPages = (sorted.size + PAGE_SIZE - 1) / PAGE_SIZE,
        )
    }

    fun filters(): ProductFilters {
        val products = productRepository.findAll()
        return ProductFilters(
            types = products.map { it.type }.distinct().sorted(),
            colors = products.map { it.color }.distinct().sorted(),
            minPrice = products.minOf { it.price },
            maxPrice = products.maxOf { it.price },
        )
    }

    fun findById(id: Int): Product = productRepository.findById(id)
        ?: throw ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Товар не найден")

    private fun invalidParameter(field: String, message: String) = ApiException(
        ErrorCode.VALIDATION_ERROR,
        "Проверьте параметры каталога",
        mapOf(field to message),
    )

    companion object {
        private const val PAGE_SIZE = 9
    }
}
