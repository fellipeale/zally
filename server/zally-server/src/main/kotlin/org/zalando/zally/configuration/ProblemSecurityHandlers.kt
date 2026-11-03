package org.zalando.zally.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler

/**
 * Writes authentication and authorization failures in the same `application/problem+json` shape as every other
 * error of the API (`title`, `status`, `detail`). The bearer token entry point still sets `WWW-Authenticate`.
 */
class ProblemSecurityHandlers(
    private val objectMapper: ObjectMapper,
    private val bearerEntryPoint: AuthenticationEntryPoint,
) : AuthenticationEntryPoint,
    AccessDeniedHandler {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        bearerEntryPoint.commence(request, response, authException)
        write(response, HttpStatus.UNAUTHORIZED, authException.message)
    }

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = write(response, HttpStatus.FORBIDDEN, accessDeniedException.message)

    private fun write(
        response: HttpServletResponse,
        status: HttpStatus,
        detail: String?,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        val body = linkedMapOf<String, Any>("title" to status.reasonPhrase, "status" to status.value())
        detail?.let { body["detail"] = it }
        objectMapper.writeValue(response.outputStream, body)
    }
}
