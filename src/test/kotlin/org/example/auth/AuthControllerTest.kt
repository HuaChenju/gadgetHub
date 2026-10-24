package org.example.auth

import org.example.error.GlobalExceptionHandler
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(AuthController::class)
@Import(AuthService::class, GlobalExceptionHandler::class)
class AuthControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `logout ends session and subsequent requests are unauthorized`() {
        val loginResult = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student"}"""))
            .andExpect(status().isOk)
            .andReturn()
        val session = loginResult.request.getSession(false) as MockHttpSession

        mvc.perform(post("/api/auth/logout").session(session))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
        assertTrue(session.isInvalid)

        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))

        val repeatedLogout = mvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
            .andReturn()
        assertNull(repeatedLogout.request.getSession(false))
    }

    @Test
    fun `me returns user from login session`() {
        val loginResult = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student"}"""))
            .andExpect(status().isOk)
            .andReturn()
        val session = loginResult.request.getSession(false) as MockHttpSession

        mvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"id":1,"login":"student"}"""))
            .andExpect(jsonPath("$.password").doesNotExist())
    }

    @Test
    fun `me without authentication returns unauthorized and does not create session`() {
        val result = mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(content().json("""{
                "code":"UNAUTHORIZED",
                "message":"Для выполнения действия войдите в систему",
                "fieldErrors":{}
            }"""))
            .andReturn()
        assertNull(result.request.getSession(false))
    }

    @Test
    fun `correct credentials create session and return user without password`() {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":" student ","password":"student"}"""))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"id":1,"login":"student"}"""))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(request().sessionAttribute(AuthController.SESSION_USER, UserResponse(1, "student")))
    }

    @Test
    fun `incorrect credentials return contract error without creating session`() {
        val result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"login":"student","password":"student "}"""))
            .andExpect(status().isUnauthorized)
            .andExpect(content().json("""{
                "code":"INVALID_CREDENTIALS",
                "message":"Такого пользователя нет, возможно неправильный логин или пароль — проверьте данные",
                "fieldErrors":{}
            }"""))
            .andReturn()
        assertNull(result.request.getSession(false))
    }

    @Test
    fun `blank or missing credentials return required field errors`() {
        for (body in listOf("""{"login":"  ","password":""}""", "{}")) {
            val result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.login").value("Заполните обязательное поле"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Заполните обязательное поле"))
                .andReturn()
            assertNull(result.request.getSession(false))
        }
    }
}
