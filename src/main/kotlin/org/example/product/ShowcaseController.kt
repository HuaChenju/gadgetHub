package org.example.product

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ShowcaseController(private val productService: ProductService) {
    @GetMapping("/api/showcase")
    fun showcase(): ShowcaseResponse = productService.showcase()
}
