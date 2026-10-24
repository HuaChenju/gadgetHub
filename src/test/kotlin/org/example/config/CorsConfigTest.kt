package org.example.config

import org.example.auth.AuthController
import org.example.auth.AuthService
import org.example.error.GlobalExceptionHandler
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuthController::class)
@Import(WebConfig::class, AuthService::class, GlobalExceptionHandler::class)
class CorsConfigTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `frontend origin can preflight and use session with readable error responses`() {
        val preflight = mvc.perform(options("/api/auth/me")
            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,OPTIONS"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type"))
            .andReturn()
        assertNull(preflight.request.getSession(false))

        val login = mvc.perform(post("/api/auth/login")
            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student"}"""))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andReturn()
        val session = login.request.getSession(false) as MockHttpSession

        mvc.perform(get("/api/auth/me").session(session)
            .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(jsonPath("$.login").value("student"))

        mvc.perform(get("/api/auth/me").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
            .andExpect(status().isUnauthorized)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `different origin is rejected before authentication`() {
        mvc.perform(options("/api/auth/login")
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
            .andExpect(status().isForbidden)
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))

        val result = mvc.perform(post("/api/auth/login")
            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student"}"""))
            .andExpect(status().isForbidden)
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .andReturn()
        assertNull(result.request.getSession(false))
    }
}
