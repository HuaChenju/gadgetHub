package org.example.error

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(ErrorTestController::class)
@Import(GlobalExceptionHandler::class)
class GlobalExceptionHandlerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `body validation returns field errors`() {
        mvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON).content("""{"phone":""}"""))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Проверьте заполненные поля"))
            .andExpect(jsonPath("$.fieldErrors.phone").value("Заполните обязательное поле"))
    }

    @Test
    fun `nested validation preserves array field paths`() {
        mvc.perform(post("/test/errors/items").contentType(MediaType.APPLICATION_JSON)
            .content("""{"items":[{"quantity":0}]}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").value("Количество должно быть положительным"))
    }

    @Test
    fun `malformed JSON returns safe error`() {
        mvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Некорректное тело запроса"))
            .andExpect(jsonPath("$.fieldErrors").isMap)
            .andExpect(jsonPath("$.trace").doesNotExist())
    }

    @Test
    fun `missing body returns validation error`() {
        mvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors").isMap)
    }

    @Test
    fun `missing Kotlin constructor field returns its name`() {
        mvc.perform(post("/test/errors/body").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.phone").isString)
    }

    @Test
    fun `wrong nested JSON type returns array field path`() {
        mvc.perform(post("/test/errors/items").contentType(MediaType.APPLICATION_JSON)
            .content("""{"items":[{"quantity":"invalid"}]}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").isString)
    }

    @Test
    fun `wrong query type returns parameter name`() {
        mvc.perform(get("/test/errors/query").param("page", "secret-invalid-value"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.page").value("Некорректный тип значения"))
    }

    @Test
    fun `missing query parameter returns field error`() {
        mvc.perform(get("/test/errors/query"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.page").value("Заполните обязательное поле"))
    }

    @Test
    fun `query constraint uses public parameter name`() {
        mvc.perform(get("/test/errors/query").param("page", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.page").value("Номер страницы должен быть не меньше 1"))
    }

    @Test
    fun `wrong path variable type returns field error`() {
        mvc.perform(get("/test/errors/product/invalid"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.id").isString)
    }

    @Test
    fun `business errors preserve contract codes and statuses`() {
        for ((kind, expectedStatus, expectedCode) in listOf(
            Triple("unauthorized", 401, "UNAUTHORIZED"),
            Triple("credentials", 401, "INVALID_CREDENTIALS"),
            Triple("product", 404, "PRODUCT_NOT_FOUND"),
            Triple("cart", 400, "PRODUCT_NOT_FOUND"),
        )) {
            mvc.perform(get("/test/errors/business/$kind"))
                .andExpect(status().`is`(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.fieldErrors").isMap)
        }
    }

    @Test
    fun `unexpected error hides implementation details`() {
        mvc.perform(get("/test/errors/unexpected"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().json("""{
                "code":"INTERNAL_ERROR",
                "message":"Не удалось выполнить действие. Попробуйте позже",
                "fieldErrors":{}
            }"""))
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist())
    }

    @Test
    fun `wrong HTTP method preserves status and Allow header`() {
        mvc.perform(post("/test/errors/query"))
            .andExpect(status().isMethodNotAllowed)
            .andExpect(header().string("Allow", "GET"))
            .andExpect(jsonPath("$.fieldErrors").isMap)
    }
}

// Вспомогательные ручки существуют только в тестовом контексте.
@RestController
class ErrorTestController {
    @PostMapping("/test/errors/body")
    fun body(@Valid @RequestBody body: TestBody) = body

    @PostMapping("/test/errors/items")
    fun items(@Valid @RequestBody body: TestItems) = body

    @GetMapping("/test/errors/query")
    fun query(
        @RequestParam("page") @Min(1, message = "Номер страницы должен быть не меньше 1") number: Int,
    ) = number

    @GetMapping("/test/errors/product/{id}")
    fun product(@PathVariable id: Int) = id

    @GetMapping("/test/errors/business/{kind}")
    fun business(@PathVariable kind: String): Nothing = when (kind) {
        "credentials" -> throw ApiException(ErrorCode.INVALID_CREDENTIALS, "Неправильный логин или пароль")
        "product" -> throw ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Товар не найден")
        "cart" -> throw ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Один из товаров не найден",
            mapOf("items[0].productId" to "Товар не найден"), HttpStatus.BAD_REQUEST)
        else -> throw ApiException(ErrorCode.UNAUTHORIZED, "Для выполнения действия войдите в систему")
    }

    @GetMapping("/test/errors/unexpected")
    fun unexpected(): Nothing = throw IllegalStateException("Internal file /private/orders.json, secret data")
}

data class TestBody(@field:NotBlank(message = "Заполните обязательное поле") val phone: String)
data class TestItems(@field:Valid val items: List<TestItem>)
data class TestItem(@field:Min(1, message = "Количество должно быть положительным") val quantity: Int)
