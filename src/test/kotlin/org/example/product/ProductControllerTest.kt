package org.example.product

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Test
    fun `public showcase returns all flagged products in the correct order without a session`() {
        val result = mvc.perform(get("/api/showcase"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.newItems.length()").value(10))
            .andExpect(jsonPath("$.hitItems.length()").value(10))
            .andExpect(jsonPath("$.newItems[0].name").isString)
            .andExpect(jsonPath("$.newItems[0].addedAt").doesNotExist())
            .andExpect(jsonPath("$.hitItems[0].popularity").doesNotExist())
            .andReturn()

        assertIds(result, listOf(1, 29, 30, 6, 32, 8, 11, 16, 23, 25), "newItems")
        assertIds(result, listOf(29, 21, 6, 13, 5, 12, 26, 2, 9, 15), "hitItems")
        assertNull(result.request.getSession(false))
    }

    @Test
    fun `filter options describe whole catalogue and combined filters precede pagination`() {
        val session = login()
        mvc.perform(get("/api/goods/filters").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.types[0]").value("Аксессуары"))
            .andExpect(jsonPath("$.colors[0]").value("Бежевый"))
            .andExpect(jsonPath("$.minPrice").value(490))
            .andExpect(jsonPath("$.maxPrice").value(89990))

        val result = mvc.perform(get("/api/goods").session(session)
            .param("minPrice", "2999").param("maxPrice", "43999")
            .param("types", "Наушники", "Смартфоны")
            .param("colors", "Белый", "Черный")
            .param("sort", "price").param("direction", "asc"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(4))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andReturn()
        assertIds(result, listOf(13, 15, 7, 8))

        mvc.perform(get("/api/goods").session(session).param("types", "").param("colors", ""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(32))
        mvc.perform(get("/api/goods").session(session).param("minPrice", "90000"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
            .andExpect(jsonPath("$.totalItems").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
    }

    @Test
    fun `invalid filters return errors for corresponding fields`() {
        val session = login()
        for ((field, value) in listOf(
            "minPrice" to "-1", "maxPrice" to "-1", "minPrice" to "1.5",
            "types" to "Несуществующий тип", "colors" to "Несуществующий цвет",
        )) {
            mvc.perform(get("/api/goods").session(session).param(field, value))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.$field").isString)
        }
        mvc.perform(get("/api/goods").session(session).param("minPrice", "1000").param("maxPrice", "500"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.maxPrice")
                .value("Максимальная цена не должна быть меньше минимальной"))
        mvc.perform(get("/api/goods/filters"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `catalogue applies default order all sort directions and fixed pagination`() {
        val session = login()
        val defaultResult = mvc.perform(get("/api/goods").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.pageSize").value(9))
            .andExpect(jsonPath("$.totalItems").value(32))
            .andExpect(jsonPath("$.totalPages").value(4))
            .andReturn()
        assertIds(defaultResult, listOf(26, 1, 27, 2, 28, 3, 29, 4, 30))

        val cases = listOf(
            Triple("newness", "asc", listOf(25, 24, 23, 22, 21, 20, 19, 18, 17)),
            Triple("price", "asc", listOf(18, 19, 20, 21, 28, 25, 17, 22, 9)),
            Triple("price", "desc", listOf(30, 31, 6, 7, 8, 29, 16, 27, 26)),
            Triple("popularity", "asc", listOf(22, 15, 8, 30, 1, 23, 16, 9, 31)),
            Triple("popularity", "desc", listOf(29, 7, 14, 21, 28, 6, 13, 20, 27)),
        )
        for ((sort, direction, ids) in cases) {
            val result = mvc.perform(get("/api/goods").session(session).param("sort", sort).param("direction", direction))
                .andExpect(status().isOk)
                .andReturn()
            assertIds(result, ids)
        }

        mvc.perform(get("/api/goods").session(session).param("page", "4"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(4))
            .andExpect(jsonPath("$.items.length()").value(5))
    }

    @Test
    fun `invalid catalogue parameters return field errors and distant page is empty`() {
        val session = login()
        for ((field, value) in listOf("sort" to "unknown", "direction" to "up", "page" to "0", "page" to "abc")) {
            mvc.perform(get("/api/goods").session(session).param(field, value))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.$field").isString)
        }

        mvc.perform(get("/api/goods").session(session).param("page", Int.MAX_VALUE.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
            .andExpect(jsonPath("$.page").value(Int.MAX_VALUE))
            .andExpect(jsonPath("$.totalItems").value(32))
            .andExpect(jsonPath("$.totalPages").value(4))

        mvc.perform(get("/api/goods"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `authenticated user receives full product without internal metadata`() {
        mvc.perform(get("/api/goods/1").session(login()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Внешний аккумулятор 10000mAh, белый"))
            .andExpect(jsonPath("$.price").value(2990))
            .andExpect(jsonPath("$.imageUrl").value("/images/goods/image_1.png"))
            .andExpect(jsonPath("$.characteristics").isArray)
            .andExpect(jsonPath("$.isNew").isBoolean)
            .andExpect(jsonPath("$.addedAt").doesNotExist())
            .andExpect(jsonPath("$.popularity").doesNotExist())
    }

    @Test
    fun `missing product invalid id and guest follow error contract`() {
        val session = login()
        mvc.perform(get("/api/goods/99999").session(session))
            .andExpect(status().isNotFound)
            .andExpect(content().json("""{
                "code":"PRODUCT_NOT_FOUND",
                "message":"Товар не найден",
                "fieldErrors":{}
            }"""))

        for (id in listOf("0", "-1", "invalid", "1.5")) {
            mvc.perform(get("/api/goods/$id").session(session))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.id").isString)
        }

        mvc.perform(get("/api/goods/1"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    private fun assertIds(result: MvcResult, expected: List<Int>, field: String = "items") {
        val items = jsonMapper.readTree(result.response.contentAsString).get(field)
        assertEquals(expected, (0 until items.size()).map { items.get(it).get("id").asInt() })
    }

    private fun login(): MockHttpSession {
        val result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student"}"""))
            .andExpect(status().isOk)
            .andReturn()
        return result.request.getSession(false) as MockHttpSession
    }
}
