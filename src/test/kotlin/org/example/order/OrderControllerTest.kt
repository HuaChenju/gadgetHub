package org.example.order

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import org.example.auth.AuthController
import org.example.auth.UserResponse

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var repository: OrderRepository

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    private val form = mapOf<String, Any?>(
        "items" to listOf(mapOf("productId" to 1, "quantity" to 2)),
        "phone" to " +79991234567 ",
        "email" to "  ",
        "deliveryMethod" to "pickup",
        "address" to "Не нужен при самовывозе",
        "paymentMethod" to "card",
    )

    @Test
    fun `history is newest first contains only session user orders and handles empty history`() {
        val session = login()
        fun create(): Long {
            val result = mvc.perform(post("/api/orders").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(form)))
                .andExpect(status().isCreated)
                .andReturn()
            return jsonMapper.readTree(result.response.contentAsString).get("number").asLong()
        }
        val first = create()
        val second = create()
        val other = repository.save(
            2,
            listOf(OrderItem(1, "Товар другого пользователя", 2990, 1, 2990)),
            OrderDetails("+79991234567", null, "pickup", null, "cash", false),
        )

        val result = mvc.perform(get("/api/orders").session(session).param("userId", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].number").value(second))
            .andExpect(jsonPath("$[1].number").value(first))
            .andExpect(jsonPath("$[0].createdAt").isString)
            .andExpect(jsonPath("$[0].totalQuantity").value(2))
            .andExpect(jsonPath("$[0].totalPrice").value(5980))
            .andExpect(jsonPath("$[0].userId").doesNotExist())
            .andExpect(jsonPath("$[0].details").doesNotExist())
            .andExpect(jsonPath("$[0].items").doesNotExist())
            .andReturn()
        val history = jsonMapper.readTree(result.response.contentAsString)
        assertTrue((0 until history.size()).none { history.get(it).get("number").asLong() == other.number })

        val emptySession = MockHttpSession()
        emptySession.setAttribute(AuthController.SESSION_USER, UserResponse(3, "empty"))
        mvc.perform(get("/api/orders").session(emptySession))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
        mvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `pickup and delivery orders persist server totals normalized details and session user`() {
        val session = login()
        val pickupResult = mvc.perform(post("/api/orders").session(session).contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(form)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.createdAt").isString)
            .andExpect(jsonPath("$.totalQuantity").value(2))
            .andExpect(jsonPath("$.totalPrice").value(5980))
            .andExpect(jsonPath("$.userId").doesNotExist())
            .andReturn()
        val number = jsonMapper.readTree(pickupResult.response.contentAsString).get("number").asLong()
        val pickup = repository.findByUserId(1).single { it.number == number }
        assertEquals("+79991234567", pickup.details.phone)
        assertNull(pickup.details.email)
        assertNull(pickup.details.address)
        assertFalse(pickup.details.packaging)
        assertEquals(2990L, pickup.items.single().price)
        assertEquals("Внешний аккумулятор 10000mAh, белый", pickup.items.single().name)

        val deliveryForm = form + mapOf(
            "deliveryMethod" to "delivery", "address" to " Санкт-Петербург, ул. Барочная, д. 7 ",
            "email" to " student@example.com ", "paymentMethod" to "cash", "packaging" to true,
        )
        val deliveryResult = mvc.perform(post("/api/orders").session(session).contentType(MediaType.APPLICATION_JSON)
            .content(jsonMapper.writeValueAsString(deliveryForm)))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.number").value(number + 1))
            .andExpect(jsonPath("$.totalPrice").value(5980))
            .andReturn()
        val next = jsonMapper.readTree(deliveryResult.response.contentAsString).get("number").asLong()
        val restored = OrderRepository(jsonMapper, ordersFile.toString()).findByUserId(1).single { it.number == next }
        assertEquals("Санкт-Петербург, ул. Барочная, д. 7", restored.details.address)
        assertEquals("student@example.com", restored.details.email)
        assertEquals(true, restored.details.packaging)
    }

    @Test
    fun `invalid form or cart cannot create orders and guests are rejected`() {
        val session = login()
        val before = repository.findByUserId(1).size
        val cases = listOf(
            Triple(mapOf("phone" to " "), "VALIDATION_ERROR", "phone"),
            Triple(mapOf("deliveryMethod" to "delivery", "address" to null), "VALIDATION_ERROR", "address"),
            Triple(mapOf("deliveryMethod" to "unknown"), "VALIDATION_ERROR", "deliveryMethod"),
            Triple(mapOf("paymentMethod" to "unknown"), "VALIDATION_ERROR", "paymentMethod"),
            Triple(mapOf("email" to "invalid"), "VALIDATION_ERROR", "email"),
            Triple(mapOf("items" to emptyList<Any>()), "VALIDATION_ERROR", "items"),
            Triple(mapOf("items" to listOf(mapOf("productId" to 99999, "quantity" to 1))), "PRODUCT_NOT_FOUND", "items[0].productId"),
            Triple(mapOf("packaging" to null), "VALIDATION_ERROR", "packaging"),
            Triple(mapOf("packaging" to "true"), "VALIDATION_ERROR", "packaging"),
        )
        for ((changes, code, field) in cases) {
            mvc.perform(post("/api/orders").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(form + changes)))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.fieldErrors['$field']").isString)
        }
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(jsonMapper.writeValueAsString(form)))
            .andExpect(status().isUnauthorized)
        assertEquals(before, repository.findByUserId(1).size)
    }

    private fun login(): MockHttpSession {
        val result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student"}"""))
            .andExpect(status().isOk)
            .andReturn()
        return result.request.getSession(false) as MockHttpSession
    }

    companion object {
        private val ordersFile = Files.createTempDirectory("gadgethub-orders-api-").resolve("orders.json")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("app.orders-file") { ordersFile.toString() }
        }

        @JvmStatic
        @AfterAll
        fun cleanup() {
            Files.deleteIfExists(ordersFile)
            Files.deleteIfExists(ordersFile.parent)
        }
    }
}
