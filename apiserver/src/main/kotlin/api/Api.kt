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
package com.geekorum.flydroid.apiserver.api

import com.geekorum.flydroid.apiserver.log
import com.geekorum.flydroid.apiserver.nomad.HttpClientNomadService
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File

private val httpClient by lazy {
    HttpClient(OkHttp) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(
                    Json {
                        ignoreUnknownKeys = true
                    }
            )
        }
        install(HttpTimeout)
    }
}

fun Route.api() {
    val virtualDeviceController by lazy {
        createVirtualDeviceController(application)
    }

    accept(ContentType.Application.Json) {
        post("/start") {
            val request = call.receiveRequest<StartRequest>()
            log.debug("start request received $request")

            try {
                val response = virtualDeviceController.startVirtualDevice(request)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: UnknownImageException) {
                call.respondText(status = HttpStatusCode.BadRequest, text = "Unknown image. Available images are ${virtualDeviceController.nomadDevices.keys}")
            } catch (e: AllocationException) {
                log.warn("Unable to start device", e)
                call.respondText(status = HttpStatusCode.ServiceUnavailable, text = "${e.message}")
            }
        }

        get("/devices/{deviceId}") {
            val deviceId = call.parameters["deviceId"]!!
            val virtualDevice = virtualDeviceController.findVirtualDevice(deviceId)
            if (virtualDevice != null) {
                call.respond(HttpStatusCode.OK, virtualDevice)
            } else {
                call.respondText(status = HttpStatusCode.NotFound, text ="")
            }
        }

        delete("/devices/{deviceId}") {
            val deviceId = call.parameters["deviceId"]!!
            val virtualDevice = virtualDeviceController.stopVirtualDevice(deviceId)
            if (virtualDevice != null) {
                call.respond(HttpStatusCode.OK, virtualDevice)
            } else {
                call.respondText(status = HttpStatusCode.NoContent, text ="")
            }
        }

        delete("/devices/by-name/{deviceName}") {
            val deviceName = call.parameters["deviceName"]!!
            val virtualDevice = virtualDeviceController.stopVirtualDeviceByName(deviceName)
            if (virtualDevice != null) {
                call.respond(HttpStatusCode.OK, virtualDevice)
            } else {
                call.respondText(status = HttpStatusCode.NoContent, text ="")
            }
        }
    }
}

@OptIn(KtorExperimentalLocationsAPI::class)
@Location("/devices/creating/{deviceId}")
internal data class CreatingDeviceLocation(val deviceId: String)

@OptIn(KtorExperimentalLocationsAPI::class)
@Location("/devices/{deviceId}")
internal data class GetDeviceLocation(val deviceId: String)

/**
 * Route for Api v2.
 * Needs the following application features
 * [Locations], [ContentNegotiation] with json
 */
@OptIn(KtorExperimentalLocationsAPI::class)
fun Route.apiV2(
        virtualDeviceController: VirtualDeviceController = createVirtualDeviceController(application)
) {
    accept(ContentType.Application.Json) {
        post("/devices") {
            val request = call.receiveRequest<StartRequest>()
            log.debug("create request received $request")

            try {
                val deviceId = virtualDeviceController.createVirtualDevice(request)
                val deviceCreatingUrl = call.url(CreatingDeviceLocation(deviceId)) {
                    path( // combine current call path with location path
                            listOf(getParentRoutePath("/devices"),
                                    encodedPath.trim('/'))
                                    .filter { it.isNotEmpty() }
                    )
                }
                call.response.header(HttpHeaders.Location, deviceCreatingUrl)
                call.respondText(status = HttpStatusCode.Accepted, text = deviceCreatingUrl)
            } catch (e: UnknownImageException) {
                call.respondText(status = HttpStatusCode.BadRequest, text = "Unknown image. Available images are ${virtualDeviceController.nomadDevices.keys}")
            }
        }

        get<CreatingDeviceLocation> {
            val deviceId = call.parameters["deviceId"]!!
            val state = virtualDeviceController.getVirtualDeviceState(deviceId)
            val status = when (state) {
                VirtualDeviceController.DeviceState.NOT_FOUND -> HttpStatusCode.NotFound
                // retrofit va essayer de parser cette reponse. ce qui va certainement donner une erreur
                VirtualDeviceController.DeviceState.CREATE_IN_PROGRESS -> HttpStatusCode.Accepted
                VirtualDeviceController.DeviceState.CREATED -> HttpStatusCode.SeeOther
            }
            if (state == VirtualDeviceController.DeviceState.CREATED) {
                val deviceUrl = call.url(GetDeviceLocation(deviceId.encodeURLParameter())) {
                    path( // combine current call path with location path
                            listOf(getParentRoutePath("/devices/creating"),
                                    encodedPath.trim('/'))
                                    .filter { it.isNotEmpty() }
                    )
                }
                call.response.header(HttpHeaders.Location, deviceUrl)
                call.respondText(status = status, text = deviceUrl)
                return@get
            }
            call.respondText(status = status, text = "")
        }

        get<GetDeviceLocation> {
            val deviceId = call.parameters["deviceId"]!!
            val virtualDevice = virtualDeviceController.findVirtualDevice(deviceId)
            if (virtualDevice != null) {
                call.respond(HttpStatusCode.OK, virtualDevice)
            } else {
                call.respondText(status = HttpStatusCode.NotFound, text = "")
            }
        }

        delete("/devices/{deviceId}") {
            val deviceId = call.parameters["deviceId"]!!
            val virtualDevice = virtualDeviceController.stopVirtualDevice(deviceId)
            if (virtualDevice != null) {
                call.respond(HttpStatusCode.OK, virtualDevice)
            } else {
                call.respondText(status = HttpStatusCode.NoContent, text = "")
            }
        }

        delete("/devices/by-name/{deviceName}") {
            val deviceName = call.parameters["deviceName"]!!
            val virtualDevice = virtualDeviceController.stopVirtualDeviceByName(deviceName)
            if (virtualDevice != null) {
                call.respond(HttpStatusCode.OK, virtualDevice)
            } else {
                call.respondText(status = HttpStatusCode.NoContent, text = "")
            }
        }
    }
}

private fun PipelineContext<*, ApplicationCall>.getParentRoutePath(currentPath: String) =
        call.request.uri.substringBefore(currentPath).trim('/')


private fun createVirtualDeviceController(application: Application): VirtualDeviceController {
    val nomadService = run {
        val baseUrl = application.environment.config.property("application.nomadUrl")
                .getString().toHttpUrl()
        val nomadToken = application.environment.config.propertyOrNull("application.nomadToken")
                ?.getString()
        HttpClientNomadService(httpClient, baseUrl, nomadToken)
    }

    val nomadDevices = application.getNomadDevices()
    return VirtualDeviceController(nomadService, nomadDevices)
}

private suspend inline fun <reified T: Any> ApplicationCall.receiveRequest(): T {
    return try {
        receive()
    } catch (e: SerializationException) {
        throw BadRequestException("unable to parse request", e)
    }
}


private fun Application.getNomadDevices(): Map<String, String> {
    val devicesFile = environment.config.propertyOrNull("application.nomadDevices")?.getString()?.let { File(it) }
    val config = ConfigFactory.parseFile(devicesFile)
    return try {
        config.getObject("devices").mapValues { (_, value) ->
            value.unwrapped() as String
        }
    } catch (e: ConfigException) {
        log.warn("No devices configured", e)
        emptyMap()
    }
}

