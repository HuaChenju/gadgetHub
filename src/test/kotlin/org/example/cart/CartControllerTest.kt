package org.example.cart

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class CartControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `cart uses catalogue prices and handles empty cart`() {
        val session = login()
        mvc.perform(post("/api/cart/calculate").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("""{"items":[{"productId":1,"quantity":2},{"productId":13,"quantity":1}]}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].product.id").value(1))
            .andExpect(jsonPath("$.items[0].product.price").value(2990))
            .andExpect(jsonPath("$.items[0].totalPrice").value(5980))
            .andExpect(jsonPath("$.items[1].product.id").value(13))
            .andExpect(jsonPath("$.totalQuantity").value(3))
            .andExpect(jsonPath("$.totalPrice").value(8979))
            .andExpect(jsonPath("$.items[0].product.addedAt").doesNotExist())

        mvc.perform(post("/api/cart/calculate").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("""{"items":[]}"""))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"items":[],"totalQuantity":0,"totalPrice":0}"""))
    }

    @Test
    fun `invalid cart is rejected completely and session is required`() {
        val session = login()
        val cases = listOf(
            Triple("""{"items":[{"productId":0,"quantity":1}]}""", "VALIDATION_ERROR", "items[0].productId"),
            Triple("""{"items":[{"productId":1,"quantity":0}]}""", "VALIDATION_ERROR", "items[0].quantity"),
            Triple("""{"items":[{"productId":1,"quantity":1.5}]}""", "VALIDATION_ERROR", "items[0].quantity"),
            Triple("""{"items":[{"productId":1,"quantity":"2"}]}""", "VALIDATION_ERROR", "items[0].quantity"),
            Triple("""{"items":[{"productId":1,"quantity":1},{"productId":1,"quantity":2}]}""", "VALIDATION_ERROR", "items[1].productId"),
            Triple("""{"items":[{"productId":1,"quantity":1},{"productId":99999,"quantity":1}]}""", "PRODUCT_NOT_FOUND", "items[1].productId"),
            Triple("""{"items":[null]}""", "VALIDATION_ERROR", "items[0]"),
            Triple("""{"items":null}""", "VALIDATION_ERROR", "items"),
        )
        for ((body, code, field) in cases) {
            mvc.perform(post("/api/cart/calculate").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.fieldErrors['$field']").isString)
                .andExpect(jsonPath("$.items").doesNotExist())
        }
        mvc.perform(post("/api/cart/calculate").contentType(MediaType.APPLICATION_JSON).content("""{"items":[]}"""))
            .andExpect(status().isUnauthorized)
    }

    private fun login(): MockHttpSession {
        val result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student"}"""))
            .andExpect(status().isOk)
            .andReturn()
        return result.request.getSession(false) as MockHttpSession
    }
}
