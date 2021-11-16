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

import com.google.common.truth.Truth.assertThat
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlin.test.Test


class ApiV2Test {

    @Test
    fun `test get unknown device returns error`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.findVirtualDevice(any()) } returns null
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Get, "/devices/unknowndevice")) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.NotFound)
            }
        }
    }

    @Test
    fun `test get existing device returns device`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.findVirtualDevice("existing") } returns VirtualDevice(
                id = "exisiting",
                ip = "127.0.0.1",
                adbPort = 42,
                consolePort = 4241,
                grpcPort = 4242
        )
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Get, "/devices/existing")) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.OK)
                assertThat(response.contentType()).isEqualTo(ContentType.Application.Json.withCharset(Charsets.UTF_8))
            }
        }
    }

    @Test
    fun `test when create device with unknown image returns error`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.createVirtualDevice(any()) } throws UnknownImageException()
        every { virtualDeviceController getProperty "nomadDevices" } returns emptyMap<String, String>()
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Post, "/devices") {
                setBody("""
                    {
                        "image": "unknown",
                        "name" : "my device",
                        "adbkey": "adb key"
                    }
                    """.trimIndent())
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.BadRequest)
            }
        }
    }

    @Test
    fun `test when create device with valid image returns creating device url`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.createVirtualDevice(any()) } returns "creatingDeviceId"
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Post, "/devices") {
                setBody("""
                    {
                        "image": "validImage",
                        "name" : "my device",
                        "adbkey": "adb key"
                    }
                    """.trimIndent())
                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            }) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.Accepted)
                val expectedCreatingDeviceUrl = "http://localhost/devices/creating/creatingDeviceId"
                val location = response.headers[HttpHeaders.Location]
                assertThat(location).isEqualTo(expectedCreatingDeviceUrl)
                assertThat(response.content).isEqualTo(expectedCreatingDeviceUrl)
            }
        }
    }


    @Test
    fun `test get creating device status for unknown device returns error`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.getVirtualDeviceState(any()) } returns VirtualDeviceController.DeviceState.NOT_FOUND
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Get, "/devices/creating/unknown")) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.NotFound)
            }
        }
    }

    @Test
    fun `test get creating device status for device still creating returns accept`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.getVirtualDeviceState(any()) } returns VirtualDeviceController.DeviceState.CREATE_IN_PROGRESS
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Get, "/devices/creating/existing")) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.Accepted)
            }
        }
    }

    @Test
    fun `test get creating device status for created device returns new url`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.getVirtualDeviceState(any()) } returns VirtualDeviceController.DeviceState.CREATED
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Get, "/devices/creating/existing")) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.SeeOther)
                val expectedDeviceUrl = "http://localhost/devices/existing"
                val location = response.headers[HttpHeaders.Location]
                assertThat(location).isEqualTo(expectedDeviceUrl)
                assertThat(response.content).isEqualTo(expectedDeviceUrl)
            }
        }
    }

    @Test
    fun `test delete unknown device by id returns no content`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.stopVirtualDevice(any()) } returns null
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Delete, "/devices/unknown")) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.NoContent)
            }
        }
    }

    @Test
    fun `test delete device by id returns deleted device`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.stopVirtualDevice(any()) } returns VirtualDevice(
                id = "existing",
                ip = "127.0.0.1",
                adbPort = 42,
                consolePort = 4241,
                grpcPort = 4242
        )
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Delete, "/devices/existing")) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.OK)
                assertThat(response.contentType()).isEqualTo(ContentType.Application.Json.withCharset(Charsets.UTF_8))
            }
        }
    }

    @Test
    fun `test delete unknown device by name returns no content`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.stopVirtualDeviceByName(any()) } returns null
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Delete, "/devices/by-name/unknown")) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.NoContent)
            }
        }
    }

    @Test
    fun `test delete device by name returns deleted device`() {
        val virtualDeviceController: VirtualDeviceController = mockk()
        coEvery { virtualDeviceController.stopVirtualDeviceByName(any()) } returns VirtualDevice(
                id = "existing",
                ip = "127.0.0.1",
                adbPort = 42,
                consolePort = 4241,
                grpcPort = 4242
        )
        withTestApplication({
            testApiV2Module(virtualDeviceController)
        }) {
            with(handleRequest(HttpMethod.Delete, "/devices/by-name/existing")) {
                assertThat(response.status()).isEqualTo(HttpStatusCode.OK)
                assertThat(response.contentType()).isEqualTo(ContentType.Application.Json.withCharset(Charsets.UTF_8))
            }
        }
    }

}


fun Application.testApiV2Module(virtualDeviceController: VirtualDeviceController = mockk()) {
    install(Locations)
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
        })
    }
    routing {
        apiV2(virtualDeviceController)
    }
}
