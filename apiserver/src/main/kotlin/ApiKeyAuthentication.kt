/*
 * Flydroid is a self hosted platform for Android emulation.
 *
 * Copyright (C) 2020-2021 by Frederic-Charles Barthelery.
 *
 * This file is part of Flydroid.
 *
 * Flydroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Flydroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Flydroid.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.geekorum.flydroid.apiserver

import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.auth.*
import io.ktor.request.ApplicationRequest
import io.ktor.request.header
import io.ktor.response.respond

/**
 * Installs ApiKey Authentication mechanism
 */
fun Authentication.Configuration.apiKey(
        name: String? = null,
        configure: ApiKeyAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = ApiKeyAuthenticationProvider(ApiKeyAuthenticationProvider.Configuration(name).apply(configure))
    val header = provider.header
    val authenticate = provider.authenticationFunction

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val credentials = call.request.apiKeyAuthenticationCredentials(header)
        val principal = credentials?.let { authenticate(call, it) }

        val cause = when {
            credentials == null -> AuthenticationFailedCause.NoCredentials
            principal == null -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge(apiKeyAuthenticationChallengeKey, cause) {
                call.respond(UnauthorizedResponse())
                it.complete()
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }

    register(provider)
}

class ApiKeyAuthenticationProvider internal constructor(
        configuration: Configuration
) : AuthenticationProvider(configuration) {

    internal val header = configuration.apiKeyHeader
    internal val authenticationFunction = configuration.authenticationFunction

    /**
     * ApiKey auth configuration
     */
    class Configuration internal constructor(name: String?) : AuthenticationProvider.Configuration(name) {
        internal var authenticationFunction: AuthenticationFunction<ApiKeyCredential> = {
            throw NotImplementedError(
                    "ApiKey auth validate function is not specified. Use apiKey { validate { ... } } to fix."
            )
        }

        var apiKeyHeader = ""

        /**
         * Sets a validation function that will check given [ApiKeyCredential] instance and return [Principal],
         * or null if credential does not correspond to an authenticated principal
         */
        fun validate(body: suspend ApplicationCall.(ApiKeyCredential) -> Principal?) {
            authenticationFunction = body
        }
    }
}

class ApiKeyValidator(
        private val keys: Set<String>
) {
    fun authenticate(credential: ApiKeyCredential): Principal? {
        return when {
            keys.isEmpty() -> NoApiKeyPrincipal
            credential.apiKey in keys -> ApiKeyPrincipal(credential.apiKey)
            else -> null
        }
    }
}

data class ApiKeyCredential(val apiKey: String) : Credential
data class ApiKeyPrincipal(val apiKey: String) : Principal
object NoApiKeyPrincipal : Principal

/**
 * Retrieves ApiKey authentication credentials for this [ApplicationRequest]
 */
fun ApplicationRequest.apiKeyAuthenticationCredentials(headerName: String): ApiKeyCredential? {
    return header(headerName)?.let {
        ApiKeyCredential(it)
    }
}

private val apiKeyAuthenticationChallengeKey: Any = "ApiKey"


