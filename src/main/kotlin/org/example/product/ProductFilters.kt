package org.example.product

data class ProductFilters(
    val types: List<String>,
    val colors: List<String>,
    val minPrice: Long,
    val maxPrice: Long,
)
