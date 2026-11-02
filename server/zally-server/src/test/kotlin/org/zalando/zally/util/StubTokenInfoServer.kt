package org.zalando.zally.util

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * Minimal OAuth2 tokeninfo endpoint for tests running with the `production` profile.
 *
 * The `uid` field matters: the stups library adds the `uid` scope to every token that carries one, so the tokens
 * meant to lack that scope must not have it. The bearer token value selects the answer: [UID_TOKEN] carries the `uid` scope, [OTHER_SCOPE_TOKEN] only an unrelated
 * one, [NO_SCOPE_TOKEN] none at all. Any other token is rejected with 400, like the real endpoint does.
 */
object StubTokenInfoServer {

    const val UID_TOKEN = "uid-token"
    const val OTHER_SCOPE_TOKEN = "other-scope-token"
    const val NO_SCOPE_TOKEN = "no-scope-token"

    private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/tokeninfo") { exchange ->
            val (status, body) = when (exchange.requestHeaders.getFirst("Authorization")) {
                "Bearer $UID_TOKEN" -> 200 to """{"access_token":"$UID_TOKEN","uid":"user-1","scope":["uid"],"expires_in":3600}"""
                "Bearer $OTHER_SCOPE_TOKEN" -> 200 to """{"access_token":"$OTHER_SCOPE_TOKEN","scope":["other"],"expires_in":3600}"""
                "Bearer $NO_SCOPE_TOKEN" -> 200 to """{"access_token":"$NO_SCOPE_TOKEN","expires_in":3600}"""
                else -> 400 to """{"error":"invalid_request"}"""
            }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val url: String get() = "http://localhost:${server.address.port}/tokeninfo"
}
