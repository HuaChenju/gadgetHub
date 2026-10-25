package org.example.product

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class Product(
    val id: Int,
    val name: String,
    val price: Long,
    val imageUrl: String,
    val type: String,
    val color: String,
    val rating: Double,
    val isNew: Boolean,
    val isHit: Boolean,
    val description: String,
    val characteristics: List<ProductCharacteristic>,
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val addedAt: LocalDate,
    @get:JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val popularity: Int,
)

data class ProductCharacteristic(
    val name: String,
    val value: String,
)
