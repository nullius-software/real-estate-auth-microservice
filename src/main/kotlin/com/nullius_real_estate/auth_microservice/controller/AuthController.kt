package com.nullius_real_estate.auth_microservice.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
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
    fun login(@RequestBody loginRequest: LoginRequest): Mono<ResponseEntity<Map<String, Any>>> {

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
                ResponseEntity.ok(
                    mapOf(
                        "accessToken" to (response["access_token"] ?: ""),
                        "refreshToken" to (response["refresh_token"] ?: ""),
                        "expiresIn" to (response["expires_in"] ?: 0),
                        "tokenType" to (response["token_type"] ?: "Bearer")
                    )
                )
            }
            .onErrorResume(WebClientResponseException::class.java) { ex ->
                Mono.just(
                    ResponseEntity.status(ex.statusCode.value())
                        .body(mapOf("message" to ex.message))
                )
            }
    }

    @GetMapping("/user/{keycloakUserId}/is-verified")
    fun isUserVerified(@PathVariable keycloakUserId: String): Mono<ResponseEntity<Boolean>> {
        return getAdminToken().flatMap { accessToken ->
            webClient.get()
                .uri("$keycloakAdminUrl/admin/realms/$keycloakRealm/users/$keycloakUserId")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<Map<String, Any>>() {})
                .map { user ->
                    ResponseEntity.ok(
                        user["emailVerified"] as? Boolean ?: false
                    )
                }
                .onErrorResume(WebClientResponseException::class.java) { ex ->
                    Mono.just(
                        ResponseEntity.status(ex.statusCode.value())
                            .body(false)
                    )
                }
        }
    }

    @PostMapping("/user/{keycloakUserId}/resend-verification-email")
    fun resendVerificationEmail(@PathVariable keycloakUserId: String): Mono<ResponseEntity<Map<String, String>>> {
        return sendVerificationEmail(keycloakUserId)
    }

    @GetMapping("/user")
    fun getUser(): Mono<ResponseEntity<Map<String, String>>> {
        return ReactiveSecurityContextHolder.getContext()
            .map { context ->
                val jwt = context.authentication.principal as Jwt
                ResponseEntity.ok(
                    mapOf(
                        "externalId" to jwt.subject,
                        "email" to (jwt.getClaimAsString("email") ?: "unknown"),
                        "token" to jwt.tokenValue
                    )
                )
            }
            .switchIfEmpty(Mono.error(RuntimeException("User not authenticated")))
            .onErrorResume(WebClientResponseException::class.java) { ex ->
                Mono.just(
                    ResponseEntity.status(ex.statusCode.value())
                        .body(mapOf("message" to ex.message))
                )
            }
    }

    @PostMapping("/register")
    fun register(@RequestBody userRequest: UserRequest): Mono<ResponseEntity<Map<String, Any>>> {
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
                    .flatMap {
                        sendVerificationEmail(keycloakUserId).thenReturn(
                            ResponseEntity.ok(mapOf("message" to "User registered", "data" to it))
                        )
                    }
                    .onErrorResume(WebClientResponseException::class.java) { ex ->
                        deleteUserInKeycloak(keycloakUserId)
                            .flatMap {
                                Mono.just(
                                    ResponseEntity.status(ex.statusCode.value())
                                        .body(mapOf("message" to ex.message))
                                )
                            }
                    }

            }
            .onErrorResume(WebClientResponseException::class.java) { ex ->
                Mono.just(
                    ResponseEntity.status(ex.statusCode.value())
                        .body(mapOf("message" to ex.message))
                )
            }
    }

    private fun sendVerificationEmail(keycloakUserId: String): Mono<ResponseEntity<Map<String, String>>> {
        return getAdminToken().flatMap { accessToken ->
            webClient.put()
                .uri("$keycloakAdminUrl/admin/realms/$keycloakRealm/users/$keycloakUserId/send-verify-email")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .toBodilessEntity()
                .map { ResponseEntity.ok(mapOf("message" to "Verification email sent")) }
                .onErrorResume(WebClientResponseException::class.java) { ex ->
                    Mono.just(
                        ResponseEntity.status(ex.statusCode.value())
                            .body(mapOf("message" to ex.message))
                    )
                }
        }
    }

    private fun getAdminToken(): Mono<String> {
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
            .map { it["access_token"] as String }
    }

    private fun deleteUserInKeycloak(userId: String): Mono<Map<String, String>> {
        return getAdminToken().flatMap { accessToken ->
            webClient.delete()
                .uri("$keycloakAdminUrl/admin/realms/$keycloakRealm/users/$userId")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .toBodilessEntity()
                .map { mapOf("message" to "User deleted") }
        }
    }

    private fun createUserInKeycloak(userRequest: UserRequest): Mono<Pair<String, String>> {
        return getAdminToken().flatMap { accessToken ->
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