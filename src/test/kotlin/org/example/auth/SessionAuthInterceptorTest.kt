package org.example.auth

import org.example.config.WebConfig
import org.example.error.GlobalExceptionHandler
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.boot.test.context.TestComponent
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(AuthController::class, SessionAccessTestController::class)
@Import(WebConfig::class, AuthService::class, GlobalExceptionHandler::class, SessionAccessTestController::class)
class SessionAuthInterceptorTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `protected endpoints reject guests and empty sessions`() {
        for (path in listOf("/api/goods", "/api/goods/filters", "/api/goods/1", "/api/orders")) {
            val result = mvc.perform(get(path))
                .andExpect(status().isUnauthorized)
                .andExpect(content().json("""{
                    "code":"UNAUTHORIZED",
                    "message":"Для выполнения действия войдите в систему",
                    "fieldErrors":{}
                }"""))
                .andReturn()
            assertNull(result.request.getSession(false))
        }
        mvc.perform(post("/api/cart/calculate").session(MockHttpSession()))
            .andExpect(status().isUnauthorized)
        mvc.perform(post("/api/orders"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `public endpoints remain accessible without a session`() {
        mvc.perform(get("/api/showcase"))
            .andExpect(status().isOk)
        mvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent)
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student"}"""))
            .andExpect(status().isOk)
    }

    @Test
    fun `login grants access and logout revokes it`() {
        val result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student"}"""))
            .andExpect(status().isOk)
            .andReturn()
        val session = result.request.getSession(false) as MockHttpSession

        mvc.perform(get("/api/goods").session(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
        mvc.perform(post("/api/orders").session(session))
            .andExpect(status().isOk)
        mvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)
        mvc.perform(get("/api/goods").session(session))
            .andExpect(status().isUnauthorized)
    }
}

@TestComponent
@RestController
class SessionAccessTestController {
    @GetMapping("/api/goods", "/api/goods/filters", "/api/goods/1", "/api/orders", "/api/showcase")
    fun getResource() = mapOf("status" to "ok")

    @PostMapping("/api/cart/calculate", "/api/orders")
    fun postResource() = mapOf("status" to "ok")
}
