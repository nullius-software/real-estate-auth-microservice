package com.nullius_real_estate.auth_microservice.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@RestController
class AuthController(
    private val webClient: WebClient,
    @Value("\${user-service.url:http://localhost:8090/api/user}") private val userServiceUrl: String,
    @Value("\${keycloak.admin.url}")
    private val keycloakAdminUrl: String,
    @Value("\${keycloak.admin.realm}")
    private val keycloakRealm: String,
    @Value("\${keycloak.admin.client-id}")
    private val keycloakClientId: String,
    @Value("\${keycloak.admin.client-secret}")
    private val keycloakClientSecret: String,
    @Value("\${spring.security.oauth2.client.registration.keycloak.client-id}")
    private val authClientId: String,
    @Value("\${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private val authClientSecret: String
) {

    @PostMapping("/login")
    fun login(@RequestBody loginRequest: LoginRequest): Mono<Map<String, Any>> {
        return webClient.post()
            .uri("$keycloakAdminUrl/realms/$keycloakRealm/protocol/openid-connect/token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .bodyValue(
                "grant_type=password" +
                        "&client_id=$authClientId" +
                        "&client_secret=$authClientSecret" +
                        "&username=${loginRequest.email}" +
                        "&password=${loginRequest.password}"
            )
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<Map<String, Any>>() {})
            .map { response ->
                mapOf(
                    "access_token" to (response["access_token"] ?: ""),
                    "refresh_token" to (response["refresh_token"] ?: ""),
                    "expires_in" to (response["expires_in"] ?: 0),
                    "token_type" to (response["token_type"] ?: "Bearer")
                )
            }
            .onErrorResume { error ->
                Mono.just(mapOf("error" to "Login failed: ${error.message}"))
            }
    }

    @GetMapping("/user")
    fun getUser(): Mono<Map<String, String>> {
        return ReactiveSecurityContextHolder.getContext()
            .map { context ->
                val jwt = context.authentication.principal as Jwt
                mapOf(
                    "externalId" to jwt.subject,
                    "email" to (jwt.getClaimAsString("email") ?: "unknown"),
                    "token" to jwt.tokenValue
                )
            }
            .switchIfEmpty(Mono.just(mapOf("error" to "User not authenticated")))
    }

    @PostMapping("/register")
    fun register(@RequestBody userRequest: UserRequest): Mono<Map<String, Any>> {
        return createUserInKeycloak(userRequest)
            .flatMap { (keycloakUserId, accessToken) ->
                webClient.post()
                    .uri(userServiceUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .bodyValue(
                        mapOf(
                            "externalId" to keycloakUserId,
                            "email" to userRequest.email,
                            "firstName" to userRequest.firstName,
                            "lastName" to userRequest.lastName
                        )
                    )
                    .retrieve()
                    .bodyToMono(object : ParameterizedTypeReference<Map<String, Any>>() {})
                    .map { mapOf("message" to "User registered", "data" to it) }
            }
            .onErrorResume { error ->
                Mono.just(mapOf("error" to "Failed to register user: ${error.message}"))
            }
    }

    private fun createUserInKeycloak(userRequest: UserRequest): Mono<Pair<String, String>> {
        return webClient.post()
            .uri("$keycloakAdminUrl/realms/$keycloakRealm/protocol/openid-connect/token")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .bodyValue(
                "grant_type=client_credentials" +
                        "&client_id=$keycloakClientId" +
                        "&client_secret=$keycloakClientSecret"
            )
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<Map<String, Any>>() {})
            .flatMap { tokenResponse ->
                val accessToken = tokenResponse["access_token"] as String
                webClient.post()
                    .uri("$keycloakAdminUrl/admin/realms/$keycloakRealm/users")
                    .header("Authorization", "Bearer $accessToken")
                    .header("Content-Type", "application/json")
                    .bodyValue(
                        mapOf(
                            "email" to userRequest.email,
                            "enabled" to true,
                            "credentials" to listOf(
                                mapOf(
                                    "type" to "password",
                                    "value" to userRequest.password,
                                    "temporary" to false
                                )
                            )
                        )
                    )
                    .retrieve()
                    .toBodilessEntity()
                    .map { response ->
                        val keycloackUserId = response.headers["Location"]?.first()?.split("/")?.last() ?: "unknown"
                        Pair(keycloackUserId, accessToken)
                    }
            }
    }
}

data class UserRequest(val email: String, val password: String, val firstName: String, val lastName: String)
data class LoginRequest(val email: String, val password: String)