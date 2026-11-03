package org.zalando.zally.configuration

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Serves the API definition of this server and the Twintip schema discovery document.
 *
 * Replaces the unmaintained `twintip-spring-web` library, with the same endpoints and CORS headers.
 */
@RestController
class SchemaController(
    @Value("\${twintip.yaml}") schema: Resource,
) {
    private val json = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
    private val yaml = ObjectMapper(YAMLFactory())
    private val node: Map<String, Any> =
        schema.inputStream.use {
            yaml.readValue(it, object : TypeReference<Map<String, Any>>() {})
        }

    @GetMapping("/.well-known/schema-discovery", produces = ["application/json"])
    fun discover() = SchemaDiscovery(schemaUrl = SCHEMA_MAPPING, schemaType = "swagger-2.0")

    @GetMapping(SCHEMA_MAPPING, produces = ["application/json", "*/*"])
    fun jsonSchema(): ResponseEntity<String> = response(json.writeValueAsString(node))

    @GetMapping(SCHEMA_MAPPING, produces = ["application/yaml", "application/x-yaml", "text/yaml"])
    fun yamlSchema(): ResponseEntity<String> = response(yaml.writeValueAsString(node))

    private fun response(body: String): ResponseEntity<String> =
        ResponseEntity
            .ok()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET")
            .header("Access-Control-Max-Age", "3600")
            .header("Access-Control-Allow-Headers", "")
            .body(body)

    companion object {
        const val SCHEMA_MAPPING = "/api"
    }
}
