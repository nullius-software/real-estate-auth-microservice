package com.nullius_real_estate.auth_microservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AuthMicroserviceApplication

fun main(args: Array<String>) {
	runApplication<AuthMicroserviceApplication>(*args)
}
