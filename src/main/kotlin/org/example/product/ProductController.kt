package org.example.product

import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/goods")
class ProductController(private val productService: ProductService) {
    @GetMapping
    fun findAll(
        @RequestParam(defaultValue = "newness") sort: String,
        @RequestParam(defaultValue = "desc") direction: String,
        @RequestParam(defaultValue = "1") @Min(1, message = "Номер страницы должен быть не меньше 1") page: Int,
        @RequestParam(required = false) @Min(0, message = "Цена не должна быть отрицательной") minPrice: Long?,
        @RequestParam(required = false) @Min(0, message = "Цена не должна быть отрицательной") maxPrice: Long?,
        @RequestParam(required = false) types: List<String>?,
        @RequestParam(required = false) colors: List<String>?,
    ): ProductPage = productService.findAll(
        sort = sort,
        direction = direction,
        page = page,
        minPrice = minPrice,
        maxPrice = maxPrice,
        types = types.orEmpty(),
        colors = colors.orEmpty(),
    )

    @GetMapping("/filters")
    fun filters(): ProductFilters = productService.filters()

    @GetMapping("/{id}")
    fun findById(
        @PathVariable @Min(1, message = "ID товара должен быть положительным") id: Int,
    ): Product = productService.findById(id)
}
