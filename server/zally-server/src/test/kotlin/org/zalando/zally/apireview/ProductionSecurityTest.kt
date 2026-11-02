package org.zalando.zally.apireview

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.zalando.zally.util.StubTokenInfoServer.NO_SCOPE_TOKEN
import org.zalando.zally.util.StubTokenInfoServer.OTHER_SCOPE_TOKEN
import org.zalando.zally.util.StubTokenInfoServer.UID_TOKEN

/**
 * Pins the access rules of `OAuthConfiguration` exactly as they behave on Spring Boot 2.7.
 *
 * Some of this is surprising (an unauthenticated request is answered with 500, `/api` is denied) and is recorded on
 * purpose: the Spring Security 6 rewrite has to either reproduce it or change it as an announced difference.
 */
class ProductionSecurityTest : ProductionProfileTest() {

    private val unknownReview = "/api-violations/6f1f1b1e-9a38-4c7e-8d0e-111111111111"

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/supported-rules", "/supported-rules/", "/supported-rules?type=MUST",
            "/review-statistics", "/review-statistics/", "/review-statistics?from=2020-01-01"
        ]
    )
    fun `token with uid scope may read the protected resources`(path: String) {
        assertThat(get(path, UID_TOKEN).status).isEqualTo(200)
    }

    @Test
    fun `token with uid scope may post and read api violations`() {
        val created = send("POST", "/api-violations", UID_TOKEN, body = """{"api_definition_string":"{}"}""")
        assertThat(created.status).isEqualTo(200)

        assertThat(get(unknownReview, UID_TOKEN).status).isEqualTo(404)
        assertThat(get("/api-violations/", UID_TOKEN).status).isEqualTo(405)
    }

    @Test
    fun `request without token is rejected`() {
        val response = get("/supported-rules")

        assertThat(response.status).isEqualTo(500)
        assertThat(response.contentType).isEqualTo("application/problem+json")
        assertThat(response.body).isEqualTo(
            """{"title":"Internal Server Error","status":500,"detail":"Full authentication is required to access this resource"}"""
        )
    }

    @ParameterizedTest
    @ValueSource(strings = [OTHER_SCOPE_TOKEN, NO_SCOPE_TOKEN])
    fun `token without uid scope is rejected as unauthorized`(token: String) {
        val response = get("/supported-rules", token)

        assertThat(response.status).isEqualTo(401)
        assertThat(response.header("www-authenticate")).contains("invalid_token", "No 'uid'-scope found in access-token!")
        assertThat(response.contentType).isEqualTo("application/json")
    }

    @Test
    fun `token rejected by the tokeninfo endpoint is unauthorized`() {
        val response = get("/supported-rules", "revoked-token")

        assertThat(response.status).isEqualTo(401)
        assertThat(response.header("www-authenticate")).isEqualTo(
            """Bearer realm="oauth2-resource", error="invalid_token", error_description="invalid_request""""
        )
        assertThat(response.body).isEqualTo("""{"error":"invalid_token","error_description":"invalid_request"}""")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/api", "/.well-known/schema-discovery", "/unknown", "/supported-rules.yaml"])
    fun `everything else is denied even with a valid token`(path: String) {
        val response = get(path, UID_TOKEN)

        assertThat(response.status).isEqualTo(500)
        assertThat(response.contentType).isEqualTo("application/problem+json")
        assertThat(response.body).isEqualTo("""{"title":"Internal Server Error","status":500,"detail":"Access is denied"}""")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/health", "/metrics"])
    fun `actuator paths are not served on the main port`(path: String) {
        // Security lets both through (/health is public, /metrics needs the uid scope), then MVC has no handler for them.
        val response = get(path, UID_TOKEN)

        assertThat(response.status).isEqualTo(404)
        assertThat(response.contentType).isEqualTo("application/json")
    }

    @Test
    fun `options requests are public`() {
        assertThat(send("OPTIONS", "/api-violations").status).isEqualTo(200)
        assertThat(send("OPTIONS", "/api").status).isEqualTo(200)
        // allowed by security, then not found by MVC
        assertThat(send("OPTIONS", "/unknown").status).isEqualTo(404)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/health", "/health/"])
    fun `health on the management port is public`(path: String) {
        val response = managementGet(path)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).isEqualTo("""{"status":"UP"}""")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/metrics", "/metrics/"])
    fun `metrics on the management port require the uid scope`(path: String) {
        assertThat(managementGet(path).status).isEqualTo(500)
        assertThat(managementGet(path, OTHER_SCOPE_TOKEN).status).isEqualTo(401)

        val response = managementGet(path, UID_TOKEN)
        assertThat(response.status).isEqualTo(200)
        assertThat(response.body).contains("\"names\"")
    }

    @ParameterizedTest
    @ValueSource(strings = ["/metrics/jvm.memory.used", "/info", "/env", "/prometheus"])
    fun `other actuator paths are denied`(path: String) {
        assertThat(managementGet(path, UID_TOKEN).status).isEqualTo(500)
    }
}
