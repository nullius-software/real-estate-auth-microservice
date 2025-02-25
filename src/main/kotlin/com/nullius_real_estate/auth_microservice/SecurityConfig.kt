package com.nullius_real_estate.auth_microservice

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf { it.disable() }
            .authorizeExchange {
                it.pathMatchers(
                    "/login",
                    "/register",

//                    Docs
                    "/webjars/**",
                    "/swagger-ui/**",
                    "/v3/**",
                ).permitAll()
                it.anyExchange().authenticated()
            }
            .oauth2Client { }
            .oauth2ResourceServer {
                it.jwt { }
            }
        return http.build()
    }
}