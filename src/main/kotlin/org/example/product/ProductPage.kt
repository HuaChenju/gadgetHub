package org.example.product

data class ProductPage(
    val items: List<Product>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
)
