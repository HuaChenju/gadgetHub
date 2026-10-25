package org.example.product

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
class ProductRepositoryTest {
    @Autowired
    private lateinit var repository: ProductRepository

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `catalogue loads all products and keeps sorting metadata out of responses`() {
        val products = repository.findAll()
        assertEquals((1..32).toList(), products.map { it.id })
        assertTrue(products.all { it.characteristics.isNotEmpty() })
        assertTrue(products.any { it.isNew && it.isHit })

        val json = jsonMapper.readTree(jsonMapper.writeValueAsString(products.first()))
        assertEquals(1, json.get("id").asInt())
        assertTrue(json.has("isNew"))
        assertTrue(json.has("isHit"))
        assertFalse(json.has("addedAt"))
        assertFalse(json.has("popularity"))
    }

    @Test
    fun `product image is publicly served as the original PNG`() {
        val product = repository.findAll().first()
        val bytes = ClassPathResource("static${product.imageUrl}").inputStream.use { it.readBytes() }

        mvc.perform(get(product.imageUrl))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
            .andExpect(content().bytes(bytes))
    }
}
