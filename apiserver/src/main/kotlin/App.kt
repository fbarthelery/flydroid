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

import com.geekorum.flydroid.apiserver.api.api
import com.geekorum.flydroid.apiserver.api.apiV2
import com.geekorum.flydroid.apiserver.nomad.NomadServiceException
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.config.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.ContentTransformationException
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.event.Level
import java.io.File


fun Application.appModule() {
    install(WebSockets) {
        pingPeriodMillis = 5000
    }
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
        })
    }
    install(CallLogging) {
        level = Level.DEBUG
    }
    install(StatusPages) {
        exception<ContentTransformationException> {
            call.respondText("Invalid request", status = HttpStatusCode.BadRequest)
        }
        exception<NomadServiceException> {
            log.warn("Nomad service is unavailable", it)
            call.respondText("Nomad service is unavailable", status = HttpStatusCode.ServiceUnavailable)
        }
    }
    install(Locations)

    val apiKeys = getApiKeys()
    log.info("Authentication required ? ${apiKeys.isNotEmpty()}")
    install(Authentication) {
        apiKey("api") {
            apiKeyHeader = "X-FLYDROID-KEY"
            val validator = ApiKeyValidator(apiKeys)
            validate { credentials ->
                validator.authenticate(credentials)
            }
        }
    }

    routing {
        get("/") {
            call.respondText("Flydroid Api server")
        }

        authenticate("api", optional = apiKeys.isEmpty()) {
            api()
            wstunnel()
            route("/v2") {
                apiV2()
                wstunnel()
            }
        }
    }
}


private fun Application.getApiKeys(): Set<String> {
    val usersMapFile = environment.config.propertyOrNull("application.apiKeys")?.getString()?.let { File(it) }
    val config = ConfigFactory.parseFile(usersMapFile)
    return config.tryGetStringList("keys")?.toSet() ?: emptySet()
}

internal val PipelineContext<*, ApplicationCall>.log: Logger get() = call.application.log
internal val WebSocketServerSession.log: Logger get() = call.application.log

fun main(args: Array<String>) {
    runBlocking {
        embeddedServer(Netty,
                commandLineEnvironment(args)
        ).start(wait = true)
    }
}
