package org.example.product

data class ShowcaseResponse(
    val newItems: List<Product>,
    val hitItems: List<Product>,
)
